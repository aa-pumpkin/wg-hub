<script setup lang="ts">
import { useId } from 'vue'
import AppField from './AppField.vue'

defineProps<{
  label: string
  error?: string
  hint?: string
  optional?: boolean
  placeholder?: string
  rows?: number
  autofocus?: boolean
}>()
const model = defineModel<string>({ required: true })
const id = useId()
</script>

<template>
  <AppField :label="label" :field-id="id" :error="error" :hint="hint" :optional="optional">
    <textarea
      :id="id"
      v-model="model"
      class="input resize-y"
      :rows="rows ?? 3"
      :placeholder="placeholder"
      :autofocus="autofocus"
      :aria-invalid="!!error"
      :aria-describedby="error ? `${id}-error` : undefined"
    />
  </AppField>
</template>
