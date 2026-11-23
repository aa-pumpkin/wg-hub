package de.wghub.app.ui.chores

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.wghub.app.LocalApp
import de.wghub.app.data.Chore
import de.wghub.app.data.ChoreInput
import de.wghub.app.data.UserSummary
import de.wghub.app.i18n.LocalTexts
import de.wghub.app.i18n.Texts
import de.wghub.app.ui.Avatar
import de.wghub.app.ui.LocalHousehold
import de.wghub.app.ui.Segmented
import de.wghub.app.ui.Wg
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

private val RECURRENCES = listOf("NONE", "DAILY", "WEEKLY", "BIWEEKLY", "MONTHLY")
private val DEFAULT_POINTS = mapOf("MAIN" to 10, "EXTRA" to 20)

private fun fieldError(t: Texts, code: String?) = code?.let {
    if (t.has("validation.$it")) t("validation.$it") else t("validation.invalid_value")
}

/** Mirrors frontend/src/components/chores/ChoreFormModal.vue. */
@Composable
internal fun ChoreForm(chore: Chore?, model: ChoresModel, onClose: () -> Unit) {
    val app = LocalApp.current
    val household = LocalHousehold.current
    val t = LocalTexts.current
    val scope = rememberCoroutineScope()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val members = household.detail.members
    val isAdmin = household.isAdmin

    var title by remember { mutableStateOf(chore?.title ?: "") }
    var description by remember { mutableStateOf(chore?.description ?: "") }
    var recurrence by remember { mutableStateOf(chore?.recurrence ?: "MONTHLY") }
    var startDate by remember { mutableStateOf(chore?.startDate ?: household.format.today().toString()) }
    // By default extra tasks go around everyone, in the order of the circle. Only current members can be picked.
    var rotation by remember {
        mutableStateOf(
            chore?.rotation?.map { it.id }?.filter { id -> members.any { it.id == id } }
                ?: members.filter { !it.frozen }.map { it.id },
        )
    }
    var kind by remember { mutableStateOf(chore?.kind ?: if (isAdmin) "MAIN" else "EXTRA") }
    var points by remember { mutableStateOf((chore?.points ?: if (isAdmin) DEFAULT_POINTS.getValue("MAIN") else 0).toString()) }
    var errors by remember { mutableStateOf(emptyMap<String, String>()) }
    var moreOpen by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var pickingDate by remember { mutableStateOf(false) }
    val isMain = kind == "MAIN"

    fun close() {
        scope.launch { sheet.hide() }.invokeOnCompletion { onClose() }
    }

    fun submit() {
        if (busy) return
        busy = true
        scope.launch {
            val input = ChoreInput(
                title = title,
                description = description,
                recurrence = recurrence,
                startDate = startDate,
                rotation = rotation,
                kind = if (chore == null) kind else null,
                points = if (isAdmin) points.toIntOrNull() ?: -1 else null,
            )
            errors = model.save(chore, input)
            busy = false
            if (errors.isEmpty()) close()
            else if (errors.keys.any { it in setOf("startDate", "rotation", "points", "description") }) moreOpen = true
        }
    }

    ModalBottomSheet(onDismissRequest = onClose, sheetState = sheet, containerColor = Wg.colors.surface) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(if (chore == null) t("chores.newChore") else t("chores.editChore"), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(t("chores.name")) },
                placeholder = { Text(t("chores.namePlaceholder")) },
                isError = "title" in errors,
                supportingText = fieldError(t, errors["title"])?.let { { Text(it) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            if (isAdmin && chore == null) {
                Column {
                    Segmented(listOf("MAIN" to t("chores.kindMain"), "EXTRA" to t("chores.kindExtra")), kind, Modifier.fillMaxWidth()) {
                        kind = it
                        // A new task starts with the usual points for its kind, until the admin changes them.
                        points = DEFAULT_POINTS.getValue(it).toString()
                    }
                    Text(
                        if (isMain) t("chores.mainHint") else t("chores.extraHint"),
                        fontSize = 13.sp,
                        color = Wg.colors.muted,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }

            if (!isMain) RecurrenceSelect(recurrence) { recurrence = it }

            Row(
                Modifier.fillMaxWidth().clickable { moreOpen = !moreOpen }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(t("common.moreOptions"), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Wg.colors.accentText)
                Icon(if (moreOpen) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = Wg.colors.accentText)
            }

            if (moreOpen) {
                if (!isMain) {
                    Box {
                        OutlinedTextField(
                            value = household.format.date(startDate),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(if (recurrence == "NONE") t("chores.startDate") else t("chores.startDateSeries")) },
                            isError = "startDate" in errors,
                            supportingText = fieldError(t, errors["startDate"])?.let { { Text(it) } },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        // Covers the field, so a tap opens the calendar instead of the keyboard.
                        Box(Modifier.matchParentSize().clickable { pickingDate = true })
                    }
                    RotationPicker(rotation, errors["rotation"]?.let { fieldError(t, it) }) { rotation = it }
                }
                if (isAdmin) {
                    OutlinedTextField(
                        value = points,
                        onValueChange = { value -> points = value.filter(Char::isDigit).take(4) },
                        label = { Text(t("chores.points")) },
                        isError = "points" in errors,
                        supportingText = fieldError(t, errors["points"])?.let { { Text(it) } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text(t("chores.memberPointsHint"), fontSize = 13.sp, color = Wg.colors.muted)
                }
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("${t("chores.description")} (${t("common.optional")})") },
                    isError = "description" in errors,
                    supportingText = fieldError(t, errors["description"])?.let { { Text(it) } },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                OutlinedButton(onClick = ::close) { Text(t("common.cancel")) }
                Button(onClick = ::submit, enabled = !busy) {
                    if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text(t("common.save"))
                }
            }
        }
    }

    if (pickingDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = LocalDate.parse(startDate).atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds(),
        )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        startDate = Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC).date.toString()
                    }
                    pickingDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text(t("common.cancel")) } },
        ) { DatePicker(state) }
    }
}

