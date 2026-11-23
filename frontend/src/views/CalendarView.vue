<script setup lang="ts">
import { ChevronLeft, ChevronRight } from 'lucide-vue-next'
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import CalendarDayList from '@/components/calendar/CalendarDayList.vue'
import CalendarItemPill from '@/components/calendar/CalendarItemPill.vue'
import EventFormModal from '@/components/calendar/EventFormModal.vue'
import { GROUP_STYLE } from '@/components/calendar/groupColors'
import WeekTasksBar from '@/components/calendar/WeekTasksBar.vue'
import AddButton from '@/components/common/AddButton.vue'
import AppButton from '@/components/common/AppButton.vue'
import AppModal from '@/components/common/AppModal.vue'
import IconButton from '@/components/common/IconButton.vue'
import LoadError from '@/components/common/LoadError.vue'
import PageHeader from '@/components/common/PageHeader.vue'
import SegmentedControl from '@/components/common/SegmentedControl.vue'
import UserAvatar from '@/components/common/UserAvatar.vue'
import { useCelebrate } from '@/composables/useCelebrate'
import { useAction } from '@/composables/useForm'
import { useFormat } from '@/composables/useFormat'
import { useLive } from '@/composables/useLive'
import {
  addDays,
  addMonths,
  buildDayMap,
  monthGrid,
  startOfWeek,
  weekDays,
  weekTaskMap,
  type CalendarGroup,
  type CalendarItem,
} from '@/lib/calendar'
import { calendarService, choreService } from '@/services/modules'
import { useAuthStore } from '@/stores/auth'
import { useHouseholdStore } from '@/stores/household'
import { useUiStore } from '@/stores/ui'
import type { CalendarEvent, Occurrence } from '@/types'

const { t } = useI18n()
const household = useHouseholdStore()
const auth = useAuthStore()
const ui = useUiStore()
const format = useFormat()
const act = useAction()
const celebrate = useCelebrate()

type View = 'month' | 'week' | 'agenda'
const AGENDA_DAYS = 60

// The month grid is too dense for a phone, so phones start in the list view.
const view = ref<View>(matchMedia('(min-width: 768px)').matches ? 'month' : 'agenda')
const anchor = ref(format.today())
const selectedDay = ref(format.today())
const events = ref<CalendarEvent[]>([])
const occurrences = ref<Occurrence[]>([])
const failed = ref(false)
const groups: CalendarGroup[] = ['CHORE', 'WG', 'OTHER']
const visibleGroups = ref(new Set<CalendarGroup>(groups))

const formOpen = ref(false)
const editing = ref<CalendarEvent | null>(null)
const newEventDate = ref<string | null>(null)
const detail = ref<CalendarItem | null>(null)

const viewOptions = computed(() => [
  { value: 'month' as const, label: t('calendar.month') },
  { value: 'week' as const, label: t('calendar.week') },
  { value: 'agenda' as const, label: t('calendar.agenda') },
])

const days = computed(() => {
  if (view.value === 'month') return monthGrid(anchor.value)
  if (view.value === 'week') return weekDays(anchor.value)
  return Array.from({ length: AGENDA_DAYS }, (_, index) => addDays(anchor.value, index))
})
const weekdayHeader = computed(() => weekDays(anchor.value))

const title = computed(() =>
  view.value === 'week'
    ? `${format.date(days.value[0]!)} – ${format.date(days.value[6]!)}`
    : format.monthTitle(anchor.value),
)

const dayMap = computed(() =>
  buildDayMap(events.value, occurrences.value, household.timezone, visibleGroups.value),
)
const weekTasks = computed(() => weekTaskMap(occurrences.value, visibleGroups.value))
const monthWeeks = computed(() =>
  Array.from({ length: days.value.length / 7 }, (_, index) => days.value.slice(index * 7, index * 7 + 7)),
)
const agenda = computed(() =>
  days.value
    .map((day) => ({
      day,
      items: dayMap.value.get(day) ?? [],
      // A week's main tasks are listed once, under the week's first visible day.
      week: format.weekdayIndex(day) === 1 || day === days.value[0] ? (weekTasks.value.get(sundayOf(day)) ?? []) : [],
    }))
    .filter((entry) => entry.items.length > 0 || entry.week.length > 0),
)

