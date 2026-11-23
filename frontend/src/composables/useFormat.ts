import { DateTime } from 'luxon'
import { useI18n } from 'vue-i18n'
import { formatCents } from '@/lib/money'
import { useHouseholdStore } from '@/stores/household'

/** Formatting in the active language and, for instants, the household's time zone. */
export function useFormat() {
  const { locale, t, te } = useI18n()
  const household = useHouseholdStore()

  const calendarDate = (date: string) =>
    DateTime.fromISO(date, { zone: 'utc' }).setLocale(locale.value)
  const instant = (iso: string) =>
    DateTime.fromISO(iso, { zone: household.timezone }).setLocale(locale.value)
  const today = () => DateTime.now().setZone(household.timezone).toISODate()!

  return {
    today,
    money: (cents: number) => formatCents(cents, locale.value),
    /** "Fr., 7. Nov." */
    date: (date: string) =>
      calendarDate(date).toLocaleString({ weekday: 'short', day: 'numeric', month: 'short' }),
    /** "Freitag, 7. November" */
    dateLong: (date: string) =>
      calendarDate(date).toLocaleString({ weekday: 'long', day: 'numeric', month: 'long' }),
    /** "Heute", "Morgen", or a short date. */
    relativeDate(date: string) {
      const offset = calendarDate(date).diff(calendarDate(today()), 'days').days
      if (offset === 0) return t('common.today')
      if (offset === 1) return t('common.tomorrow')
      if (offset === -1) return t('common.yesterday')
      return calendarDate(date).toLocaleString({
        weekday: 'short',
        day: 'numeric',
        month: 'short',
      })
    },
    monthTitle: (date: string) =>
      calendarDate(date).toLocaleString({ month: 'long', year: 'numeric' }),
    weekdayShort: (date: string) => calendarDate(date).toLocaleString({ weekday: 'short' }),
    dayNumber: (date: string) => calendarDate(date).day,
    /** 1 = Monday … 7 = Sunday. */
    weekdayIndex: (date: string) => calendarDate(date).weekday,
    time: (iso: string) => instant(iso).toFormat('HH:mm'),
    /** The calendar date of an instant in the household's zone. */
    instantDate: (iso: string) => instant(iso).toISODate()!,
    /** "7. Nov., 14:32" */
    dateTime: (iso: string) =>
      instant(iso).toLocaleString({
        day: 'numeric',
        month: 'short',
        hour: '2-digit',
        minute: '2-digit',
      }),
    quantity: (value: number) => new Intl.NumberFormat(locale.value).format(value),
    /** "2 л". Units are stored as codes; older free-text units are shown as they are. */
    amount(quantity: number | null, unit: string | null) {
      const label = unit && te(`shopping.units.${unit}`) ? t(`shopping.units.${unit}`) : (unit ?? '')
      const count = quantity === null ? '' : new Intl.NumberFormat(locale.value).format(quantity)
      return `${count} ${label}`.trim()
    },
  }
}
