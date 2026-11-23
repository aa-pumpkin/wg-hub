<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import AppButton from '@/components/common/AppButton.vue'
import AppInput from '@/components/common/AppInput.vue'
import AppModal from '@/components/common/AppModal.vue'
import { useForm } from '@/composables/useForm'
import { useFormat } from '@/composables/useFormat'
import { parseEurosToCents } from '@/lib/money'
import { ApiError } from '@/services/api'
import { shoppingService } from '@/services/modules'
import { useAuthStore } from '@/stores/auth'
import { useHouseholdStore } from '@/stores/household'
import { useUiStore } from '@/stores/ui'
import type { ShoppingItem } from '@/types'

const props = defineProps<{ item: ShoppingItem | null }>()
const emit = defineEmits<{ close: []; bought: [] }>()

const { t } = useI18n()
const household = useHouseholdStore()
const auth = useAuthStore()
const ui = useUiStore()
const format = useFormat()
const { run, submitting, errors } = useForm()
const price = ref('')

const open = computed({
  get: () => props.item !== null,
  set: (value: boolean) => {
    if (!value) emit('close')
  },
})
watch(
  () => props.item,
  () => {
    price.value = ''
    errors.value = {}
  },
)

const cents = computed(() => parseEurosToCents(price.value))
const payers = computed(() => {
  const me = auth.user?.id
  return household.members.filter((member) => !member.frozen || member.id === me).length
})

/** Spells out what happens before anyone's balance changes. */
const preview = computed(() => {
  const item = props.item
  if (!item || !cents.value) return t('shopping.noPriceHint')
  if (item.personal) {
    return t('shopping.previewPersonal', { name: item.createdBy.displayName, amount: format.money(cents.value) })
  }
  return t('shopping.previewWg', { amount: format.money(Math.ceil(cents.value / payers.value)), count: payers.value })
})

async function submit() {
  const item = props.item
  if (!item) return
  let added = false
  const saved = await run(async () => {
    if (price.value.trim() && (cents.value === null || cents.value <= 0)) {
      throw new ApiError(400, 'validation_error', { price: 'invalid_amount' })
    }
    const bought = await shoppingService.setStatus(household.id, item.id, 'BOUGHT', cents.value ?? undefined)
    added = bought.expense !== null
  })
  if (saved) {
    if (added) ui.toast(t('shopping.addedExpense'))
    emit('bought')
  }
}
</script>

<template>
  <AppModal v-model:open="open" :title="t('shopping.buyTitle', { name: item?.name ?? '' })">
    <form class="space-y-4" novalidate @submit.prevent="submit">
      <AppInput
        v-model="price"
        :label="t('shopping.price')"
        :error="errors.price"
        inputmode="decimal"
        placeholder="0,00"
        optional
        autofocus
      />
      <p class="text-sm text-muted">{{ preview }}</p>
      <div class="flex justify-end gap-2">
        <AppButton @click="open = false">{{ t('common.cancel') }}</AppButton>
        <AppButton variant="primary" type="submit" :loading="submitting">{{ t('shopping.confirmBuy') }}</AppButton>
      </div>
    </form>
  </AppModal>
</template>
