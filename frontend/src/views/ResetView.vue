<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import AppButton from '@/components/common/AppButton.vue'
import AppInput from '@/components/common/AppInput.vue'
import { useForm } from '@/composables/useForm'
import AuthLayout from '@/layouts/AuthLayout.vue'
import { authService } from '@/services/auth'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const ui = useUiStore()
const { run, submitting, errors } = useForm()
const newPassword = ref('')

async function submit() {
  const ok = await run(async () => {
    auth.setUser(
      await authService.resetPassword({
        token: route.params.token as string,
        newPassword: newPassword.value,
      }),
    )
  })
  if (ok) {
    ui.toast(t('reset.done'))
    await router.replace({ name: 'home' })
  }
}
</script>

<template>
  <AuthLayout>
    <div class="card p-6">
      <h1 class="text-lg font-semibold">{{ t('reset.title') }}</h1>
      <p class="mt-0.5 mb-5 text-sm text-muted">{{ t('reset.subtitle') }}</p>
      <form class="space-y-4" novalidate @submit.prevent="submit">
        <AppInput
          v-model="newPassword"
          type="password"
          :label="t('profile.newPassword')"
          :hint="t('auth.passwordHint')"
          :error="errors.newPassword"
          autocomplete="new-password"
          autofocus
        />
        <AppButton variant="primary" type="submit" block :loading="submitting">{{ t('reset.submit') }}</AppButton>
      </form>
    </div>
  </AuthLayout>
</template>
