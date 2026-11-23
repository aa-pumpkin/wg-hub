<script setup lang="ts">
import { Plus } from 'lucide-vue-next'

/** Inline in the page header on desktop; a thumb-reachable floating button on phones. */
defineProps<{ label: string }>()
</script>

<template>
  <button
    type="button"
    :aria-label="label"
    class="fixed right-4 bottom-[calc(4.75rem+env(safe-area-inset-bottom))] z-20 inline-flex size-14 items-center justify-center gap-1.5 rounded-full bg-accent text-sm font-medium text-accent-fg shadow-lg transition-colors hover:bg-accent-hover md:static md:h-9 md:w-auto md:rounded-[10px] md:px-3.5 md:shadow-none"
  >
    <Plus class="size-6 md:size-4" aria-hidden="true" />
    <span class="hidden md:inline">{{ label }}</span>
  </button>
</template>
