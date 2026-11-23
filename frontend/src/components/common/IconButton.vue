<script setup lang="ts">
defineProps<{ label: string }>()
</script>

<template>
  <button
    type="button"
    :aria-label="label"
    :title="label"
    class="tap inline-flex items-center justify-center rounded-[10px] text-muted transition-colors hover:bg-subtle hover:text-fg"
  >
    <slot />
  </button>
</template>
