import { createI18n } from 'vue-i18n'
import de from '@/locales/de'
import en from '@/locales/en'
import ru from '@/locales/ru'
import { russianPlural } from '@/lib/plural'
import { LOCALES, type Locale } from '@/types'

const KEY = 'wghub.locale'

function initialLocale(): Locale {
  const stored = localStorage.getItem(KEY)
  if (LOCALES.includes(stored as Locale)) return stored as Locale
  // First visit: follow the browser if we have that language, otherwise German.
  const browser = navigator.language.slice(0, 2)
  return LOCALES.includes(browser as Locale) ? (browser as Locale) : 'de'
}

export const i18n = createI18n({
  legacy: false,
  locale: initialLocale(),
  fallbackLocale: 'de',
  messages: { de, en, ru },
  pluralRules: { ru: russianPlural },
})

document.documentElement.lang = initialLocale()

export function setLocale(locale: Locale) {
  i18n.global.locale.value = locale
  localStorage.setItem(KEY, locale)
  document.documentElement.lang = locale
}
