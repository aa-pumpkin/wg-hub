package de.wghub.app.i18n

import kotlin.math.abs

/** "12,50 €" / "€12.50", like Intl.NumberFormat with currency EUR on the website. */
fun formatCents(cents: Int, locale: String): String {
    val sign = if (cents < 0) "-" else ""
    val value = abs(cents)
    val euros = value / 100
    val rest = (value % 100).toString().padStart(2, '0')
    val grouped = euros.toString().reversed().chunked(3).joinToString(if (locale == "en") "," else if (locale == "ru") " " else ".").reversed()
    return when (locale) {
        "en" -> "$sign€$grouped.$rest"
        else -> "$sign$grouped,$rest €"
    }
}

/** Cents as an editable value without currency sign, e.g. "12,50". */
fun centsToInput(cents: Int, locale: String): String {
    val euros = cents / 100
    val rest = (abs(cents) % 100).toString().padStart(2, '0')
    return "$euros${if (locale == "en") "." else ","}$rest"
}

/**
 * Reads a typed euro amount ("12,50", "12.5", "1.234,56", "1,234.56") into cents, working on
 * the digits only. Same rules as parseEurosToCents in frontend/src/lib/money.ts.
 */
fun parseEurosToCents(input: String): Int? {
    val text = input.trim().replace(Regex("\\s|€"), "")
    if (text.isEmpty()) return null
    val match = Regex("^(\\d[\\d.,]*?)(?:([.,])(\\d{1,2}))?$").find(text) ?: return null
    val whole = match.groupValues[1]
    val mark = match.groups[2]?.value
    val fraction = match.groups[3]?.value ?: ""
    if (Regex("[.,]").containsMatchIn(whole) && !Regex("^\\d{1,3}([.,]\\d{3})+$").matches(whole)) return null
    if (mark != null && whole.contains(mark)) return null
    val euros = whole.replace(Regex("[.,]"), "").toLongOrNull() ?: return null
    val total = euros * 100 + fraction.padEnd(2, '0').toLong()
    return if (total <= Int.MAX_VALUE) total.toInt() else null
}

/** Typed decimal number like "1,5" or "2". */
fun parseDecimal(input: String): Double? = input.trim().replace(',', '.').takeIf { it.isNotEmpty() }?.toDoubleOrNull()

fun formatDecimal(value: Double, locale: String): String {
    val text = if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
    return if (locale == "en") text else text.replace('.', ',')
}
