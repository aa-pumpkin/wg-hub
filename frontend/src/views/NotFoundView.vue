<script setup lang="ts">
import AuthLayout from '@/layouts/AuthLayout.vue'
</script>

<template>
  <AuthLayout>
    <div class="card p-6 text-center">
      <h1 class="font-semibold">{{ $t('notFound.title') }}</h1>
      <p class="mt-1 mb-4 text-sm text-muted">{{ $t('notFound.text') }}</p>
      <RouterLink :to="{ name: 'home' }" class="text-sm font-medium text-accent-text hover:underline">
        {{ $t('notFound.home') }}
      </RouterLink>
    </div>
  </AuthLayout>
</template>
