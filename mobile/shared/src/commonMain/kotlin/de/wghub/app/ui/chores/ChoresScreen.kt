package de.wghub.app.ui.chores

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.wghub.app.LocalApp
import de.wghub.app.data.Chore
import de.wghub.app.data.Occurrence
import de.wghub.app.i18n.LocalTexts
import de.wghub.app.i18n.plusDays
import de.wghub.app.i18n.startOfWeek
import de.wghub.app.ui.Avatar
import de.wghub.app.ui.CardList
import de.wghub.app.ui.CheckCircle
import de.wghub.app.ui.EmptyNote
import de.wghub.app.ui.ListRow
import de.wghub.app.ui.LoadFailed
import de.wghub.app.ui.LocalHousehold
import de.wghub.app.ui.MenuItem
import de.wghub.app.ui.OfflineBanner
import de.wghub.app.ui.RowMenu
import de.wghub.app.ui.RowMeta
import de.wghub.app.ui.RowTitle
import de.wghub.app.ui.SectionTitle
import de.wghub.app.ui.Segmented
import de.wghub.app.ui.Wg
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

private enum class ChoreTab { Open, Plan, History }

/** Mirrors frontend/src/views/ChoresView.vue. */
@Composable
fun ChoresScreen() {
    val app = LocalApp.current
    val household = LocalHousehold.current
    val t = LocalTexts.current
    val scope = rememberCoroutineScope()
    val model = remember(household.id) { ChoresModel(app, household.id) }
    val pending by app.repo.pending.collectAsState()
    val offline by app.repo.offline.collectAsState()

    var tab by rememberSaveable { mutableStateOf(ChoreTab.Open) }
    var mineOnly by rememberSaveable { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var formOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Chore?>(null) }
    var reassigning by remember { mutableStateOf<Occurrence?>(null) }
    var ending by remember { mutableStateOf<Chore?>(null) }

    LaunchedEffect(model, household.refreshTick) { model.load() }

    fun run(block: suspend () -> Unit) {
        scope.launch { block() }
    }

    val actions = ChoreActions(
        complete = { run { model.complete(it) } },
        menuFor = { item ->
            listOf(
                MenuItem(t("chores.reassign")) { reassigning = item },
                MenuItem(t("chores.skip")) { run { model.skip(item) } },
            )
        },
        edit = { editing = it; formOpen = true },
        end = { ending = it },
    )

    Box(Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                refreshing = true
                scope.launch {
                    app.repo.flush()
                    model.load()
                    refreshing = false
                }
            },
        ) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (offline || pending.isNotEmpty()) OfflineBanner(pending.size)
                Segmented(
                    listOf(ChoreTab.Open to t("chores.tabOpen"), ChoreTab.Plan to t("chores.tabPlan"), ChoreTab.History to t("chores.tabHistory")),
                    tab,
                    Modifier.fillMaxWidth(),
                ) { tab = it }

                when {
                    model.failed -> LoadFailed { run { model.load() } }
                    !model.loaded -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    tab == ChoreTab.Open -> {
                        Segmented(listOf(false to t("common.all"), true to t("common.mine")), mineOnly) { mineOnly = it }
                        OpenTab(model, mineOnly, actions)
                    }
                    tab == ChoreTab.Plan -> PlanTab(model, actions)
                    else -> HistoryTab(model.history()) { run { model.reopen(it) } }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { editing = null; formOpen = true },
            icon = { Icon(Icons.Default.Add, null) },
            text = { Text(t("chores.add")) },
            containerColor = Wg.colors.accent,
            contentColor = Wg.colors.accentFg,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    if (formOpen) {
        ChoreForm(chore = editing, model = model, onClose = { formOpen = false })
    }

    reassigning?.let { item ->
        AlertDialog(
            onDismissRequest = { reassigning = null },
            title = { Text(t("chores.reassignTitle")) },
            text = {
                Column {
                    household.detail.members.filter { !it.frozen }.forEach { member ->
                        ListRow(Modifier.clickable {
                            reassigning = null
                            run { model.reassign(item, member.id) }
                        }) {
                            Avatar(de.wghub.app.data.UserSummary(member.id, member.displayName, member.avatarColor))
                            Text(member.displayName)
                        }
                    }
                    ListRow(Modifier.clickable {
                        reassigning = null
                        run { model.reassign(item, null) }
                    }) { Text(t("chores.anyone"), color = Wg.colors.muted) }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { reassigning = null }) { Text(t("common.cancel")) } },
        )
    }

    ending?.let { chore ->
        AlertDialog(
            onDismissRequest = { ending = null },
            title = { Text(t("chores.endSeriesConfirm", "title" to chore.title)) },
            text = { Text(t("chores.endSeriesText")) },
            confirmButton = {
                TextButton(onClick = {
                    ending = null
                    run { model.end(chore) }
                }) {
                    Text(
                        if (chore.recurrence == "NONE") t("chores.deleteTask") else t("chores.endSeries"),
                        color = Wg.colors.danger,
                    )
                }
            },
            dismissButton = { TextButton(onClick = { ending = null }) { Text(t("common.cancel")) } },
        )
    }
}

