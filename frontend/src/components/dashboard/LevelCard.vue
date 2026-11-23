<script setup lang="ts">
import { Flame } from 'lucide-vue-next'
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { MemberProgress } from '@/types'

const props = defineProps<{ progress: MemberProgress }>()
const { t } = useI18n()

const percent = computed(() => {
  const { points, levelFloor, levelNext } = props.progress
  // Strikes can take a total below zero; the bar simply stays empty.
  return Math.max(0, Math.round(((points - levelFloor) / (levelNext - levelFloor)) * 100))
})
</script>

<template>
  <div class="card flex h-full flex-col justify-center gap-2 px-4 py-3">
    <div class="flex items-baseline justify-between gap-2">
      <p class="min-w-0 truncate text-sm font-semibold">
        {{ t('points.levelShort', { level: progress.level }) }} · {{ t(`points.levels.l${Math.min(progress.level, 10)}`) }}
      </p>
      <p class="shrink-0 text-sm tabular-nums">{{ t('points.total', { points: progress.points }) }}</p>
    </div>
    <div
      class="h-2 overflow-hidden rounded-full bg-subtle"
      role="progressbar"
      :aria-valuenow="percent"
      aria-valuemin="0"
      aria-valuemax="100"
      :aria-label="t('points.toNext', { points: progress.levelNext - progress.points })"
    >
      <div class="h-full rounded-full bg-accent transition-all" :style="{ width: `${percent}%` }" />
    </div>
    <div class="flex flex-wrap items-center justify-between gap-x-3 text-[13px] text-muted">
      <span>{{ t('points.toNext', { points: progress.levelNext - progress.points }) }}</span>
      <span v-if="progress.streak > 0" class="flex items-center gap-1 whitespace-nowrap text-warn">
        <Flame class="size-3.5" aria-hidden="true" />{{ t('points.streak', progress.streak) }}
      </span>
    </div>
  </div>
</template>
