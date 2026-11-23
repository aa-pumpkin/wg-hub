package de.wghub.app.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.wghub.app.LocalApp
import de.wghub.app.data.CalendarEvent
import de.wghub.app.data.CalendarRange
import de.wghub.app.data.Occurrence
import de.wghub.app.i18n.Format
import de.wghub.app.i18n.LocalTexts
import de.wghub.app.i18n.plusDays
import de.wghub.app.i18n.startOfWeek
import de.wghub.app.ui.Avatar
import de.wghub.app.ui.Card
import de.wghub.app.ui.ConfirmDialog
import de.wghub.app.ui.Hint
import de.wghub.app.ui.Loaded
import de.wghub.app.ui.Loader
import de.wghub.app.ui.LocalHousehold
import de.wghub.app.ui.ScreenColumn
import de.wghub.app.ui.SectionTitle
import de.wghub.app.ui.Segmented
import de.wghub.app.ui.Wg
import de.wghub.app.ui.attempt
import de.wghub.app.ui.chores.ChoresModel
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

private enum class View { Month, Week, Agenda }
private const val AGENDA_DAYS = 60

/** Chores, WG events and other dates are told apart by colour everywhere in the calendar. */
@Composable
fun groupColor(group: String): Color = when (group) {
    "CHORE" -> Wg.colors.accent
    "WG" -> Color(0xFF8B7FD6)
    else -> Color(0xFFD9A23B)
}

data class CalendarItem(
    val key: String,
    val group: String,
    val title: String,
    /** "14:00", or null for all-day items. */
    val time: String?,
    val done: Boolean,
    val event: CalendarEvent? = null,
    val occurrence: Occurrence? = null,
)

/** The calendar days an event covers in the household's zone. */
private fun eventDays(event: CalendarEvent, format: Format): List<String> {
    val start = LocalDate.parse(format.instantDate(event.startsAt))
    val end = LocalDate.parse(format.instantDate(event.endsAt ?: event.startsAt))
    val days = mutableListOf<String>()
    var day = start
    // Bounded so a wrongly entered end date cannot hang the screen.
    while (day <= end && days.size < 366) {
        days += day.toString()
        day = day.plusDays(1)
    }
    return days
}

/** Same as buildDayMap in frontend/src/lib/calendar.ts. */
private fun buildDayMap(range: CalendarRange, format: Format, visible: Set<String>): Map<String, List<CalendarItem>> {
    val map = mutableMapOf<String, MutableList<CalendarItem>>()
    for (o in range.occurrences) {
        // Main tasks are for a whole week, so they are shown on the week, not on a day.
        if ("CHORE" !in visible || o.status == "SKIPPED" || o.kind == "MAIN") continue
        map.getOrPut(o.dueDate) { mutableListOf() } += CalendarItem("o-${o.id}", "CHORE", o.title, null, o.status == "COMPLETED", occurrence = o)
    }
    for (e in range.events) {
        if (e.category !in visible) continue
        eventDays(e, format).forEachIndexed { index, day ->
            map.getOrPut(day) { mutableListOf() } += CalendarItem(
                "e-${e.id}-$day", e.category, e.title,
                if (e.allDay || index > 0) null else format.time(e.startsAt), false, event = e,
            )
        }
    }
    return map.mapValues { (_, list) -> list.sortedBy { it.time ?: "" } }
}

/** Main tasks by the Sunday of their week. */
private fun weekTaskMap(range: CalendarRange, visible: Set<String>): Map<String, List<Occurrence>> =
    if ("CHORE" !in visible) emptyMap()
    else range.occurrences.filter { it.kind == "MAIN" && it.status != "SKIPPED" }.groupBy { it.dueDate }

private fun monthGrid(anchor: LocalDate): List<LocalDate> {
    val first = startOfWeek(LocalDate(anchor.year, anchor.month, 1))
    val nextMonth = LocalDate(anchor.year, anchor.month, 1).plus(DatePeriod(months = 1))
    val last = startOfWeek(nextMonth.plusDays(-1)).plusDays(6)
    val days = mutableListOf<LocalDate>()
    var day = first
    while (day <= last) {
        days += day
        day = day.plusDays(1)
    }
    return days
}