function openOccurrence(occurrence: Occurrence) {
  detail.value = {
    key: occurrence.id,
    kind: 'occurrence',
    group: 'CHORE',
    title: occurrence.title,
    time: null,
    done: occurrence.status === 'COMPLETED',
    occurrence,
  }
}
const today = computed(() => format.today())
const sundayOf = (day: string) => addDays(startOfWeek(day), 6)
const anchorMonth = computed(() => anchor.value.slice(0, 7))

let latestRequest = 0

async function load() {
  const range = days.value
  const request = ++latestRequest
  try {
    const data = await calendarService.range(household.id, range[0]!, range.at(-1)!)
    // A slow answer for a range the person already left must not replace the current one.
    if (request !== latestRequest) return
    events.value = data.events
    occurrences.value = data.occurrences
    failed.value = false
  } catch {
    failed.value = true
  }
}
onMounted(load)
watch(days, load)
useLive(['calendar', 'chores'], load)

function move(step: number) {
  if (view.value === 'month') anchor.value = addMonths(anchor.value, step)
  else if (view.value === 'week') anchor.value = addDays(anchor.value, step * 7)
  else anchor.value = addDays(anchor.value, step * AGENDA_DAYS)
  selectedDay.value = anchor.value
}

function goToday() {
  anchor.value = format.today()
  selectedDay.value = anchor.value
}

function toggleGroup(group: CalendarGroup) {
  const next = new Set(visibleGroups.value)
  if (next.has(group)) next.delete(group)
  else next.add(group)
  visibleGroups.value = next
}

function openNew(date?: string) {
  editing.value = null
  newEventDate.value = date ?? selectedDay.value
  formOpen.value = true
}

const detailOpen = computed({
  get: () => detail.value !== null,
  set: (value: boolean) => {
    if (!value) detail.value = null
  },
})

const canManageEvent = (event: CalendarEvent) =>
  household.isAdmin || event.createdBy.id === auth.user?.id

function editEvent(event: CalendarEvent) {
  detail.value = null
  editing.value = event
  formOpen.value = true
}

async function deleteEvent(event: CalendarEvent) {
  const confirmed = await ui.confirm({
    title: t('calendar.deleteConfirm', { title: event.title }),
    confirmLabel: t('common.delete'),
  })
  if (!confirmed) return
  detail.value = null
  const ok = await act(async () => {
    await calendarService.remove(household.id, event.id)
    await load()
  })
  if (ok) ui.toast(t('calendar.deleted'))
}

async function toggleOccurrence(occurrence: Occurrence) {
  detail.value = null
  await act(async () => {
    if (occurrence.status === 'COMPLETED') {
      await choreService.act(household.id, occurrence.id, 'reopen')
    } else {
      celebrate(await choreService.complete(household.id, occurrence.id))
    }
    await load()
  })
}

const eventWhen = (event: CalendarEvent) => {
  const startDate = format.dateLong(format.instantDate(event.startsAt))
  if (event.allDay) {
    if (!event.endsAt || format.instantDate(event.endsAt) === format.instantDate(event.startsAt)) return startDate
    return `${startDate} – ${format.dateLong(format.instantDate(event.endsAt))}`
  }
  const start = `${startDate}, ${format.time(event.startsAt)}`
  if (!event.endsAt) return start
  return format.instantDate(event.endsAt) === format.instantDate(event.startsAt)
    ? `${start} – ${format.time(event.endsAt)}`
    : `${start} – ${format.dateLong(format.instantDate(event.endsAt))}, ${format.time(event.endsAt)}`
}
</script>

