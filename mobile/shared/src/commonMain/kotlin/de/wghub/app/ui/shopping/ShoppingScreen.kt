package de.wghub.app.ui.shopping

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.wghub.app.AppState
import de.wghub.app.LocalApp
import de.wghub.app.data.ApiException
import de.wghub.app.data.PendingAction
import de.wghub.app.data.ShoppingItem
import de.wghub.app.data.ShoppingItemResponse
import de.wghub.app.data.ShoppingResponse
import de.wghub.app.data.json
import de.wghub.app.i18n.LocalTexts
import de.wghub.app.i18n.Texts
import de.wghub.app.i18n.formatCents
import de.wghub.app.i18n.formatDecimal
import de.wghub.app.ui.CardList
import de.wghub.app.ui.CheckCircle
import de.wghub.app.ui.ConfirmDialog
import de.wghub.app.ui.EmptyNote
import de.wghub.app.ui.ListRow
import de.wghub.app.ui.Loaded
import de.wghub.app.ui.Loader
import de.wghub.app.ui.LocalHousehold
import de.wghub.app.ui.MenuItem
import de.wghub.app.ui.RowMenu
import de.wghub.app.ui.RowMeta
import de.wghub.app.ui.RowTitle
import de.wghub.app.ui.ScreenColumn
import de.wghub.app.ui.SectionTitle
import de.wghub.app.ui.Wg
import de.wghub.app.ui.attempt
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.random.Random

/** "2 l": units are stored as codes; older free-text units are shown as they are. */
fun Texts.amount(quantity: Double?, unit: String?): String {
    val label = if (unit != null && has("shopping.units.$unit")) this("shopping.units.$unit") else unit.orEmpty()
    val count = quantity?.let { formatDecimal(it, locale) }.orEmpty()
    return "$count $label".trim()
}

/** Ticking items off, also without a connection. Shared by the shopping tab and the overview. */
class ShoppingActions(private val app: AppState, private val householdId: String) {
    private val base = "/households/$householdId/shopping"

    /** Items as the user should see them: with unsent ticks applied. */
    fun withPending(items: List<ShoppingItem>): List<ShoppingItem> {
        val waiting = app.repo.pending.value.filter { it.householdId == householdId && it.type == "shopping-status" }
            .associate { it.targetId to it.value }
        return items.map { item -> waiting[item.id]?.let { item.copy(status = it) } ?: item }
    }

    /** Returns the updated item, or null when kept for later or refused. */
    suspend fun setStatus(item: ShoppingItem, status: String, priceCents: Int? = null): ShoppingItem? {
        val body = buildJsonObject {
            put("status", status)
            if (priceCents != null) put("priceCents", priceCents)
        }.toString()
        val action = PendingAction(
            id = Random.nextLong().toULong().toString(16),
            method = "POST",
            path = "$base/${item.id}/status",
            body = body,
            type = "shopping-status",
            householdId = householdId,
            targetId = item.id,
            value = status,
        )
        return try {
            val response = app.repo.sendOrQueue(action)
            if (response == null) {
                app.onMessage(app.texts!!("app.queued"))
                null
            } else {
                json.decodeFromString(ShoppingItemResponse.serializer(), response).item
            }
        } catch (error: ApiException) {
            app.onMessage(app.errorText(error))
            null
        }
    }
}

