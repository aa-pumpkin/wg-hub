package de.wghub.app.i18n

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.time.Instant

private val weekdays = mapOf(
    "de" to listOf("Mo.", "Di.", "Mi.", "Do.", "Fr.", "Sa.", "So."),
    "en" to listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"),
    "ru" to listOf("пн", "вт", "ср", "чт", "пт", "сб", "вс"),
)
private val months = mapOf(
    "de" to listOf("Jan.", "Feb.", "März", "Apr.", "Mai", "Juni", "Juli", "Aug.", "Sept.", "Okt.", "Nov.", "Dez."),
    "en" to listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"),
    "ru" to listOf("янв.", "февр.", "мар.", "апр.", "мая", "июн.", "июл.", "авг.", "сент.", "окт.", "нояб.", "дек."),
)

private val weekdaysLong = mapOf(
    "de" to listOf("Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag", "Sonntag"),
    "en" to listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"),
    "ru" to listOf("понедельник", "вторник", "среда", "четверг", "пятница", "суббота", "воскресенье"),
)
private val monthsLong = mapOf(
    "de" to listOf("Januar", "Februar", "März", "April", "Mai", "Juni", "Juli", "August", "September", "Oktober", "November", "Dezember"),
    "en" to listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"),
    "ru" to listOf("января", "февраля", "марта", "апреля", "мая", "июня", "июля", "августа", "сентября", "октября", "ноября", "декабря"),
)
private val monthsStandalone = mapOf(
    "ru" to listOf("январь", "февраль", "март", "апрель", "май", "июнь", "июль", "август", "сентябрь", "октябрь", "ноябрь", "декабрь"),
)

/** Dates the way the website shows them (frontend/src/composables/useFormat.ts). */
class Format(private val texts: Texts, timezone: String) {
    private val zone = runCatching { TimeZone.of(timezone) }.getOrElse { TimeZone.of("Europe/Berlin") }
    private val locale = texts.locale

    fun today(): LocalDate = Clock.System.todayIn(zone)

    /** "Fr., 7. Nov." / "Fri, Nov 7" / "пт, 7 нояб." */
    fun date(iso: String): String = date(LocalDate.parse(iso))

    fun date(day: LocalDate): String {
        val weekday = weekdays.getValue(locale)[day.dayOfWeek.isoDayNumber - 1]
        return when (locale) {
            "en" -> "$weekday, ${dayMonth(day)}"
            else -> "$weekday, ${dayMonth(day)}"
        }
    }

    private fun dayMonth(day: LocalDate): String {
        val month = months.getValue(locale)[day.month.number - 1]
        return when (locale) {
            "de" -> "${day.day}. $month"
            "en" -> "$month ${day.day}"
            else -> "${day.day} $month"
        }
    }

    /** "Freitag, 7. November" / "Friday, November 7" / "пятница, 7 ноября" */
    fun dateLong(iso: String): String {
        val day = LocalDate.parse(iso)
        val weekday = weekdaysLong.getValue(locale)[day.dayOfWeek.isoDayNumber - 1]
        val month = monthsLong.getValue(locale)[day.month.number - 1]
        return when (locale) {
            "de" -> "$weekday, ${day.day}. $month"
            "en" -> "$weekday, $month ${day.day}"
            else -> "$weekday, ${day.day} $month"
        }
    }

    /** "November 2026" / "ноябрь 2026" */
    fun monthTitle(iso: String): String {
        val day = LocalDate.parse(iso)
        val month = (monthsStandalone[locale] ?: monthsLong.getValue(locale))[day.month.number - 1]
        return "$month ${day.year}"
    }

    fun weekdayShort(iso: String): String = weekdays.getValue(locale)[LocalDate.parse(iso).dayOfWeek.isoDayNumber - 1]

    /** "14:30" in the household's time zone. */
    fun time(iso: String): String {
        val local = Instant.parse(iso).toLocalDateTime(zone)
        return "${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
    }

    /** The calendar date of an instant in the household's zone, "2026-11-07". */
    fun instantDate(iso: String): String = Instant.parse(iso).toLocalDateTime(zone).date.toString()

    /** "2026-11-07T14:30" in the household's zone, for pre-filling forms. */
    fun wallClock(iso: String): String {
        val local = Instant.parse(iso).toLocalDateTime(zone)
        return "${local.date}T${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
    }

    val timeZone get() = zone

    /** "7. Nov. 2026" / "Nov 7, 2026" / "7 нояб. 2026" for an instant. */
    fun dateMedium(iso: String): String {
        val day = Instant.parse(iso).toLocalDateTime(zone).date
        return if (locale == "en") "${dayMonth(day)}, ${day.year}" else "${dayMonth(day)} ${day.year}"
    }

    /** "Heute", "Morgen", "Gestern" or a short date. */
    fun relativeDate(iso: String): String {
        val day = LocalDate.parse(iso)
        return when (today().daysUntil(day)) {
            0 -> texts("common.today")
            1 -> texts("common.tomorrow")
            -1 -> texts("common.yesterday")
            else -> date(day)
        }
    }

    /** "7. Nov., 14:32" in the household's time zone. */
    fun dateTime(iso: String): String {
        val local = Instant.parse(iso).toLocalDateTime(zone)
        val time = "${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
        return "${dayMonth(local.date)}, $time"
    }
}

fun LocalDate.plusDays(days: Int): LocalDate = plus(DatePeriod(days = days))

/** Monday of the week [day] is in. */
fun startOfWeek(day: LocalDate): LocalDate = day.plusDays(-(day.dayOfWeek.isoDayNumber - 1))
