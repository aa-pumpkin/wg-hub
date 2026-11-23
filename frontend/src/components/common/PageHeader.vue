<script setup lang="ts">
defineProps<{ title: string; subtitle?: string }>()
</script>

<template>
  <header class="mb-5 flex items-center justify-between gap-3">
    <div class="min-w-0">
      <h1 class="truncate text-xl font-semibold tracking-tight md:text-2xl">{{ title }}</h1>
      <p v-if="subtitle" class="truncate text-sm text-muted">{{ subtitle }}</p>
    </div>
    <div class="flex shrink-0 items-center gap-2"><slot /></div>
  </header>
</template>
