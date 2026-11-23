import { useI18n } from 'vue-i18n'
import { setLocale } from '@/i18n'
import { authService } from '@/services/auth'
import { useAuthStore } from '@/stores/auth'
import type { Locale } from '@/types'

/** Each person's own language: remembered on their account, so it follows them to any device. */
export function useLanguage() {
  const { locale } = useI18n()
  const auth = useAuthStore()

  async function setLanguage(next: Locale) {
    setLocale(next)
    if (auth.user) auth.setUser(await authService.updateProfile({ locale: next }))
  }

  return { locale, setLanguage }
}
