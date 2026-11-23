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
import { useForm } from '@/composables/useForm'
import { useFormat } from '@/composables/useFormat'
import { instantToWallClock } from '@/lib/calendar'
import { calendarService } from '@/services/modules'
import { useHouseholdStore } from '@/stores/household'
import { useUiStore } from '@/stores/ui'
import { EVENT_CATEGORIES, type CalendarEvent, type EventCategory } from '@/types'

const props = defineProps<{ event?: CalendarEvent | null; initialDate?: string | null }>()
const emit = defineEmits<{ saved: [] }>()
const open = defineModel<boolean>('open', { required: true })

const { t } = useI18n()
const household = useHouseholdStore()
const ui = useUiStore()
const format = useFormat()
const { run, submitting, errors } = useForm()

const form = reactive({
  title: '',
  description: '',
  category: 'WG' as EventCategory,
  allDay: false,
  startDate: '',
  startTime: '19:00',
  endDate: '',
  endTime: '',
  participantIds: [] as string[],
})

watch(open, (isOpen) => {
  if (!isOpen) return
  errors.value = {}
  const event = props.event
  const zone = household.timezone
  const start = event ? instantToWallClock(event.startsAt, zone) : null
  const end = event?.endsAt ? instantToWallClock(event.endsAt, zone) : null
  form.title = event?.title ?? ''
  form.description = event?.description ?? ''
  form.category = event?.category ?? 'WG'
  form.allDay = event?.allDay ?? false
  form.startDate = start?.slice(0, 10) ?? props.initialDate ?? format.today()
  form.startTime = start?.slice(11) ?? '19:00'
  form.endDate = end?.slice(0, 10) ?? ''
  form.endTime = end?.slice(11) ?? ''
  form.participantIds = (event?.participants ?? [])
    .map((participant) => participant.id)
    .filter((id) => household.members.some((member) => member.id === id))
})

const moreHasError = computed(() => Boolean(errors.value.endsAt || errors.value.description))
const categoryOptions = computed(() =>
  EVENT_CATEGORIES.map((value) => ({ value, label: t(`calendar.categories.${value}`) })),
)

/** Wall-clock values for the API; the backend applies the household's time zone. */
function endValue(): string | null {
  if (form.allDay) return form.endDate ? `${form.endDate}T00:00` : null
  if (!form.endTime) return null
  return `${form.endDate || form.startDate}T${form.endTime}`
}

async function submit() {
  const saved = await run(async () => {
    const body = {
      title: form.title,
      description: form.description,
      category: form.category,
      allDay: form.allDay,
      startsAt: `${form.startDate}T${form.allDay ? '00:00' : form.startTime}`,
      endsAt: endValue(),
      participantIds: form.participantIds,
    }
    if (props.event) await calendarService.update(household.id, props.event.id, body)
    else await calendarService.create(household.id, body)
  })
  if (saved) {
    open.value = false
    ui.toast(t('calendar.saved'))
    emit('saved')
  }
}
</script>

<template>
  <AppModal v-model:open="open" :title="event ? t('calendar.editEvent') : t('calendar.newEvent')">
    <form class="space-y-4" novalidate @submit.prevent="submit">
      <AppInput
        v-model="form.title"
        :label="t('calendar.name')"
        :placeholder="t('calendar.namePlaceholder')"
        :error="errors.title"
        autofocus
      />
      <label class="flex min-h-11 items-center gap-2.5 text-sm">
        <input v-model="form.allDay" type="checkbox" class="size-4 accent-[var(--accent)]" />
        {{ t('calendar.allDay') }}
      </label>
      <div class="grid grid-cols-[1fr_8rem] gap-3">
        <AppInput v-model="form.startDate" type="date" :label="t('calendar.startDate')" :error="errors.startsAt" :class="form.allDay && 'col-span-2'" />
        <AppInput v-if="!form.allDay" v-model="form.startTime" type="time" :label="t('calendar.time')" />
      </div>
      <MoreOptions :open="moreHasError">
        <div class="grid grid-cols-[1fr_8rem] gap-3">
          <AppInput v-model="form.endDate" type="date" :label="form.allDay ? t('calendar.endDate') : t('calendar.end')" :error="errors.endsAt" optional :class="form.allDay && 'col-span-2'" />
          <AppInput v-if="!form.allDay" v-model="form.endTime" type="time" :label="t('calendar.time')" />
        </div>
        <AppSelect v-model="form.category" :label="t('calendar.category')" :options="categoryOptions" />
        <MemberPicker v-model="form.participantIds" :label="t('calendar.participants')" />
        <AppTextarea
          v-model="form.description"
          :label="t('calendar.description')"
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
