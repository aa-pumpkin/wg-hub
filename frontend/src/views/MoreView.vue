<script setup lang="ts">
import { ChevronRight, CircleUser, Repeat, Settings, StickyNote, Trophy, Users, UtensilsCrossed, Wallet } from 'lucide-vue-next'
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import LocaleSwitcher from '@/components/common/LocaleSwitcher.vue'
import PageHeader from '@/components/common/PageHeader.vue'
import { useHouseholdStore } from '@/stores/household'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const household = useHouseholdStore()

const links = computed(() => [
  { name: 'notes', label: t('nav.notes'), icon: StickyNote },
  { name: 'expenses', label: t('nav.expenses'), icon: Wallet },
  { name: 'points', label: t('nav.points'), icon: Trophy },
  ...(household.current?.dishwasherTarget != null
    ? [{ name: 'dishwasher', label: t('nav.dishwasher'), icon: UtensilsCrossed }]
    : []),
  { name: 'members', label: t('nav.members'), icon: Users },
  { name: 'settings', label: t('nav.settings'), icon: Settings },
  { name: 'profile', label: t('nav.profile'), icon: CircleUser },
])
const others = computed(() => household.list.filter((item) => item.id !== household.id))
</script>

<template>
  <div>
    <PageHeader :title="household.current?.name ?? ''" />
    <ul class="card divide-y divide-line">
      <li v-for="link in links" :key="link.name">
        <RouterLink
          :to="{ name: link.name, params: { householdId: route.params.householdId } }"
          class="flex min-h-13 items-center gap-3 px-4 text-sm"
        >
          <component :is="link.icon" class="size-5 text-muted" aria-hidden="true" />
          <span class="flex-1">{{ link.label }}</span>
          <ChevronRight class="size-4 text-muted" aria-hidden="true" />
        </RouterLink>
      </li>
    </ul>

    <div class="card mt-4 space-y-1 p-1.5">
      <LocaleSwitcher />
    </div>

    <h2 class="section-title mt-6 mb-1.5">{{ t('nav.switchWg') }}</h2>
    <ul class="card divide-y divide-line">
      <li v-for="other in others" :key="other.id">
        <button
          type="button"
          class="flex min-h-13 w-full items-center gap-3 px-4 text-left text-sm"
          @click="router.push({ name: 'dashboard', params: { householdId: other.id } })"
        >
          <span class="text-lg" aria-hidden="true">{{ other.icon }}</span>
          {{ other.name }}
        </button>
      </li>
      <li>
        <RouterLink :to="{ name: 'welcome' }" class="flex min-h-13 items-center gap-3 px-4 text-sm">
          <Repeat class="size-5 text-muted" aria-hidden="true" />
          {{ t('nav.newWg') }}
        </RouterLink>
      </li>
    </ul>
  </div>
</template>
