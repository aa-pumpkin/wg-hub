<script setup lang="ts">
import { computed, reactive, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import AppButton from '@/components/common/AppButton.vue'
import AppInput from '@/components/common/AppInput.vue'
import AppModal from '@/components/common/AppModal.vue'
import AppSelect from '@/components/common/AppSelect.vue'
import MemberPicker from '@/components/common/MemberPicker.vue'
import SegmentedControl from '@/components/common/SegmentedControl.vue'
import { useForm } from '@/composables/useForm'
import { useFormat } from '@/composables/useFormat'
import { centsToInput, parseEurosToCents } from '@/lib/money'
import { ApiError } from '@/services/api'
import { expenseService } from '@/services/modules'
import { useAuthStore } from '@/stores/auth'
import { useHouseholdStore } from '@/stores/household'
import { useUiStore } from '@/stores/ui'
import type { Expense } from '@/types'

const props = defineProps<{ expense?: Expense | null }>()
const emit = defineEmits<{ saved: [] }>()
const open = defineModel<boolean>('open', { required: true })

const { t, locale } = useI18n()
const household = useHouseholdStore()
const auth = useAuthStore()
const ui = useUiStore()
const format = useFormat()
const { run, submitting, errors } = useForm()

const form = reactive({
  description: '',
  amount: '',
  paidById: '',
  date: '',
  splitMode: 'EQUAL' as 'EQUAL' | 'CUSTOM',
  participantIds: [] as string[],
  shares: {} as Record<string, string>,
})

watch(open, (isOpen) => {
  if (!isOpen) return
  errors.value = {}
  const expense = props.expense
  form.description = expense?.description ?? ''
  form.amount = expense ? centsToInput(expense.amountCents, locale.value) : ''
  form.paidById = expense?.paidBy.id ?? auth.user?.id ?? ''
  form.date = expense?.date ?? format.today()
  form.splitMode = expense?.splitMode ?? 'EQUAL'
  form.participantIds = expense
    ? expense.participants.map((participant) => participant.user.id)
    : household.members.filter((member) => !member.frozen).map((member) => member.id)
  form.shares = Object.fromEntries(
    (expense?.participants ?? []).map((participant) => [
      participant.user.id,
      centsToInput(participant.shareCents, locale.value),
    ]),
  )
})

const payerOptions = computed(() =>
  household.members.map((member) => ({ value: member.id, label: member.displayName })),
)
const splitOptions = computed(() => [
  { value: 'EQUAL' as const, label: t('expenses.splitEqual') },
  { value: 'CUSTOM' as const, label: t('expenses.splitCustom') },
])
const amountCents = computed(() => parseEurosToCents(form.amount))
const chosenMembers = computed(() =>
  household.members.filter((member) => form.participantIds.includes(member.id)),
)
const perPerson = computed(() =>
  amountCents.value && form.participantIds.length
    ? format.money(Math.ceil(amountCents.value / form.participantIds.length))
    : null,
)
const customRemaining = computed(() => {
  if (amountCents.value === null) return null
  let assigned = 0
  for (const id of form.participantIds) {
    const share = form.shares[id]?.trim()
    if (!share) continue
    const cents = parseEurosToCents(share)
    if (cents === null) return null
    assigned += cents
  }
  return amountCents.value - assigned
})

async function submit() {
  const saved = await run(async () => {
    const cents = amountCents.value
    if (cents === null || cents <= 0) {
      throw new ApiError(400, 'validation_error', { amountCents: 'invalid_amount' })
    }
    if (form.participantIds.length === 0) {
      throw new ApiError(400, 'validation_error', { participants: 'pick_one' })
    }
    const participants =
      form.splitMode === 'EQUAL'
        ? form.participantIds.map((userId) => ({ userId }))
        : form.participantIds.map((userId) => {
            const shareCents = parseEurosToCents(form.shares[userId]?.trim() || '0')
            if (shareCents === null) {
              throw new ApiError(400, 'validation_error', { participants: 'invalid_amount' })
            }
            return { userId, shareCents }
          })
    const body = {
      description: form.description,
      amountCents: cents,
      paidById: form.paidById,
      date: form.date,
      splitMode: form.splitMode,
      participants,
    }
    if (props.expense) await expenseService.update(household.id, props.expense.id, body)
    else await expenseService.create(household.id, body)
  })
  if (saved) {
    open.value = false
    ui.toast(t('expenses.saved'))
    emit('saved')
  }
}
</script>

<template>
  <AppModal v-model:open="open" :title="expense ? t('expenses.editExpense') : t('expenses.newExpense')">
    <form class="space-y-4" novalidate @submit.prevent="submit">
      <AppInput
        v-model="form.description"
        :label="t('expenses.description')"
        :placeholder="t('expenses.descriptionPlaceholder')"
        :error="errors.description"
        autofocus
      />
      <div class="grid grid-cols-2 gap-3">
        <AppInput
          v-model="form.amount"
          :label="t('expenses.amount')"
          :error="errors.amountCents"
          inputmode="decimal"
          placeholder="0,00"
        />
        <AppInput v-model="form.date" type="date" :label="t('expenses.date')" :error="errors.date" />
      </div>
      <AppSelect
        v-model="form.paidById"
        :label="t('expenses.paidBy')"
        :options="payerOptions"
        :error="errors.paidById"
      />
      <MemberPicker
        v-model="form.participantIds"
        :label="t('expenses.splitBetween')"
        :error="errors.participants"
      />
      <SegmentedControl v-model="form.splitMode" :label="t('expenses.splitBetween')" :options="splitOptions" />
      <p v-if="form.splitMode === 'EQUAL' && perPerson" class="text-sm text-muted">
        {{ t('expenses.perPerson', { amount: perPerson }) }}
      </p>
      <div v-if="form.splitMode === 'CUSTOM'" class="space-y-2">
        <AppInput
          v-for="member in chosenMembers"
          :key="member.id"
          v-model="form.shares[member.id] as string"
          :label="member.displayName"
          inputmode="decimal"
          placeholder="0,00"
        />
        <p v-if="customRemaining !== null" class="text-sm" :class="customRemaining === 0 ? 'text-accent-text' : 'text-warn'">
          <template v-if="customRemaining === 0">{{ t('expenses.sharesOk') }}</template>
          <template v-else-if="customRemaining > 0">{{ t('expenses.remaining', { amount: format.money(customRemaining) }) }}</template>
          <template v-else>{{ t('expenses.tooMuch', { amount: format.money(-customRemaining) }) }}</template>
        </p>
      </div>
      <div class="flex justify-end gap-2 pt-1">
        <AppButton @click="open = false">{{ t('common.cancel') }}</AppButton>
        <AppButton variant="primary" type="submit" :loading="submitting">{{ t('common.save') }}</AppButton>
      </div>
    </form>
  </AppModal>
</template>
