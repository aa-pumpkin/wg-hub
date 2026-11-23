<script setup lang="ts">
import { Check } from 'lucide-vue-next'
import { reactive, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import AppButton from '@/components/common/AppButton.vue'
import AppModal from '@/components/common/AppModal.vue'
import AppTextarea from '@/components/common/AppTextarea.vue'
import { useForm } from '@/composables/useForm'
import { noteService } from '@/services/modules'
import { useHouseholdStore } from '@/stores/household'
import { NOTE_COLORS, type Note, type NoteColor } from '@/types'

const props = defineProps<{ note?: Note | null }>()
const emit = defineEmits<{ saved: [] }>()
const open = defineModel<boolean>('open', { required: true })

const { t } = useI18n()
const household = useHouseholdStore()
const { run, submitting, errors } = useForm()

const form = reactive({ content: '', color: 'DEFAULT' as NoteColor, pinned: false })

watch(open, (isOpen) => {
  if (!isOpen) return
  errors.value = {}
  form.content = props.note?.content ?? ''
  form.color = props.note?.color ?? 'DEFAULT'
  form.pinned = false
})

async function submit() {
  const saved = await run(async () => {
    if (props.note) {
      await noteService.update(household.id, props.note.id, {
        content: form.content,
        color: form.color,
      })
    } else {
      await noteService.create(household.id, { ...form })
    }
  })
  if (saved) {
    open.value = false
    emit('saved')
  }
}
</script>

<template>
  <AppModal v-model:open="open" :title="note ? t('notes.editNote') : t('notes.newNote')">
    <form class="space-y-4" novalidate @submit.prevent="submit">
      <AppTextarea
        v-model="form.content"
        :label="t('notes.content')"
        :placeholder="t('notes.contentPlaceholder')"
        :error="errors.content"
        :rows="4"
        autofocus
      />
      <fieldset>
        <legend class="mb-1.5 text-sm font-medium">{{ t('notes.color') }}</legend>
        <div class="flex flex-wrap gap-2">
          <button
            v-for="color in NOTE_COLORS"
            :key="color"
            type="button"
            :aria-pressed="form.color === color"
            :aria-label="t(`notes.colors.${color}`)"
            :title="t(`notes.colors.${color}`)"
            class="inline-flex size-10 items-center justify-center rounded-full border border-line"
            :class="form.color === color && 'ring-2 ring-accent ring-offset-2 ring-offset-surface'"
            :style="{ background: `var(--note-${color.toLowerCase()})` }"
            @click="form.color = color"
          >
            <Check v-if="form.color === color" class="size-4" aria-hidden="true" />
          </button>
        </div>
      </fieldset>
      <label v-if="!note" class="flex min-h-11 items-center gap-2.5 text-sm">
        <input v-model="form.pinned" type="checkbox" class="size-4 accent-[var(--accent)]" />
        {{ t('notes.pinNew') }}
      </label>
      <div class="flex justify-end gap-2 pt-1">
        <AppButton @click="open = false">{{ t('common.cancel') }}</AppButton>
        <AppButton variant="primary" type="submit" :loading="submitting">{{ t('common.save') }}</AppButton>
      </div>
    </form>
  </AppModal>
</template>
