<script setup lang="ts">
import {
  CalendarDays,
  House,
  ListChecks,
  Menu,
  ShoppingCart,
  StickyNote,
  Users,
  Wallet,
  Settings,
  Trophy,
  UtensilsCrossed,
} from 'lucide-vue-next'
import { computed, onUnmounted, ref, watch, type Component } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppMenu, { type MenuItem } from '@/components/common/AppMenu.vue'
import LocaleSwitcher from '@/components/common/LocaleSwitcher.vue'
import UserAvatar from '@/components/common/UserAvatar.vue'
import { connectLive, disconnectLive, useLive } from '@/composables/useLive'
import { ApiError } from '@/services/api'
import { useAuthStore } from '@/stores/auth'
import { useHouseholdStore } from '@/stores/household'
import { useI18n } from 'vue-i18n'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const household = useHouseholdStore()
const { t } = useI18n()

const ready = ref(false)
const failed = ref(false)
const routeId = computed(() => route.params.householdId as string)

interface NavItem {
  name: string
  label: string
  icon: Component
}

const primaryNav = computed<NavItem[]>(() => [
  { name: 'dashboard', label: t('nav.dashboard'), icon: House },
  { name: 'chores', label: t('nav.chores'), icon: ListChecks },
  { name: 'shopping', label: t('nav.shopping'), icon: ShoppingCart },
  { name: 'calendar', label: t('nav.calendar'), icon: CalendarDays },
  { name: 'notes', label: t('nav.notes'), icon: StickyNote },
  { name: 'expenses', label: t('nav.expenses'), icon: Wallet },
  { name: 'points', label: t('nav.points'), icon: Trophy },
  // Not every WG has a dishwasher, so this appears once the admin turns it on.
  ...(household.current?.dishwasherTarget != null
    ? [{ name: 'dishwasher', label: t('nav.dishwasher'), icon: UtensilsCrossed }]
    : []),
])
const secondaryNav = computed<NavItem[]>(() => [
  { name: 'members', label: t('nav.members'), icon: Users },
  { name: 'settings', label: t('nav.settings'), icon: Settings },
])
// The phone's bottom bar: the four daily things, everything else behind "More".
const mobileNav = computed<NavItem[]>(() => [
  ...primaryNav.value.slice(0, 4),
  { name: 'more', label: t('common.more'), icon: Menu },
])
const moreRoutes = new Set(['more', 'notes', 'expenses', 'points', 'dishwasher', 'members', 'settings', 'profile'])

const isActive = (name: string) =>
  route.name === name || (name === 'more' && moreRoutes.has(String(route.name)))

const switchItems = computed<MenuItem[]>(() => [
  ...household.list
    .filter((item) => item.id !== routeId.value)
    .map((item) => ({
      label: `${item.icon} ${item.name}`,
      action: () => router.push({ name: 'dashboard', params: { householdId: item.id } }),
    })),
  { label: t('nav.newWg'), action: () => router.push({ name: 'welcome' }) },
])

async function open(id: string) {
  ready.value = false
  failed.value = false
  try {
    await Promise.all([household.open(id), household.loadList()])
    connectLive(id)
    ready.value = true
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) {
      await router.replace({ name: 'home' })
    } else {
      failed.value = true
    }
  }
}

watch(routeId, (id) => id && open(id), { immediate: true })

async function refreshHousehold() {
  try {
    await household.refresh()
    await household.loadList()
  } catch (error) {
    // Removed from this WG by someone else.
    if (error instanceof ApiError && error.status === 404) await router.replace({ name: 'home' })
  }
}
useLive(['members', 'household'], refreshHousehold)

onUnmounted(() => {
  disconnectLive()
  household.close()
})
</script>

