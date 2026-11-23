import { defineStore } from 'pinia'
import { ref } from 'vue'

export type ThemeMode = 'system' | 'light' | 'dark'

export interface Toast {
  id: number
  message: string
  kind: 'success' | 'error'
}

interface ConfirmRequest {
  title: string
  message?: string
  confirmLabel: string
  danger: boolean
  resolve: (confirmed: boolean) => void
}

const THEME_KEY = 'wghub.theme'
let nextToastId = 1

export const useUiStore = defineStore('ui', () => {
  const toasts = ref<Toast[]>([])
  const confirmation = ref<ConfirmRequest | null>(null)
  const theme = ref<ThemeMode>((localStorage.getItem(THEME_KEY) as ThemeMode | null) ?? 'system')

  function toast(message: string, kind: Toast['kind'] = 'success') {
    const id = nextToastId++
    toasts.value.push({ id, message, kind })
    setTimeout(() => dismissToast(id), kind === 'error' ? 6000 : 3000)
  }

  function dismissToast(id: number) {
    toasts.value = toasts.value.filter((toast) => toast.id !== id)
  }

  /** Asks before something dangerous. Resolves to true if the person confirmed. */
  function confirm(options: Omit<ConfirmRequest, 'resolve' | 'danger'> & { danger?: boolean }) {
    return new Promise<boolean>((resolve) => {
      confirmation.value = { danger: true, ...options, resolve }
    })
  }

  function answerConfirmation(confirmed: boolean) {
    confirmation.value?.resolve(confirmed)
    confirmation.value = null
  }

  function applyTheme() {
    const dark =
      theme.value === 'dark' ||
      (theme.value === 'system' && matchMedia('(prefers-color-scheme: dark)').matches)
    document.documentElement.classList.toggle('dark', dark)
  }

  function setTheme(mode: ThemeMode) {
    theme.value = mode
    localStorage.setItem(THEME_KEY, mode)
    applyTheme()
  }

  matchMedia('(prefers-color-scheme: dark)').addEventListener('change', applyTheme)

  return {
    toasts,
    confirmation,
    theme,
    toast,
    dismissToast,
    confirm,
    answerConfirmation,
    setTheme,
  }
})
