import { defineStore } from 'pinia'
import { ref } from 'vue'
import { setLocale } from '@/i18n'
import { ApiError } from '@/services/api'
import { authService } from '@/services/auth'
import type { User } from '@/types'

export const useAuthStore = defineStore('auth', () => {
  const user = ref<User | null>(null)
  const checked = ref(false)

  function setUser(next: User | null) {
    user.value = next
    if (next) setLocale(next.locale)
  }

  /** Finds out once per page load whether there is a session. */
  async function ensureChecked() {
    if (checked.value) return
    try {
      setUser(await authService.me())
      checked.value = true
    } catch (error) {
      // Only a real 401 settles it. After a network failure the next navigation asks again.
      if (error instanceof ApiError && error.status === 401) checked.value = true
      else if (!(error instanceof ApiError)) throw error
    }
  }

  async function login(username: string, password: string) {
    setUser(await authService.login({ username, password }))
  }

  async function register(input: Parameters<typeof authService.register>[0]) {
    setUser(await authService.register(input))
  }

  async function logout() {
    await authService.logout()
    user.value = null
  }

  return { user, checked, setUser, ensureChecked, login, register, logout }
})
