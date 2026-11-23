package de.wghub.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.ShoppingBasket
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.wghub.app.LocalApp
import de.wghub.app.data.Dashboard
import de.wghub.app.data.ShoppingItem
import de.wghub.app.i18n.LocalTexts
import de.wghub.app.i18n.formatCents
import de.wghub.app.ui.Avatar
import de.wghub.app.ui.Card
import de.wghub.app.ui.CheckCircle
import de.wghub.app.ui.ListRow
import de.wghub.app.ui.Loaded
import de.wghub.app.ui.Loader
import de.wghub.app.ui.LocalHousehold
import de.wghub.app.ui.LocalNav
import de.wghub.app.ui.Page
import de.wghub.app.ui.ScreenColumn
import de.wghub.app.ui.Tab
import de.wghub.app.ui.Wg
import de.wghub.app.ui.calendar.EventForm
import de.wghub.app.ui.calendar.groupColor
import de.wghub.app.ui.chores.ChoreForm
import de.wghub.app.ui.chores.ChoresModel
import de.wghub.app.ui.chores.OccurrenceRow
import de.wghub.app.ui.dishwasher.DishwasherModel
import de.wghub.app.ui.notes.NoteCard
import de.wghub.app.ui.notes.NoteForm
import de.wghub.app.ui.points.LevelCard
import de.wghub.app.ui.shopping.BuyDialog
import de.wghub.app.ui.shopping.ShoppingActions
import de.wghub.app.ui.shopping.ShoppingForm
import de.wghub.app.ui.shopping.amount
import kotlinx.coroutines.launch

private enum class Quick { Chore, Item, Note, Event }

/** Same number of rows in every card keeps them short; the header links to the rest. */
private const val MAX_ROWS = 5

/** A card with a title that opens the full screen. Mirrors DashboardCard.vue. */
@Composable
private fun DashboardCard(title: String, meta: String? = null, onOpen: () -> Unit, content: @Composable () -> Unit) {
    Card {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onOpen).heightIn(min = 48.dp).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            if (meta != null) Text(meta, fontSize = 13.sp, color = Wg.colors.muted)
            Spacer(Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Wg.colors.muted)
        }
        content()
    }
}

