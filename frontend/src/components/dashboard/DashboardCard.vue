<script setup lang="ts">
import { ChevronRight } from 'lucide-vue-next'
import type { RouteLocationRaw } from 'vue-router'

defineProps<{ title: string; to: RouteLocationRaw }>()
</script>

<template>
  <section class="card h-full overflow-hidden">
    <RouterLink :to="to" class="group flex min-h-12 items-center gap-2 px-4">
      <h2 class="text-sm font-semibold">{{ title }}</h2>
      <span class="text-[13px] text-muted"><slot name="meta" /></span>
      <ChevronRight class="ml-auto size-4 text-muted transition-transform group-hover:translate-x-0.5" aria-hidden="true" />
    </RouterLink>
    <slot />
  </section>
</template>