<template>
  <div class="min-h-dvh md:grid md:grid-cols-[15rem_1fr]">
    <!-- Desktop sidebar -->
    <aside class="sticky top-0 hidden h-dvh flex-col border-r border-line px-3 py-4 md:flex">
      <div class="flex items-center gap-1 px-1 pb-4">
        <span class="text-lg" aria-hidden="true">{{ household.current?.icon }}</span>
        <span class="min-w-0 flex-1 truncate pl-1 text-sm font-semibold">{{ household.current?.name }}</span>
        <AppMenu :items="switchItems" :label="t('nav.switchWg')" class="[&>button]:size-8" />
      </div>
      <nav :aria-label="t('nav.main')" class="flex flex-1 flex-col gap-0.5">
        <RouterLink
          v-for="item in primaryNav"
          :key="item.name"
          :to="{ name: item.name, params: { householdId: routeId } }"
          class="flex h-9 items-center gap-2.5 rounded-lg px-2.5 text-sm transition-colors"
          :class="isActive(item.name) ? 'bg-subtle font-medium' : 'text-muted hover:bg-subtle hover:text-fg'"
          :aria-current="isActive(item.name) ? 'page' : undefined"
        >
          <component :is="item.icon" class="size-4" aria-hidden="true" />
          {{ item.label }}
        </RouterLink>
        <div class="my-3 border-t border-line" />
        <RouterLink
          v-for="item in secondaryNav"
          :key="item.name"
          :to="{ name: item.name, params: { householdId: routeId } }"
          class="flex h-9 items-center gap-2.5 rounded-lg px-2.5 text-sm transition-colors"
          :class="isActive(item.name) ? 'bg-subtle font-medium' : 'text-muted hover:bg-subtle hover:text-fg'"
          :aria-current="isActive(item.name) ? 'page' : undefined"
        >
          <component :is="item.icon" class="size-4" aria-hidden="true" />
          {{ item.label }}
        </RouterLink>
      </nav>
      <LocaleSwitcher class="mb-2" />
      <RouterLink
        :to="{ name: 'profile', params: { householdId: routeId } }"
        class="flex items-center gap-2.5 rounded-lg p-1.5 text-sm hover:bg-subtle"
        :class="route.name === 'profile' && 'bg-subtle'"
      >
        <UserAvatar v-if="auth.user" :name="auth.user.displayName" :color="auth.user.avatarColor" />
        <span class="min-w-0 flex-1 truncate">{{ auth.user?.displayName }}</span>
      </RouterLink>
    </aside>

    <div class="flex min-w-0 flex-col">
      <main class="mx-auto w-full max-w-5xl flex-1 px-4 pt-5 pb-28 md:px-8 md:pt-8 md:pb-10">
        <template v-if="ready">
          <RouterView :key="routeId" />
        </template>
        <div v-else-if="failed" class="py-16 text-center">
          <p class="mb-3 text-sm text-muted">{{ t('common.loadFailed') }}</p>
          <button class="text-sm font-medium text-accent-text underline" @click="open(routeId)">
            {{ t('common.retry') }}
          </button>
        </div>
        <div v-else class="space-y-3" aria-busy="true">
          <div class="skeleton h-8 w-48" />
          <div class="skeleton h-40" />
          <div class="skeleton h-40" />
        </div>
      </main>
    </div>

    <!-- Phone bottom navigation -->
    <nav
      :aria-label="t('nav.main')"
      class="fixed inset-x-0 bottom-0 z-20 grid grid-cols-5 border-t border-line bg-surface pb-[env(safe-area-inset-bottom)] md:hidden"
    >
      <RouterLink
        v-for="item in mobileNav"
        :key="item.name"
        :to="{ name: item.name, params: { householdId: routeId } }"
        class="flex h-14 flex-col items-center justify-center gap-0.5 text-[11px]"
        :class="isActive(item.name) ? 'font-medium text-accent-text' : 'text-muted'"
        :aria-current="isActive(item.name) ? 'page' : undefined"
      >
        <component :is="item.icon" class="size-5" aria-hidden="true" />
        {{ item.label }}
      </RouterLink>
    </nav>
  </div>
</template>