/** Mirrors frontend/src/views/CalendarView.vue. */
@Composable
fun CalendarScreen() {
    val app = LocalApp.current
    val household = LocalHousehold.current
    val t = LocalTexts.current
    val format = household.format
    val scope = rememberCoroutineScope()
    val today = format.today()

    // The month grid is dense on a phone, so the list view comes first, like on the website.
    var view by rememberSaveable { mutableStateOf(View.Agenda) }
    var anchor by remember { mutableStateOf(today) }
    var selected by remember { mutableStateOf(today) }
    var visible by remember { mutableStateOf(setOf("CHORE", "WG", "OTHER")) }
    var formOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<CalendarEvent?>(null) }
    var detail by remember { mutableStateOf<CalendarItem?>(null) }
    var deleting by remember { mutableStateOf<CalendarEvent?>(null) }

    val days = when (view) {
        View.Month -> monthGrid(anchor)
        View.Week -> (0..6).map { startOfWeek(anchor).plusDays(it) }
        View.Agenda -> (0 until AGENDA_DAYS).map { anchor.plusDays(it) }
    }
    val loader = remember(household.id, days.first(), days.last()) {
        Loader(app, "/households/${household.id}/calendar?from=${days.first()}&to=${days.last()}", CalendarRange.serializer())
    }
    LaunchedEffect(loader, household.refreshTick) { loader.load() }

    fun move(step: Int) {
        anchor = when (view) {
            View.Month -> LocalDate(anchor.year, anchor.month, 1).plus(DatePeriod(months = step))
            View.Week -> anchor.plusDays(step * 7)
            View.Agenda -> anchor.plusDays(step * AGENDA_DAYS)
        }
        selected = anchor
    }

    val title = if (view == View.Week) "${format.date(days.first())} – ${format.date(days.last())}" else format.monthTitle(anchor.toString())

    ScreenColumn(onRefresh = { loader.load() }, addLabel = t("calendar.add"), onAdd = { editing = null; formOpen = true }) {
        Segmented(
            listOf(View.Month to t("calendar.month"), View.Week to t("calendar.week"), View.Agenda to t("calendar.agenda")),
            view,
            Modifier.fillMaxWidth(),
        ) { view = it; anchor = today; selected = today }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { move(-1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, t("calendar.previous")) }
            IconButton(onClick = { move(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, t("calendar.next")) }
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = { anchor = today; selected = today }) { Text(t("calendar.today")) }
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("CHORE", "WG", "OTHER").forEach { group ->
                val on = group in visible
                Row(
                    Modifier.border(1.dp, Wg.colors.line, RoundedCornerShape(50))
                        .clickable { visible = if (on) visible - group else visible + group }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(Modifier.size(8.dp).background(groupColor(group).copy(alpha = if (on) 1f else 0.4f), CircleShape))
                    Text(
                        t("calendar.categories.$group"),
                        fontSize = 13.sp,
                        color = if (on) Wg.colors.fg else Wg.colors.muted,
                        textDecoration = if (on) null else TextDecoration.LineThrough,
                    )
                }
            }
        }

        Loaded(loader, { scope.launch { loader.load() } }) { range ->
            val dayMap = buildDayMap(range, format, visible)
            val weekTasks = weekTaskMap(range, visible)
            val openOccurrence = { o: Occurrence -> detail = CalendarItem(o.id, "CHORE", o.title, null, o.status == "COMPLETED", occurrence = o) }
            when (view) {
                View.Month -> {
                    MonthGrid(days, anchor, today, selected, dayMap, weekTasks, onSelect = { selected = it }, onOpenTask = openOccurrence)
                    Column {
                        SectionTitle(format.dateLong(selected.toString()))
                        DayList(dayMap[selected.toString()].orEmpty(), emptyText = t("calendar.nothingThatDay")) { detail = it }
                    }
                }
                View.Week -> {
                    WeekTasksBar(weekTasks[days.last().toString()].orEmpty(), openOccurrence)
                    days.forEach { day ->
                        Column {
                            SectionTitle("${format.weekdayShort(day.toString())} ${day.day}", danger = false)
                            DayList(dayMap[day.toString()].orEmpty(), emptyText = null) { detail = it }
                        }
                    }
                }
                View.Agenda -> {
                    val entries = days.mapIndexedNotNull { index, day ->
                        val items = dayMap[day.toString()].orEmpty()
                        // A week's main tasks are listed once, under the week's first visible day.
                        val week = if (day.dayOfWeek.ordinal == 0 || index == 0) weekTasks[startOfWeek(day).plusDays(6).toString()].orEmpty() else emptyList()
                        if (items.isEmpty() && week.isEmpty()) null else Triple(day, items, week)
                    }
                    if (entries.isEmpty()) Hint(t("calendar.agendaEmpty"), Modifier.fillMaxWidth().padding(vertical = 40.dp))
                    entries.forEach { (day, items, week) ->
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                (if (day == today) "${t("common.today")}, " else "") + format.dateLong(day.toString()),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (day == today) Wg.colors.accentText else Wg.colors.muted,
                            )
                            if (week.isNotEmpty()) WeekTasksBar(week, openOccurrence)
                            if (items.isNotEmpty()) DayList(items, null) { detail = it }
                        }
                    }
                }
            }
        }
        Hint(t("calendar.timeZoneHint", "zone" to household.detail.household.timezone), Modifier.align(Alignment.CenterHorizontally))
    }

    if (formOpen) EventForm(editing, selected.toString(), onClose = { formOpen = false }, onSaved = { scope.launch { loader.load() } })

    detail?.let { item ->
        DetailDialog(
            item,
            onDismiss = { detail = null },
            onEdit = { detail = null; editing = it; formOpen = true },
            onDelete = { detail = null; deleting = it },
            onToggle = { o ->
                detail = null
                scope.launch {
                    val chores = ChoresModel(app, household.id)
                    if (o.status == "COMPLETED") chores.reopen(o) else chores.complete(o)
                    loader.load()
                }
            },
        )
    }
    deleting?.let { event ->
        ConfirmDialog(t("calendar.deleteConfirm", "title" to event.title), confirmLabel = t("common.delete"), onDismiss = { deleting = null }) {
            scope.launch {
                if (app.attempt { app.repo.call("DELETE", "/households/${household.id}/events/${event.id}") }) app.onMessage(t("calendar.deleted"))
                loader.load()
            }
        }
    }
}

