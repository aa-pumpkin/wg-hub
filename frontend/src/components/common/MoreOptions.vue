<script setup lang="ts">
import { ChevronDown } from 'lucide-vue-next'

/** Rarely needed fields stay out of the way. Opens by itself when one of them has a problem. */
defineProps<{ open?: boolean }>()
</script>

<template>
  <details class="group" :open="open">
    <summary class="tap flex w-fit cursor-pointer list-none items-center gap-1 text-sm text-muted hover:text-fg [&::-webkit-details-marker]:hidden">
      <ChevronDown class="size-4 transition-transform group-open:rotate-180" aria-hidden="true" />
      {{ $t('common.moreOptions') }}
    </summary>
    <div class="mt-2 space-y-4">
      <slot />
    </div>
  </details>
</template>
