package de.wghub.app.ui.calendar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.wghub.app.LocalApp
import de.wghub.app.data.CalendarEvent
import de.wghub.app.i18n.LocalTexts
import de.wghub.app.ui.DateField
import de.wghub.app.ui.Field
import de.wghub.app.ui.FormSheet
import de.wghub.app.ui.LocalHousehold
import de.wghub.app.ui.MemberPicker
import de.wghub.app.ui.PickerField
import de.wghub.app.ui.Select
import de.wghub.app.ui.TimeField
import de.wghub.app.ui.Wg
import de.wghub.app.ui.fieldError
import de.wghub.app.ui.submit
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Mirrors frontend/src/components/calendar/EventFormModal.vue. */
@Composable
fun EventForm(event: CalendarEvent?, initialDate: String?, onClose: () -> Unit, onSaved: () -> Unit) {
    val app = LocalApp.current
    val household = LocalHousehold.current
    val t = LocalTexts.current
    val format = household.format
    val scope = rememberCoroutineScope()
    val start = event?.let { format.wallClock(it.startsAt) }
    val end = event?.endsAt?.let(format::wallClock)

    var title by remember { mutableStateOf(event?.title ?: "") }
    var description by remember { mutableStateOf(event?.description ?: "") }
    var category by remember { mutableStateOf(event?.category ?: "WG") }
    var allDay by remember { mutableStateOf(event?.allDay ?: false) }
    var startDate by remember { mutableStateOf(start?.take(10) ?: initialDate ?: format.today().toString()) }
    var startTime by remember { mutableStateOf(start?.drop(11) ?: "19:00") }
    var endDate by remember { mutableStateOf(end?.take(10) ?: "") }
    var endTime by remember { mutableStateOf(end?.drop(11) ?: "") }
    var participants by remember {
        mutableStateOf(event?.participants?.map { it.id }?.filter { id -> household.members.any { it.id == id } } ?: emptyList())
    }
    var errors by remember { mutableStateOf(emptyMap<String, String>()) }
    var more by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    /** Wall-clock values for the API; the backend applies the household's time zone. */
    fun endValue(): String? = when {
        allDay -> endDate.takeIf { it.isNotEmpty() }?.let { "${it}T00:00" }
        endTime.isEmpty() -> null
        else -> "${endDate.ifEmpty { startDate }}T$endTime"
    }

    fun save() {
        busy = true
        scope.launch {
            val body = buildJsonObject {
                put("title", title)
                put("description", description)
                put("category", category)
                put("allDay", allDay)
                put("startsAt", "${startDate}T${if (allDay) "00:00" else startTime}")
                put("endsAt", endValue()?.let(::JsonPrimitive) ?: JsonNull)
                put("participantIds", JsonArray(participants.map(::JsonPrimitive)))
            }.toString()
            val base = "/households/${household.id}/events"
            errors = app.submit {
                if (event == null) app.repo.call("POST", base, body) else app.repo.call("PATCH", "$base/${event.id}", body)
            }
            busy = false
            if (errors.isEmpty()) {
                app.onMessage(t("calendar.saved"))
                onClose()
                onSaved()
            } else if (errors.keys.any { it == "endsAt" || it == "description" }) more = true
        }
    }

    FormSheet(if (event == null) t("calendar.newEvent") else t("calendar.editEvent"), busy, onClose, ::save) {
        Field(title, { title = it }, t("calendar.name"), t.fieldError(errors["title"]), t("calendar.namePlaceholder"))
        Row(Modifier.fillMaxWidth().clickable { allDay = !allDay }, verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = allDay, onCheckedChange = null, modifier = Modifier.padding(end = 10.dp, top = 8.dp, bottom = 8.dp))
            Text(t("calendar.allDay"), fontSize = 14.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DateField(startDate, t("calendar.startDate"), t.fieldError(errors["startsAt"]), Modifier.weight(1f)) { startDate = it }
            if (!allDay) TimeField(startTime, t("calendar.time"), Modifier.weight(0.6f)) { startTime = it }
        }
        Row(
            Modifier.fillMaxWidth().clickable { more = !more }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(t("common.moreOptions"), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Wg.colors.accentText)
            Icon(if (more) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = Wg.colors.accentText)
        }
        if (more) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val endLabel = "${if (allDay) t("calendar.endDate") else t("calendar.end")} (${t("common.optional")})"
                if (endDate.isEmpty()) {
                    PickerField("", endLabel, t.fieldError(errors["endsAt"]), Modifier.weight(1f)) { endDate = startDate }
                } else {
                    DateField(endDate, endLabel, t.fieldError(errors["endsAt"]), Modifier.weight(1f)) { endDate = it }
                }
                if (!allDay) {
                    if (endTime.isEmpty()) PickerField("", t("calendar.time"), modifier = Modifier.weight(0.6f)) { endTime = startTime }
                    else TimeField(endTime, t("calendar.time"), Modifier.weight(0.6f)) { endTime = it }
                }
            }
            Select(category, listOf("WG", "CHORE", "OTHER").map { it to t("calendar.categories.$it") }, t("calendar.category")) { category = it }
            MemberPicker(household.members, participants, t("calendar.participants")) { participants = it }
            Field(description, { description = it }, t("calendar.description"), t.fieldError(errors["description"]), optional = true, singleLine = false, minLines = 2)
        }
    }
}
