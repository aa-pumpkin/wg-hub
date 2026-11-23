<script setup lang="ts">
import { LogOut } from 'lucide-vue-next'
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import AppButton from '@/components/common/AppButton.vue'
import AppInput from '@/components/common/AppInput.vue'
import { useForm } from '@/composables/useForm'
import AuthLayout from '@/layouts/AuthLayout.vue'
import { householdService } from '@/services/households'
import { useAuthStore } from '@/stores/auth'
import { useHouseholdStore } from '@/stores/household'

const { t } = useI18n()
const auth = useAuthStore()
const household = useHouseholdStore()
const router = useRouter()

const name = ref('')
const code = ref('')
const createForm = useForm()
const joinForm = useForm()

async function create() {
  let id = ''
  const ok = await createForm.run(async () => {
    id = (await household.create({ name: name.value })).id
  })
  if (ok) await router.push({ name: 'dashboard', params: { householdId: id } })
}

/** Accepts the whole link or the bare code. */
function extractCode(input: string) {
  return input.trim().split('/').filter(Boolean).pop() ?? ''
}

async function join() {
  const value = extractCode(code.value)
  if (!value) {
    joinForm.setFieldError('code', 'required')
    return
  }
  let id = ''
  const ok = await joinForm.run(async () => {
    id = await householdService.acceptInvitation(value)
  })
  if (ok) await router.push({ name: 'dashboard', params: { householdId: id } })
}

async function logout() {
  await auth.logout()
  household.reset()
  await router.push({ name: 'login' })
}
</script>

<template>
  <AuthLayout>
    <div class="mb-4 text-center">
      <h1 class="text-lg font-semibold">{{ t('welcome.title', { name: auth.user?.displayName }) }}</h1>
      <p class="text-sm text-muted">{{ t('welcome.subtitle') }}</p>
    </div>
    <div class="space-y-3">
      <form class="card space-y-3 p-5" novalidate @submit.prevent="create">
        <div>
          <h2 class="font-medium">{{ t('welcome.createTitle') }}</h2>
          <p class="text-sm text-muted">{{ t('welcome.createText') }}</p>
        </div>
        <AppInput
          v-model="name"
          :label="t('welcome.wgName')"
          :placeholder="t('welcome.wgNamePlaceholder')"
          :error="createForm.errors.value.name"
        />
        <AppButton variant="primary" type="submit" block :loading="createForm.submitting.value">
          {{ t('common.create') }}
        </AppButton>
      </form>
      <form class="card space-y-3 p-5" novalidate @submit.prevent="join">
        <div>
          <h2 class="font-medium">{{ t('welcome.joinTitle') }}</h2>
          <p class="text-sm text-muted">{{ t('welcome.joinText') }}</p>
        </div>
        <AppInput v-model="code" :label="t('welcome.code')" :error="joinForm.errors.value.code" />
        <AppButton type="submit" block :loading="joinForm.submitting.value">{{ t('welcome.join') }}</AppButton>
      </form>
    </div>
    <div class="mt-4 flex justify-center">
      <AppButton variant="ghost" @click="logout">
        <LogOut class="size-4" aria-hidden="true" />
        {{ t('auth.logout') }}
      </AppButton>
    </div>
  </AuthLayout>
</template>
