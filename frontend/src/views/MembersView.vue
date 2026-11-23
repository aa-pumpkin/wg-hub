<script setup lang="ts">
import { Copy, LogOut, Snowflake, Trash2, UserPlus } from 'lucide-vue-next'
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import AppButton from '@/components/common/AppButton.vue'
import AppMenu, { type MenuItem } from '@/components/common/AppMenu.vue'
import AppModal from '@/components/common/AppModal.vue'
import AppSelect from '@/components/common/AppSelect.vue'
import IconButton from '@/components/common/IconButton.vue'
import PageHeader from '@/components/common/PageHeader.vue'
import UserAvatar from '@/components/common/UserAvatar.vue'
import { useAction } from '@/composables/useForm'
import { useLive } from '@/composables/useLive'
import { householdService } from '@/services/households'
import { pointsService } from '@/services/modules'
import { useAuthStore } from '@/stores/auth'
import { useHouseholdStore } from '@/stores/household'
import { useUiStore } from '@/stores/ui'
import type { Invitation, Member, MemberStats } from '@/types'
import { DateTime } from 'luxon'

const { t, locale } = useI18n()
const household = useHouseholdStore()
const auth = useAuthStore()
const ui = useUiStore()
const router = useRouter()
const act = useAction()

const stats = ref<MemberStats>({ open: {}, completed: {} })
const invitations = ref<Invitation[]>([])
const validity = ref<'1' | '7' | '30' | 'never'>('7')
const creating = ref(false)

const validityOptions = computed(() => [
  { value: '1' as const, label: t('members.valid1') },
  { value: '7' as const, label: t('members.valid7') },
  { value: '30' as const, label: t('members.valid30') },
  { value: 'never' as const, label: t('members.validForever') },
])

const inviteUrl = (invitation: Invitation) => `${location.origin}/invite/${invitation.code}`
const shortDate = (iso: string) =>
  DateTime.fromISO(iso, { zone: household.timezone })
    .setLocale(locale.value)
    .toLocaleString(DateTime.DATE_MED)

const levels = ref<Record<string, number>>({})
const levelOf = (userId: string) => levels.value[userId]

async function loadStats() {
  await act(async () => {
    const [memberStats, progress] = await Promise.all([
      householdService.stats(household.id),
      pointsService.leaderboard(household.id),
    ])
    stats.value = memberStats
    levels.value = Object.fromEntries(progress.map((entry) => [entry.user.id, entry.level]))
  })
}

async function loadInvitations() {
  if (!household.isAdmin) return
  await act(async () => {
    invitations.value = await householdService.invitations(household.id)
  })
}

onMounted(() => {
  void loadStats()
  void loadInvitations()
})
useLive(['members', 'chores'], loadStats)
useLive(['members'], loadInvitations)
watch(() => household.isAdmin, loadInvitations)

async function createLink() {
  creating.value = true
  await act(async () => {
    await householdService.createInvitation(
      household.id,
      validity.value === 'never' ? null : Number(validity.value),
    )
    await loadInvitations()
  })
  creating.value = false
}

async function copyLink(url: string, event: MouseEvent) {
  try {
    await navigator.clipboard.writeText(url)
    ui.toast(t('members.copied'))
  } catch {
    // Clipboard needs HTTPS. Select the text instead, so copying by hand is one step.
    const row = (event.currentTarget as HTMLElement).closest('[data-link-row]')
    row?.querySelector('input')?.select()
    ui.toast(t('members.copyFailed'), 'error')
  }
}

async function revoke(invitation: Invitation) {
  const confirmed = await ui.confirm({
    title: t('members.revokeConfirm'),
    message: t('members.revokeText'),
    confirmLabel: t('members.revoke'),
  })
  if (!confirmed) return
  await act(async () => {
    await householdService.revokeInvitation(household.id, invitation.id)
    await loadInvitations()
  })
}

