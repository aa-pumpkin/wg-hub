package de.wghub.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.wghub.app.LocalApp
import de.wghub.app.data.MemberStats
import de.wghub.app.data.UserResponse
import de.wghub.app.data.json
import de.wghub.app.i18n.LocalTexts
import de.wghub.app.i18n.centsToInput
import de.wghub.app.i18n.parseEurosToCents
import de.wghub.app.ui.Card
import de.wghub.app.ui.ConfirmDialog
import de.wghub.app.ui.Field
import de.wghub.app.ui.Hint
import de.wghub.app.ui.Loader
import de.wghub.app.ui.LocalHousehold
import de.wghub.app.ui.ScreenColumn
import de.wghub.app.ui.Segmented
import de.wghub.app.ui.Select
import de.wghub.app.ui.Wg
import de.wghub.app.ui.attempt
import de.wghub.app.ui.fieldError
import de.wghub.app.ui.submit
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private val ICONS = listOf("🏠", "🌻", "🌿", "🐈", "☕", "🍕", "🎸", "🌈", "🚲", "🧡", "🌙", "🐝")
private val COLORS = listOf("sage", "sky", "rose", "amber", "lilac", "teal")

@Composable
private fun SaveButton(busy: Boolean, label: String, primary: Boolean = true, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        if (primary) Button(onClick = onClick, enabled = !busy) { Text(label) }
        else OutlinedButton(onClick = onClick, enabled = !busy) { Text(label) }
    }
}

