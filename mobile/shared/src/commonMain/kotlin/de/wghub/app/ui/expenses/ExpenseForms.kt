package de.wghub.app.ui.expenses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.wghub.app.LocalApp
import de.wghub.app.data.Expense
import de.wghub.app.i18n.LocalTexts
import de.wghub.app.i18n.centsToInput
import de.wghub.app.i18n.formatCents
import de.wghub.app.i18n.parseEurosToCents
import de.wghub.app.ui.DateField
import de.wghub.app.ui.Field
import de.wghub.app.ui.FormSheet
import de.wghub.app.ui.LocalHousehold
import de.wghub.app.ui.MemberPicker
import de.wghub.app.ui.Segmented
import de.wghub.app.ui.Select
import de.wghub.app.ui.Wg
import de.wghub.app.ui.fieldError
import de.wghub.app.ui.submit
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Mirrors frontend/src/components/expenses/ExpenseFormModal.vue. */
@Composable
fun ExpenseForm(expense: Expense?, onClose: () -> Unit, onSaved: () -> Unit) {
    val app = LocalApp.current
    val household = LocalHousehold.current
    val t = LocalTexts.current
    val scope = rememberCoroutineScope()
    val money = { cents: Int -> formatCents(cents, t.locale) }

    var description by remember { mutableStateOf(expense?.description ?: "") }
    var amount by remember { mutableStateOf(expense?.let { centsToInput(it.amountCents, t.locale) } ?: "") }
    var paidById by remember { mutableStateOf(expense?.paidBy?.id ?: app.user?.id ?: "") }
    var date by remember { mutableStateOf(expense?.date ?: household.format.today().toString()) }
    var splitMode by remember { mutableStateOf(expense?.splitMode ?: "EQUAL") }
    var participantIds by remember {
        mutableStateOf(expense?.participants?.map { it.user.id } ?: household.active.map { it.id })
    }
    var shares by remember {
        mutableStateOf(expense?.participants?.associate { it.user.id to centsToInput(it.shareCents, t.locale) } ?: emptyMap())
    }
    var errors by remember { mutableStateOf(emptyMap<String, String>()) }
    var busy by remember { mutableStateOf(false) }

    val cents = parseEurosToCents(amount)
    val perPerson = if (cents != null && cents > 0 && participantIds.isNotEmpty()) money((cents + participantIds.size - 1) / participantIds.size) else null
    val remaining: Int? = cents?.let { total ->
        var assigned = 0
        for (id in participantIds) {
            val share = shares[id]?.trim().orEmpty()
            if (share.isEmpty()) continue
            assigned += parseEurosToCents(share) ?: return@let null
        }
        total - assigned
    }

    fun save() {
        if (cents == null || cents <= 0) {
            errors = mapOf("amountCents" to "invalid_amount"); return
        }
        if (participantIds.isEmpty()) {
            errors = mapOf("participants" to "pick_one"); return
        }
        val participants = participantIds.map { id ->
            buildJsonObject {
                put("userId", id)
                if (splitMode == "CUSTOM") {
                    val share = parseEurosToCents(shares[id]?.trim()?.ifEmpty { "0" } ?: "0")
                    if (share == null) {
                        errors = mapOf("participants" to "invalid_amount"); return
                    }
                    put("shareCents", share)
                }
            }
        }
        busy = true
        scope.launch {
            val body = buildJsonObject {
                put("description", description)
                put("amountCents", cents)
                put("paidById", paidById)
                put("date", date)
                put("splitMode", splitMode)
                put("participants", JsonArray(participants))
            }.toString()
            val base = "/households/${household.id}/expenses"
            errors = app.submit {
                if (expense == null) app.repo.call("POST", base, body) else app.repo.call("PATCH", "$base/${expense.id}", body)
            }
            busy = false
            if (errors.isEmpty()) {
                app.onMessage(t("expenses.saved"))
                onClose()
                onSaved()
            }
        }
    }

    FormSheet(if (expense == null) t("expenses.newExpense") else t("expenses.editExpense"), busy, onClose, ::save) {
        Field(description, { description = it }, t("expenses.description"), t.fieldError(errors["description"]), t("expenses.descriptionPlaceholder"))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Field(amount, { amount = it }, t("expenses.amount"), t.fieldError(errors["amountCents"]), "0,00", number = true, modifier = Modifier.weight(1f))
            DateField(date, t("expenses.date"), t.fieldError(errors["date"]), Modifier.weight(1f)) { date = it }
        }
        Select(paidById, household.members.map { it.id to it.displayName }, t("expenses.paidBy")) { paidById = it }
        MemberPicker(household.members, participantIds, t("expenses.splitBetween"), error = t.fieldError(errors["participants"])) { participantIds = it }
        Segmented(listOf("EQUAL" to t("expenses.splitEqual"), "CUSTOM" to t("expenses.splitCustom")), splitMode, Modifier.fillMaxWidth()) { splitMode = it }
        if (splitMode == "EQUAL" && perPerson != null) Text(t("expenses.perPerson", "amount" to perPerson), fontSize = 14.sp, color = Wg.colors.muted)
        if (splitMode == "CUSTOM") {
            household.members.filter { it.id in participantIds }.forEach { member ->
                Field(shares[member.id] ?: "", { shares = shares + (member.id to it) }, member.displayName, placeholder = "0,00", number = true)
            }
            remaining?.let {
                Text(
                    when {
                        it == 0 -> t("expenses.sharesOk")
                        it > 0 -> t("expenses.remaining", "amount" to money(it))
                        else -> t("expenses.tooMuch", "amount" to money(-it))
                    },
                    fontSize = 14.sp,
                    color = if (it == 0) Wg.colors.accentText else Wg.colors.warn,
                )
            }
        }
    }
}

