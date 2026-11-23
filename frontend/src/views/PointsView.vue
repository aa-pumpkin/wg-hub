<script setup lang="ts">
import { Flame, Lock } from 'lucide-vue-next'
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import LoadError from '@/components/common/LoadError.vue'
import PageHeader from '@/components/common/PageHeader.vue'
import SegmentedControl from '@/components/common/SegmentedControl.vue'
import SkeletonList from '@/components/common/SkeletonList.vue'
import UserAvatar from '@/components/common/UserAvatar.vue'
import LevelCard from '@/components/dashboard/LevelCard.vue'
import { useLive } from '@/composables/useLive'
import { pointsService } from '@/services/modules'
import { useAuthStore } from '@/stores/auth'
import { useHouseholdStore } from '@/stores/household'
import type { MemberProgress } from '@/types'

const BADGES = [
  { key: 'first', icon: '🧽' },
  { key: 'ten', icon: '🧹' },
  { key: 'fifty', icon: '🏅' },
  { key: 'hundred', icon: '🏆' },
  { key: 'helper', icon: '🤝' },
  { key: 'streak4', icon: '🔥' },
  { key: 'streak12', icon: '☄️' },
  { key: 'level5', icon: '⭐' },
] as const

const { t } = useI18n()
const auth = useAuthStore()
const household = useHouseholdStore()

const members = ref<MemberProgress[]>([])
const loading = ref(true)
const failed = ref(false)
const period = ref<'month' | 'all'>('month')

const periods = computed(() => [
  { value: 'month' as const, label: t('points.month') },
  { value: 'all' as const, label: t('points.allTime') },
])
const score = (member: MemberProgress) =>
  period.value === 'month' ? member.monthPoints : member.points
const ranking = computed(() => [...members.value].sort((a, b) => score(b) - score(a)))
const me = computed(() => members.value.find((member) => member.user.id === auth.user?.id) ?? null)

async function load() {
  try {
    members.value = await pointsService.leaderboard(household.id)
    failed.value = false
  } catch {
    failed.value = members.value.length === 0
  } finally {
    loading.value = false
  }
}
onMounted(load)
useLive(['chores', 'members'], load)
</script>

<template>
  <div>
    <PageHeader :title="t('points.title')" />

    <LoadError v-if="failed" @retry="load" />
    <SkeletonList v-else-if="loading" />
    <div v-else class="grid grid-cols-1 items-start gap-5 lg:grid-cols-2">
      <div class="space-y-5">
        <section>
          <h2 class="section-title mb-1.5 flex min-h-10 items-center">{{ t('points.yourLevel') }}</h2>
          <LevelCard v-if="me" :progress="me" />
        </section>
        <section>
          <h2 class="section-title mb-1.5">{{ t('points.badges') }}</h2>
          <ul class="card grid grid-cols-4 gap-1 p-2">
            <li
              v-for="badge in BADGES"
              :key="badge.key"
              class="flex min-h-20 flex-col items-center rounded-lg p-1 text-center"
              :title="t(`points.badgeHints.${badge.key}`)"
              :class="me?.badges.includes(badge.key) ? '' : 'opacity-45'"
            >
              <span class="flex h-8 items-center justify-center">
                <span v-if="me?.badges.includes(badge.key)" class="text-2xl" aria-hidden="true">{{ badge.icon }}</span>
                <Lock v-else class="size-5 text-muted" aria-hidden="true" />
              </span>
              <span class="mt-1 text-[11px] leading-tight">{{ t(`points.badgeNames.${badge.key}`) }}</span>
            </li>
          </ul>
        </section>
      </div>

      <div class="space-y-5">
        <section>
          <div class="mb-1.5 flex min-h-10 flex-wrap items-center justify-between gap-2">
            <h2 class="section-title">{{ t('points.ranking') }}</h2>
            <SegmentedControl v-model="period" :options="periods" :label="t('points.ranking')" />
          </div>
          <ol class="card divide-y divide-line">
            <li v-for="(member, index) in ranking" :key="member.user.id" class="list-row">
              <span class="w-5 text-center text-sm font-semibold text-muted tabular-nums">{{ index + 1 }}</span>
              <UserAvatar :name="member.user.displayName" :color="member.user.avatarColor" />
              <div class="min-w-0 flex-1">
                <p class="truncate text-sm font-medium">
                  {{ member.user.displayName }}
                  <span v-if="member.user.id === auth.user?.id" class="font-normal text-muted">({{ t('common.you') }})</span>
                </p>
                <p class="truncate text-[13px] text-muted">
                  {{ t('points.levelShort', { level: member.level }) }} · {{ t(`points.levels.l${Math.min(member.level, 10)}`) }}
                </p>
              </div>
              <span v-if="member.streak > 0" class="flex items-center gap-0.5 text-[13px] text-warn" :title="t('points.streak', member.streak)">
                <Flame class="size-3.5" aria-hidden="true" />{{ member.streak }}
              </span>
              <span class="w-14 text-right text-sm font-semibold tabular-nums">{{ score(member) }}</span>
            </li>
          </ol>
        </section>
        <section>
          <h2 class="section-title mb-1.5">{{ t('points.howTitle') }}</h2>
          <ul class="card space-y-1.5 p-4 text-sm text-muted">
            <li>{{ t('points.howOnTime') }}</li>
            <li>{{ t('points.howLate') }}</li>
            <li>{{ t('points.howStreak') }}</li>
            <li v-if="household.current?.dishwasherTarget != null">{{ t('points.howStrike') }}</li>
          </ul>
        </section>
      </div>
    </div>
  </div>
</template>
