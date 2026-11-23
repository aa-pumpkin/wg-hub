<script setup lang="ts">
import { computed, reactive, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import AppButton from '@/components/common/AppButton.vue'
import AppInput from '@/components/common/AppInput.vue'
import AppModal from '@/components/common/AppModal.vue'
import AppSelect from '@/components/common/AppSelect.vue'
import { useForm } from '@/composables/useForm'
import { useFormat } from '@/composables/useFormat'
import { parseEurosToCents } from '@/lib/money'
import { ApiError } from '@/services/api'
import { expenseService } from '@/services/modules'
import { useAuthStore } from '@/stores/auth'
import { useHouseholdStore } from '@/stores/household'
import { useUiStore } from '@/stores/ui'

const emit = defineEmits<{ saved: [] }>()
const open = defineModel<boolean>('open', { required: true })

const { t } = useI18n()
const household = useHouseholdStore()
const auth = useAuthStore()
const ui = useUiStore()
const format = useFormat()
const { run, submitting, errors } = useForm()

const form = reactive({ fromUserId: '', toUserId: '', amount: '', date: '', note: '' })

watch(open, (isOpen) => {
  if (!isOpen) return
  errors.value = {}
  form.fromUserId = auth.user?.id ?? ''
  form.toUserId = household.members.find((member) => member.id !== auth.user?.id)?.id ?? ''
  form.amount = ''
  form.date = format.today()
  form.note = ''
})

const memberOptions = computed(() =>
  household.members.map((member) => ({ value: member.id, label: member.displayName })),
)

async function submit() {
  const saved = await run(async () => {
    const amountCents = parseEurosToCents(form.amount)
    if (amountCents === null || amountCents <= 0) {
      throw new ApiError(400, 'validation_error', { amountCents: 'invalid_amount' })
    }
    await expenseService.createSettlement(household.id, {
      fromUserId: form.fromUserId,
      toUserId: form.toUserId,
      amountCents,
      date: form.date,
      note: form.note,
    })
  })
  if (saved) {
    open.value = false
    ui.toast(t('expenses.saved'))
    emit('saved')
  }
}
</script>

<template>
  <AppModal v-model:open="open" :title="t('expenses.newSettlement')">
    <form class="space-y-4" novalidate @submit.prevent="submit">
      <div class="grid grid-cols-2 gap-3">
        <AppSelect v-model="form.fromUserId" :label="t('expenses.from')" :options="memberOptions" />
        <AppSelect v-model="form.toUserId" :label="t('expenses.to')" :options="memberOptions" />
      </div>
      <div class="grid grid-cols-2 gap-3">
        <AppInput
          v-model="form.amount"
          :label="t('expenses.amount')"
          :error="errors.amountCents"
          inputmode="decimal"
          placeholder="0,00"
          autofocus
        />
        <AppInput v-model="form.date" type="date" :label="t('expenses.date')" :error="errors.date" />
      </div>
      <AppInput v-model="form.note" :label="t('expenses.settlementNote')" :error="errors.note" optional />
      <div class="flex justify-end gap-2 pt-1">
        <AppButton @click="open = false">{{ t('common.cancel') }}</AppButton>
        <AppButton variant="primary" type="submit" :loading="submitting">{{ t('common.save') }}</AppButton>
      </div>
    </form>
  </AppModal>
</template>
