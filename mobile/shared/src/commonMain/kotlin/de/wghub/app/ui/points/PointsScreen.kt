package de.wghub.app.ui.points

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.wghub.app.LocalApp
import de.wghub.app.data.LeaderboardResponse
import de.wghub.app.data.MemberProgress
import de.wghub.app.i18n.LocalTexts
import de.wghub.app.ui.Avatar
import de.wghub.app.ui.Card
import de.wghub.app.ui.CardList
import de.wghub.app.ui.ListRow
import de.wghub.app.ui.Loaded
import de.wghub.app.ui.Loader
import de.wghub.app.ui.LocalHousehold
import de.wghub.app.ui.RowMeta
import de.wghub.app.ui.ScreenColumn
import de.wghub.app.ui.SectionTitle
import de.wghub.app.ui.Segmented
import de.wghub.app.ui.Wg
import kotlinx.coroutines.launch

private val BADGES = listOf(
    "first" to "🧽", "ten" to "🧹", "fifty" to "🏅", "hundred" to "🏆",
    "helper" to "🤝", "streak4" to "🔥", "streak12" to "☄️", "level5" to "⭐",
)

/** Level, points and progress bar. Mirrors frontend/src/components/dashboard/LevelCard.vue. */
@Composable
fun LevelCard(progress: MemberProgress, modifier: Modifier = Modifier) {
    val t = LocalTexts.current
    val span = (progress.levelNext - progress.levelFloor).coerceAtLeast(1)
    // Strikes can take a total below zero; the bar simply stays empty.
    val fraction = ((progress.points - progress.levelFloor).toFloat() / span).coerceIn(0f, 1f)
    Card(modifier) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row {
                Text(
                    "${t("points.levelShort", "level" to progress.level)} · ${t("points.levels.l${minOf(progress.level, 10)}")}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(t("points.total", "points" to progress.points), fontSize = 14.sp)
            }
            Box(Modifier.fillMaxWidth().height(8.dp).background(Wg.colors.subtle, RoundedCornerShape(50))) {
                Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(Wg.colors.accent, RoundedCornerShape(50)))
            }
            Row {
                Text(t("points.toNext", "points" to (progress.levelNext - progress.points)), fontSize = 13.sp, color = Wg.colors.muted, modifier = Modifier.weight(1f))
                if (progress.streak > 0) {
                    Icon(Icons.Default.LocalFireDepartment, null, Modifier.size(14.dp), tint = Wg.colors.warn)
                    Text(t("points.streak", "count" to progress.streak), fontSize = 13.sp, color = Wg.colors.warn)
                }
            }
        }
    }
}

/** Mirrors frontend/src/views/PointsView.vue. */
@Composable
fun PointsScreen() {
    val app = LocalApp.current
    val household = LocalHousehold.current
    val t = LocalTexts.current
    val scope = rememberCoroutineScope()
    val loader = remember(household.id) { Loader(app, "/households/${household.id}/leaderboard", LeaderboardResponse.serializer()) }
    var monthOnly by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(loader, household.refreshTick) { loader.load() }

    ScreenColumn(onRefresh = { loader.load() }) {
        Loaded(loader, { scope.launch { loader.load() } }) { response ->
            val me = response.members.firstOrNull { it.user.id == app.user?.id }
            Column {
                SectionTitle(t("points.yourLevel"))
                if (me != null) LevelCard(me)
            }
            Column {
                SectionTitle(t("points.badges"))
                Card {
                    BADGES.chunked(4).forEach { row ->
                        Row(Modifier.fillMaxWidth().padding(6.dp)) {
                            row.forEach { (key, icon) ->
                                val earned = me?.badges?.contains(key) == true
                                Column(
                                    Modifier.weight(1f).heightIn(min = 76.dp).alpha(if (earned) 1f else 0.45f).padding(4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Box(Modifier.height(32.dp), contentAlignment = Alignment.Center) {
                                        if (earned) Text(icon, fontSize = 24.sp)
                                        else Icon(Icons.Default.Lock, t("points.badgeHints.$key"), Modifier.size(20.dp), tint = Wg.colors.muted)
                                    }
                                    Text(t("points.badgeNames.$key"), fontSize = 11.sp, textAlign = TextAlign.Center, lineHeight = 13.sp)
                                }
                            }
                        }
                    }
                }
            }
            Column {
                SectionTitle(t("points.ranking"))
                Segmented(listOf(true to t("points.month"), false to t("points.allTime")), monthOnly, Modifier.fillMaxWidth().padding(bottom = 8.dp)) { monthOnly = it }
                val score = { m: MemberProgress -> if (monthOnly) m.monthPoints else m.points }
                CardList(response.members.sortedByDescending(score).withIndex().toList()) { (index, member) ->
                    ListRow {
                        Text("${index + 1}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Wg.colors.muted, modifier = Modifier.width(20.dp), textAlign = TextAlign.Center)
                        Avatar(member.user, 32.dp)
                        Column(Modifier.weight(1f)) {
                            Text(
                                member.user.displayName + if (member.user.id == app.user?.id) " (${t("common.you")})" else "",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                            )
                            RowMeta("${t("points.levelShort", "level" to member.level)} · ${t("points.levels.l${minOf(member.level, 10)}")}")
                        }
                        if (member.streak > 0) {
                            Icon(Icons.Default.LocalFireDepartment, t("points.streak", "count" to member.streak), Modifier.size(14.dp), tint = Wg.colors.warn)
                            Text("${member.streak}", fontSize = 13.sp, color = Wg.colors.warn)
                        }
                        Text("${score(member)}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End, modifier = Modifier.width(48.dp))
                    }
                }
            }
            Column {
                SectionTitle(t("points.howTitle"))
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        buildList {
                            add("points.howOnTime"); add("points.howLate"); add("points.howStreak")
                            if (household.detail.household.dishwasherTarget != null) add("points.howStrike")
                        }.forEach { Text(t(it), fontSize = 14.sp, color = Wg.colors.muted) }
                    }
                }
            }
        }
    }
}
