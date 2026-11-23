<script setup lang="ts">
import { Check, LogOut } from 'lucide-vue-next'
import { computed, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import AppButton from '@/components/common/AppButton.vue'
import AppInput from '@/components/common/AppInput.vue'
import LocaleSwitcher from '@/components/common/LocaleSwitcher.vue'
import PageHeader from '@/components/common/PageHeader.vue'
import SegmentedControl from '@/components/common/SegmentedControl.vue'
import { useAction, useForm } from '@/composables/useForm'
import { useLive } from '@/composables/useLive'
import { authService } from '@/services/auth'
import { householdService } from '@/services/households'
import { useAuthStore } from '@/stores/auth'
import { useHouseholdStore } from '@/stores/household'
import { useUiStore, type ThemeMode } from '@/stores/ui'
import type { MemberStats } from '@/types'

const COLORS = ['sage', 'sky', 'rose', 'amber', 'lilac', 'teal'] as const

const { t } = useI18n()
const auth = useAuthStore()
const household = useHouseholdStore()
const ui = useUiStore()
const router = useRouter()
const act = useAction()
const profileForm = useForm()
const passwordForm = useForm()

const profile = reactive({
  displayName: auth.user?.displayName ?? '',
  username: auth.user?.username ?? '',
  avatarColor: auth.user?.avatarColor ?? 'sage',
})
const passwords = reactive({ currentPassword: '', newPassword: '' })
const stats = ref<MemberStats>({ open: {}, completed: {} })

async function loadStats() {
  await act(async () => {
    stats.value = await householdService.stats(household.id)
  })
}
void loadStats()
useLive(['chores'], loadStats)

const myOpen = computed(() => stats.value.open[auth.user?.id ?? ''] ?? 0)
const myDone = computed(() => stats.value.completed[auth.user?.id ?? ''] ?? 0)

const themeOptions = computed(() => [
  { value: 'system' as ThemeMode, label: t('profile.themeSystem') },
  { value: 'light' as ThemeMode, label: t('profile.themeLight') },
  { value: 'dark' as ThemeMode, label: t('profile.themeDark') },
])
const theme = computed({ get: () => ui.theme, set: ui.setTheme })

async function saveProfile() {
  const ok = await profileForm.run(async () => {
    auth.setUser(await authService.updateProfile({ ...profile }))
    await household.refresh()
  })
  if (ok) ui.toast(t('profile.saved'))
}

async function changePassword() {
  const ok = await passwordForm.run(() => authService.changePassword({ ...passwords }))
  if (ok) {
    passwords.currentPassword = ''
    passwords.newPassword = ''
    ui.toast(t('profile.passwordChanged'))
  }
}

async function logout() {
  await act(async () => {
    await auth.logout()
    household.reset()
    await router.push({ name: 'login' })
  })
}
</script>

<template>
  <div class="mx-auto max-w-2xl space-y-5">
    <PageHeader :title="t('profile.title')" />

    <form class="card space-y-4 p-5" novalidate @submit.prevent="saveProfile">
      <h2 class="font-medium">{{ t('profile.account') }}</h2>
      <AppInput v-model="profile.displayName" :label="t('profile.name')" :error="profileForm.errors.value.displayName" autocomplete="given-name" />
      <AppInput
        v-model="profile.username"
        :label="t('auth.username')"
        :hint="t('auth.usernameHint')"
        :error="profileForm.errors.value.username"
        autocomplete="username"
        autocapitalize="none"
      />
      <fieldset>
        <legend class="mb-1.5 text-sm font-medium">{{ t('profile.color') }}</legend>
        <div class="flex flex-wrap gap-2">
          <button
            v-for="color in COLORS"
            :key="color"
            type="button"
            :aria-pressed="profile.avatarColor === color"
            :aria-label="t(`profile.colors.${color}`)"
            :title="t(`profile.colors.${color}`)"
            class="inline-flex size-10 items-center justify-center rounded-full"
            :class="profile.avatarColor === color && 'ring-2 ring-accent ring-offset-2 ring-offset-surface'"
            :style="{ background: `var(--avatar-${color})`, color: 'var(--avatar-fg)' }"
            @click="profile.avatarColor = color"
          >
            <Check v-if="profile.avatarColor === color" class="size-4" aria-hidden="true" />
          </button>
        </div>
      </fieldset>
      <div class="flex justify-end">
        <AppButton variant="primary" type="submit" :loading="profileForm.submitting.value">{{ t('common.save') }}</AppButton>
      </div>
    </form>

    <section class="card p-5">
      <h2 class="mb-1 font-medium">{{ t('profile.stats') }}</h2>
      <p class="text-sm text-muted">
        {{ t('members.openTasks', { count: myOpen }) }} · {{ t('members.doneTasks', { count: myDone }) }}
      </p>
    </section>

    <section class="card space-y-4 p-5">
      <h2 class="font-medium">{{ t('profile.appearance') }}</h2>
      <div class="flex flex-wrap items-center justify-between gap-2">
        <span class="text-sm">{{ t('profile.theme') }}</span>
        <SegmentedControl v-model="theme" :options="themeOptions" :label="t('profile.theme')" />
      </div>
      <div class="flex flex-wrap items-center justify-between gap-2">
        <span class="text-sm">{{ t('profile.language') }}</span>
        <LocaleSwitcher class="w-40" />
      </div>
    </section>

    <form class="card space-y-4 p-5" novalidate @submit.prevent="changePassword">
      <h2 class="font-medium">{{ t('profile.security') }}</h2>
      <AppInput
        v-model="passwords.currentPassword"
        type="password"
        :label="t('profile.currentPassword')"
        :error="passwordForm.errors.value.currentPassword"
        autocomplete="current-password"
      />
      <AppInput
        v-model="passwords.newPassword"
        type="password"
        :label="t('profile.newPassword')"
        :hint="t('auth.passwordHint')"
        :error="passwordForm.errors.value.newPassword"
        autocomplete="new-password"
      />
      <div class="flex justify-end">
        <AppButton type="submit" :loading="passwordForm.submitting.value">{{ t('profile.changePassword') }}</AppButton>
      </div>
    </form>

    <AppButton variant="ghost" @click="logout">
      <LogOut class="size-4" aria-hidden="true" />
      {{ t('auth.logout') }}
    </AppButton>
  </div>
</template>
