<script setup lang="ts">
import LocaleSwitcher from '@/components/common/LocaleSwitcher.vue'
</script>

<template>
  <main class="relative flex min-h-dvh flex-col items-center justify-center px-4 py-14">
    <LocaleSwitcher class="absolute top-3 right-3 w-36" />
    <div class="mb-6 flex items-center gap-2.5">
      <img src="/icon.svg" alt="" class="size-8" />
      <span class="text-lg font-semibold tracking-tight">WG Hub</span>
    </div>
    <div class="w-full max-w-sm">
      <slot />
    </div>
  </main>
</template>
