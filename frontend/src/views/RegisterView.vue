<script setup lang="ts">
import { reactive } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import AppButton from '@/components/common/AppButton.vue'
import AppInput from '@/components/common/AppInput.vue'
import { useForm } from '@/composables/useForm'
import AuthLayout from '@/layouts/AuthLayout.vue'
import { useAuthStore } from '@/stores/auth'
import type { Locale } from '@/types'
import { safeRedirect } from './redirect'

const { t, locale } = useI18n()
const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const { run, submitting, errors } = useForm()
const form = reactive({ displayName: '', username: '', password: '' })

async function submit() {
  const ok = await run(() => auth.register({ ...form, locale: locale.value as Locale }))
  if (ok) await router.replace(safeRedirect(route.query.redirect))
}
</script>

<template>
  <AuthLayout>
    <div class="card p-6">
      <h1 class="text-lg font-semibold">{{ t('auth.registerTitle') }}</h1>
      <p class="mt-0.5 mb-5 text-sm text-muted">{{ t('auth.registerSubtitle') }}</p>
      <form class="space-y-4" novalidate @submit.prevent="submit">
        <AppInput
          v-model="form.displayName"
          :label="t('auth.displayName')"
          :hint="t('auth.displayNameHint')"
          :error="errors.displayName"
          autocomplete="given-name"
          autofocus
        />
        <AppInput
          v-model="form.username"
          :label="t('auth.username')"
          :hint="t('auth.usernameHint')"
          :error="errors.username"
          autocomplete="username"
          autocapitalize="none"
        />
        <AppInput
          v-model="form.password"
          type="password"
          :label="t('auth.password')"
          :hint="t('auth.passwordHint')"
          :error="errors.password"
          autocomplete="new-password"
        />
        <AppButton variant="primary" type="submit" block :loading="submitting">{{ t('auth.register') }}</AppButton>
      </form>
      <p class="mt-4 text-[13px] text-muted">{{ t('auth.anyDeviceHint') }}</p>
    </div>
    <p class="mt-4 text-center text-sm text-muted">
      {{ t('auth.hasAccount') }}
      <RouterLink :to="{ name: 'login', query: route.query }" class="font-medium text-accent-text hover:underline">
        {{ t('auth.login') }}
      </RouterLink>
    </p>
  </AuthLayout>
</template>
