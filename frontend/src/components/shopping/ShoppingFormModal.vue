<script setup lang="ts">
import { computed, reactive, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import AppButton from '@/components/common/AppButton.vue'
import AppInput from '@/components/common/AppInput.vue'
import AppModal from '@/components/common/AppModal.vue'
import AppSelect from '@/components/common/AppSelect.vue'
import SegmentedControl from '@/components/common/SegmentedControl.vue'
import { useForm } from '@/composables/useForm'
import { ApiError } from '@/services/api'
import { shoppingService } from '@/services/modules'
import { useHouseholdStore } from '@/stores/household'
import { SHOPPING_UNITS, type ShoppingItem } from '@/types'

const props = defineProps<{ item?: ShoppingItem | null }>()
const emit = defineEmits<{ saved: [] }>()
const open = defineModel<boolean>('open', { required: true })

const { t, te, locale } = useI18n()
const household = useHouseholdStore()
const { run, submitting, errors } = useForm()

const form = reactive({
  name: '',
  scope: 'wg' as 'wg' | 'personal',
  quantity: '',
  unit: 'pcs',
  note: '',
})

watch(open, (isOpen) => {
  if (!isOpen) return
  errors.value = {}
  const item = props.item
  form.name = item?.name ?? ''
  form.scope = item?.personal ? 'personal' : 'wg'
  form.quantity =
    item?.quantity != null ? new Intl.NumberFormat(locale.value).format(item.quantity) : ''
  form.unit = item?.unit ?? 'pcs'
  form.note = item?.note ?? ''
})

const scopes = computed(() => [
  { value: 'wg' as const, label: t('shopping.forWg') },
  { value: 'personal' as const, label: t('shopping.forMe') },
])
const unitOptions = computed(() => {
  // An older item may have a free-text unit; keep it selectable.
  const units: string[] = [...SHOPPING_UNITS]
  if (!units.includes(form.unit)) units.push(form.unit)
  return units.map((unit) => ({
    value: unit,
    label: te(`shopping.units.${unit}`) ? t(`shopping.units.${unit}`) : unit,
  }))
})

async function submit() {
  const saved = await run(async () => {
    const text = form.quantity.trim().replace(',', '.')
    const quantity = text ? Number(text) : null
    if (quantity !== null && !Number.isFinite(quantity)) {
      throw new ApiError(400, 'validation_error', { quantity: 'invalid_value' })
    }
    const body = {
      name: form.name,
      personal: form.scope === 'personal',
      quantity,
      // A unit means nothing without an amount.
      unit: quantity === null ? '' : form.unit,
      note: form.note,
    }
    if (props.item) await shoppingService.update(household.id, props.item.id, body)
    else await shoppingService.create(household.id, body)
  })
  if (saved) {
    open.value = false
    emit('saved')
  }
}
</script>

<template>
  <AppModal v-model:open="open" :title="item ? t('shopping.editItem') : t('shopping.newItem')">
    <form class="space-y-4" novalidate @submit.prevent="submit">
      <AppInput
        v-model="form.name"
        :label="t('shopping.name')"
        :placeholder="t('shopping.namePlaceholder')"
        :error="errors.name"
        autofocus
      />
      <div>
        <p class="mb-1.5 text-sm font-medium">{{ t('shopping.scopeLabel') }}</p>
        <SegmentedControl v-model="form.scope" :options="scopes" :label="t('shopping.scopeLabel')" class="flex [&>button]:flex-1" />
        <p class="mt-1.5 text-[13px] text-muted">
          {{ form.scope === 'wg' ? t('shopping.forWgHint') : t('shopping.forMeHint') }}
        </p>
      </div>
      <div class="grid grid-cols-2 gap-3">
        <AppInput
          v-model="form.quantity"
          :label="t('shopping.quantity')"
          :error="errors.quantity"
          inputmode="decimal"
          optional
        />
        <AppSelect v-model="form.unit" :label="t('shopping.unit')" :options="unitOptions" />
      </div>
      <AppInput
        v-model="form.note"
        :label="t('shopping.note')"
        :placeholder="t('shopping.notePlaceholder')"
        :error="errors.note"
        optional
      />
      <div class="flex justify-end gap-2 pt-1">
        <AppButton @click="open = false">{{ t('common.cancel') }}</AppButton>
        <AppButton variant="primary" type="submit" :loading="submitting">{{ t('common.save') }}</AppButton>
      </div>
    </form>
  </AppModal>
</template>
