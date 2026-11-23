<script setup lang="ts">
defineProps<{
  label: string
  fieldId: string
  error?: string
  hint?: string
  optional?: boolean
}>()
</script>

<template>
  <div>
    <label :for="fieldId" class="mb-1.5 block text-sm font-medium">
      {{ label }}
      <span v-if="optional" class="font-normal text-muted">({{ $t('common.optional') }})</span>
    </label>
    <slot />
    <p v-if="error" :id="`${fieldId}-error`" class="mt-1.5 text-[13px] text-danger" role="alert">
      {{ error }}
    </p>
    <p v-else-if="hint" class="mt-1.5 text-[13px] text-muted">{{ hint }}</p>
  </div>
</template>
