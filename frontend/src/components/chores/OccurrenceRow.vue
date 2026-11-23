<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import CheckCircle from '@/components/common/CheckCircle.vue'
import UserAvatar from '@/components/common/UserAvatar.vue'
import { useFormat } from '@/composables/useFormat'
import { addDays } from '@/lib/calendar'
import type { Occurrence } from '@/types'

const props = defineProps<{ occurrence: Occurrence; showDate?: boolean; overdue?: boolean }>()
defineEmits<{ toggle: [] }>()
const { t } = useI18n()
const format = useFormat()

const done = computed(() => props.occurrence.status === 'COMPLETED')
// Repeating chores can be ticked off from a week before they are due, not earlier.
const locked = computed(
  () =>
    !done.value &&
    props.occurrence.recurrence !== 'NONE' &&
    props.occurrence.dueDate >= addDays(format.today(), 7),
)
const pointsLabel = computed(() => {
  const value = props.occurrence.awardedPoints ?? props.occurrence.points
  return value > 0 ? `+${value}` : null
})
// A main task is for the whole week, so its deadline is what matters.
const meta = computed(() => {
  if (props.occurrence.kind === 'MAIN' && props.occurrence.status === 'PENDING') {
    return t('chores.dueUntil', { date: format.date(props.occurrence.dueDate) })
  }
  return props.showDate ? format.relativeDate(props.occurrence.dueDate) : null
})
</script>

<template>
  <div class="list-row">
    <CheckCircle
      :checked="done"
      :label="t('chores.markDone', { title: occurrence.title })"
      :disabled="locked"
      :title="locked ? t('errors.too_early') : undefined"
      @toggle="$emit('toggle')"
    />
    <div class="min-w-0 flex-1">
      <p class="truncate text-sm font-medium" :class="done && 'text-muted line-through'">
        {{ occurrence.title }}
      </p>
      <p v-if="meta" class="truncate text-[13px]" :class="overdue ? 'text-danger' : 'text-muted'">{{ meta }}</p>
    </div>
    <span v-if="pointsLabel" class="shrink-0 text-xs font-medium text-accent-text tabular-nums">{{ pointsLabel }}</span>
    <UserAvatar
      v-if="occurrence.assignee"
      :name="occurrence.assignee.displayName"
      :color="occurrence.assignee.avatarColor"
      size="sm"
    />
    <slot />
  </div>
</template>