@Composable
private fun RecurrenceSelect(value: String, onChange: (String) -> Unit) {
    val t = LocalTexts.current
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        OutlinedTextField(
            value = t("chores.recurrences.$value"),
            onValueChange = {},
            readOnly = true,
            label = { Text(t("chores.recurrence")) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            RECURRENCES.forEach { option ->
                DropdownMenuItem(text = { Text(t("chores.recurrences.$option")) }, onClick = {
                    onChange(option)
                    open = false
                })
            }
        }
    }
}

/** Who takes turns, numbered in the order they were picked (like MemberPicker on the website). */
@Composable
private fun RotationPicker(selected: List<String>, error: String?, onChange: (List<String>) -> Unit) {
    val household = LocalHousehold.current
    val t = LocalTexts.current
    Column {
        Text(t("chores.responsible"), fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Text(t("chores.rotationHint"), fontSize = 13.sp, color = Wg.colors.muted)
        household.detail.members.forEach { member ->
            val position = selected.indexOf(member.id)
            Row(
                Modifier.fillMaxWidth().clickable {
                    onChange(if (position >= 0) selected - member.id else selected + member.id)
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = position >= 0, onCheckedChange = null, modifier = Modifier.padding(12.dp))
                Avatar(UserSummary(member.id, member.displayName, member.avatarColor), 24.dp)
                Text(member.displayName, fontSize = 14.sp, modifier = Modifier.weight(1f).padding(start = 8.dp))
                if (position >= 0 && selected.size > 1) {
                    Text("${position + 1}", fontSize = 13.sp, color = Wg.colors.muted, modifier = Modifier.padding(end = 8.dp))
                }
            }
        }
        if (error != null) Text(error, fontSize = 12.sp, color = Wg.colors.danger)
    }
}