async function setRole(member: Member, role: Member['role']) {
  await act(async () => {
    await householdService.setRole(household.id, member.id, role)
    await household.refresh()
  })
}

async function freeze(member: Member) {
  const confirmed = await ui.confirm({
    title: t('members.freezeConfirm', { name: member.displayName }),
    message: t('members.freezeText'),
    confirmLabel: t('members.freeze'),
    danger: false,
  })
  if (!confirmed) return
  await act(async () => {
    await householdService.setFrozen(household.id, member.id, true)
    await household.refresh()
    await loadStats()
  })
}

async function unfreeze(member: Member) {
  const ok = await act(async () => {
    await householdService.setFrozen(household.id, member.id, false)
    await household.refresh()
    await loadStats()
  })
  if (ok) ui.toast(t('members.unfrozen', { name: member.displayName }))
}

async function removeMember(member: Member) {
  const confirmed = await ui.confirm({
    title: t('members.removeConfirm', { name: member.displayName }),
    message: t('members.removeText'),
    confirmLabel: t('members.remove'),
  })
  if (!confirmed) return
  const ok = await act(async () => {
    await householdService.removeMember(household.id, member.id)
    await household.refresh()
    await loadStats()
  })
  if (ok) ui.toast(t('members.removed', { name: member.displayName }))
}

async function leave() {
  const confirmed = await ui.confirm({
    title: t('members.leaveConfirm', { name: household.current?.name ?? '' }),
    message: t('members.leaveText'),
    confirmLabel: t('members.leave'),
  })
  if (!confirmed) return
  const ok = await act(() => householdService.leave(household.id))
  if (ok) {
    ui.toast(t('members.left'))
    await router.push({ name: 'home' })
  }
}

const resetLink = ref<{ name: string; url: string } | null>(null)
const resetOpen = computed({
  get: () => resetLink.value !== null,
  set: (value: boolean) => {
    if (!value) resetLink.value = null
  },
})

async function createReset(member: Member) {
  await act(async () => {
    const { token } = await householdService.createPasswordReset(household.id, member.id)
    resetLink.value = { name: member.displayName, url: `${location.origin}/reset/${token}` }
  })
}

function menuFor(member: Member): MenuItem[] {
  const freezeItem: MenuItem = member.frozen
    ? { label: t('members.unfreeze'), action: () => unfreeze(member) }
    : { label: t('members.freeze'), action: () => freeze(member) }
  // Going away is everyone's own call; doing it for someone else takes an admin.
  if (!household.isAdmin) return member.id === auth.user?.id ? [freezeItem] : []
  const items: MenuItem[] = [
    freezeItem,
    member.role === 'ADMIN'
      ? { label: t('members.makeMember'), action: () => setRole(member, 'MEMBER') }
      : { label: t('members.makeAdmin'), action: () => setRole(member, 'ADMIN') },
  ]
  if (member.id !== auth.user?.id) {
    items.push({ label: t('members.passwordReset'), action: () => createReset(member) })
    items.push({ label: t('members.remove'), action: () => removeMember(member), danger: true })
  }
  return items
}
</script>

