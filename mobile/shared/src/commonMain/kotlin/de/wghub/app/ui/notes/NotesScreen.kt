package de.wghub.app.ui.notes

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.wghub.app.LocalApp
import de.wghub.app.data.Note
import de.wghub.app.data.NotesResponse
import de.wghub.app.i18n.LocalTexts
import de.wghub.app.ui.Avatar
import de.wghub.app.ui.ConfirmDialog
import de.wghub.app.ui.EmptyNote
import de.wghub.app.ui.Field
import de.wghub.app.ui.FormSheet
import de.wghub.app.ui.Loaded
import de.wghub.app.ui.Loader
import de.wghub.app.ui.LocalHousehold
import de.wghub.app.ui.MenuItem
import de.wghub.app.ui.RowMenu
import de.wghub.app.ui.ScreenColumn
import de.wghub.app.ui.Segmented
import de.wghub.app.ui.Wg
import de.wghub.app.ui.attempt
import de.wghub.app.ui.fieldError
import de.wghub.app.ui.submit
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private val NOTE_COLORS = listOf("DEFAULT", "YELLOW", "GREEN", "BLUE", "PINK", "LILAC")

/** A note as a coloured card. Shared with the overview. */
@Composable
fun NoteCard(note: Note, maxLines: Int = Int.MAX_VALUE, menu: List<MenuItem> = emptyList()) {
    val household = LocalHousehold.current
    val t = LocalTexts.current
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Wg.colors.notes[note.color] ?: Wg.colors.surface,
        border = BorderStroke(1.dp, Wg.colors.line),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 14.dp, end = 4.dp, top = 12.dp, bottom = 4.dp)) {
            Row(Modifier.padding(end = 10.dp)) {
                if (note.pinned) Icon(Icons.Default.PushPin, t("notes.pinned"), Modifier.size(14.dp).padding(top = 2.dp), tint = Wg.colors.fg)
                // Shown as plain text, never as markup.
                Text(note.content, fontSize = 14.sp, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                Avatar(note.author, 22.dp)
                Text(
                    "${note.author.displayName}, ${household.format.dateTime(note.createdAt)}",
                    fontSize = 12.sp,
                    color = Wg.colors.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(vertical = 10.dp),
                )
                RowMenu(menu)
            }
        }
    }
}

/** Mirrors frontend/src/views/NotesView.vue. */
@Composable
fun NotesScreen() {
    val app = LocalApp.current
    val household = LocalHousehold.current
    val t = LocalTexts.current
    val scope = rememberCoroutineScope()
    var archive by rememberSaveable { mutableStateOf(false) }
    val loader = remember(household.id, archive) { Loader(app, "/households/${household.id}/notes?archived=$archive", NotesResponse.serializer()) }
    var formOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Note?>(null) }
    var deleting by remember { mutableStateOf<Note?>(null) }
    val base = "/households/${household.id}/notes"

    LaunchedEffect(loader, household.refreshTick) { loader.load() }

    fun change(method: String, path: String, body: String? = null) = scope.launch {
        app.attempt { app.repo.call(method, path, body) }
        loader.load()
    }

    fun menuFor(note: Note): List<MenuItem> {
        val isAuthor = note.author.id == app.user?.id
        return buildList {
            if (isAuthor && note.archivedAt == null) add(MenuItem(t("common.edit")) { editing = note; formOpen = true })
            if (note.archivedAt == null) {
                add(MenuItem(if (note.pinned) t("notes.unpin") else t("notes.pin")) {
                    change("POST", "$base/${note.id}/pin", buildJsonObject { put("pinned", !note.pinned) }.toString())
                })
            }
            if (isAuthor || household.isAdmin) {
                add(MenuItem(if (note.archivedAt != null) t("notes.unarchive") else t("notes.archive")) {
                    change("POST", "$base/${note.id}/archive", buildJsonObject { put("archived", note.archivedAt == null) }.toString())
                })
                add(MenuItem(t("common.delete"), danger = true) { deleting = note })
            }
        }
    }

    ScreenColumn(onRefresh = { loader.load() }, addLabel = t("notes.add"), onAdd = { editing = null; formOpen = true }) {
        Segmented(listOf(false to t("notes.showActive"), true to t("notes.showArchive")), archive) { archive = it }
        Loaded(loader, { scope.launch { loader.load() } }) { response ->
            if (response.notes.isEmpty()) {
                if (archive) EmptyNote(t("notes.emptyArchive")) else EmptyNote(t("notes.emptyTitle"), t("notes.emptyText"))
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                response.notes.forEach { NoteCard(it, menu = menuFor(it)) }
            }
        }
    }

    if (formOpen) NoteForm(editing, onClose = { formOpen = false }, onSaved = { scope.launch { loader.load() } })
    deleting?.let { note ->
        ConfirmDialog(t("notes.deleteConfirm"), confirmLabel = t("common.delete"), onDismiss = { deleting = null }) {
            change("DELETE", "$base/${note.id}")
        }
    }
}

/** Mirrors frontend/src/components/notes/NoteFormModal.vue. */
@Composable
fun NoteForm(note: Note?, onClose: () -> Unit, onSaved: () -> Unit) {
    val app = LocalApp.current
    val household = LocalHousehold.current
    val t = LocalTexts.current
    val scope = rememberCoroutineScope()
    var content by remember { mutableStateOf(note?.content ?: "") }
    var color by remember { mutableStateOf(note?.color ?: "DEFAULT") }
    var pinned by remember { mutableStateOf(false) }
    var errors by remember { mutableStateOf(emptyMap<String, String>()) }
    var busy by remember { mutableStateOf(false) }

    fun save() {
        busy = true
        scope.launch {
            val base = "/households/${household.id}/notes"
            errors = app.submit {
                if (note == null) {
                    app.repo.call("POST", base, buildJsonObject {
                        put("content", content); put("color", color); put("pinned", pinned)
                    }.toString())
                } else {
                    app.repo.call("PATCH", "$base/${note.id}", buildJsonObject { put("content", content); put("color", color) }.toString())
                }
            }
            busy = false
            if (errors.isEmpty()) {
                onClose()
                onSaved()
            }
        }
    }

    FormSheet(if (note == null) t("notes.newNote") else t("notes.editNote"), busy, onClose, ::save) {
        Field(content, { content = it }, t("notes.content"), t.fieldError(errors["content"]), t("notes.contentPlaceholder"), singleLine = false, minLines = 4)
        Column {
            Text(t("notes.color"), fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NOTE_COLORS.forEach { option ->
                    Box(
                        Modifier.size(44.dp)
                            .border(if (option == color) 2.dp else 1.dp, if (option == color) Wg.colors.accent else Wg.colors.line, CircleShape)
                            .background(Wg.colors.notes.getValue(option), CircleShape)
                            .clickable { color = option },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (option == color) Icon(Icons.Default.Check, t("notes.colors.$option"), Modifier.size(18.dp), tint = Wg.colors.fg)
                    }
                }
            }
        }
        if (note == null) {
            Row(Modifier.fillMaxWidth().clickable { pinned = !pinned }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = pinned, onCheckedChange = null, modifier = Modifier.padding(end = 10.dp, top = 8.dp, bottom = 8.dp))
                Text(t("notes.pinNew"), fontSize = 14.sp)
            }
        }
    }
}
