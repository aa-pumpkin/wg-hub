package de.wghub.app.ui.shopping

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.wghub.app.LocalApp
import de.wghub.app.data.ShoppingItem
import de.wghub.app.i18n.LocalTexts
import de.wghub.app.i18n.formatCents
import de.wghub.app.i18n.formatDecimal
import de.wghub.app.i18n.parseDecimal
import de.wghub.app.i18n.parseEurosToCents
import de.wghub.app.ui.Field
import de.wghub.app.ui.FormSheet
import de.wghub.app.ui.Hint
import de.wghub.app.ui.LocalHousehold
import de.wghub.app.ui.Segmented
import de.wghub.app.ui.Select
import de.wghub.app.ui.fieldError
import de.wghub.app.ui.submit
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private val UNITS = listOf("pcs", "l", "kg", "g", "pack")

/** Mirrors frontend/src/components/shopping/ShoppingFormModal.vue. */
@Composable
fun ShoppingForm(item: ShoppingItem?, onClose: () -> Unit, onSaved: () -> Unit) {
    val app = LocalApp.current
    val household = LocalHousehold.current
    val t = LocalTexts.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(item?.name ?: "") }
    var personal by remember { mutableStateOf(item?.personal ?: false) }
    var quantity by remember { mutableStateOf(item?.quantity?.let { formatDecimal(it, t.locale) } ?: "") }
    var unit by remember { mutableStateOf(item?.unit ?: "pcs") }
    var note by remember { mutableStateOf(item?.note ?: "") }
    var errors by remember { mutableStateOf(emptyMap<String, String>()) }
    var busy by remember { mutableStateOf(false) }

    // An older item may have a free-text unit; keep it selectable.
    val units = (if (unit in UNITS) UNITS else UNITS + unit).map {
        it to if (t.has("shopping.units.$it")) t("shopping.units.$it") else it
    }

    fun save() {
        val amount = parseDecimal(quantity)
        if (quantity.isNotBlank() && amount == null) {
            errors = mapOf("quantity" to "invalid_value")
            return
        }
        busy = true
        scope.launch {
            val body = buildJsonObject {
                put("name", name)
                put("personal", personal)
                put("quantity", amount?.let(::JsonPrimitive) ?: JsonNull)
                // A unit means nothing without an amount.
                put("unit", if (amount == null) "" else unit)
                put("note", note)
            }.toString()
            val base = "/households/${household.id}/shopping"
            errors = app.submit {
                if (item == null) app.repo.call("POST", base, body) else app.repo.call("PATCH", "$base/${item.id}", body)
            }
            busy = false
            if (errors.isEmpty()) {
                onClose()
                onSaved()
            }
        }
    }

    FormSheet(if (item == null) t("shopping.newItem") else t("shopping.editItem"), busy, onClose, ::save) {
        Field(name, { name = it }, t("shopping.name"), t.fieldError(errors["name"]), t("shopping.namePlaceholder"))
        Column {
            Text(t("shopping.scopeLabel"), fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 6.dp))
            Segmented(listOf(false to t("shopping.forWg"), true to t("shopping.forMe")), personal, Modifier.fillMaxWidth()) { personal = it }
            Hint(if (personal) t("shopping.forMeHint") else t("shopping.forWgHint"), Modifier.padding(top = 6.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Field(quantity, { quantity = it }, t("shopping.quantity"), t.fieldError(errors["quantity"]), number = true, modifier = Modifier.weight(1f))
            Select(unit, units, t("shopping.unit"), Modifier.weight(1f)) { unit = it }
        }
        Field(note, { note = it }, t("shopping.note"), t.fieldError(errors["note"]), t("shopping.notePlaceholder"), optional = true)
    }
}

/** Asks for the price when buying: it becomes a shared expense. Mirrors BuyItemDialog.vue. */
@Composable
fun BuyDialog(item: ShoppingItem, actions: ShoppingActions, onClose: () -> Unit, onBought: () -> Unit) {
    val app = LocalApp.current
    val household = LocalHousehold.current
    val t = LocalTexts.current
    val scope = rememberCoroutineScope()
    var price by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val cents = parseEurosToCents(price)
    val payers = household.members.count { !it.frozen || it.id == app.user?.id }.coerceAtLeast(1)

    /** Spells out what happens before anyone's balance changes. */
    val preview = when {
        cents == null || cents == 0 -> t("shopping.noPriceHint")
        item.personal -> t("shopping.previewPersonal", "name" to item.createdBy.displayName, "amount" to formatCents(cents, t.locale))
        else -> t("shopping.previewWg", "amount" to formatCents((cents + payers - 1) / payers, t.locale), "count" to payers)
    }

    fun save() {
        if (price.isNotBlank() && (cents == null || cents <= 0)) {
            error = t.fieldError("invalid_amount")
            return
        }
        busy = true
        scope.launch {
            val bought = actions.setStatus(item, "BOUGHT", cents)
            busy = false
            if (bought?.expense != null) app.onMessage(t("shopping.addedExpense"))
            onClose()
            onBought()
        }
    }

    FormSheet(t("shopping.buyTitle", "name" to item.name), busy, onClose, ::save, saveLabel = t("shopping.confirmBuy")) {
        Field(price, { price = it; error = null }, t("shopping.price"), error, "0,00", optional = true, number = true)
        Text(preview, fontSize = 14.sp, color = de.wghub.app.ui.Wg.colors.muted)
    }
}
