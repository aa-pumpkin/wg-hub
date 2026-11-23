<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{ name: string; color?: string; size?: 'xs' | 'sm' | 'md' | 'lg' }>(),
  { color: 'sage', size: 'md' },
)
const initial = computed(() => [...props.name.trim()][0]?.toUpperCase() ?? '?')
const sizeClass = computed(
  () => ({ xs: 'size-4 text-[9px]', sm: 'size-6 text-[11px]', md: 'size-8 text-[13px]', lg: 'size-11 text-base' })[props.size],
)
</script>

<template>
  <span
    class="inline-flex shrink-0 items-center justify-center rounded-full font-semibold select-none"
    :class="sizeClass"
    :style="{ background: `var(--avatar-${color})`, color: 'var(--avatar-fg)' }"
    :title="name"
    aria-hidden="true"
  >
    {{ initial }}
  </span>
</template>