<template>
  <div>
    <PageHeader :title="t('members.title')" />

    <ul class="card divide-y divide-line">
      <li v-for="member in household.members" :key="member.id" class="list-row">
        <UserAvatar :name="member.displayName" :color="member.avatarColor" size="lg" />
        <div class="min-w-0 flex-1">
          <p class="flex flex-wrap items-center gap-x-2 gap-y-0.5 text-sm font-medium">
            <span class="min-w-0 break-words">
              {{ member.displayName }}
              <span v-if="member.id === auth.user?.id" class="font-normal text-muted">({{ t('common.you') }})</span>
            </span>
            <span v-if="levelOf(member.id)" class="rounded-full bg-subtle px-2 py-px text-[11px] font-medium whitespace-nowrap text-muted">
              {{ t('points.levelShort', { level: levelOf(member.id) }) }}
            </span>
            <span v-if="member.frozen" class="flex items-center gap-1 rounded-full bg-subtle px-2 py-px text-[11px] font-medium whitespace-nowrap text-muted">
              <Snowflake class="size-3" aria-hidden="true" />{{ t('members.frozen') }}
            </span>
            <span v-if="member.role === 'ADMIN'" class="rounded-full bg-accent-soft px-2 py-px text-[11px] font-medium text-accent-text">
              {{ t('members.admin') }}
            </span>
          </p>
          <p class="truncate text-[13px] text-muted">{{ member.username }}</p>
          <p class="text-[13px] text-muted">
            {{ t('members.openTasks', { count: stats.open[member.id] ?? 0 }) }} ·
            {{ t('members.doneTasks', { count: stats.completed[member.id] ?? 0 }) }} ·
            {{ t('members.joined', { date: shortDate(member.joinedAt) }) }}
          </p>
        </div>
        <AppMenu v-if="menuFor(member).length" :items="menuFor(member)" :label="t('common.actions')" />
      </li>
    </ul>

    <section v-if="household.isAdmin" class="mt-6">
      <h2 class="mb-0.5 flex items-center gap-2 font-medium">
        <UserPlus class="size-4" aria-hidden="true" />
        {{ t('members.inviteTitle') }}
      </h2>
      <p class="mb-3 text-sm text-muted">{{ t('members.inviteText') }}</p>
      <div class="mb-3 flex flex-wrap items-end gap-2">
        <AppSelect v-model="validity" :label="t('members.validity')" :options="validityOptions" class="w-44" />
        <AppButton variant="primary" :loading="creating" class="min-h-[42px]" @click="createLink">
          {{ t('members.newLink') }}
        </AppButton>
      </div>
      <p v-if="invitations.length === 0" class="text-sm text-muted">{{ t('members.noLinks') }}</p>
      <ul v-else class="card divide-y divide-line">
        <li v-for="invitation in invitations" :key="invitation.id" class="px-4 py-3" data-link-row>
          <div class="flex items-center gap-1">
            <input
              class="input min-h-9 min-w-0 flex-1 font-mono !text-[13px]"
              readonly
              :value="inviteUrl(invitation)"
              :aria-label="t('members.copy')"
              @focus="($event.target as HTMLInputElement).select()"
            />
            <IconButton :label="t('members.copy')" @click="copyLink(inviteUrl(invitation), $event)">
              <Copy class="size-4" aria-hidden="true" />
            </IconButton>
            <IconButton :label="t('members.revoke')" @click="revoke(invitation)">
              <Trash2 class="size-4" aria-hidden="true" />
            </IconButton>
          </div>
          <p class="mt-1 text-[13px] text-muted">
            {{ invitation.expiresAt ? t('members.expires', { date: shortDate(invitation.expiresAt) }) : t('members.neverExpires') }}
            · {{ t('members.used', { count: invitation.useCount }) }}
          </p>
        </li>
      </ul>
    </section>

    <AppModal v-model:open="resetOpen" :title="t('members.resetTitle', { name: resetLink?.name ?? '' })">
      <p class="mb-3 text-sm text-muted">{{ t('members.resetText') }}</p>
      <div class="flex items-center gap-1" data-link-row>
        <input
          class="input min-h-9 min-w-0 flex-1 font-mono !text-[13px]"
          readonly
          :value="resetLink?.url"
          :aria-label="t('members.copy')"
          @focus="($event.target as HTMLInputElement).select()"
        />
        <IconButton :label="t('members.copy')" @click="copyLink(resetLink?.url ?? '', $event)">
          <Copy class="size-4" aria-hidden="true" />
        </IconButton>
      </div>
    </AppModal>

    <div class="mt-8">
      <AppButton variant="ghost" @click="leave">
        <LogOut class="size-4" aria-hidden="true" />
        {{ t('members.leave') }}
      </AppButton>
    </div>
  </div>
</template>