/** Mirrors frontend/src/views/SettingsView.vue. */
@Composable
fun SettingsScreen() {
    val app = LocalApp.current
    val household = LocalHousehold.current
    val t = LocalTexts.current
    val scope = rememberCoroutineScope()
    val current = household.detail.household
    val admin = household.isAdmin

    var name by remember(current) { mutableStateOf(current.name) }
    var address by remember(current) { mutableStateOf(current.address ?: "") }
    var icon by remember(current) { mutableStateOf(current.icon) }
    var timezone by remember(current) { mutableStateOf(current.timezone) }
    var beerPrice by remember(current) { mutableStateOf(centsToInput(current.beerPriceCents, t.locale)) }
    var dishwasherOn by remember(current) { mutableStateOf(current.dishwasherTarget != null) }
    var dishwasherTarget by remember(current) { mutableStateOf((current.dishwasherTarget ?: 20).toString()) }
    var errors by remember { mutableStateOf(emptyMap<String, String>()) }
    var busy by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val zones = remember { (TimeZone.availableZoneIds + current.timezone).sorted().map { it to it.replace('_', ' ') } }

    fun save() {
        val cents = parseEurosToCents(beerPrice)
        if (cents == null || cents <= 0) {
            errors = mapOf("beerPriceCents" to "invalid_amount"); return
        }
        busy = true
        scope.launch {
            val body = buildJsonObject {
                put("name", name); put("address", address); put("icon", icon); put("timezone", timezone)
                put("beerPriceCents", cents)
                put("dishwasherTarget", if (dishwasherOn) dishwasherTarget.toIntOrNull()?.let(::JsonPrimitive) ?: JsonPrimitive(-1) else JsonNull)
            }.toString()
            errors = app.submit { app.repo.call("PATCH", "/households/${household.id}", body) }
            busy = false
            if (errors.isEmpty()) {
                app.onMessage(t("settings.saved"))
                app.refreshHousehold()
            }
        }
    }

    ScreenColumn(onRefresh = { app.refreshHousehold() }) {
        if (!admin) {
            Text(
                t("settings.adminOnly"),
                fontSize = 14.sp,
                color = Wg.colors.muted,
                modifier = Modifier.fillMaxWidth().background(Wg.colors.subtle, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        Card {
            Column(Modifier.padding(20.dp).alpha(if (admin) 1f else 0.6f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Field(name, { if (admin) name = it }, t("settings.name"), t.fieldError(errors["name"]))
                Field(address, { if (admin) address = it }, t("settings.address"), t.fieldError(errors["address"]), optional = true)
                Column {
                    Text(t("settings.icon"), fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ICONS.forEach { option ->
                            Box(
                                Modifier.size(44.dp)
                                    .background(if (option == icon) Wg.colors.accentSoft else Wg.colors.surface, RoundedCornerShape(10.dp))
                                    .border(1.dp, if (option == icon) Wg.colors.accent else Wg.colors.line, RoundedCornerShape(10.dp))
                                    .clickable(enabled = admin) { icon = option },
                                contentAlignment = Alignment.Center,
                            ) { Text(option, fontSize = 20.sp) }
                        }
                    }
                }
                Column {
                    if (admin) Select(timezone, zones, t("settings.timezone")) { timezone = it }
                    else Field(timezone, {}, t("settings.timezone"))
                    Hint(t("settings.timezoneHint"), Modifier.padding(top = 4.dp))
                }
                Column {
                    Field(beerPrice, { if (admin) beerPrice = it }, t("settings.beerPrice"), t.fieldError(errors["beerPriceCents"]), number = true)
                    Hint(t("settings.beerPriceHint"))
                }
                Column {
                    Row(Modifier.fillMaxWidth().clickable(enabled = admin) { dishwasherOn = !dishwasherOn }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = dishwasherOn, onCheckedChange = null, modifier = Modifier.padding(end = 10.dp, top = 8.dp, bottom = 8.dp))
                        Text(t("settings.dishwasherOn"), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                    Hint(t("settings.dishwasherHint"))
                }
                if (dishwasherOn) {
                    Field(dishwasherTarget, { if (admin) dishwasherTarget = it.filter(Char::isDigit) }, t("settings.dishwasherTarget"), t.fieldError(errors["dishwasherTarget"]), number = true)
                }
                if (admin) SaveButton(busy, t("common.save"), onClick = ::save)
            }
        }
        if (admin) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(t("settings.dangerZone"), fontWeight = FontWeight.Medium, color = Wg.colors.danger)
                Hint(t("settings.deleteWgText"))
                Button(
                    onClick = { deleting = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Wg.colors.danger, contentColor = Wg.colors.surface),
                ) { Text(t("settings.deleteWg")) }
            }
        }
    }

    if (deleting) {
        ConfirmDialog(t("settings.deleteConfirm", "name" to current.name), t("settings.deleteWgText"), t("settings.deleteWg"), onDismiss = { deleting = false }) {
            scope.launch {
                if (app.attempt { app.repo.call("DELETE", "/households/${household.id}") }) {
                    app.onMessage(t("settings.deleted"))
                    app.leftHousehold()
                }
            }
        }
    }
}

/** Mirrors frontend/src/views/ProfileView.vue. */
@Composable
fun ProfileScreen() {
    val app = LocalApp.current
    val household = LocalHousehold.current
    val t = LocalTexts.current
    val scope = rememberCoroutineScope()
    val user = app.user ?: return
    val stats = remember(household.id) { Loader(app, "/households/${household.id}/members/stats", MemberStats.serializer()) }
    LaunchedEffect(stats, household.refreshTick) { stats.load() }

    var displayName by remember { mutableStateOf(user.displayName) }
    var username by remember { mutableStateOf(user.username) }
    var color by remember { mutableStateOf(user.avatarColor) }
    var profileErrors by remember { mutableStateOf(emptyMap<String, String>()) }
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var passwordErrors by remember { mutableStateOf(emptyMap<String, String>()) }
    var busy by remember { mutableStateOf(false) }

    ScreenColumn(onRefresh = { stats.load() }) {
        Card {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(t("profile.account"), fontWeight = FontWeight.Medium)
                Field(displayName, { displayName = it }, t("profile.name"), t.fieldError(profileErrors["displayName"]))
                Column {
                    Field(username, { username = it }, t("auth.username"), t.fieldError(profileErrors["username"]))
                    Hint(t("auth.usernameHint"))
                }
                Column {
                    Text(t("profile.color"), fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        COLORS.forEach { option ->
                            Box(
                                Modifier.size(44.dp)
                                    .border(if (option == color) 2.dp else 0.dp, Wg.colors.accent, CircleShape)
                                    .padding(3.dp)
                                    .background(Wg.colors.avatars.getValue(option), CircleShape)
                                    .clickable { color = option },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (option == color) Icon(Icons.Default.Check, t("profile.colors.$option"), Modifier.size(18.dp), tint = Wg.colors.avatarFg)
                            }
                        }
                    }
                }
                SaveButton(busy, t("common.save")) {
                    busy = true
                    scope.launch {
                        profileErrors = app.submit {
                            val body = buildJsonObject { put("displayName", displayName); put("username", username); put("avatarColor", color) }.toString()
                            app.applyUser(json.decodeFromString(UserResponse.serializer(), app.repo.call("PATCH", "/me", body)).user)
                            app.refreshHousehold()
                        }
                        busy = false
                        if (profileErrors.isEmpty()) app.onMessage(t("profile.saved"))
                    }
                }
            }
        }

        Card {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(t("profile.stats"), fontWeight = FontWeight.Medium)
                Hint(
                    "${t("members.openTasks", "count" to (stats.value?.open?.get(user.id) ?: 0))} · " +
                        t("members.doneTasks", "count" to (stats.value?.completed?.get(user.id) ?: 0)),
                )
            }
        }

        Card {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(t("profile.appearance"), fontWeight = FontWeight.Medium)
                Text(t("profile.theme"), fontSize = 14.sp)
                Segmented(
                    listOf("system" to t("profile.themeSystem"), "light" to t("profile.themeLight"), "dark" to t("profile.themeDark")),
                    app.theme,
                    Modifier.fillMaxWidth(),
                ) { app.chooseTheme(it) }
                Text(t("profile.language"), fontSize = 14.sp)
                Segmented(listOf("de" to "Deutsch", "en" to "English", "ru" to "Русский"), user.locale, Modifier.fillMaxWidth()) { locale ->
                    scope.launch { app.attempt { app.setLocale(locale) } }
                }
            }
        }

        Card {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(t("profile.security"), fontWeight = FontWeight.Medium)
                Field(currentPassword, { currentPassword = it }, t("profile.currentPassword"), t.fieldError(passwordErrors["currentPassword"]), password = true)
                Column {
                    Field(newPassword, { newPassword = it }, t("profile.newPassword"), t.fieldError(passwordErrors["newPassword"]), password = true)
                    Hint(t("auth.passwordHint"))
                }
                SaveButton(busy, t("profile.changePassword"), primary = false) {
                    busy = true
                    scope.launch {
                        passwordErrors = app.submit {
                            app.repo.call("POST", "/me/password", buildJsonObject {
                                put("currentPassword", currentPassword); put("newPassword", newPassword)
                            }.toString())
                        }
                        busy = false
                        if (passwordErrors.isEmpty()) {
                            currentPassword = ""
                            newPassword = ""
                            app.onMessage(t("profile.passwordChanged"))
                        }
                    }
                }
            }
        }
    }
}
