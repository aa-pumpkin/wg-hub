package de.wghub.app.ui.expenses

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.wghub.app.LocalApp
import de.wghub.app.data.Balances
import de.wghub.app.data.Expense
import de.wghub.app.data.ExpensesResponse
import de.wghub.app.data.Settlement
import de.wghub.app.data.SettlementsResponse
import de.wghub.app.data.SuggestedPayment
import de.wghub.app.i18n.LocalTexts
import de.wghub.app.i18n.formatCents
import de.wghub.app.ui.Avatar
import de.wghub.app.ui.Card
import de.wghub.app.ui.CardList
import de.wghub.app.ui.ConfirmDialog
import de.wghub.app.ui.EmptyNote
import de.wghub.app.ui.ListRow
import de.wghub.app.ui.LoadFailed
import de.wghub.app.ui.Loader
import de.wghub.app.ui.Loading
import de.wghub.app.ui.LocalHousehold
import de.wghub.app.ui.MenuItem
import de.wghub.app.ui.RowMenu
import de.wghub.app.ui.RowMeta
import de.wghub.app.ui.ScreenColumn
import de.wghub.app.ui.SectionTitle
import de.wghub.app.ui.Wg
import de.wghub.app.ui.attempt
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private data class Confirm(val title: String, val text: String?, val label: String, val danger: Boolean, val action: suspend () -> Unit)