<template>
  <div>
    <PageHeader :title="t('calendar.title')">
      <AddButton :label="t('calendar.add')" @click="openNew()" />
    </PageHeader>

    <div class="mb-3 flex flex-wrap items-center justify-between gap-2">
      <div class="flex items-center gap-1">
        <IconButton :label="t('calendar.previous')" @click="move(-1)">
          <ChevronLeft class="size-5" aria-hidden="true" />
        </IconButton>
        <IconButton :label="t('calendar.next')" @click="move(1)">
          <ChevronRight class="size-5" aria-hidden="true" />
        </IconButton>
        <h2 class="ml-1 text-sm font-semibold md:text-base" aria-live="polite">{{ title }}</h2>
      </div>
      <div class="flex flex-wrap items-center gap-2">
        <AppButton variant="ghost" @click="goToday">{{ t('calendar.today') }}</AppButton>
        <SegmentedControl v-model="view" :options="viewOptions" :label="t('calendar.title')" />
      </div>
    </div>

    <div class="mb-4 flex flex-wrap gap-1.5" role="group" :aria-label="t('calendar.filter')">
      <button
        v-for="group in groups"
        :key="group"
        type="button"
        :aria-pressed="visibleGroups.has(group)"
        class="inline-flex min-h-9 items-center gap-1.5 rounded-full border border-line px-3 text-[13px] transition-opacity"
        :class="!visibleGroups.has(group) && 'text-muted line-through opacity-60'"
        @click="toggleGroup(group)"
      >
        <span class="size-2 rounded-full" :class="GROUP_STYLE[group].dot" aria-hidden="true" />
        {{ t(`calendar.categories.${group}`) }}
      </button>
    </div>

    <LoadError v-if="failed" @retry="load" />

    <!-- Month -->
    <template v-else-if="view === 'month'">
      <div class="card overflow-hidden">
        <div class="grid grid-cols-7 border-b border-line">
          <div v-for="day in weekdayHeader" :key="day" class="py-1.5 text-center text-xs text-muted">
            {{ format.weekdayShort(day) }}
          </div>
        </div>
        <div v-for="week in monthWeeks" :key="week[0]">
          <WeekTasksBar :items="weekTasks.get(week[6]!) ?? []" class="border-b border-line" @open="openOccurrence" />
          <div class="grid grid-cols-7">
            <div
              v-for="day in week"
              :key="day"
              class="min-h-13 cursor-pointer border-r border-b border-line p-1 last:border-r-0 md:min-h-24 [&:nth-child(7n)]:border-r-0"
              :class="[
                !day.startsWith(anchorMonth) && 'bg-subtle/50',
                selectedDay === day && 'max-md:bg-accent-soft',
              ]"
              @click="selectedDay = day"
              @dblclick="openNew(day)"
            >
              <button
                type="button"
                class="mx-auto mb-0.5 flex size-6 items-center justify-center rounded-full text-xs"
                :class="[
                  day === today ? 'bg-accent font-semibold text-accent-fg' : '',
                  day !== today && !day.startsWith(anchorMonth) ? 'text-muted' : '',
                ]"
                :aria-label="format.dateLong(day)"
                @click="selectedDay = day"
              >
                {{ format.dayNumber(day) }}
              </button>
              <!-- Labels on desktop, dots on phones. -->
              <div class="hidden space-y-0.5 md:block">
                <CalendarItemPill
                  v-for="item in (dayMap.get(day) ?? []).slice(0, 3)"
                  :key="item.key"
                  :item="item"
                  @open="detail = item"
                />
                <button
                  v-if="(dayMap.get(day)?.length ?? 0) > 3"
                  type="button"
                  class="w-full text-center text-xs text-muted hover:text-fg"
                  @click="selectedDay = day"
                >
                  +{{ dayMap.get(day)!.length - 3 }}
                </button>
              </div>
              <div class="flex flex-wrap justify-center gap-0.5 md:hidden" aria-hidden="true">
                <span
                  v-for="item in (dayMap.get(day) ?? []).slice(0, 4)"
                  :key="item.key"
                  class="size-1.5 rounded-full"
                  :class="GROUP_STYLE[item.group].dot"
                />
              </div>
            </div>
          </div>
        </div>
      </div>
      <section class="mt-4">
        <h3 class="section-title mb-1.5">{{ format.dateLong(selectedDay) }}</h3>
        <div class="card overflow-hidden">
          <p v-if="!dayMap.get(selectedDay)?.length" class="px-4 py-4 text-sm text-muted">
            {{ t('calendar.nothingThatDay') }}
          </p>
          <CalendarDayList v-else :items="dayMap.get(selectedDay)!" @open="detail = $event" />
        </div>
      </section>
    </template>

    <!-- Week -->
    <div v-else-if="view === 'week'" class="space-y-2">
      <WeekTasksBar :items="weekTasks.get(days[6]!) ?? []" class="card overflow-hidden" @open="openOccurrence" />
      <div class="grid grid-cols-1 gap-2 md:grid-cols-7">
      <section
        v-for="day in days"
        :key="day"
        class="card min-w-0 overflow-hidden md:min-h-64"
        :class="day === today && 'border-accent'"
      >
        <h3 class="flex items-baseline gap-1.5 px-4 py-2 text-[13px] md:flex-col md:items-center md:gap-0 md:px-2">
          <span class="text-muted">{{ format.weekdayShort(day) }}</span>
          <span class="font-semibold md:text-lg">{{ format.dayNumber(day) }}</span>
        </h3>
        <div class="space-y-1 px-1.5 pb-2 max-md:hidden">
          <CalendarItemPill v-for="item in dayMap.get(day) ?? []" :key="item.key" :item="item" class="!whitespace-normal" @open="detail = item" />
        </div>
        <div class="md:hidden">
          <CalendarDayList v-if="dayMap.get(day)?.length" :items="dayMap.get(day)!" @open="detail = $event" />
        </div>
      </section>
      </div>
    </div>

    <!-- Agenda -->
    <template v-else>
      <p v-if="agenda.length === 0" class="py-14 text-center text-sm text-muted">{{ t('calendar.agendaEmpty') }}</p>
      <div v-else class="space-y-4">
        <section v-for="entry in agenda" :key="entry.day">
          <h3 class="section-title mb-1.5" :class="entry.day === today && 'text-accent-text'">
            {{ entry.day === today ? `${t('common.today')}, ` : '' }}{{ format.dateLong(entry.day) }}
          </h3>
          <WeekTasksBar v-if="entry.week.length" :items="entry.week" class="card mb-2 overflow-hidden" @open="openOccurrence" />
          <div v-if="entry.items.length" class="card overflow-hidden">
            <CalendarDayList :items="entry.items" @open="detail = $event" />
          </div>
        </section>
      </div>
    </template>

    <p class="mt-4 text-center text-xs text-muted">{{ t('calendar.timeZoneHint', { zone: household.timezone }) }}</p>

    <EventFormModal v-model:open="formOpen" :event="editing" :initial-date="newEventDate" @saved="load" />

    <AppModal v-model:open="detailOpen" :title="detail?.title ?? ''">
      <template v-if="detail?.event">
        <p class="text-sm">{{ eventWhen(detail.event) }}</p>
        <p v-if="detail.event.description" class="mt-2 text-sm whitespace-pre-wrap text-muted">{{ detail.event.description }}</p>
        <div v-if="detail.event.participants.length" class="mt-3 flex flex-wrap gap-1.5">
          <span
            v-for="participant in detail.event.participants"
            :key="participant.id"
            class="inline-flex items-center gap-1.5 rounded-full border border-line py-0.5 pr-2.5 pl-0.5 text-[13px]"
          >
            <UserAvatar :name="participant.displayName" :color="participant.avatarColor" size="sm" />
            {{ participant.displayName }}
          </span>
        </div>
        <p class="mt-3 text-[13px] text-muted">{{ t('calendar.createdBy', { name: detail.event.createdBy.displayName }) }}</p>
        <div v-if="canManageEvent(detail.event)" class="mt-5 flex justify-end gap-2">
          <AppButton variant="danger" @click="deleteEvent(detail!.event!)">{{ t('common.delete') }}</AppButton>
          <AppButton variant="primary" @click="editEvent(detail!.event!)">{{ t('common.edit') }}</AppButton>
        </div>
      </template>
      <template v-else-if="detail?.occurrence">
        <p class="text-sm">
          {{
            detail.occurrence.kind === 'MAIN'
              ? t('chores.dueUntil', { date: format.date(detail.occurrence.dueDate) })
              : format.dateLong(detail.occurrence.dueDate)
          }}
        </p>
        <p class="mt-1 text-sm text-muted">{{ t('calendar.choreDetail') }}</p>
        <p v-if="detail.occurrence.description" class="mt-2 text-sm whitespace-pre-wrap text-muted">{{ detail.occurrence.description }}</p>
        <p class="mt-3 text-sm">
          {{
            detail.occurrence.assignee
              ? t('calendar.assignedTo', { name: detail.occurrence.assignee.displayName })
              : t('calendar.unassigned')
          }}
        </p>
        <div class="mt-5 flex justify-end">
          <AppButton variant="primary" @click="toggleOccurrence(detail!.occurrence!)">
            {{ detail.occurrence.status === 'COMPLETED' ? t('chores.reopen') : t('chores.done') }}
          </AppButton>
        </div>
      </template>
    </AppModal>
  </div>
</template>
