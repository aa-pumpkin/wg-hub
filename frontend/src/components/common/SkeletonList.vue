<script setup lang="ts">
defineProps<{ rows?: number }>()
</script>

<template>
  <div class="space-y-2" aria-busy="true">
    <div v-for="row in rows ?? 4" :key="row" class="skeleton h-14" />
  </div>
</template>
