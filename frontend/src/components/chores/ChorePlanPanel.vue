<script setup lang="ts">
import { ArrowDown, ArrowUp, Check, RotateCw, Snowflake } from 'lucide-vue-next'
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import AppButton from '@/components/common/AppButton.vue'
import AppMenu from '@/components/common/AppMenu.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import IconButton from '@/components/common/IconButton.vue'
import LoadError from '@/components/common/LoadError.vue'
import SkeletonList from '@/components/common/SkeletonList.vue'
import UserAvatar from '@/components/common/UserAvatar.vue'
import { useAction } from '@/composables/useForm'
import { useFormat } from '@/composables/useFormat'
import { useLive } from '@/composables/useLive'
import { addDays } from '@/lib/calendar'
import { householdService } from '@/services/households'
import { choreService } from '@/services/modules'
import { useChoreStore } from '@/stores/chores'
import { useHouseholdStore } from '@/stores/household'
import type { Chore, ChorePlan } from '@/types'

defineEmits<{ edit: [chore: Chore]; end: [chore: Chore] }>()

const { t } = useI18n()
const household = useHouseholdStore()
const store = useChoreStore()
const format = useFormat()
const act = useAction()

const plan = ref<ChorePlan | null>(null)
const failed = ref(false)

const mainTasks = computed(() =>
  store.series.filter((chore) => chore.kind === 'MAIN').sort((a, b) => a.position - b.position),
)
const seats = computed(() => plan.value?.circle ?? [])

async function load() {
  try {
    plan.value = await choreService.plan(household.id)
    failed.value = false
  } catch {
    failed.value = plan.value === null
  }
}
onMounted(load)
useLive(['chores', 'members'], load)

const weekRange = (dueDate: string) => `${format.date(addDays(dueDate, -6))} – ${format.date(dueDate)}`
const weekTitle = (index: number) =>
  index === 0 ? t('chores.planThisWeek') : index === 1 ? t('chores.planNextWeek') : t('chores.planInWeeks', { count: index })

function swapped<T>(list: readonly T[], index: number, step: number): T[] {
  const next = [...list]
  const other = index + step
  ;[next[index], next[other]] = [next[other]!, next[index]!]
  return next
}

const update = (action: () => Promise<unknown>) =>
  act(async () => {
    await action()
    await Promise.all([load(), store.load(household.id)])
  })

const saveCircle = (userIds: string[]) => update(() => choreService.setCircle(household.id, userIds))

function moveSeat(index: number, step: number) {
  return saveCircle(swapped(seats.value.map((seat) => seat.user.id), index, step))
}

const setFrozen = (userId: string, frozen: boolean) =>
  update(async () => {
    await householdService.setFrozen(household.id, userId, frozen)
    await household.refresh()
  })
</script>

<template>
  <LoadError v-if="failed" @retry="load" />
  <SkeletonList v-else-if="!plan" />
  <div v-else class="space-y-6">
    <EmptyState v-if="mainTasks.length === 0" :icon="RotateCw" :title="t('chores.noMain')" :text="t('chores.noMainText')" class="card" />
    <template v-else>
      <section>
        <h2 class="section-title mb-1.5">{{ t('chores.planTitle') }}</h2>
        <div class="grid grid-cols-1 gap-3 lg:grid-cols-2">
          <div v-for="(week, index) in plan.weeks" :key="week.dueDate" class="card overflow-hidden" :class="index === 0 && 'border-accent'">
            <h3 class="flex items-baseline justify-between gap-2 px-4 pt-3 pb-1.5">
              <span class="text-sm font-semibold">{{ weekTitle(index) }}</span>
              <span class="text-xs text-muted">{{ weekRange(week.dueDate) }}</span>
            </h3>
            <ul class="divide-y divide-line">
              <li v-for="item in week.items" :key="item.id" class="list-row">
                <span class="min-w-0 flex-1 truncate text-sm" :class="item.status === 'COMPLETED' && 'text-muted line-through'">{{ item.title }}</span>
                <Check v-if="item.status === 'COMPLETED'" class="size-4 text-accent-text" :aria-label="t('chores.done')" />
                <template v-if="item.assignee">
                  <span class="w-20 truncate text-right text-[13px] text-muted">{{ item.assignee.displayName }}</span>
                  <UserAvatar :name="item.assignee.displayName" :color="item.assignee.avatarColor" size="sm" />
                </template>
              </li>
            </ul>
          </div>
        </div>
      </section>
    </template>

    <div class="grid grid-cols-1 items-start gap-5 lg:grid-cols-2">
      <section>
        <h2 class="section-title mb-1.5">{{ t('chores.circleTitle') }}</h2>
        <ul class="card divide-y divide-line">
          <li v-for="(seat, index) in seats" :key="seat.user.id" class="list-row">
            <span class="w-4 text-center text-[13px] text-muted tabular-nums">{{ index + 1 }}</span>
            <UserAvatar :name="seat.user.displayName" :color="seat.user.avatarColor" size="sm" />
            <span class="min-w-0 flex-1 truncate text-sm" :class="seat.frozen && 'text-muted'">
              {{ seat.user.displayName }}
            </span>
            <span v-if="seat.frozen" class="flex items-center gap-1 text-[13px] whitespace-nowrap text-muted">
              <Snowflake class="size-3.5" aria-hidden="true" />{{ t('members.frozen') }}
            </span>
            <template v-if="household.isAdmin">
              <IconButton :label="t('chores.moveUp')" :disabled="index === 0" class="disabled:opacity-30" @click="moveSeat(index, -1)">
                <ArrowUp class="size-4" aria-hidden="true" />
              </IconButton>
              <IconButton :label="t('chores.moveDown')" :disabled="index === seats.length - 1" class="disabled:opacity-30" @click="moveSeat(index, 1)">
                <ArrowDown class="size-4" aria-hidden="true" />
              </IconButton>
              <AppMenu
                :label="t('common.actions')"
                :items="[
                  seat.frozen
                    ? { label: t('members.unfreeze'), action: () => setFrozen(seat.user.id, false) }
                    : { label: t('members.freeze'), action: () => setFrozen(seat.user.id, true) },
                ]"
              />
            </template>
          </li>
        </ul>
        <p class="mt-1.5 text-[13px] text-muted">{{ t('chores.circleHint') }}</p>
      </section>

      <section>
        <h2 class="section-title mb-1.5">{{ t('chores.mainTitle') }}</h2>
        <ul v-if="mainTasks.length" class="card divide-y divide-line">
          <li v-for="task in mainTasks" :key="task.id" class="list-row">
            <div class="min-w-0 flex-1">
              <p class="truncate text-sm font-medium">{{ task.title }}</p>
              <p class="truncate text-[13px] text-muted">{{ t('points.short', { points: task.points }) }}</p>
            </div>
            <AppMenu
              v-if="household.isAdmin"
              :label="t('common.actions')"
              :items="[
                { label: t('common.edit'), action: () => $emit('edit', task) },
                { label: t('chores.endSeries'), action: () => $emit('end', task), danger: true },
              ]"
            />
          </li>
        </ul>
        <div v-if="household.isAdmin && mainTasks.length" class="mt-2 flex flex-wrap items-center gap-2">
          <span class="text-[13px] text-muted">{{ t('chores.shiftLabel') }}</span>
          <AppButton @click="update(() => choreService.shift(household.id, -1))">{{ t('chores.shiftBack') }}</AppButton>
          <AppButton @click="update(() => choreService.shift(household.id, 1))">{{ t('chores.shiftForward') }}</AppButton>
        </div>
      </section>
    </div>
  </div>
</template>
