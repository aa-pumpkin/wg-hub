<script setup lang="ts">
import { Check } from 'lucide-vue-next'

/** The big round tick used for completing chores and buying items. */
defineProps<{ checked: boolean; label: string; disabled?: boolean }>()
defineEmits<{ toggle: [] }>()
</script>

<template>
  <button
    type="button"
    role="checkbox"
    :aria-checked="checked"
    :aria-label="label"
    :disabled="disabled"
    class="tap group inline-flex shrink-0 items-center justify-center disabled:cursor-not-allowed disabled:opacity-40"
    @click.stop="$emit('toggle')"
  >
    <span
      class="inline-flex size-6 items-center justify-center rounded-full border-[1.5px] transition-colors"
      :class="
        checked
          ? 'border-accent bg-accent text-accent-fg'
          : 'border-muted/50 group-hover:border-accent group-hover:bg-accent-soft'
      "
    >
      <Check v-if="checked" class="size-3.5" stroke-width="3" aria-hidden="true" />
    </span>
  </button>
</template>
