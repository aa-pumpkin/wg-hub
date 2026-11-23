package de.wghub.app.ui.members

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.wghub.app.LocalApp
import de.wghub.app.data.InvitationsResponse
import de.wghub.app.data.LeaderboardResponse
import de.wghub.app.data.Member
import de.wghub.app.data.MemberStats
import de.wghub.app.data.json
import de.wghub.app.i18n.LocalTexts
import de.wghub.app.ui.Avatar
import de.wghub.app.ui.Card
import de.wghub.app.ui.CardList
import de.wghub.app.ui.ConfirmDialog
import de.wghub.app.ui.Hint
import de.wghub.app.ui.LinkRow
import de.wghub.app.ui.ListRow
import de.wghub.app.ui.Loader
import de.wghub.app.ui.LocalHousehold
import de.wghub.app.ui.MenuItem
import de.wghub.app.ui.RowMenu
import de.wghub.app.ui.RowMeta
import de.wghub.app.ui.ScreenColumn
import de.wghub.app.ui.Select
import de.wghub.app.ui.Wg
import de.wghub.app.ui.attempt
import de.wghub.app.ui.summary
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private data class Confirm(val title: String, val text: String?, val label: String, val danger: Boolean, val action: suspend () -> Unit)

@Composable
private fun Badge(text: String, accent: Boolean = false, snow: Boolean = false) {
    Row(
        Modifier.background(if (accent) Wg.colors.accentSoft else Wg.colors.subtle, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        if (snow) Icon(Icons.Default.AcUnit, null, Modifier.size(11.dp), tint = Wg.colors.muted)
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = if (accent) Wg.colors.accentText else Wg.colors.muted)
    }
}

