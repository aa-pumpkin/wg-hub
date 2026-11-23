<script setup lang="ts">
import { useId } from 'vue'
import AppField from './AppField.vue'

defineProps<{
  label: string
  type?: string
  error?: string
  hint?: string
  optional?: boolean
  placeholder?: string
  autocomplete?: string
  inputmode?: 'text' | 'decimal' | 'email' | 'numeric'
  disabled?: boolean
  autofocus?: boolean
  autocapitalize?: string
}>()
const model = defineModel<string>({ required: true })
const id = useId()
</script>

<template>
  <AppField :label="label" :field-id="id" :error="error" :hint="hint" :optional="optional">
    <input
      :id="id"
      v-model="model"
      class="input"
      :type="type ?? 'text'"
      :placeholder="placeholder"
      :autocomplete="autocomplete"
      :inputmode="inputmode"
      :disabled="disabled"
      :autofocus="autofocus"
      :autocapitalize="autocapitalize"
      :aria-invalid="!!error"
      :aria-describedby="error ? `${id}-error` : undefined"
    />
  </AppField>
</template>