/** Mirrors frontend/src/views/ShoppingView.vue. */
@Composable
fun ShoppingScreen() {
    val app = LocalApp.current
    val household = LocalHousehold.current
    val t = LocalTexts.current
    val scope = rememberCoroutineScope()
    val loader = remember(household.id) { Loader(app, "/households/${household.id}/shopping", ShoppingResponse.serializer()) }
    val actions = remember(household.id) { ShoppingActions(app, household.id) }
    app.repo.pending.collectAsState().value

    var showBought by rememberSaveable { mutableStateOf(false) }
    var formOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ShoppingItem?>(null) }
    var buying by remember { mutableStateOf<ShoppingItem?>(null) }
    var deleting by remember { mutableStateOf<ShoppingItem?>(null) }
    var puttingBack by remember { mutableStateOf<ShoppingItem?>(null) }
    var clearing by remember { mutableStateOf(false) }

    LaunchedEffect(loader, household.refreshTick) { loader.load() }
    fun reload() = scope.launch { loader.load() }

    fun toggle(item: ShoppingItem, status: String) = scope.launch {
        actions.setStatus(item, status)
        loader.load()
    }

    /** Buying your own personal item involves nobody else, so there is nothing to ask. */
    fun buy(item: ShoppingItem) {
        if (item.personal && item.createdBy.id == app.user?.id) toggle(item, "BOUGHT") else buying = item
    }

    ScreenColumn(onRefresh = { loader.load() }, addLabel = t("shopping.add"), onAdd = { editing = null; formOpen = true }) {
        Loaded(loader, ::reload) { response ->
            val items = actions.withPending(response.items)
            val pending = items.filter { it.status == "PENDING" }
            val bought = items.filter { it.status == "BOUGHT" }
            val sections = listOf(
                t("shopping.forWg") to pending.filter { !it.personal },
                t("shopping.personalSection") to pending.filter { it.personal },
            ).filter { it.second.isNotEmpty() }

            if (pending.isEmpty()) EmptyNote(t("shopping.emptyTitle"), t("shopping.emptyText"))
            sections.forEach { (title, list) ->
                Column {
                    SectionTitle(title)
                    CardList(list) { item ->
                        val details = listOfNotNull(
                            if (item.personal) t("shopping.personalFor", "name" to item.createdBy.displayName) else null,
                            item.note?.takeIf { it.isNotBlank() },
                        ).joinToString(" · ")
                        ListRow {
                            CheckCircle(checked = false, enabled = true) { buy(item) }
                            Column(Modifier.weight(1f)) {
                                RowTitle(item.name)
                                if (details.isNotEmpty()) RowMeta(details)
                            }
                            if (item.quantity != null) Text(t.amount(item.quantity, item.unit), fontSize = 14.sp, color = Wg.colors.muted)
                            RowMenu(
                                listOf(
                                    MenuItem(t("common.edit")) { editing = item; formOpen = true },
                                    MenuItem(t("common.delete"), danger = true) { deleting = item },
                                ),
                            )
                        }
                    }
                }
            }

            if (bought.isNotEmpty()) {
                Column {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            Modifier.weight(1f).clickable { showBought = !showBought }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(if (showBought) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = Wg.colors.muted)
                            Text(
                                t("shopping.boughtCount", "count" to bought.size),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Wg.colors.muted,
                            )
                        }
                        TextButton(onClick = { clearing = true }) { Text(t("shopping.clearBought")) }
                    }
                    if (showBought) {
                        CardList(bought) { item ->
                            ListRow {
                                CheckCircle(checked = true, enabled = true) {
                                    if (item.expense != null) puttingBack = item else toggle(item, "PENDING")
                                }
                                Text(
                                    item.name,
                                    fontSize = 14.sp,
                                    color = Wg.colors.muted,
                                    textDecoration = TextDecoration.LineThrough,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f),
                                )
                                item.expense?.let {
                                    Text(formatCents(it.amountCents, t.locale), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                }
                                item.boughtBy?.let { Text(it.displayName, fontSize = 13.sp, color = Wg.colors.muted) }
                            }
                        }
                    }
                }
            }
        }
    }

    if (formOpen) ShoppingForm(editing, onClose = { formOpen = false }, onSaved = ::reload)
    buying?.let { item -> BuyDialog(item, actions, onClose = { buying = null }, onBought = ::reload) }

    deleting?.let { item ->
        ConfirmDialog(
            title = t("shopping.deleteConfirm", "name" to item.name),
            confirmLabel = t("common.delete"),
            onDismiss = { deleting = null },
        ) {
            scope.launch {
                app.attempt { app.repo.call("DELETE", "/households/${household.id}/shopping/${item.id}") }
                loader.load()
            }
        }
    }
    puttingBack?.let { item ->
        ConfirmDialog(
            title = t("shopping.markPending", "name" to item.name),
            text = t("shopping.putBackText", "amount" to formatCents(item.expense?.amountCents ?: 0, t.locale)),
            confirmLabel = t("shopping.putBack"),
            danger = false,
            onDismiss = { puttingBack = null },
        ) { toggle(item, "PENDING") }
    }
    if (clearing) {
        ConfirmDialog(
            title = t("shopping.clearConfirm"),
            confirmLabel = t("shopping.clearBought"),
            onDismiss = { clearing = false },
        ) {
            scope.launch {
                if (app.attempt { app.repo.call("POST", "/households/${household.id}/shopping/clear-bought") }) {
                    app.onMessage(t("shopping.cleared"))
                }
                loader.load()
            }
        }
    }
}
