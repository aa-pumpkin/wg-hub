<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import AppButton from '@/components/common/AppButton.vue'
import { useForm } from '@/composables/useForm'
import AuthLayout from '@/layouts/AuthLayout.vue'
import { householdService } from '@/services/households'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'
import type { InvitationPreview } from '@/types'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const ui = useUiStore()
const { run, submitting } = useForm()

const code = computed(() => route.params.code as string)
const preview = ref<InvitationPreview | null>(null)
const state = ref<'loading' | 'valid' | 'invalid'>('loading')

onMounted(async () => {
  try {
    preview.value = await householdService.previewInvitation(code.value)
    state.value = 'valid'
  } catch {
    state.value = 'invalid'
  }
})

async function accept() {
  let householdId = ''
  const ok = await run(async () => {
    householdId = await householdService.acceptInvitation(code.value)
  })
  if (ok) {
    ui.toast(t('invite.joined', { name: preview.value?.householdName ?? '' }))
    await router.push({ name: 'dashboard', params: { householdId } })
  }
}
</script>

<template>
  <AuthLayout>
    <div class="card p-6 text-center">
      <div v-if="state === 'loading'" class="skeleton mx-auto h-24" aria-busy="true" />
      <template v-else-if="state === 'invalid'">
        <h1 class="font-semibold">{{ t('invite.invalid') }}</h1>
        <p class="mt-1 mb-4 text-sm text-muted">{{ t('invite.invalidHint') }}</p>
        <RouterLink :to="{ name: 'home' }" class="text-sm font-medium text-accent-text hover:underline">
          {{ t('invite.goHome') }}
        </RouterLink>
      </template>
      <template v-else-if="preview">
        <p class="text-sm text-muted">{{ t('invite.subtitle') }}</p>
        <p class="mt-3 text-4xl" aria-hidden="true">{{ preview.householdIcon }}</p>
        <h1 class="mt-2 text-lg font-semibold">{{ preview.householdName }}</h1>
        <p class="mb-5 text-sm text-muted">
          {{ t('invite.members', { count: preview.memberCount }, preview.memberCount) }}
        </p>
        <AppButton v-if="auth.user" variant="primary" block :loading="submitting" @click="accept">
          {{ t('invite.accept') }}
        </AppButton>
        <div v-else class="space-y-2">
          <RouterLink
            :to="{ name: 'register', query: { redirect: route.fullPath } }"
            class="tap flex items-center justify-center rounded-[10px] bg-accent text-sm font-medium text-accent-fg hover:bg-accent-hover"
          >
            {{ t('invite.registerToAccept') }}
          </RouterLink>
          <RouterLink
            :to="{ name: 'login', query: { redirect: route.fullPath } }"
            class="tap flex items-center justify-center rounded-[10px] border border-line text-sm font-medium hover:bg-subtle"
          >
            {{ t('invite.loginToAccept') }}
          </RouterLink>
        </div>
      </template>
    </div>
  </AuthLayout>
</template>
