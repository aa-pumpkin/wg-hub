<script setup lang="ts" generic="T extends string">
import { useId } from 'vue'
import AppField from './AppField.vue'

defineProps<{
  label: string
  options: readonly { value: T; label: string }[]
  error?: string
  hint?: string
  disabled?: boolean
}>()
const model = defineModel<T>({ required: true })
const id = useId()
</script>

<template>
  <AppField :label="label" :field-id="id" :error="error" :hint="hint">
    <select
      :id="id"
      v-model="model"
      class="input"
      :disabled="disabled"
      :aria-invalid="!!error"
      :aria-describedby="error ? `${id}-error` : undefined"
    >
      <option v-for="option in options" :key="option.value" :value="option.value">
        {{ option.label }}
      </option>
    </select>
  </AppField>
</template>
