package de.wghub.app.i18n

import androidx.compose.runtime.compositionLocalOf
import de.wghub.app.generated.Res
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.abs

/**
 * The website's translations (copied in at build time by scripts/sync-texts.mjs),
 * with the same {name} placeholders and "a | b | c" plural forms as vue-i18n.
 */
class Texts(private val all: Map<String, Map<String, String>>, val locale: String) {
    private val messages = all[locale] ?: all.getValue("de")
    private val fallback = all.getValue("de")

    fun has(key: String) = key in messages || key in fallback

    operator fun invoke(key: String, vararg args: Pair<String, Any>): String {
        var text = messages[key] ?: fallback[key] ?: key
        val count = args.firstOrNull { it.first == "count" }?.second as? Int
        if (text.contains(" | ")) {
            val forms = text.split(" | ")
            text = forms[pluralIndex(count ?: 1, forms.size)]
        }
        for ((name, value) in args) text = text.replace("{$name}", value.toString())
        // vue-i18n literal syntax, e.g. {'@'}
        return text.replace(Regex("\\{'([^']*)'\\}"), "$1")
    }

    fun withLocale(locale: String) = Texts(all, if (locale in all) locale else "de")

    private fun pluralIndex(n: Int, forms: Int): Int {
        if (locale == "ru") {
            val last = abs(n) % 10
            val lastTwo = abs(n) % 100
            val form = when {
                last == 1 && lastTwo != 11 -> 0
                last in 2..4 && (lastTwo < 12 || lastTwo > 14) -> 1
                else -> 2
            }
            if (forms == 4) return if (n == 0) 0 else form + 1
            return minOf(form, forms - 1)
        }
        // vue-i18n default: "one | other", or "zero | one | other".
        return if (forms >= 3) minOf(abs(n), 2) else if (abs(n) == 1) 0 else 1
    }

    companion object {
        suspend fun load(locale: String): Texts {
            val root = Json.parseToJsonElement(Res.readBytes("files/texts.json").decodeToString()).jsonObject
            val all = root.mapValues { (_, value) ->
                (value as JsonObject).mapValues { it.value.jsonPrimitive.content }
            }
            return Texts(all, if (locale in all) locale else "de")
        }
    }
}

val LocalTexts = compositionLocalOf<Texts> { error("Texts not loaded") }
