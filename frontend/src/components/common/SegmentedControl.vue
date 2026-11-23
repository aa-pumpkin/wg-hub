<script setup lang="ts" generic="T extends string">
defineProps<{ options: readonly { value: T; label: string }[]; label: string }>()
const model = defineModel<T>({ required: true })
</script>

<template>
  <div role="group" :aria-label="label" class="inline-flex rounded-[10px] bg-subtle p-0.5">
    <button
      v-for="option in options"
      :key="option.value"
      type="button"
      :aria-pressed="model === option.value"
      class="min-h-9 rounded-lg px-2.5 text-sm whitespace-nowrap transition-colors md:px-3"
      :class="
        model === option.value ? 'bg-surface font-medium text-fg shadow-sm' : 'text-muted hover:text-fg'
      "
      @click="model = option.value"
    >
      {{ option.label }}
    </button>
  </div>
</template>
