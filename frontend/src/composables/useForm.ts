import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ApiError } from '@/services/api'
import { useUiStore } from '@/stores/ui'

/** Shows an API failure as a translated toast. */
export function useErrorToast() {
  const { t, te } = useI18n()
  const ui = useUiStore()
  return (error: unknown) => {
    const code = error instanceof ApiError ? error.code : 'internal_error'
    ui.toast(te(`errors.${code}`) ? t(`errors.${code}`) : t('errors.internal_error'), 'error')
    if (!(error instanceof ApiError)) console.error(error)
  }
}

/**
 * Submit state for a form. Validation lives on the backend (one source of truth):
 * its per-field errors appear under the matching input, anything else as a toast.
 */
export function useForm() {
  const { t, te } = useI18n()
  const showError = useErrorToast()
  const submitting = ref(false)
  const errors = ref<Record<string, string>>({})

  function setFieldError(field: string, code: string) {
    errors.value = { ...errors.value, [field]: t(`validation.${code}`) }
  }

  async function run(action: () => Promise<void>): Promise<boolean> {
    if (submitting.value) return false
    submitting.value = true
    errors.value = {}
    try {
      await action()
      return true
    } catch (error) {
      const fields = error instanceof ApiError ? Object.entries(error.fields) : []
      if (fields.length > 0) {
        errors.value = Object.fromEntries(
          fields.map(([field, code]) => [
            field,
            te(`validation.${code}`) ? t(`validation.${code}`) : t('validation.invalid_value'),
          ]),
        )
      } else {
        showError(error)
      }
      return false
    } finally {
      submitting.value = false
    }
  }

  return { submitting, errors, run, setFieldError }
}

/** Wraps a one-click action (delete, toggle…): failures become a toast. */
export function useAction() {
  const showError = useErrorToast()
  return async (action: () => Promise<unknown>): Promise<boolean> => {
    try {
      await action()
      return true
    } catch (error) {
      showError(error)
      return false
    }
  }
}