@Composable
private fun MonthGrid(
    days: List<LocalDate>,
    anchor: LocalDate,
    today: LocalDate,
    selected: LocalDate,
    dayMap: Map<String, List<CalendarItem>>,
    weekTasks: Map<String, List<Occurrence>>,
    onSelect: (LocalDate) -> Unit,
    onOpenTask: (Occurrence) -> Unit,
) {
    val format = LocalHousehold.current.format
    Card {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            days.take(7).forEach { day ->
                Text(format.weekdayShort(day.toString()), fontSize = 12.sp, color = Wg.colors.muted, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        HorizontalDivider(color = Wg.colors.line)
        days.chunked(7).forEach { week ->
            val tasks = weekTasks[week.last().toString()].orEmpty()
            if (tasks.isNotEmpty()) WeekTasksBar(tasks, onOpenTask, compact = true)
            Row(Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    val inMonth = day.month == anchor.month
                    val items = dayMap[day.toString()].orEmpty()
                    Column(
                        Modifier.weight(1f).heightIn(min = 52.dp)
                            .background(if (day == selected) Wg.colors.accentSoft else if (!inMonth) Wg.colors.subtle.copy(alpha = 0.5f) else Color.Transparent)
                            .clickable { onSelect(day) }
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier.size(26.dp).background(if (day == today) Wg.colors.accent else Color.Transparent, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "${day.day}",
                                fontSize = 12.sp,
                                fontWeight = if (day == today) FontWeight.SemiBold else FontWeight.Normal,
                                color = when {
                                    day == today -> Wg.colors.accentFg
                                    !inMonth -> Wg.colors.muted
                                    else -> Wg.colors.fg
                                },
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(top = 2.dp)) {
                            items.take(4).forEach { Box(Modifier.size(6.dp).background(groupColor(it.group), CircleShape)) }
                        }
                    }
                }
            }
            HorizontalDivider(color = Wg.colors.line)
        }
    }
}

/** The week's main tasks: they belong to the whole week, not to its last day. */
@Composable
private fun WeekTasksBar(items: List<Occurrence>, onOpen: (Occurrence) -> Unit, compact: Boolean = false) {
    if (items.isEmpty()) return
    FlowRow(
        Modifier.fillMaxWidth().background(Wg.colors.accentSoft.copy(alpha = 0.6f), if (compact) RoundedCornerShape(0.dp) else RoundedCornerShape(10.dp)).padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items.forEach { item ->
            val done = item.status == "COMPLETED"
            Row(
                Modifier.background(Wg.colors.surface, RoundedCornerShape(6.dp)).clickable { onOpen(item) }.padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (done) Icon(Icons.Default.Check, null, tint = Wg.colors.accentText, modifier = Modifier.size(12.dp))
                Text(
                    item.title,
                    fontSize = 12.sp,
                    color = if (done) Wg.colors.muted else Wg.colors.fg,
                    textDecoration = if (done) TextDecoration.LineThrough else null,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                item.assignee?.let { Avatar(it, 16.dp) }
            }
        }
    }
}

@Composable
private fun DayList(items: List<CalendarItem>, emptyText: String?, onOpen: (CalendarItem) -> Unit) {
    val t = LocalTexts.current
    if (items.isEmpty()) {
        if (emptyText != null) Card { Text(emptyText, fontSize = 14.sp, color = Wg.colors.muted, modifier = Modifier.padding(16.dp)) }
        return
    }
    Card {
        items.forEachIndexed { index, item ->
            if (index > 0) HorizontalDivider(color = Wg.colors.line)
            Row(
                Modifier.fillMaxWidth().clickable { onOpen(item) }.heightIn(min = 48.dp).padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(Modifier.size(8.dp).background(groupColor(item.group), CircleShape))
                Text(
                    item.title,
                    fontSize = 14.sp,
                    color = if (item.done) Wg.colors.muted else Wg.colors.fg,
                    textDecoration = if (item.done) TextDecoration.LineThrough else null,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(item.time ?: t("calendar.allDay"), fontSize = 13.sp, color = Wg.colors.muted)
                item.occurrence?.assignee?.let { Avatar(it, 22.dp) }
            }
        }
    }
}

@Composable
private fun DetailDialog(
    item: CalendarItem,
    onDismiss: () -> Unit,
    onEdit: (CalendarEvent) -> Unit,
    onDelete: (CalendarEvent) -> Unit,
    onToggle: (Occurrence) -> Unit,
) {
    val app = LocalApp.current
    val household = LocalHousehold.current
    val t = LocalTexts.current
    val format = household.format
    val event = item.event
    val occurrence = item.occurrence
    val canManage = event != null && (household.isAdmin || event.createdBy.id == app.user?.id)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (event != null) {
                    Text(eventWhen(event, format), fontSize = 14.sp)
                    event.description?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 14.sp, color = Wg.colors.muted) }
                    if (event.participants.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            event.participants.forEach { p ->
                                Row(
                                    Modifier.border(1.dp, Wg.colors.line, RoundedCornerShape(50)).padding(start = 2.dp, end = 10.dp, top = 2.dp, bottom = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Avatar(p, 22.dp)
                                    Text(p.displayName, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                    Hint(t("calendar.createdBy", "name" to event.createdBy.displayName))
                }
                if (occurrence != null) {
                    Text(
                        if (occurrence.kind == "MAIN") t("chores.dueUntil", "date" to format.date(occurrence.dueDate)) else format.dateLong(occurrence.dueDate),
                        fontSize = 14.sp,
                    )
                    Hint(t("calendar.choreDetail"))
                    occurrence.description?.takeIf { it.isNotBlank() }?.let { Hint(it) }
                    Text(
                        occurrence.assignee?.let { t("calendar.assignedTo", "name" to it.displayName) } ?: t("calendar.unassigned"),
                        fontSize = 14.sp,
                    )
                }
            }
        },
        confirmButton = {
            when {
                occurrence != null -> TextButton(onClick = { onToggle(occurrence) }) {
                    Text(if (occurrence.status == "COMPLETED") t("chores.reopen") else t("chores.done"))
                }
                canManage -> TextButton(onClick = { onEdit(event!!) }) { Text(t("common.edit")) }
            }
        },
        dismissButton = {
            Row {
                if (canManage) TextButton(onClick = { onDelete(event!!) }) { Text(t("common.delete"), color = Wg.colors.danger) }
                TextButton(onClick = onDismiss) { Text(t("common.close")) }
            }
        },
    )
}

private fun eventWhen(event: CalendarEvent, format: Format): String {
    val startDay = format.instantDate(event.startsAt)
    val startDate = format.dateLong(startDay)
    val endDay = event.endsAt?.let(format::instantDate)
    if (event.allDay) {
        return if (endDay == null || endDay == startDay) startDate else "$startDate – ${format.dateLong(endDay)}"
    }
    val start = "$startDate, ${format.time(event.startsAt)}"
    val end = event.endsAt ?: return start
    return if (endDay == startDay) "$start – ${format.time(end)}" else "$start – ${format.dateLong(endDay!!)}, ${format.time(end)}"
}