/** Mirrors frontend/src/views/ExpensesView.vue. */
@Composable
fun ExpensesScreen() {
    val app = LocalApp.current
    val household = LocalHousehold.current
    val t = LocalTexts.current
    val scope = rememberCoroutineScope()
    val money = { cents: Int -> formatCents(cents, t.locale) }
    val base = "/households/${household.id}"
    val expenses = remember(household.id) { Loader(app, "$base/expenses", ExpensesResponse.serializer()) }
    val settlements = remember(household.id) { Loader(app, "$base/settlements", SettlementsResponse.serializer()) }
    val balances = remember(household.id) { Loader(app, "$base/balances", Balances.serializer()) }
    var expenseOpen by remember { mutableStateOf(false) }
    var settlementOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Expense?>(null) }
    var confirm by remember { mutableStateOf<Confirm?>(null) }

    suspend fun load() {
        expenses.load()
        settlements.load()
        balances.load()
    }
    LaunchedEffect(household.id, household.refreshTick) { load() }

    fun run(block: suspend () -> Unit) = scope.launch {
        app.attempt(block)
        load()
    }

    val me = app.user?.id
    val today = household.format.today().toString()

    ScreenColumn(onRefresh = ::load, addLabel = t("expenses.add"), onAdd = { editing = null; expenseOpen = true }) {
        val data = balances.value
        val expenseList = expenses.value?.expenses
        val settlementList = settlements.value?.settlements
        if (data == null || expenseList == null || settlementList == null) {
            if (balances.failed || expenses.failed || settlements.failed) LoadFailed { scope.launch { load() } } else Loading()
            return@ScreenColumn
        }
        val nameOf = { id: String -> data.balances.firstOrNull { it.user.id == id }?.user?.displayName ?: "–" }
        val beerFor = { p: SuggestedPayment -> data.beerRequests.firstOrNull { it.fromUserId == p.fromUserId && it.toUserId == p.toUserId } }

        Column {
            SectionTitle(t("expenses.balances"))
            CardList(data.balances.sortedByDescending { it.cents }) { balance ->
                ListRow {
                    Avatar(balance.user)
                    Text(
                        balance.user.displayName + if (household.members.none { it.id == balance.user.id }) " (${t("expenses.formerMember")})" else "",
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        (if (balance.cents > 0) "+" else "") + money(balance.cents),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = when {
                            balance.cents > 0 -> Wg.colors.accentText
                            balance.cents < 0 -> Wg.colors.danger
                            else -> Wg.colors.muted
                        },
                    )
                }
            }
        }

        Column {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SectionTitle(t("expenses.settleUp"), Modifier.weight(1f))
                TextButton(onClick = { settlementOpen = true }) { Text(t("expenses.recordPayment")) }
            }
            if (data.suggestions.isEmpty()) {
                Card { Text(t("expenses.allEven"), fontSize = 14.sp, color = Wg.colors.muted, modifier = Modifier.padding(16.dp)) }
            } else {
                Card {
                    data.suggestions.forEachIndexed { index, payment ->
                        if (index > 0) HorizontalDivider(color = Wg.colors.line)
                        val beer = beerFor(payment)
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(nameOf(payment.fromUserId), fontSize = 14.sp, maxLines = 1)
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(14.dp), tint = Wg.colors.muted)
                                Text(nameOf(payment.toUserId), fontSize = 14.sp, maxLines = 1, modifier = Modifier.weight(1f))
                                Text(money(payment.amountCents), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            }
                            if (beer != null) {
                                Text(t("expenses.beerAsked", "name" to nameOf(payment.toUserId), "count" to beer.beers), fontSize = 13.sp, color = Wg.colors.warn)
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (beer != null && payment.toUserId == me) {
                                    Button(onClick = { run { app.repo.call("POST", "$base/beer-requests/${beer.id}/received") } }) { Text(t("expenses.beerReceived")) }
                                    OutlinedButton(onClick = { run { app.repo.call("DELETE", "$base/beer-requests/${beer.id}") } }) { Text(t("expenses.beerCancel")) }
                                } else {
                                    OutlinedButton(onClick = {
                                        confirm = Confirm(
                                            t("expenses.markPaidConfirm", "from" to nameOf(payment.fromUserId), "to" to nameOf(payment.toUserId), "amount" to money(payment.amountCents)),
                                            t("expenses.markPaidConfirmText"),
                                            t("expenses.confirmPaid"),
                                            false,
                                        ) {
                                            app.repo.call("POST", "$base/settlements", settlementBody(payment, today, null))
                                            app.onMessage(t("expenses.saved"))
                                        }
                                    }) { Text(t("expenses.markPaid")) }
                                }
                                if (payment.toUserId == me && beer == null) {
                                    OutlinedButton(onClick = {
                                        val price = household.detail.household.beerPriceCents.takeIf { it > 0 } ?: 150
                                        confirm = Confirm(
                                            t("expenses.askBeerConfirm", "name" to nameOf(payment.fromUserId), "count" to (payment.amountCents + price - 1) / price),
                                            t("expenses.askBeerText", "price" to money(price)),
                                            t("expenses.askBeer"),
                                            false,
                                        ) {
                                            app.repo.call("POST", "$base/beer-requests", buildJsonObject {
                                                put("fromUserId", payment.fromUserId); put("amountCents", payment.amountCents)
                                            }.toString())
                                        }
                                    }) { Text(t("expenses.askBeer")) }
                                    TextButton(onClick = {
                                        confirm = Confirm(
                                            t("expenses.forgiveConfirm", "name" to nameOf(payment.fromUserId), "amount" to money(payment.amountCents)),
                                            null,
                                            t("expenses.forgive"),
                                            false,
                                        ) {
                                            app.repo.call("POST", "$base/settlements", settlementBody(payment, today, "FORGIVEN"))
                                            app.onMessage(t("expenses.forgiven"))
                                        }
                                    }) { Text(t("expenses.forgive")) }
                                }
                            }
                        }
                    }
                }
            }
        }

        Column {
            SectionTitle(t("expenses.history"))
            val history = (expenseList.map { it.date to (it as Any) } + settlementList.map { it.date to (it as Any) })
                .sortedByDescending { it.first }
            if (history.isEmpty()) EmptyNote(t("expenses.emptyTitle"), t("expenses.emptyText"))
            else CardList(history) { (date, entry) ->
                val canManage = { createdById: String -> household.isAdmin || createdById == me }
                ListRow {
                    when (entry) {
                        is Expense -> {
                            Avatar(entry.paidBy)
                            Column(Modifier.weight(1f)) {
                                Row {
                                    Text(entry.description, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    Text(money(entry.amountCents), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                }
                                RowMeta("${household.format.date(date)} · ${t("expenses.paidByLine", "name" to entry.paidBy.displayName, "count" to entry.participants.size)}")
                                entry.participants.firstOrNull { it.user.id == me }?.let { RowMeta(t("expenses.yourShare", "amount" to money(it.shareCents))) }
                            }
                            if (canManage(entry.createdById)) {
                                RowMenu(listOf(
                                    MenuItem(t("common.edit")) { editing = entry; expenseOpen = true },
                                    MenuItem(t("common.delete"), danger = true) {
                                        confirm = Confirm(t("expenses.deleteConfirm", "title" to entry.description), null, t("common.delete"), true) {
                                            app.repo.call("DELETE", "$base/expenses/${entry.id}")
                                            app.onMessage(t("expenses.deleted"))
                                        }
                                    },
                                ))
                            }
                        }
                        is Settlement -> {
                            Box(Modifier.size(28.dp).background(Wg.colors.accentSoft, CircleShape), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Handshake, null, Modifier.size(16.dp), tint = Wg.colors.accentText)
                            }
                            Column(Modifier.weight(1f)) {
                                Row {
                                    Text(settlementText(entry, t), fontSize = 14.sp, maxLines = 2, modifier = Modifier.weight(1f))
                                    Text(money(entry.amountCents), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                }
                                RowMeta(household.format.date(date) + (entry.note?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""))
                            }
                            if (canManage(entry.createdById)) {
                                RowMenu(listOf(MenuItem(t("common.delete"), danger = true) {
                                    confirm = Confirm(t("expenses.deleteSettlementConfirm"), null, t("common.delete"), true) {
                                        app.repo.call("DELETE", "$base/settlements/${entry.id}")
                                    }
                                }))
                            }
                        }
                    }
                }
            }
        }
    }

    if (expenseOpen) ExpenseForm(editing, onClose = { expenseOpen = false }, onSaved = { scope.launch { load() } })
    if (settlementOpen) SettlementForm(onClose = { settlementOpen = false }, onSaved = { scope.launch { load() } })
    confirm?.let { c ->
        ConfirmDialog(c.title, c.text, c.label, c.danger, onDismiss = { confirm = null }) { run(c.action) }
    }
}

private fun settlementBody(payment: SuggestedPayment, date: String, kind: String?) = buildJsonObject {
    put("fromUserId", payment.fromUserId)
    put("toUserId", payment.toUserId)
    put("amountCents", payment.amountCents)
    put("date", date)
    if (kind != null) put("kind", kind)
}.toString()

private fun settlementText(s: Settlement, t: de.wghub.app.i18n.Texts): String {
    val names = arrayOf("from" to s.fromUser.displayName, "to" to s.toUser.displayName)
    return when (s.kind) {
        "FORGIVEN" -> t("expenses.forgivenLine", *names)
        "BEER" -> t("expenses.beerLine", *names, "count" to (s.beers ?: 0))
        else -> t("expenses.settlementLine", *names)
    }
}