@Composable
private fun Empty(text: String, icon: ImageVector? = null) {
    Row(Modifier.padding(horizontal = 14.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (icon != null) Icon(icon, null, Modifier.size(16.dp), tint = Wg.colors.muted)
        Text(text, fontSize = 14.sp, color = Wg.colors.muted)
    }
}

/** Mirrors frontend/src/views/DashboardView.vue. */
@Composable
fun DashboardScreen() {
    val app = LocalApp.current
    val household = LocalHousehold.current
    val nav = LocalNav.current
    val t = LocalTexts.current
    val format = household.format
    val scope = rememberCoroutineScope()
    val loader = remember(household.id) { Loader(app, "/households/${household.id}/dashboard", Dashboard.serializer()) }
    val chores = remember(household.id) { ChoresModel(app, household.id) }
    val shopping = remember(household.id) { ShoppingActions(app, household.id) }
    val dishwasher = remember(household.id) { DishwasherModel(app, household.id) }
    val hasDishwasher = household.detail.household.dishwasherTarget != null
    val pending by app.repo.pending.collectAsState()
    var quick by remember { mutableStateOf<Quick?>(null) }
    var buying by remember { mutableStateOf<ShoppingItem?>(null) }

    suspend fun load() {
        loader.load()
        if (hasDishwasher) dishwasher.load()
    }
    LaunchedEffect(household.id, household.refreshTick) { load() }
    fun reload() = scope.launch { load() }

    ScreenColumn(onRefresh = ::load) {
        Column {
            Text(t("dashboard.greeting", "name" to (app.user?.displayName ?: "")), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(format.dateLong(format.today().toString()), fontSize = 14.sp, color = Wg.colors.muted)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                Triple(Quick.Chore, Icons.AutoMirrored.Filled.PlaylistAdd, "dashboard.quickTask"),
                Triple(Quick.Item, Icons.Default.ShoppingBasket, "dashboard.quickItem"),
                Triple(Quick.Note, Icons.AutoMirrored.Filled.StickyNote2, "dashboard.quickNote"),
                Triple(Quick.Event, Icons.Default.EventAvailable, "dashboard.quickEvent"),
            ).forEach { (kind, icon, label) ->
                Card(Modifier.weight(1f)) {
                    Column(
                        Modifier.fillMaxWidth().clickable { quick = kind }.heightIn(min = 64.dp).padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                    ) {
                        Icon(icon, null, Modifier.size(20.dp), tint = Wg.colors.accentText)
                        Text(t(label), fontSize = 12.sp, maxLines = 1)
                    }
                }
            }
        }

        Loaded(loader, { reload() }) { data ->
            data.myProgress?.let { progress ->
                Box(Modifier.clickable { nav.open(Page.Points) }) { LevelCard(progress) }
            }

            val cents = data.myBalanceCents
            Card {
                Row(
                    Modifier.fillMaxWidth().clickable { nav.open(Page.Expenses) }.heightIn(min = 52.dp).padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(t("dashboard.balance"), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(
                        when {
                            cents > 0 -> t("dashboard.balancePositive", "amount" to formatCents(cents, t.locale))
                            cents < 0 -> t("dashboard.balanceNegative", "amount" to formatCents(-cents, t.locale))
                            else -> t("dashboard.balanceEven")
                        },
                        fontSize = 14.sp,
                        color = when {
                            cents > 0 -> Wg.colors.accentText
                            cents < 0 -> Wg.colors.danger
                            else -> Wg.colors.muted
                        },
                    )
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Wg.colors.muted)
                }
            }

            if (hasDishwasher) {
                dishwasher.state()?.let { state ->
                    val mine = state.members.firstOrNull { it.user.id == app.user?.id }
                    val crown = state.members.firstOrNull { it.user.id == state.crownHolderId }?.user?.displayName
                    Card {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f).clickable { nav.open(Page.Dishwasher) }) {
                                Text(t("dishwasher.title"), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    t("dishwasher.you", "total" to state.total, "target" to (state.target ?: 0), "count" to (mine?.count ?: 0)) +
                                        (crown?.let { " · " + t("dishwasher.crownOf", "name" to it) } ?: ""),
                                    fontSize = 13.sp,
                                    color = Wg.colors.muted,
                                    maxLines = 2,
                                )
                            }
                            Button(onClick = { scope.launch { dishwasher.tap() } }, enabled = !dishwasher.busy) { Text("+1 · ${t("dishwasher.tap")}") }
                        }
                    }
                }
            }

            // Tasks: unsent ticks are already shown as done.
            val waiting = pending.filter { it.householdId == household.id }
            val doneOffline = waiting.filter { it.type == "complete" }.mapNotNull { it.targetId }.toSet()
            val reopenedOffline = waiting.filter { it.type == "reopen" }.mapNotNull { it.targetId }.toSet()
            val tasks = data.tasks.map {
                when (it.id) {
                    in doneOffline -> it.copy(status = "COMPLETED")
                    in reopenedOffline -> it.copy(status = "PENDING")
                    else -> it
                }
            }.sortedBy { it.status == "COMPLETED" }.take(MAX_ROWS)
            DashboardCard(t("dashboard.tasks"), onOpen = { nav.open(Tab.Chores) }) {
                if (tasks.isEmpty()) Empty(t("dashboard.noTasks"), Icons.Default.Celebration)
                tasks.forEachIndexed { index, task ->
                    if (index > 0) HorizontalDivider(color = Wg.colors.line)
                    OccurrenceRow(
                        task,
                        overdue = task.dueDate < data.today && task.status == "PENDING",
                        showDate = task.dueDate != data.today,
                    ) {
                        scope.launch {
                            if (task.status == "COMPLETED") chores.reopen(task) else chores.complete(task)
                            loader.load()
                        }
                    }
                }
            }

            val items = shopping.withPending(data.shopping.items).filter { it.status == "PENDING" }
            val boughtOffline = data.shopping.items.size - items.size
            val count = (data.shopping.pendingCount - boughtOffline).coerceAtLeast(0)
            DashboardCard(t("dashboard.shopping"), t("dashboard.shoppingCount", "count" to count), onOpen = { nav.open(Tab.Shopping) }) {
                if (items.isEmpty()) Empty(t("dashboard.shoppingEmpty"))
                items.take(MAX_ROWS).forEachIndexed { index, item ->
                    if (index > 0) HorizontalDivider(color = Wg.colors.line)
                    ListRow {
                        CheckCircle(checked = false, enabled = true) {
                            // Your own personal item involves nobody else, so there is nothing to ask.
                            if (item.personal && item.createdBy.id == app.user?.id) {
                                scope.launch { shopping.setStatus(item, "BOUGHT"); loader.load() }
                            } else buying = item
                        }
                        Text(item.name, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        if (item.quantity != null || item.unit != null) Text(t.amount(item.quantity, item.unit), fontSize = 13.sp, color = Wg.colors.muted)
                    }
                }
            }

            /** Next up: events and chores merged and sorted by day. */
            data class Entry(val key: String, val date: String, val time: String, val title: String, val group: String, val assignee: de.wghub.app.data.UserSummary?)
            val upcoming = (
                data.events.map {
                    Entry("e-${it.id}", format.instantDate(it.startsAt), if (it.allDay) "" else format.time(it.startsAt), it.title, it.category, null)
                } + data.upcomingTasks.map { Entry("t-${it.id}", it.dueDate, "", it.title, "CHORE", it.assignee) }
                ).sortedBy { it.date + it.time }.take(MAX_ROWS)
            DashboardCard(t("dashboard.upcoming"), onOpen = { nav.open(Tab.Calendar) }) {
                if (upcoming.isEmpty()) Empty(t("dashboard.noUpcoming"))
                upcoming.forEachIndexed { index, entry ->
                    if (index > 0) HorizontalDivider(color = Wg.colors.line)
                    ListRow {
                        Box(Modifier.size(8.dp).background(groupColor(entry.group), CircleShape))
                        Text(entry.title, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(
                            format.relativeDate(entry.date) + if (entry.time.isNotEmpty()) ", ${entry.time}" else "",
                            fontSize = 13.sp,
                            color = Wg.colors.muted,
                        )
                        if (entry.assignee != null) Avatar(entry.assignee, 24.dp) else Spacer(Modifier.width(24.dp))
                    }
                }
            }

            DashboardCard(t("dashboard.notes"), onOpen = { nav.open(Page.Notes) }) {
                if (data.notes.isEmpty()) Empty(t("dashboard.noNotes"))
                Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    data.notes.take(3).forEach { NoteCard(it, maxLines = 2) }
                }
            }
        }
    }

    when (quick) {
        Quick.Chore -> ChoreForm(chore = null, model = chores, onClose = { quick = null; reload() })
        Quick.Item -> ShoppingForm(null, onClose = { quick = null }, onSaved = { reload() })
        Quick.Note -> NoteForm(null, onClose = { quick = null }, onSaved = { reload() })
        Quick.Event -> EventForm(null, null, onClose = { quick = null }, onSaved = { reload() })
        null -> {}
    }
    buying?.let { item -> BuyDialog(item, shopping, onClose = { buying = null }, onBought = { reload() }) }
}
