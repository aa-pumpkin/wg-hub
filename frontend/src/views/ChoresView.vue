<script setup lang="ts">
import { CheckCheck, ListChecks, RotateCcw } from 'lucide-vue-next'
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import ChoreFormModal from '@/components/chores/ChoreFormModal.vue'
import ChorePlanPanel from '@/components/chores/ChorePlanPanel.vue'
import OccurrenceRow from '@/components/chores/OccurrenceRow.vue'
import AddButton from '@/components/common/AddButton.vue'
import AppButton from '@/components/common/AppButton.vue'
import AppMenu from '@/components/common/AppMenu.vue'
import AppModal from '@/components/common/AppModal.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import IconButton from '@/components/common/IconButton.vue'
import LoadError from '@/components/common/LoadError.vue'
import PageHeader from '@/components/common/PageHeader.vue'
import SegmentedControl from '@/components/common/SegmentedControl.vue'
import SkeletonList from '@/components/common/SkeletonList.vue'
import UserAvatar from '@/components/common/UserAvatar.vue'
import { useCelebrate } from '@/composables/useCelebrate'
import { useAction } from '@/composables/useForm'
import { useFormat } from '@/composables/useFormat'
import { useLive } from '@/composables/useLive'
import { addDays, startOfWeek } from '@/lib/calendar'
import { choreService } from '@/services/modules'
import { useAuthStore } from '@/stores/auth'
import { useChoreStore } from '@/stores/chores'
import { useHouseholdStore } from '@/stores/household'
import { useUiStore } from '@/stores/ui'
import type { Chore, Occurrence } from '@/types'

const { t } = useI18n()
const store = useChoreStore()
const household = useHouseholdStore()
const auth = useAuthStore()
const ui = useUiStore()
const format = useFormat()
const act = useAction()
const celebrate = useCelebrate()

type Tab = 'open' | 'plan' | 'history'
const tab = ref<Tab>('open')
const scope = ref<'all' | 'mine'>('all')
const showLater = ref(false)
const failed = ref(false)
const loading = ref(true)
const formOpen = ref(false)
const editing = ref<Chore | null>(null)
const reassigning = ref<Occurrence | null>(null)

const tabs = computed(() => [
  { value: 'open' as const, label: t('chores.tabOpen') },
  { value: 'plan' as const, label: t('chores.tabPlan') },
  { value: 'history' as const, label: t('chores.tabHistory') },
])
const scopes = computed(() => [
  { value: 'all' as const, label: t('common.all') },
  { value: 'mine' as const, label: t('common.mine') },
])

async function load() {
  try {
    await store.load(household.id)
    failed.value = false
  } catch {
    failed.value = store.loadedFor !== household.id
  } finally {
    loading.value = false
  }
}
onMounted(load)
useLive(['chores', 'members'], load)

const visible = computed(() => (scope.value === 'mine' ? store.mine(store.open) : store.open))
const buckets = computed(() => {
  const today = format.today()
  const weekEnd = addDays(startOfWeek(today), 6)
  const list = visible.value
  const open = list.filter((item) => item.dueDate >= today)
  return {
    overdue: list.filter((item) => item.dueDate < today),
    main: open.filter((item) => item.kind === 'MAIN' && item.dueDate <= weekEnd),
    extra: open.filter((item) => item.kind === 'EXTRA' && item.dueDate <= weekEnd),
    later: open.filter((item) => item.dueDate > weekEnd),
  }
})
const sections = computed(() =>
  [
    { key: 'overdue', title: t('chores.overdue'), items: buckets.value.overdue, overdue: true },
    { key: 'main', title: t('chores.mainThisWeek'), items: buckets.value.main, overdue: false },
    { key: 'extra', title: t('chores.extraThisWeek'), items: buckets.value.extra, overdue: false },
  ].filter((section) => section.items.length > 0),
)
// With nothing due soon, the later work is the content of the page rather than a footnote.
const laterVisible = computed(() => showLater.value || sections.value.length === 0)
const extras = computed(() => store.series.filter((chore) => chore.kind === 'EXTRA'))

function openNew() {
  editing.value = null
  formOpen.value = true
}

function openEdit(chore: Chore) {
  editing.value = chore
  formOpen.value = true
}

const canManage = (chore: Chore) => household.isAdmin || chore.createdById === auth.user?.id

async function endSeries(chore: Chore) {
  const confirmed = await ui.confirm({
    title: t('chores.endSeriesConfirm', { title: chore.title }),
    message: t('chores.endSeriesText'),
    confirmLabel: chore.recurrence === 'NONE' ? t('chores.deleteTask') : t('chores.endSeries'),
  })
  if (!confirmed) return
  const ok = await act(async () => {
    await choreService.end(household.id, chore.id)
    await load()
  })
  if (ok) ui.toast(t('chores.ended'))
}

const complete = (occurrence: Occurrence) =>
  act(async () => celebrate(await store.complete(household.id, occurrence)))
const skip = (occurrence: Occurrence) => act(() => store.skip(household.id, occurrence))
const reopen = (occurrence: Occurrence) => act(() => store.reopen(household.id, occurrence))

const reassignOpen = computed({
  get: () => reassigning.value !== null,
  set: (value: boolean) => {
    if (!value) reassigning.value = null
  },
})

async function reassignTo(assigneeId: string | null) {
  const occurrence = reassigning.value
  reassigning.value = null
  if (occurrence) await act(() => store.reassign(household.id, occurrence, assigneeId))
}

function menuFor(occurrence: Occurrence) {
  return [
    { label: t('chores.reassign'), action: () => (reassigning.value = occurrence) },
    { label: t('chores.skip'), action: () => skip(occurrence) },
  ]
}
</script>

