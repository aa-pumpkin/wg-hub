<script setup lang="ts">
import { CloudOff } from 'lucide-vue-next'
import AppButton from './AppButton.vue'
import EmptyState from './EmptyState.vue'

defineEmits<{ retry: [] }>()
</script>

<template>
  <EmptyState :icon="CloudOff" :title="$t('common.loadFailed')">
    <AppButton @click="$emit('retry')">{{ $t('common.retry') }}</AppButton>
  </EmptyState>
</template>
