<script setup lang="ts">
import { Loader2 } from 'lucide-vue-next'
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    variant?: 'primary' | 'secondary' | 'ghost' | 'danger'
    type?: 'button' | 'submit'
    loading?: boolean
    disabled?: boolean
    block?: boolean
  }>(),
  { variant: 'secondary', type: 'button' },
)

const variantClass = computed(
  () =>
    ({
      primary: 'bg-accent text-accent-fg hover:bg-accent-hover',
      secondary: 'bg-surface text-fg border border-line hover:bg-subtle',
      ghost: 'text-muted hover:bg-subtle hover:text-fg',
      danger: 'bg-danger-soft text-danger hover:brightness-95 dark:hover:brightness-125',
    })[props.variant],
)
</script>

<template>
  <button
    :type="type"
    :disabled="disabled || loading"
    :aria-busy="loading"
    class="tap inline-flex items-center justify-center gap-2 rounded-[10px] px-3.5 text-sm font-medium transition-colors disabled:cursor-not-allowed disabled:opacity-50"
    :class="[variantClass, block && 'w-full']"
  >
    <Loader2 v-if="loading" class="size-4 animate-spin" aria-hidden="true" />
    <slot />
  </button>
</template>
