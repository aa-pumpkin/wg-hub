<script setup lang="ts">
import { ChevronDown, ShoppingCart } from 'lucide-vue-next'
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import AddButton from '@/components/common/AddButton.vue'
import AppButton from '@/components/common/AppButton.vue'
import AppMenu from '@/components/common/AppMenu.vue'
import CheckCircle from '@/components/common/CheckCircle.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import LoadError from '@/components/common/LoadError.vue'
import PageHeader from '@/components/common/PageHeader.vue'
import SkeletonList from '@/components/common/SkeletonList.vue'
import BuyItemDialog from '@/components/shopping/BuyItemDialog.vue'
import ShoppingFormModal from '@/components/shopping/ShoppingFormModal.vue'
import { useAction } from '@/composables/useForm'
import { useFormat } from '@/composables/useFormat'
import { useLive } from '@/composables/useLive'
import { useAuthStore } from '@/stores/auth'
import { useHouseholdStore } from '@/stores/household'
import { useShoppingStore } from '@/stores/shopping'
import { useUiStore } from '@/stores/ui'
import type { ShoppingItem } from '@/types'

const { t } = useI18n()
const store = useShoppingStore()
const auth = useAuthStore()
const household = useHouseholdStore()
const ui = useUiStore()
const format = useFormat()
const act = useAction()

const failed = ref(false)
const loading = ref(true)
const showBought = ref(false)
const formOpen = ref(false)
const editing = ref<ShoppingItem | null>(null)
const buying = ref<ShoppingItem | null>(null)

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
useLive(['shopping'], load)

const sections = computed(() =>
  [
    { key: 'wg', title: t('shopping.forWg'), items: store.forWg },
    { key: 'personal', title: t('shopping.personalSection'), items: store.personal },
  ].filter((section) => section.items.length > 0),
)

function openNew() {
  editing.value = null
  formOpen.value = true
}

function openEdit(item: ShoppingItem) {
  editing.value = item
  formOpen.value = true
}

/** Buying your own personal item involves nobody else, so there is nothing to ask. */
function buy(item: ShoppingItem) {
  if (item.personal && item.createdBy.id === auth.user?.id) {
    void act(() => store.toggle(household.id, item))
  } else {
    buying.value = item
  }
}

async function bought() {
  buying.value = null
  await load()
}

async function putBack(item: ShoppingItem) {
  if (item.expense) {
    const confirmed = await ui.confirm({
      title: t('shopping.markPending', { name: item.name }),
      message: t('shopping.putBackText', { amount: format.money(item.expense.amountCents) }),
      confirmLabel: t('shopping.putBack'),
    })
    if (!confirmed) return
  }
  await act(() => store.toggle(household.id, item))
}

async function remove(item: ShoppingItem) {
  const confirmed = await ui.confirm({
    title: t('shopping.deleteConfirm', { name: item.name }),
    confirmLabel: t('common.delete'),
  })
  if (confirmed) await act(() => store.remove(household.id, item.id))
}

async function clearBought() {
  const confirmed = await ui.confirm({
    title: t('shopping.clearConfirm'),
    confirmLabel: t('shopping.clearBought'),
  })
  if (!confirmed) return
  const ok = await act(() => store.clearBought(household.id))
  if (ok) ui.toast(t('shopping.cleared'))
}

function details(item: ShoppingItem) {
  return [
    item.personal ? t('shopping.personalFor', { name: item.createdBy.displayName }) : null,
    item.note,
  ]
    .filter(Boolean)
    .join(' · ')
}
</script>

<template>
  <div>
    <PageHeader :title="t('shopping.title')">
      <AddButton :label="t('shopping.add')" @click="openNew" />
    </PageHeader>

    <LoadError v-if="failed" @retry="load" />
    <SkeletonList v-else-if="loading" />
    <template v-else>
      <EmptyState
        v-if="store.pending.length === 0"
        :icon="ShoppingCart"
        :title="t('shopping.emptyTitle')"
        :text="t('shopping.emptyText')"
      />
      <div v-else class="space-y-5">
        <section v-for="section in sections" :key="section.key">
          <h2 class="section-title mb-1.5">{{ section.title }}</h2>
          <ul class="card divide-y divide-line">
            <li v-for="item in section.items" :key="item.id" class="list-row">
              <CheckCircle :checked="false" :label="t('shopping.markBought', { name: item.name })" @toggle="buy(item)" />
              <div class="min-w-0 flex-1">
                <p class="truncate text-sm font-medium">{{ item.name }}</p>
                <p v-if="details(item)" class="truncate text-[13px] text-muted">{{ details(item) }}</p>
              </div>
              <span v-if="item.quantity != null" class="shrink-0 text-sm text-muted tabular-nums">
                {{ format.amount(item.quantity, item.unit) }}
              </span>
              <AppMenu
                :label="t('common.actions')"
                :items="[
                  { label: t('common.edit'), action: () => openEdit(item) },
                  { label: t('common.delete'), action: () => remove(item), danger: true },
                ]"
              />
            </li>
          </ul>
        </section>
      </div>

      <section v-if="store.bought.length > 0" class="mt-6">
        <div class="flex items-center justify-between gap-2">
          <button
            type="button"
            class="section-title tap flex items-center gap-1 hover:text-fg"
            :aria-expanded="showBought"
            @click="showBought = !showBought"
          >
            <ChevronDown class="size-4 transition-transform" :class="!showBought && '-rotate-90'" aria-hidden="true" />
            {{ t('shopping.boughtCount', { count: store.bought.length }) }}
          </button>
          <AppButton variant="ghost" @click="clearBought">{{ t('shopping.clearBought') }}</AppButton>
        </div>
        <ul v-if="showBought" class="card mt-1 divide-y divide-line">
          <li v-for="item in store.bought" :key="item.id" class="list-row">
            <CheckCircle checked :label="t('shopping.markPending', { name: item.name })" @toggle="putBack(item)" />
            <p class="min-w-0 flex-1 truncate text-sm text-muted line-through">{{ item.name }}</p>
            <span v-if="item.expense" class="text-[13px] font-medium tabular-nums">{{ format.money(item.expense.amountCents) }}</span>
            <span v-if="item.boughtBy" class="text-[13px] text-muted">{{ item.boughtBy.displayName }}</span>
          </li>
        </ul>
      </section>
    </template>

    <ShoppingFormModal v-model:open="formOpen" :item="editing" @saved="load" />
    <BuyItemDialog :item="buying" @close="buying = null" @bought="bought" />
  </div>
</template>