/** Mirrors frontend/src/components/expenses/SettlementFormModal.vue. */
@Composable
fun SettlementForm(onClose: () -> Unit, onSaved: () -> Unit) {
    val app = LocalApp.current
    val household = LocalHousehold.current
    val t = LocalTexts.current
    val scope = rememberCoroutineScope()
    val me = app.user?.id ?: ""
    var from by remember { mutableStateOf(me) }
    var to by remember { mutableStateOf(household.members.firstOrNull { it.id != me }?.id ?: "") }
    var amount by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(household.format.today().toString()) }
    var note by remember { mutableStateOf("") }
    var errors by remember { mutableStateOf(emptyMap<String, String>()) }
    var busy by remember { mutableStateOf(false) }
    val options = household.members.map { it.id to it.displayName }

    fun save() {
        val cents = parseEurosToCents(amount)
        if (cents == null || cents <= 0) {
            errors = mapOf("amountCents" to "invalid_amount"); return
        }
        busy = true
        scope.launch {
            val body = buildJsonObject {
                put("fromUserId", from); put("toUserId", to); put("amountCents", cents); put("date", date); put("note", note)
            }.toString()
            errors = app.submit { app.repo.call("POST", "/households/${household.id}/settlements", body) }
            busy = false
            if (errors.isEmpty()) {
                app.onMessage(t("expenses.saved"))
                onClose()
                onSaved()
            }
        }
    }

    FormSheet(t("expenses.newSettlement"), busy, onClose, ::save) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Select(from, options, t("expenses.from"), Modifier.weight(1f)) { from = it }
            Select(to, options, t("expenses.to"), Modifier.weight(1f)) { to = it }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Field(amount, { amount = it }, t("expenses.amount"), t.fieldError(errors["amountCents"]), "0,00", number = true, modifier = Modifier.weight(1f))
            DateField(date, t("expenses.date"), t.fieldError(errors["date"]), Modifier.weight(1f)) { date = it }
        }
        Field(note, { note = it }, t("expenses.settlementNote"), t.fieldError(errors["note"]), optional = true)
    }
}
