<script setup lang="ts">
import { computed, reactive, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import AppButton from '@/components/common/AppButton.vue'
import AppInput from '@/components/common/AppInput.vue'
import AppSelect from '@/components/common/AppSelect.vue'
import PageHeader from '@/components/common/PageHeader.vue'
import { useAction, useForm } from '@/composables/useForm'
import { centsToInput, parseEurosToCents } from '@/lib/money'
import { ApiError } from '@/services/api'
import { householdService } from '@/services/households'
import { useHouseholdStore } from '@/stores/household'
import { useUiStore } from '@/stores/ui'

const ICONS = ['🏠', '🌻', '🌿', '🐈', '☕', '🍕', '🎸', '🌈', '🚲', '🧡', '🌙', '🐝']

const { t, locale } = useI18n()
const household = useHouseholdStore()
const ui = useUiStore()
const router = useRouter()
const { run, submitting, errors } = useForm()
const act = useAction()

const form = reactive({
  name: '',
  address: '',
  icon: '',
  timezone: '',
  beerPrice: '',
  dishwasherOn: false,
  dishwasherTarget: '20',
})

watch(
  () => household.current,
  (current) => {
    if (!current) return
    form.name = current.name
    form.address = current.address ?? ''
    form.icon = current.icon
    form.timezone = current.timezone
    form.beerPrice = centsToInput(current.beerPriceCents, locale.value)
    form.dishwasherOn = current.dishwasherTarget !== null
    form.dishwasherTarget = String(current.dishwasherTarget ?? 20)
  },
  { immediate: true },
)

const timezoneOptions = computed(() => {
  const zones = new Set([...Intl.supportedValuesOf('timeZone'), form.timezone])
  return [...zones].sort().map((zone) => ({ value: zone, label: zone.replaceAll('_', ' ') }))
})

async function save() {
  const ok = await run(async () => {
    const beerPriceCents = parseEurosToCents(form.beerPrice)
    if (beerPriceCents === null || beerPriceCents <= 0) {
      throw new ApiError(400, 'validation_error', { beerPriceCents: 'invalid_amount' })
    }
    await household.update({
      name: form.name,
      address: form.address,
      icon: form.icon,
      timezone: form.timezone,
      beerPriceCents,
      dishwasherTarget: form.dishwasherOn ? Number(form.dishwasherTarget) : null,
    })
  })
  if (ok) ui.toast(t('settings.saved'))
}

async function remove() {
  const confirmed = await ui.confirm({
    title: t('settings.deleteConfirm', { name: household.current?.name ?? '' }),
    message: t('settings.deleteWgText'),
    confirmLabel: t('settings.deleteWg'),
  })
  if (!confirmed) return
  const ok = await act(() => householdService.remove(household.id))
  if (ok) {
    ui.toast(t('settings.deleted'))
    await router.push({ name: 'home' })
  }
}
</script>

<template>
  <div class="mx-auto max-w-2xl">
    <PageHeader :title="t('settings.title')" />

    <p v-if="!household.isAdmin" class="mb-4 rounded-[10px] bg-subtle px-3 py-2 text-sm text-muted">
      {{ t('settings.adminOnly') }}
    </p>

    <form class="card space-y-4 p-5" novalidate @submit.prevent="save">
      <fieldset :disabled="!household.isAdmin" class="space-y-4">
        <AppInput v-model="form.name" :label="t('settings.name')" :error="errors.name" :disabled="!household.isAdmin" />
        <AppInput v-model="form.address" :label="t('settings.address')" :error="errors.address" optional :disabled="!household.isAdmin" />
        <div>
          <p class="mb-1.5 text-sm font-medium">{{ t('settings.icon') }}</p>
          <div class="flex flex-wrap gap-1.5">
            <button
              v-for="icon in ICONS"
              :key="icon"
              type="button"
              :aria-pressed="form.icon === icon"
              class="inline-flex size-11 items-center justify-center rounded-[10px] border text-lg disabled:opacity-50"
              :class="form.icon === icon ? 'border-accent bg-accent-soft' : 'border-line hover:bg-subtle'"
              @click="form.icon = icon"
            >
              {{ icon }}
            </button>
          </div>
        </div>
        <AppSelect
          v-model="form.timezone"
          :label="t('settings.timezone')"
          :hint="t('settings.timezoneHint')"
          :error="errors.timezone"
          :options="timezoneOptions"
          :disabled="!household.isAdmin"
        />
        <AppInput
          v-model="form.beerPrice"
          :label="t('settings.beerPrice')"
          :hint="t('settings.beerPriceHint')"
          :error="errors.beerPriceCents"
          :disabled="!household.isAdmin"
          inputmode="decimal"
        />
        <div>
          <label class="flex min-h-11 items-center gap-2.5 text-sm font-medium">
            <input v-model="form.dishwasherOn" type="checkbox" class="size-4 accent-[var(--accent)]" />
            {{ t('settings.dishwasherOn') }}
          </label>
          <p class="text-[13px] text-muted">{{ t('settings.dishwasherHint') }}</p>
        </div>
        <AppInput
          v-if="form.dishwasherOn"
          v-model="form.dishwasherTarget"
          :label="t('settings.dishwasherTarget')"
          :error="errors.dishwasherTarget"
          :disabled="!household.isAdmin"
          inputmode="numeric"
        />
      </fieldset>
      <div v-if="household.isAdmin" class="flex justify-end">
        <AppButton variant="primary" type="submit" :loading="submitting">{{ t('common.save') }}</AppButton>
      </div>
    </form>

    <section v-if="household.isAdmin" class="mt-6">
      <h2 class="mb-1 font-medium text-danger">{{ t('settings.dangerZone') }}</h2>
      <p class="mb-3 text-sm text-muted">{{ t('settings.deleteWgText') }}</p>
      <AppButton variant="danger" @click="remove">{{ t('settings.deleteWg') }}</AppButton>
    </section>
  </div>
</template>
