<script setup lang="ts">
import AppConfirmHost from '@/components/common/AppConfirmHost.vue'
import AppToasts from '@/components/common/AppToasts.vue'
</script>

<template>
  <RouterView />
  <AppConfirmHost />
  <AppToasts />
</template>
