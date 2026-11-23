package de.wghub.app.ui.chores

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.wghub.app.LocalApp
import de.wghub.app.data.Chore
import de.wghub.app.i18n.LocalTexts
import de.wghub.app.i18n.plusDays
import de.wghub.app.ui.Avatar
import de.wghub.app.ui.Card
import de.wghub.app.ui.CardList
import de.wghub.app.ui.EmptyNote
import de.wghub.app.ui.ListRow
import de.wghub.app.ui.LocalHousehold
import de.wghub.app.ui.MenuItem
import de.wghub.app.ui.RowMenu
import de.wghub.app.ui.RowMeta
import de.wghub.app.ui.RowTitle
import de.wghub.app.ui.SectionTitle
import de.wghub.app.ui.Wg
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/** Mirrors frontend/src/components/chores/ChorePlanPanel.vue and the extra tasks list. */
@Composable
internal fun PlanTab(model: ChoresModel, actions: ChoreActions) {
    val app = LocalApp.current
    val household = LocalHousehold.current
    val t = LocalTexts.current
    val scope = rememberCoroutineScope()
    val plan = model.plan ?: return
    val format = household.format
    val mainTasks = model.series.filter { it.kind == "MAIN" }.sortedBy { it.position }
    val extras = model.series.filter { it.kind == "EXTRA" }
    val seats = plan.circle
    val canManage = { chore: Chore -> household.isAdmin || chore.createdById == app.user?.id }

    if (mainTasks.isEmpty()) {
        EmptyNote(t("chores.noMain"), t("chores.noMainText"))
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle(t("chores.planTitle"))
            plan.weeks.forEachIndexed { index, week ->
                val title = when (index) {
                    0 -> t("chores.planThisWeek")
                    1 -> t("chores.planNextWeek")
                    else -> t("chores.planInWeeks", "count" to index)
                }
                val due = LocalDate.parse(week.dueDate)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Wg.colors.surface,
                    border = BorderStroke(1.dp, if (index == 0) Wg.colors.accent else Wg.colors.line),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text("${format.date(due.plusDays(-6))} – ${format.date(due)}", fontSize = 12.sp, color = Wg.colors.muted)
                        }
                        week.items.forEachIndexed { i, item ->
                            if (i > 0) HorizontalDivider(color = Wg.colors.line)
                            val done = item.status == "COMPLETED"
                            ListRow {
                                Text(
                                    item.title,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (done) Wg.colors.muted else Wg.colors.fg,
                                    textDecoration = if (done) TextDecoration.LineThrough else null,
                                    modifier = Modifier.weight(1f),
                                )
                                if (done) Icon(Icons.Default.Check, t("chores.done"), tint = Wg.colors.accentText, modifier = Modifier.size(16.dp))
                                item.assignee?.let {
                                    Text(
                                        it.displayName,
                                        fontSize = 13.sp,
                                        color = Wg.colors.muted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.End,
                                        modifier = Modifier.widthIn(max = 90.dp),
                                    )
                                    Avatar(it)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    Column {
        SectionTitle(t("chores.circleTitle"))
        CardList(seats.withIndex().toList()) { (index, seat) ->
            ListRow {
                Text("${index + 1}", fontSize = 13.sp, color = Wg.colors.muted, modifier = Modifier.widthIn(min = 14.dp))
                Avatar(seat.user)
                Text(
                    seat.user.displayName,
                    fontSize = 14.sp,
                    color = if (seat.frozen) Wg.colors.muted else Wg.colors.fg,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (seat.frozen) {
                    Icon(Icons.Default.AcUnit, null, tint = Wg.colors.muted, modifier = Modifier.size(14.dp))
                    Text(t("members.frozen"), fontSize = 13.sp, color = Wg.colors.muted)
                }
                if (household.isAdmin) {
                    val ids = seats.map { it.user.id }
                    fun move(step: Int) {
                        val next = ids.toMutableList()
                        next[index] = ids[index + step]
                        next[index + step] = ids[index]
                        scope.launch { model.setCircle(next) }
                    }
                    IconButton(onClick = { move(-1) }, enabled = index > 0, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.ArrowUpward, t("chores.moveUp"), modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = { move(1) }, enabled = index < seats.size - 1, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.ArrowDownward, t("chores.moveDown"), modifier = Modifier.size(18.dp))
                    }
                    RowMenu(
                        listOf(
                            if (seat.frozen) MenuItem(t("members.unfreeze")) { scope.launch { model.setFrozen(seat.user.id, false) } }
                            else MenuItem(t("members.freeze")) { scope.launch { model.setFrozen(seat.user.id, true) } },
                        ),
                    )
                }
            }
        }
        Text(t("chores.circleHint"), fontSize = 13.sp, color = Wg.colors.muted, modifier = Modifier.padding(top = 6.dp))
    }

    Column {
        SectionTitle(t("chores.mainTitle"))
        if (mainTasks.isNotEmpty()) {
            CardList(mainTasks) { task ->
                ListRow {
                    Column(Modifier.weight(1f)) {
                        RowTitle(task.title)
                        RowMeta(t("points.short", "points" to task.points))
                    }
                    if (household.isAdmin) {
                        RowMenu(
                            listOf(
                                MenuItem(t("common.edit")) { actions.edit(task) },
                                MenuItem(t("chores.endSeries"), danger = true) { actions.end(task) },
                            ),
                        )
                    }
                }
            }
        }
        if (household.isAdmin && mainTasks.isNotEmpty()) {
            Text(t("chores.shiftLabel"), fontSize = 13.sp, color = Wg.colors.muted, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { scope.launch { model.shift(-1) } }) { Text(t("chores.shiftBack")) }
                OutlinedButton(onClick = { scope.launch { model.shift(1) } }) { Text(t("chores.shiftForward")) }
            }
        }
    }

    Column {
        SectionTitle(t("chores.extraTitle"))
        if (extras.isEmpty()) {
            Card { Text(t("chores.noExtra"), fontSize = 14.sp, color = Wg.colors.muted, modifier = Modifier.padding(16.dp)) }
        } else {
            CardList(extras) { chore ->
                ListRow {
                    Column(Modifier.weight(1f)) {
                        RowTitle(chore.title)
                        RowMeta("${t("chores.recurrences.${chore.recurrence}")} · ${t("points.short", "points" to chore.points)}")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
                        chore.rotation.take(4).forEach { Avatar(it, 24.dp) }
                    }
                    if (canManage(chore)) {
                        RowMenu(
                            listOf(
                                MenuItem(t("common.edit")) { actions.edit(chore) },
                                MenuItem(
                                    if (chore.recurrence == "NONE") t("chores.deleteTask") else t("chores.endSeries"),
                                    danger = true,
                                ) { actions.end(chore) },
                            ),
                        )
                    }
                }
            }
        }
    }
}
