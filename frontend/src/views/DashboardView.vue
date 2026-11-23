<script setup lang="ts">
import {
  ArrowRight,
  CalendarPlus,
  ListPlus,
  PartyPopper,
  Pin,
  ShoppingBasket,
  StickyNote,
} from 'lucide-vue-next'
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import EventFormModal from '@/components/calendar/EventFormModal.vue'
import ChoreFormModal from '@/components/chores/ChoreFormModal.vue'
import CheckCircle from '@/components/common/CheckCircle.vue'
import OccurrenceRow from '@/components/chores/OccurrenceRow.vue'
import LoadError from '@/components/common/LoadError.vue'
import PageHeader from '@/components/common/PageHeader.vue'
import UserAvatar from '@/components/common/UserAvatar.vue'
import DishwasherStrip from '@/components/dashboard/DishwasherStrip.vue'
import DashboardCard from '@/components/dashboard/DashboardCard.vue'
import LevelCard from '@/components/dashboard/LevelCard.vue'
import NoteFormModal from '@/components/notes/NoteFormModal.vue'
import BuyItemDialog from '@/components/shopping/BuyItemDialog.vue'
import ShoppingFormModal from '@/components/shopping/ShoppingFormModal.vue'
import { useCelebrate } from '@/composables/useCelebrate'
import { useAction } from '@/composables/useForm'
import { useFormat } from '@/composables/useFormat'
import { useLive } from '@/composables/useLive'
import { choreService, shoppingService } from '@/services/modules'
import { useDashboardStore } from '@/stores/dashboard'
import { useAuthStore } from '@/stores/auth'
import { useHouseholdStore } from '@/stores/household'
import type { Occurrence, ShoppingItem } from '@/types'

const { t } = useI18n()
const route = useRoute()
const auth = useAuthStore()
const household = useHouseholdStore()
const store = useDashboardStore()
const format = useFormat()
const act = useAction()
const celebrate = useCelebrate()

const failed = ref(false)
const modal = ref<'chore' | 'item' | 'note' | 'event' | null>(null)

const modalOpen = (name: NonNullable<typeof modal.value>) =>
  computed({
    get: () => modal.value === name,
    set: (value: boolean) => {
      modal.value = value ? name : null
    },
  })
const choreOpen = modalOpen('chore')
const itemOpen = modalOpen('item')
const noteOpen = modalOpen('note')
const eventOpen = modalOpen('event')

async function load() {
  try {
    await store.load(household.id)
    failed.value = false
  } catch {
    if (!store.data) failed.value = true
  }
}
onMounted(load)
useLive(['chores', 'shopping', 'calendar', 'notes', 'expenses'], load)

const linkTo = (name: string) => ({ name, params: { householdId: route.params.householdId } })

/** Next up: events and chores merged and sorted by day. */
const upcoming = computed(() => {
  const data = store.data
  if (!data) return []
  return [
    ...data.events.map((event) => ({
      key: `e-${event.id}`,
      date: format.instantDate(event.startsAt),
      time: event.allDay ? '' : format.time(event.startsAt),
      title: event.title,
      dot: event.category === 'OTHER' ? 'bg-[#d9a23b]' : event.category === 'CHORE' ? 'bg-accent' : 'bg-[#8b7fd6]',
      assignee: null as Occurrence['assignee'],
    })),
    ...data.upcomingTasks.map((task) => ({
      key: `t-${task.id}`,
      date: task.dueDate,
      time: '',
      title: task.title,
      dot: 'bg-accent',
      assignee: task.assignee,
    })),
  ]
    .sort((a, b) => (a.date + a.time).localeCompare(b.date + b.time))
    .slice(0, 5)
})

const myBalance = computed(() => {
  const cents = store.data?.myBalanceCents ?? 0
  if (cents > 0) return { text: t('dashboard.balancePositive', { amount: format.money(cents) }), tone: 'text-accent-text' }
  if (cents < 0) return { text: t('dashboard.balanceNegative', { amount: format.money(-cents) }), tone: 'text-danger' }
  return { text: t('dashboard.balanceEven'), tone: 'text-muted' }
})