/** Mirrors frontend/src/views/MembersView.vue. */
@Suppress("DEPRECATION")
@Composable
fun MembersScreen() {
    val app = LocalApp.current
    val household = LocalHousehold.current
    val t = LocalTexts.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val base = "/households/${household.id}"
    val stats = remember(household.id) { Loader(app, "$base/members/stats", MemberStats.serializer()) }
    val levels = remember(household.id) { Loader(app, "$base/leaderboard", LeaderboardResponse.serializer()) }
    val invitations = remember(household.id) { Loader(app, "$base/invitations", InvitationsResponse.serializer()) }
    var validity by remember { mutableStateOf("7") }
    var confirm by remember { mutableStateOf<Confirm?>(null) }
    var resetLink by remember { mutableStateOf<Pair<String, String>?>(null) }
    val server = app.repo.settings.serverUrl.trimEnd('/')

    suspend fun load() {
        stats.load()
        levels.load()
        if (household.isAdmin) invitations.load()
    }
    LaunchedEffect(household.id, household.refreshTick, household.isAdmin) { load() }

    fun run(block: suspend () -> Unit) = scope.launch {
        app.attempt(block)
        app.refreshHousehold()
        load()
    }

    fun copy(text: String) {
        clipboard.setText(AnnotatedString(text))
        app.onMessage(t("members.copied"))
    }

    fun menuFor(member: Member): List<MenuItem> {
        val freezeItem = if (member.frozen) {
            MenuItem(t("members.unfreeze")) {
                run {
                    app.repo.call("POST", "$base/members/${member.id}/freeze", buildJsonObject { put("frozen", false) }.toString())
                    app.onMessage(t("members.unfrozen", "name" to member.displayName))
                }
            }
        } else {
            MenuItem(t("members.freeze")) {
                confirm = Confirm(t("members.freezeConfirm", "name" to member.displayName), t("members.freezeText"), t("members.freeze"), false) {
                    app.repo.call("POST", "$base/members/${member.id}/freeze", buildJsonObject { put("frozen", true) }.toString())
                }
            }
        }
        // Going away is everyone's own call; doing it for someone else takes an admin.
        if (!household.isAdmin) return if (member.id == app.user?.id) listOf(freezeItem) else emptyList()
        return buildList {
            add(freezeItem)
            val nextRole = if (member.role == "ADMIN") "MEMBER" else "ADMIN"
            add(MenuItem(t(if (member.role == "ADMIN") "members.makeMember" else "members.makeAdmin")) {
                run { app.repo.call("PATCH", "$base/members/${member.id}", buildJsonObject { put("role", nextRole) }.toString()) }
            })
            if (member.id != app.user?.id) {
                add(MenuItem(t("members.passwordReset")) {
                    scope.launch {
                        app.attempt {
                            val token = json.parseToJsonElement(app.repo.call("POST", "$base/members/${member.id}/password-reset"))
                                .jsonObject["token"]!!.jsonPrimitive.content
                            resetLink = member.displayName to "$server/reset/$token"
                        }
                    }
                })
                add(MenuItem(t("members.remove"), danger = true) {
                    confirm = Confirm(t("members.removeConfirm", "name" to member.displayName), t("members.removeText"), t("members.remove"), true) {
                        app.repo.call("DELETE", "$base/members/${member.id}")
                        app.onMessage(t("members.removed", "name" to member.displayName))
                    }
                })
            }
        }
    }

    ScreenColumn(onRefresh = ::load) {
        val levelOf = levels.value?.members?.associate { it.user.id to it.level }.orEmpty()
        CardList(household.members) { member ->
            ListRow {
                Avatar(member.summary(), 40.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            member.displayName + if (member.id == app.user?.id) " (${t("common.you")})" else "",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        levelOf[member.id]?.let { Badge(t("points.levelShort", "level" to it)) }
                        if (member.frozen) Badge(t("members.frozen"), snow = true)
                        if (member.role == "ADMIN") Badge(t("members.admin"), accent = true)
                    }
                    RowMeta(member.username)
                    val s = stats.value
                    Text(
                        "${t("members.openTasks", "count" to (s?.open?.get(member.id) ?: 0))} · " +
                            "${t("members.doneTasks", "count" to (s?.completed?.get(member.id) ?: 0))} · " +
                            t("members.joined", "date" to household.format.dateMedium(member.joinedAt)),
                        fontSize = 13.sp,
                        color = Wg.colors.muted,
                    )
                }
                RowMenu(menuFor(member))
            }
        }

        if (household.isAdmin) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.PersonAdd, null, Modifier.size(18.dp))
                    Text(t("members.inviteTitle"), fontWeight = FontWeight.Medium)
                }
                Hint(t("members.inviteText"))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Select(
                        validity,
                        listOf("1" to t("members.valid1"), "7" to t("members.valid7"), "30" to t("members.valid30"), "never" to t("members.validForever")),
                        t("members.validity"),
                        Modifier.weight(1f),
                    ) { validity = it }
                    Button(onClick = {
                        run {
                            app.repo.call("POST", "$base/invitations", buildJsonObject {
                                put("expiresInDays", validity.toIntOrNull()?.let(::JsonPrimitive) ?: JsonNull)
                            }.toString())
                        }
                    }) { Text(t("members.newLink")) }
                }
                val links = invitations.value?.invitations.orEmpty()
                if (links.isEmpty()) Hint(t("members.noLinks"))
                else Card {
                    links.forEachIndexed { index, invitation ->
                        if (index > 0) HorizontalDivider(color = Wg.colors.line)
                        val url = "$server/invite/${invitation.code}"
                        Column(Modifier.padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(url, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                IconButton(onClick = { copy(url) }) { Icon(Icons.Default.ContentCopy, t("members.copy"), tint = Wg.colors.muted) }
                                IconButton(onClick = {
                                    confirm = Confirm(t("members.revokeConfirm"), t("members.revokeText"), t("members.revoke"), true) {
                                        app.repo.call("DELETE", "$base/invitations/${invitation.id}")
                                    }
                                }) { Icon(Icons.Default.Delete, t("members.revoke"), tint = Wg.colors.muted) }
                            }
                            Hint(
                                (invitation.expiresAt?.let { t("members.expires", "date" to household.format.dateMedium(it)) } ?: t("members.neverExpires")) +
                                    " · " + t("members.used", "count" to invitation.useCount),
                            )
                        }
                    }
                }
            }
        }

        Card {
            LinkRow(t("members.leave"), Icons.AutoMirrored.Filled.Logout, danger = true) {
                confirm = Confirm(
                    t("members.leaveConfirm", "name" to household.detail.household.name),
                    t("members.leaveText"),
                    t("members.leave"),
                    true,
                ) {
                    app.repo.call("POST", "$base/leave")
                    app.onMessage(t("members.left"))
                    app.leftHousehold()
                }
            }
        }
    }

    confirm?.let { c -> ConfirmDialog(c.title, c.text, c.label, c.danger, onDismiss = { confirm = null }) { run(c.action) } }
    resetLink?.let { (name, url) ->
        AlertDialog(
            onDismissRequest = { resetLink = null },
            title = { Text(t("members.resetTitle", "name" to name)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Hint(t("members.resetText"))
                    Text(url, fontSize = 13.sp)
                }
            },
            confirmButton = { TextButton(onClick = { copy(url) }) { Text(t("members.copy")) } },
            dismissButton = { TextButton(onClick = { resetLink = null }) { Text(t("common.close")) } },
        )
    }
}
