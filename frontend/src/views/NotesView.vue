<script setup lang="ts">
import { Archive, Pin } from 'lucide-vue-next'
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import AddButton from '@/components/common/AddButton.vue'
import AppMenu, { type MenuItem } from '@/components/common/AppMenu.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import LoadError from '@/components/common/LoadError.vue'
import PageHeader from '@/components/common/PageHeader.vue'
import SegmentedControl from '@/components/common/SegmentedControl.vue'
import UserAvatar from '@/components/common/UserAvatar.vue'
import NoteFormModal from '@/components/notes/NoteFormModal.vue'
import { useAction } from '@/composables/useForm'
import { useFormat } from '@/composables/useFormat'
import { useLive } from '@/composables/useLive'
import { noteService } from '@/services/modules'
import { useAuthStore } from '@/stores/auth'
import { useHouseholdStore } from '@/stores/household'
import { useNoteStore } from '@/stores/notes'
import { useUiStore } from '@/stores/ui'
import type { Note } from '@/types'

const { t } = useI18n()
const store = useNoteStore()
const household = useHouseholdStore()
const auth = useAuthStore()
const ui = useUiStore()
const format = useFormat()
const act = useAction()

const shelf = ref<'active' | 'archive'>('active')
const loading = ref(true)
const failed = ref(false)
const formOpen = ref(false)
const editing = ref<Note | null>(null)

const shelves = computed(() => [
  { value: 'active' as const, label: t('notes.showActive') },
  { value: 'archive' as const, label: t('notes.showArchive') },
])

async function load() {
  try {
    await store.load(household.id, shelf.value === 'archive')
    failed.value = false
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
}
onMounted(load)
watch(shelf, () => {
  loading.value = true
  void load()
})
useLive(['notes'], load)

function openNew() {
  editing.value = null
  formOpen.value = true
}

const change = (action: () => Promise<unknown>) =>
  act(async () => {
    await action()
    await load()
  })

async function remove(note: Note) {
  const confirmed = await ui.confirm({
    title: t('notes.deleteConfirm'),
    confirmLabel: t('common.delete'),
  })
  if (confirmed) await change(() => noteService.remove(household.id, note.id))
}

function menuFor(note: Note): MenuItem[] {
  const isAuthor = note.author.id === auth.user?.id
  const canModerate = isAuthor || household.isAdmin
  const items: MenuItem[] = []
  if (isAuthor && !note.archivedAt) {
    items.push({
      label: t('common.edit'),
      action: () => {
        editing.value = note
        formOpen.value = true
      },
    })
  }
  if (!note.archivedAt) {
    items.push({
      label: note.pinned ? t('notes.unpin') : t('notes.pin'),
      action: () => change(() => noteService.pin(household.id, note.id, !note.pinned)),
    })
  }
  if (canModerate) {
    items.push({
      label: note.archivedAt ? t('notes.unarchive') : t('notes.archive'),
      action: () => change(() => noteService.archive(household.id, note.id, !note.archivedAt)),
    })
    items.push({ label: t('common.delete'), action: () => remove(note), danger: true })
  }
  return items
}
</script>

<template>
  <div>
    <PageHeader :title="t('notes.title')">
      <AddButton :label="t('notes.add')" @click="openNew" />
    </PageHeader>
    <div class="mb-4">
      <SegmentedControl v-model="shelf" :options="shelves" :label="t('notes.title')" />
    </div>

    <LoadError v-if="failed" @retry="load" />
    <div v-else-if="loading" class="grid grid-cols-1 gap-3 md:grid-cols-2 lg:grid-cols-3" aria-busy="true">
      <div v-for="n in 3" :key="n" class="skeleton h-32" />
    </div>
    <EmptyState
      v-else-if="store.notes.length === 0"
      :icon="shelf === 'archive' ? Archive : Pin"
      :title="shelf === 'archive' ? t('notes.emptyArchive') : t('notes.emptyTitle')"
      :text="shelf === 'archive' ? undefined : t('notes.emptyText')"
    />
    <div v-else class="columns-1 gap-3 md:columns-2 lg:columns-3">
      <article
        v-for="note in store.notes"
        :key="note.id"
        class="card mb-3 break-inside-avoid p-4 pb-2"
        :style="{ background: `var(--note-${note.color.toLowerCase()})` }"
      >
        <!-- Rendered as text, never as HTML, so a note cannot inject markup. -->
        <p class="text-sm wrap-anywhere whitespace-pre-wrap">
          <Pin v-if="note.pinned" class="mr-1 inline size-3.5 align-[-2px]" :aria-label="t('notes.pinned')" />{{ note.content }}
        </p>
        <div class="mt-2 flex items-center gap-2">
          <UserAvatar :name="note.author.displayName" :color="note.author.avatarColor" size="sm" />
          <p class="min-w-0 flex-1 truncate text-xs text-muted">
            {{ note.author.displayName }}, {{ format.dateTime(note.createdAt) }}
          </p>
          <AppMenu v-if="menuFor(note).length" :items="menuFor(note)" :label="t('common.actions')" />
        </div>
      </article>
    </div>

    <NoteFormModal v-model:open="formOpen" :note="editing" @saved="load" />
  </div>
</template>
