package de.wghub.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import de.wghub.app.AppState
import de.wghub.app.LocalApp
import de.wghub.app.data.ApiException
import de.wghub.app.data.Member
import de.wghub.app.data.UserSummary
import de.wghub.app.i18n.LocalTexts
import de.wghub.app.i18n.Texts
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.KSerializer
import kotlin.time.Instant

/** One server resource: shows the saved copy at once, then the fresh one. */
class Loader<T>(private val app: AppState, val path: String, private val serializer: KSerializer<T>) {
    var value by mutableStateOf(app.repo.cached(path, serializer))
        private set
    var failed by mutableStateOf(false)
        private set

    suspend fun load() {
        try {
            value = app.repo.fetch(path, serializer)
            failed = false
        } catch (error: ApiException) {
            failed = value == null
            if (!error.isOffline && value != null) app.onMessage(app.errorText(error))
        }
    }
}

/** Runs a change against the server; shows the error and returns false if it fails. */
suspend fun AppState.attempt(block: suspend () -> Unit): Boolean =
    try {
        block()
        true
    } catch (error: ApiException) {
        onMessage(errorText(error))
        false
    }

/**
 * Runs a form submit. Returns field errors from the server (empty when saved);
 * other failures are shown as a message and returned under the key "".
 */
suspend fun AppState.submit(block: suspend () -> Unit): Map<String, String> =
    try {
        block()
        emptyMap()
    } catch (error: ApiException) {
        if (error.fields.isNotEmpty()) error.fields
        else {
            onMessage(errorText(error))
            mapOf("" to error.code)
        }
    }

fun Texts.fieldError(code: String?) = code?.let { if (has("validation.$it")) this("validation.$it") else this("validation.invalid_value") }

fun Member.summary() = UserSummary(id, displayName, avatarColor)

/**
 * The body of a tab: pull to refresh, the offline note, then the content.
 * [onAdd] shows the round "+" button.
 */
@Composable
fun ScreenColumn(
    onRefresh: suspend () -> Unit,
    addLabel: String? = null,
    onAdd: () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val pending by app.repo.pending.collectAsState()
    val offline by app.repo.offline.collectAsState()
    var refreshing by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                refreshing = true
                scope.launch {
                    app.repo.flush()
                    onRefresh()
                    refreshing = false
                }
            },
        ) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = if (addLabel != null) 96.dp else 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (offline || pending.isNotEmpty()) OfflineBanner(pending.size)
                content()
            }
        }
        if (addLabel != null) {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text(addLabel) },
                containerColor = Wg.colors.accent,
                contentColor = Wg.colors.accentFg,
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
    }
}

@Composable
fun Loading() {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

/** Shows content when [loader] has data, a retry box when nothing could be loaded. */
@Composable
fun <T> Loaded(loader: Loader<T>, onRetry: () -> Unit, content: @Composable (T) -> Unit) {
    val value = loader.value
    when {
        value != null -> content(value)
        loader.failed -> LoadFailed(onRetry)
        else -> Loading()
    }
}

/** A yes/no question before something that cannot easily be undone. */
@Composable
fun ConfirmDialog(
    title: String,
    text: String? = null,
    confirmLabel: String,
    danger: Boolean = true,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val t = LocalTexts.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = text?.let { { Text(it) } },
        confirmButton = {
            TextButton(onClick = { onDismiss(); onConfirm() }) {
                Text(confirmLabel, color = if (danger) Wg.colors.danger else Wg.colors.accentText)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("common.cancel")) } },
    )
}

/** A form that slides up from the bottom, with Cancel and Save. */
@Composable
fun FormSheet(
    title: String,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    saveLabel: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val t = LocalTexts.current
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = Wg.colors.surface) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            content()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                OutlinedButton(onClick = onDismiss) { Text(t("common.cancel")) }
                Button(onClick = onSave, enabled = !busy) {
                    if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text(saveLabel ?: t("common.save"))
                }
            }
        }
    }
}

@Composable
fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    error: String? = null,
    placeholder: String? = null,
    optional: Boolean = false,
    number: Boolean = false,
    password: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    val t = LocalTexts.current
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(if (optional) "$label (${t("common.optional")})" else label) },
        placeholder = placeholder?.let { { Text(it) } },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        singleLine = singleLine,
        minLines = minLines,
        visualTransformation = if (password) androidx.compose.ui.text.input.PasswordVisualTransformation()
        else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = when {
                password -> KeyboardType.Password
                number -> KeyboardType.Decimal
                else -> KeyboardType.Text
            },
        ),
        modifier = modifier,
    )
}