<template>
  <div>
    <PageHeader :title="t('chores.title')">
      <AddButton :label="t('chores.add')" @click="openNew" />
    </PageHeader>

    <div class="mb-4 flex flex-wrap items-center justify-between gap-2">
      <SegmentedControl v-model="tab" :options="tabs" :label="t('chores.title')" />
      <SegmentedControl v-if="tab === 'open'" v-model="scope" :options="scopes" :label="t('common.mine')" />
    </div>

    <LoadError v-if="failed" @retry="load" />
    <SkeletonList v-else-if="loading" />

    <!-- Upcoming -->
    <template v-else-if="tab === 'open'">
      <EmptyState
        v-if="visible.length === 0"
        :icon="ListChecks"
        :title="scope === 'mine' ? t('chores.emptyMine') : t('chores.emptyTitle')"
        :text="scope === 'mine' ? undefined : t('chores.emptyText')"
      />
      <div v-else class="space-y-5">
        <section v-for="section in sections" :key="section.key">
          <h2 class="section-title mb-1.5" :class="section.overdue && 'text-danger'">{{ section.title }}</h2>
          <ul class="card divide-y divide-line">
            <li v-for="occurrence in section.items" :key="occurrence.id">
              <OccurrenceRow :occurrence="occurrence" show-date :overdue="section.overdue" @toggle="complete(occurrence)">
                <AppMenu :items="menuFor(occurrence)" :label="t('common.actions')" />
              </OccurrenceRow>
            </li>
          </ul>
        </section>
        <section v-if="buckets.later.length > 0">
          <h2 v-if="sections.length === 0" class="section-title mb-1.5">{{ t('chores.later') }}</h2>
          <button
            v-else
            type="button"
            class="section-title tap flex items-center hover:text-fg"
            :aria-expanded="showLater"
            @click="showLater = !showLater"
          >
            {{ showLater ? t('chores.hideLater') : t('chores.showLater') }} ({{ buckets.later.length }})
          </button>
          <ul v-if="laterVisible" class="card divide-y divide-line">
            <li v-for="occurrence in buckets.later" :key="occurrence.id">
              <OccurrenceRow :occurrence="occurrence" show-date @toggle="complete(occurrence)">
                <AppMenu :items="menuFor(occurrence)" :label="t('common.actions')" />
              </OccurrenceRow>
            </li>
          </ul>
        </section>
      </div>
    </template>

    <!-- Plan: the weekly circle, then extra tasks -->
    <template v-else-if="tab === 'plan'">
      <ChorePlanPanel @edit="openEdit" @end="endSeries" />
      <section class="mt-6">
        <h2 class="section-title mb-1.5">{{ t('chores.extraTitle') }}</h2>
        <p v-if="extras.length === 0" class="card px-4 py-4 text-sm text-muted">{{ t('chores.noExtra') }}</p>
        <ul v-else class="card divide-y divide-line">
          <li v-for="chore in extras" :key="chore.id" class="list-row">
            <div class="min-w-0 flex-1">
              <p class="truncate text-sm font-medium">{{ chore.title }}</p>
              <p class="truncate text-[13px] text-muted">
                {{ t(`chores.recurrences.${chore.recurrence}`) }} · {{ t('points.short', { points: chore.points }) }}
              </p>
            </div>
            <div class="flex -space-x-1.5" :title="t('chores.rotationOrder', { names: chore.rotation.map((m) => m.displayName).join(' → ') })">
              <UserAvatar
                v-for="member in chore.rotation"
                :key="member.id"
                :name="member.displayName"
                :color="member.avatarColor"
                size="sm"
                class="ring-2 ring-surface"
              />
            </div>
            <AppMenu
              v-if="canManage(chore)"
              :label="t('common.actions')"
              :items="[
                { label: t('common.edit'), action: () => openEdit(chore) },
                { label: chore.recurrence === 'NONE' ? t('chores.deleteTask') : t('chores.endSeries'), action: () => endSeries(chore), danger: true },
              ]"
            />
          </li>
        </ul>
      </section>
    </template>

    <!-- History -->
    <template v-else>
      <EmptyState v-if="store.history.length === 0" :icon="CheckCheck" :title="t('chores.emptyHistory')" />
      <ul v-else class="card divide-y divide-line">
        <li v-for="occurrence in store.history" :key="occurrence.id" class="list-row">
          <div class="min-w-0 flex-1">
            <p class="truncate text-sm font-medium">{{ occurrence.title }}</p>
            <p class="truncate text-[13px] text-muted">
              {{
                t('chores.doneBy', {
                  name: occurrence.completedBy?.displayName ?? '–',
                  date: occurrence.completedAt ? format.dateTime(occurrence.completedAt) : format.date(occurrence.dueDate),
                })
              }}
            </p>
          </div>
          <IconButton :label="t('chores.reopen')" @click="reopen(occurrence)">
            <RotateCcw class="size-4" aria-hidden="true" />
          </IconButton>
        </li>
      </ul>
    </template>

    <ChoreFormModal v-model:open="formOpen" :chore="editing" @saved="load" />

    <AppModal v-model:open="reassignOpen" :title="t('chores.reassignTitle')">
      <div class="space-y-1.5">
        <AppButton
          v-for="member in household.members.filter((person) => !person.frozen)"
          :key="member.id"
          block
          class="!justify-start"
          @click="reassignTo(member.id)"
        >
          <UserAvatar :name="member.displayName" :color="member.avatarColor" size="sm" />
          {{ member.displayName }}
        </AppButton>
        <AppButton block variant="ghost" @click="reassignTo(null)">{{ t('chores.anyone') }}</AppButton>
      </div>
    </AppModal>
  </div>
</template>
