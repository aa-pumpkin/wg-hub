<script setup lang="ts">
import { computed, reactive, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import AppButton from '@/components/common/AppButton.vue'
import AppInput from '@/components/common/AppInput.vue'
import AppModal from '@/components/common/AppModal.vue'
import AppSelect from '@/components/common/AppSelect.vue'
import AppTextarea from '@/components/common/AppTextarea.vue'
import MoreOptions from '@/components/common/MoreOptions.vue'
import MemberPicker from '@/components/common/MemberPicker.vue'
import SegmentedControl from '@/components/common/SegmentedControl.vue'
import { useForm } from '@/composables/useForm'
import { useFormat } from '@/composables/useFormat'
import { choreService } from '@/services/modules'
import { useHouseholdStore } from '@/stores/household'
import { useUiStore } from '@/stores/ui'
import { RECURRENCES, type Chore, type ChoreInput, type ChoreKind } from '@/types'

const DEFAULT_POINTS = { MAIN: 10, EXTRA: 20 } as const

const props = defineProps<{ chore?: Chore | null }>()
const emit = defineEmits<{ saved: [] }>()
const open = defineModel<boolean>('open', { required: true })

const { t } = useI18n()
const household = useHouseholdStore()
const ui = useUiStore()
const format = useFormat()
const { run, submitting, errors } = useForm()

const blank = () => ({
  title: '',
  description: '',
  recurrence: 'MONTHLY' as ChoreInput['recurrence'],
  startDate: format.today(),
  // By default extra tasks go around everyone, in the order of the circle.
  rotation: household.members.filter((member) => !member.frozen).map((member) => member.id),
  kind: (household.isAdmin ? 'MAIN' : 'EXTRA') as ChoreKind,
  points: String(household.isAdmin ? DEFAULT_POINTS.MAIN : 0),
})
const form = reactive(blank())
const isMain = computed(() => form.kind === 'MAIN')
const moreHasError = computed(() =>
  Boolean(errors.value.startDate || errors.value.rotation || errors.value.points || errors.value.description),
)
const kindOptions = computed(() => [
  { value: 'MAIN' as const, label: t('chores.kindMain') },
  { value: 'EXTRA' as const, label: t('chores.kindExtra') },
])

watch(open, (isOpen) => {
  if (!isOpen) return
  errors.value = {}
  const chore = props.chore
  Object.assign(
    form,
    chore
      ? {
          title: chore.title,
          description: chore.description ?? '',
          recurrence: chore.recurrence,
          startDate: chore.startDate,
          // Only current members can be selected.
          rotation: chore.rotation
            .map((member) => member.id)
            .filter((id) => household.members.some((member) => member.id === id)),
          kind: chore.kind,
          points: String(chore.points),
        }
      : blank(),
  )
})

// A new task starts with the usual points for its kind, until the admin changes them.
watch(
  () => form.kind,
  (kind) => {
    if (!props.chore && household.isAdmin) form.points = String(DEFAULT_POINTS[kind])
  },
)

const recurrenceOptions = computed(() =>
  RECURRENCES.map((value) => ({ value, label: t(`chores.recurrences.${value}`) })),
)

async function submit() {
  const saved = await run(async () => {
    const { points, kind, ...fields } = form
    const body: ChoreInput = {
      ...fields,
      ...(household.isAdmin ? { points: Number(points) } : {}),
      ...(props.chore ? {} : { kind }),
    }
    if (props.chore) await choreService.update(household.id, props.chore.id, body)
    else await choreService.create(household.id, body)
  })
  if (saved) {
    open.value = false
    ui.toast(t('chores.saved'))
    emit('saved')
  }
}
</script>

<template>
  <AppModal v-model:open="open" :title="chore ? t('chores.editChore') : t('chores.newChore')">
    <form class="space-y-4" novalidate @submit.prevent="submit">
      <AppInput
        v-model="form.title"
        :label="t('chores.name')"
        :placeholder="t('chores.namePlaceholder')"
        :error="errors.title"
        autofocus
      />
      <div v-if="household.isAdmin && !chore">
        <SegmentedControl v-model="form.kind" :options="kindOptions" :label="t('chores.kindLabel')" />
        <p class="mt-1.5 text-[13px] text-muted">{{ isMain ? t('chores.mainHint') : t('chores.extraHint') }}</p>
      </div>
      <AppSelect v-if="!isMain" v-model="form.recurrence" :label="t('chores.recurrence')" :options="recurrenceOptions" />
      <MoreOptions :open="moreHasError">
        <AppInput
          v-if="!isMain"
          v-model="form.startDate"
          type="date"
          :label="form.recurrence === 'NONE' ? t('chores.startDate') : t('chores.startDateSeries')"
          :error="errors.startDate"
        />
        <MemberPicker
          v-if="!isMain"
          v-model="form.rotation"
          :label="t('chores.responsible')"
          :hint="t('chores.rotationHint')"
          :error="errors.rotation"
          show-order
        />
        <AppInput
          v-if="household.isAdmin"
          v-model="form.points"
          :label="t('chores.points')"
          :error="errors.points"
          inputmode="numeric"
        />
        <p v-else class="text-[13px] text-muted">{{ t('chores.memberPointsHint') }}</p>
        <AppTextarea
          v-model="form.description"
          :label="t('chores.description')"
          :error="errors.description"
          optional
          :rows="2"
        />
      </MoreOptions>
      <div class="flex justify-end gap-2 pt-1">
        <AppButton @click="open = false">{{ t('common.cancel') }}</AppButton>
        <AppButton variant="primary" type="submit" :loading="submitting">{{ t('common.save') }}</AppButton>
      </div>
    </form>
  </AppModal>
</template>