@Composable
fun <T> Select(value: T, options: List<Pair<T, String>>, label: String, modifier: Modifier = Modifier.fillMaxWidth(), onChange: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }, modifier = modifier) {
        OutlinedTextField(
            value = options.firstOrNull { it.first == value }?.second ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (option, text) ->
                DropdownMenuItem(text = { Text(text) }, onClick = {
                    onChange(option)
                    open = false
                })
            }
        }
    }
}

/** A read-only field that opens a picker when tapped. */
@Composable
fun PickerField(text: String, label: String, error: String? = null, modifier: Modifier = Modifier.fillMaxWidth(), onClick: () -> Unit) {
    Box(modifier) {
        OutlinedTextField(
            value = text,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth(),
        )
        Box(Modifier.matchParentSize().clickable(onClick = onClick))
    }
}

/** Date field showing e.g. "Fr., 7. Nov.", storing "2026-11-07". */
@Composable
fun DateField(value: String, label: String, error: String? = null, modifier: Modifier = Modifier.fillMaxWidth(), onChange: (String) -> Unit) {
    val household = LocalHousehold.current
    val t = LocalTexts.current
    var picking by remember { mutableStateOf(false) }
    PickerField(household.format.date(value), label, error, modifier) { picking = true }
    if (picking) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = LocalDate.parse(value).atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds(),
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        onChange(Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC).date.toString())
                    }
                    picking = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(t("common.cancel")) } },
        ) { DatePicker(state) }
    }
}

/** Time field "14:30". */
@Composable
fun TimeField(value: String, label: String, modifier: Modifier = Modifier.fillMaxWidth(), onChange: (String) -> Unit) {
    val t = LocalTexts.current
    var picking by remember { mutableStateOf(false) }
    PickerField(value, label, modifier = modifier) { picking = true }
    if (picking) {
        val (h, m) = value.split(":").map { it.toIntOrNull() ?: 0 }.let { (it.getOrElse(0) { 0 }) to (it.getOrElse(1) { 0 }) }
        val state = rememberTimePickerState(initialHour = h, initialMinute = m, is24Hour = true)
        AlertDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    onChange("${state.hour.toString().padStart(2, '0')}:${state.minute.toString().padStart(2, '0')}")
                    picking = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(t("common.cancel")) } },
            text = { TimePicker(state) },
        )
    }
}

/** Tick members; with [showOrder] the order of ticking is shown (like MemberPicker on the website). */
@Composable
fun MemberPicker(
    members: List<Member>,
    selected: List<String>,
    label: String,
    hint: String? = null,
    error: String? = null,
    showOrder: Boolean = false,
    onChange: (List<String>) -> Unit,
) {
    Column {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        if (hint != null) Text(hint, fontSize = 13.sp, color = Wg.colors.muted)
        members.forEach { member ->
            val position = selected.indexOf(member.id)
            Row(
                Modifier.fillMaxWidth().clickable { onChange(if (position >= 0) selected - member.id else selected + member.id) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = position >= 0, onCheckedChange = null, modifier = Modifier.padding(12.dp))
                Avatar(member.summary(), 24.dp)
                Text(member.displayName, fontSize = 14.sp, modifier = Modifier.weight(1f).padding(start = 8.dp))
                if (showOrder && position >= 0 && selected.size > 1) {
                    Text("${position + 1}", fontSize = 13.sp, color = Wg.colors.muted, modifier = Modifier.padding(end = 8.dp))
                }
            }
        }
        if (error != null) Text(error, fontSize = 12.sp, color = Wg.colors.danger)
    }
}

/** A row in a card that opens something, with a chevron. */
@Composable
fun LinkRow(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector?, danger: Boolean = false, onClick: () -> Unit) {
    ListRow(Modifier.clickable(onClick = onClick)) {
        if (icon != null) Icon(icon, null, tint = if (danger) Wg.colors.danger else Wg.colors.muted)
        Text(label, fontSize = 15.sp, color = if (danger) Wg.colors.danger else Wg.colors.fg, modifier = Modifier.weight(1f))
    }
}

@Composable
fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(text, fontSize = 13.sp, color = Wg.colors.muted, modifier = modifier)
}
