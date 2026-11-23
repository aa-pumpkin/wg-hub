<script setup lang="ts">
import type { Component } from 'vue'

defineProps<{ icon?: Component; title: string; text?: string }>()
</script>

<template>
  <div class="flex flex-col items-center px-6 py-14 text-center">
    <component :is="icon" v-if="icon" class="mb-3 size-7 text-muted" aria-hidden="true" />
    <p class="font-medium">{{ title }}</p>
    <p v-if="text" class="mt-1 max-w-sm text-sm text-muted">{{ text }}</p>
    <div v-if="$slots.default" class="mt-4"><slot /></div>
  </div>
</template>