async function toggleTask(task: Occurrence) {
  const reopening = task.status === 'COMPLETED'
  store.setTaskStatus(task.id, reopening ? 'PENDING' : 'COMPLETED')
  const ok = await act(async () => {
    if (reopening) await choreService.act(household.id, task.id, 'reopen')
    else celebrate(await choreService.complete(household.id, task.id))
    await load()
  })
  if (!ok) await load()
}

// Same number of rows in every card keeps the two columns level; the header links to the rest.
const MAX_ROWS = 5
const visibleTasks = computed(() =>
  [...(store.data?.tasks ?? [])]
    .sort((a, b) => Number(a.status === 'COMPLETED') - Number(b.status === 'COMPLETED'))
    .slice(0, MAX_ROWS),
)

const buying = ref<ShoppingItem | null>(null)

async function buyItem(item: ShoppingItem) {
  // Your own personal item involves nobody else, so there is nothing to ask.
  if (item.personal && item.createdBy.id === auth.user?.id) {
    store.removeShoppingItem(item.id)
    const ok = await act(() => shoppingService.setStatus(household.id, item.id, 'BOUGHT'))
    if (!ok) await load()
  } else {
    buying.value = item
  }
}

async function bought() {
  buying.value = null
  await load()
}

const quickActions = computed(() => [
  { label: t('dashboard.quickTask'), icon: ListPlus, open: () => (modal.value = 'chore') },
  { label: t('dashboard.quickItem'), icon: ShoppingBasket, open: () => (modal.value = 'item') },
  { label: t('dashboard.quickNote'), icon: StickyNote, open: () => (modal.value = 'note') },
  { label: t('dashboard.quickEvent'), icon: CalendarPlus, open: () => (modal.value = 'event') },
])
</script>