class ChoreActions(
    val complete: (Occurrence) -> Unit,
    val menuFor: (Occurrence) -> List<MenuItem>,
    val edit: (Chore) -> Unit,
    val end: (Chore) -> Unit,
)

@Composable
private fun OpenTab(model: ChoresModel, mineOnly: Boolean, actions: ChoreActions) {
    val app = LocalApp.current
    val household = LocalHousehold.current
    val t = LocalTexts.current
    var showLater by rememberSaveable { mutableStateOf(false) }

    val all = model.open(household.detail.members)
    val visible = if (mineOnly) all.filter { it.assignee?.id == app.user?.id } else all
    if (visible.isEmpty()) {
        if (mineOnly) EmptyNote(t("chores.emptyMine")) else EmptyNote(t("chores.emptyTitle"), t("chores.emptyText"))
        return
    }

    val today = household.format.today()
    val weekEnd = startOfWeek(today).plusDays(6)
    val due = { item: Occurrence -> LocalDate.parse(item.dueDate) }
    val upcoming = visible.filter { due(it) >= today }
    val overdue = visible.filter { due(it) < today }
    val main = upcoming.filter { it.kind == "MAIN" && due(it) <= weekEnd }
    val extra = upcoming.filter { it.kind == "EXTRA" && due(it) <= weekEnd }
    val later = upcoming.filter { due(it) > weekEnd }

    val sections = listOf(
        Triple(t("chores.overdue"), overdue, true),
        Triple(t("chores.mainThisWeek"), main, false),
        Triple(t("chores.extraThisWeek"), extra, false),
    ).filter { it.second.isNotEmpty() }

    sections.forEach { (title, items, isOverdue) ->
        Column {
            SectionTitle(title, danger = isOverdue)
            CardList(items) { OccurrenceRow(it, overdue = isOverdue, menu = actions.menuFor(it)) { actions.complete(it) } }
        }
    }
    if (later.isNotEmpty()) {
        // With nothing due soon, the later work is the content of the page rather than a footnote.
        val laterVisible = showLater || sections.isEmpty()
        Column {
            if (sections.isEmpty()) SectionTitle(t("chores.later"))
            else Text(
                "${if (showLater) t("chores.hideLater") else t("chores.showLater")} (${later.size})",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Wg.colors.muted,
                modifier = Modifier.clickable { showLater = !showLater }.padding(vertical = 8.dp, horizontal = 2.dp),
            )
            if (laterVisible) CardList(later) { OccurrenceRow(it, overdue = false, menu = actions.menuFor(it)) { actions.complete(it) } }
        }
    }
}

/** Mirrors frontend/src/components/chores/OccurrenceRow.vue. */
@Composable
fun OccurrenceRow(item: Occurrence, overdue: Boolean, showDate: Boolean = true, menu: List<MenuItem> = emptyList(), onToggle: () -> Unit) {
    val household = LocalHousehold.current
    val t = LocalTexts.current
    val done = item.status == "COMPLETED"
    // Repeating chores can be ticked off from a week before they are due, not earlier.
    val locked = !done && item.recurrence != "NONE" &&
        LocalDate.parse(item.dueDate) >= household.format.today().plusDays(7)
    val points = item.awardedPoints ?: item.points
    // A main task is for the whole week, so its deadline is what matters.
    val meta = when {
        item.kind == "MAIN" && item.status == "PENDING" -> t("chores.dueUntil", "date" to household.format.date(item.dueDate))
        showDate -> household.format.relativeDate(item.dueDate)
        else -> null
    }

    ListRow {
        CheckCircle(checked = done, enabled = !locked, onToggle = onToggle)
        Column(Modifier.weight(1f)) {
            RowTitle(item.title, done)
            if (meta != null) RowMeta(meta, danger = overdue)
        }
        if (points > 0) Text("+$points", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Wg.colors.accentText)
        item.assignee?.let { Avatar(it) }
        RowMenu(menu)
    }
}

@Composable
private fun HistoryTab(history: List<Occurrence>, onReopen: (Occurrence) -> Unit) {
    val household = LocalHousehold.current
    val t = LocalTexts.current
    if (history.isEmpty()) {
        EmptyNote(t("chores.emptyHistory"))
        return
    }
    CardList(history) { item ->
        ListRow {
            Column(Modifier.weight(1f)) {
                RowTitle(item.title)
                RowMeta(
                    t(
                        "chores.doneBy",
                        "name" to (item.completedBy?.displayName ?: "–"),
                        "date" to (item.completedAt?.let(household.format::dateTime) ?: household.format.date(item.dueDate)),
                    ),
                )
            }
            IconButton(onClick = { onReopen(item) }) {
                Icon(Icons.Default.Replay, t("chores.reopen"), tint = Wg.colors.muted)
            }
        }
    }
}
