<script setup lang="ts">
import { Globe } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import { useAction } from '@/composables/useForm'
import { useLanguage } from '@/composables/useLanguage'
import type { Locale } from '@/types'

const LANGUAGES: { value: Locale; label: string }[] = [
  { value: 'de', label: 'Deutsch' },
  { value: 'en', label: 'English' },
  { value: 'ru', label: 'Русский' },
]

const { t } = useI18n()
const { locale, setLanguage } = useLanguage()
const act = useAction()

function change(event: Event) {
  const next = (event.target as HTMLSelectElement).value as Locale
  void act(() => setLanguage(next))
}
</script>

<template>
  <label class="flex items-center gap-2 rounded-lg px-2 text-sm text-muted hover:bg-subtle">
    <Globe class="size-4 shrink-0" aria-hidden="true" />
    <span class="sr-only">{{ t('profile.language') }}</span>
    <select
      :value="locale"
      class="min-h-9 w-full cursor-pointer bg-transparent text-fg outline-none"
      @change="change"
    >
      <option v-for="language in LANGUAGES" :key="language.value" :value="language.value" class="bg-surface">
        {{ language.label }}
      </option>
    </select>
  </label>
</template>