<template>
  <div>
    <PageHeader
      :title="t('dashboard.greeting', { name: auth.user?.displayName })"
      :subtitle="store.data ? format.dateLong(store.data.today) : undefined"
    />

    <div class="mb-4 grid grid-cols-4 gap-2" role="group" :aria-label="t('dashboard.quickActions')">
      <button
        v-for="action in quickActions"
        :key="action.label"
        type="button"
        class="card flex min-h-16 flex-col items-center justify-center gap-1 px-1 text-[13px] transition-colors hover:bg-subtle md:min-h-11 md:flex-row md:gap-2"
        @click="action.open"
      >
        <component :is="action.icon" class="size-5 text-accent-text md:size-4" aria-hidden="true" />
        {{ action.label }}
      </button>
    </div>

    <LoadError v-if="failed" @retry="load" />
    <div v-else-if="!store.data" class="grid grid-cols-1 gap-4 md:grid-cols-2" aria-busy="true">
      <div v-for="n in 4" :key="n" class="skeleton h-52" />
    </div>
    <div v-else class="grid grid-cols-1 gap-4 md:grid-cols-2">
      <RouterLink :to="linkTo('points')" class="block transition-opacity hover:opacity-90">
        <LevelCard v-if="store.data.myProgress" :progress="store.data.myProgress" />
      </RouterLink>
      <RouterLink :to="linkTo('expenses')" class="card flex min-h-13 items-center justify-between px-4 py-3 transition-colors hover:bg-subtle">
        <span class="text-sm font-semibold">{{ t('dashboard.balance') }}</span>
        <span class="flex items-center gap-2 text-sm" :class="myBalance.tone">
          {{ myBalance.text }}
          <ArrowRight class="size-4 text-muted" aria-hidden="true" />
        </span>
      </RouterLink>

      <DishwasherStrip v-if="household.current?.dishwasherTarget != null" class="md:col-span-2" />

      <DashboardCard :title="t('dashboard.tasks')" :to="linkTo('chores')">
        <div v-if="store.data.tasks.length === 0" class="flex items-center gap-2 px-4 py-6 text-sm text-muted">
          <PartyPopper class="size-4" aria-hidden="true" />
          {{ t('dashboard.noTasks') }}
        </div>
        <ul v-else class="divide-y divide-line">
          <li v-for="task in visibleTasks" :key="task.id">
            <OccurrenceRow
              :occurrence="task"
              :show-date="task.dueDate !== store.data.today"
              :overdue="task.dueDate < store.data.today && task.status === 'PENDING'"
              @toggle="toggleTask(task)"
            />
          </li>
        </ul>
      </DashboardCard>

      <DashboardCard :title="t('dashboard.shopping')" :to="linkTo('shopping')">
        <template #meta>
          {{ t('dashboard.shoppingCount', store.data.shopping.pendingCount) }}
        </template>
        <p v-if="store.data.shopping.items.length === 0" class="px-4 pb-4 text-sm text-muted">
          {{ t('dashboard.shoppingEmpty') }}
        </p>
        <ul v-else class="divide-y divide-line">
          <li
            v-for="item in store.data.shopping.items.slice(0, 5)"
            :key="item.id"
            class="list-row"
          >
            <CheckCircle
              :checked="false"
              :label="t('shopping.markBought', { name: item.name })"
              @toggle="buyItem(item)"
            />
            <span class="min-w-0 flex-1 truncate text-sm">{{ item.name }}</span>
            <span v-if="item.quantity != null || item.unit" class="text-[13px] text-muted">
              {{ format.amount(item.quantity, item.unit) }}
            </span>
          </li>
        </ul>
      </DashboardCard>

      <DashboardCard :title="t('dashboard.upcoming')" :to="linkTo('calendar')">
        <p v-if="upcoming.length === 0" class="px-4 py-6 text-sm text-muted">{{ t('dashboard.noUpcoming') }}</p>
        <ul v-else class="divide-y divide-line">
          <li v-for="entry in upcoming" :key="entry.key" class="list-row">
            <span class="size-2 shrink-0 rounded-full" :class="entry.dot" aria-hidden="true" />
            <span class="min-w-0 flex-1 truncate text-sm">{{ entry.title }}</span>
            <span class="text-[13px] whitespace-nowrap text-muted">
              {{ format.relativeDate(entry.date) }}<template v-if="entry.time">, {{ entry.time }}</template>
            </span>
            <UserAvatar v-if="entry.assignee" :name="entry.assignee.displayName" :color="entry.assignee.avatarColor" size="sm" />
            <span v-else class="size-6 shrink-0" aria-hidden="true" />
          </li>
        </ul>
      </DashboardCard>

      <DashboardCard :title="t('dashboard.notes')" :to="linkTo('notes')">
        <p v-if="store.data.notes.length === 0" class="px-4 py-6 text-sm text-muted">{{ t('dashboard.noNotes') }}</p>
        <ul v-else class="space-y-2 px-4 pb-4">
          <li
            v-for="note in store.data.notes.slice(0, 3)"
            :key="note.id"
            class="rounded-[10px] border border-line px-3 py-2"
            :style="{ background: `var(--note-${note.color.toLowerCase()})` }"
          >
            <p class="line-clamp-2 text-sm whitespace-pre-wrap">
              <Pin v-if="note.pinned" class="mr-1 inline size-3.5 align-[-2px]" :aria-label="t('notes.pinned')" />{{ note.content }}
            </p>
            <p class="mt-1 text-xs text-muted">{{ note.author.displayName }}, {{ format.dateTime(note.createdAt) }}</p>
          </li>
        </ul>
      </DashboardCard>
    </div>

    <ChoreFormModal v-model:open="choreOpen" @saved="load" />
    <ShoppingFormModal v-model:open="itemOpen" @saved="load" />
    <BuyItemDialog :item="buying" @close="buying = null" @bought="bought" />
    <NoteFormModal v-model:open="noteOpen" @saved="load" />
    <EventFormModal v-model:open="eventOpen" @saved="load" />
  </div>
</template>
