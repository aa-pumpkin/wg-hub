<script setup lang="ts">
import { ArrowRight, Handshake, ReceiptEuro } from 'lucide-vue-next'
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import AddButton from '@/components/common/AddButton.vue'
import AppButton from '@/components/common/AppButton.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import AppMenu from '@/components/common/AppMenu.vue'
import LoadError from '@/components/common/LoadError.vue'
import PageHeader from '@/components/common/PageHeader.vue'
import SkeletonList from '@/components/common/SkeletonList.vue'
import UserAvatar from '@/components/common/UserAvatar.vue'
import ExpenseFormModal from '@/components/expenses/ExpenseFormModal.vue'
import SettlementFormModal from '@/components/expenses/SettlementFormModal.vue'
import { useAction } from '@/composables/useForm'
import { useFormat } from '@/composables/useFormat'
import { useLive } from '@/composables/useLive'
import { expenseService } from '@/services/modules'
import { useAuthStore } from '@/stores/auth'
import { useExpenseStore } from '@/stores/expenses'
import { useHouseholdStore } from '@/stores/household'
import { useUiStore } from '@/stores/ui'
import type { Expense, Settlement, SuggestedPayment } from '@/types'

const { t } = useI18n()
const store = useExpenseStore()
const household = useHouseholdStore()
const auth = useAuthStore()
const ui = useUiStore()
const format = useFormat()
const act = useAction()

const loading = ref(true)
const failed = ref(false)
const expenseOpen = ref(false)
const settlementOpen = ref(false)
const editing = ref<Expense | null>(null)

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
useLive(['expenses', 'members'], load)

const isMember = (id: string) => household.members.some((member) => member.id === id)
const sortedBalances = computed(() => [...store.balances].sort((a, b) => b.cents - a.cents))
const nameOf = (id: string) =>
  store.balances.find((balance) => balance.user.id === id)?.user.displayName ?? '–'

function settlementText(settlement: Settlement) {
  const names = { from: settlement.fromUser.displayName, to: settlement.toUser.displayName }
  if (settlement.kind === 'FORGIVEN') return t('expenses.forgivenLine', names)
  if (settlement.kind === 'BEER') return t('expenses.beerLine', { ...names, count: settlement.beers ?? 0 })
  return t('expenses.settlementLine', names)
}

const canManage = (createdById: string) => household.isAdmin || createdById === auth.user?.id

function myShare(expense: Expense) {
  return expense.participants.find((participant) => participant.user.id === auth.user?.id)?.shareCents
}

function openNew() {
  editing.value = null
  expenseOpen.value = true
}

function openEdit(expense: Expense) {
  editing.value = expense
  expenseOpen.value = true
}

async function markPaid(payment: SuggestedPayment) {
  const confirmed = await ui.confirm({
    title: t('expenses.markPaidConfirm', {
      from: nameOf(payment.fromUserId),
      to: nameOf(payment.toUserId),
      amount: format.money(payment.amountCents),
    }),
    message: t('expenses.markPaidConfirmText'),
    confirmLabel: t('expenses.confirmPaid'),
    danger: false,
  })
  if (!confirmed) return
  const ok = await act(async () => {
    await expenseService.createSettlement(household.id, { ...payment, date: format.today() })
    await load()
  })
  if (ok) ui.toast(t('expenses.saved'))
}

const beerFor = (payment: SuggestedPayment) =>
  store.beerRequests.find(
    (request) => request.fromUserId === payment.fromUserId && request.toUserId === payment.toUserId,
  )

async function forgive(payment: SuggestedPayment) {
  const confirmed = await ui.confirm({
    title: t('expenses.forgiveConfirm', {
      name: nameOf(payment.fromUserId),
      amount: format.money(payment.amountCents),
    }),
    confirmLabel: t('expenses.forgive'),
    danger: false,
  })
  if (!confirmed) return
  const ok = await act(async () => {
    await expenseService.createSettlement(household.id, {
      ...payment,
      date: format.today(),
      kind: 'FORGIVEN',
    })
    await load()
  })
  if (ok) ui.toast(t('expenses.forgiven'))
}

async function askBeer(payment: SuggestedPayment) {
  const price = household.current?.beerPriceCents ?? 150
  const confirmed = await ui.confirm({
    title: t('expenses.askBeerConfirm', {
      name: nameOf(payment.fromUserId),
      count: Math.ceil(payment.amountCents / price),
    }),
    message: t('expenses.askBeerText', { price: format.money(price) }),
    confirmLabel: t('expenses.askBeer'),
    danger: false,
  })
  if (!confirmed) return
  await act(async () => {
    await expenseService.askForBeer(household.id, payment.fromUserId, payment.amountCents)
    await load()
  })
}

const beerReceived = (payment: SuggestedPayment) =>
  act(async () => {
    await expenseService.beerReceived(household.id, beerFor(payment)!.id)
    await load()
  })

const cancelBeer = (payment: SuggestedPayment) =>
  act(async () => {
    await expenseService.cancelBeer(household.id, beerFor(payment)!.id)
    await load()
  })

async function removeExpense(expense: Expense) {
  const confirmed = await ui.confirm({
    title: t('expenses.deleteConfirm', { title: expense.description }),
    confirmLabel: t('common.delete'),
  })
  if (!confirmed) return
  const ok = await act(async () => {
    await expenseService.remove(household.id, expense.id)
    await load()
  })
  if (ok) ui.toast(t('expenses.deleted'))
}

async function removeSettlement(settlement: Settlement) {
  const confirmed = await ui.confirm({
    title: t('expenses.deleteSettlementConfirm'),
    confirmLabel: t('common.delete'),
  })
  if (!confirmed) return
  await act(async () => {
    await expenseService.removeSettlement(household.id, settlement.id)
    await load()
  })
}
</script>

<template>
  <div>
    <PageHeader :title="t('expenses.title')">
      <AddButton :label="t('expenses.add')" @click="openNew" />
    </PageHeader>

    <LoadError v-if="failed" @retry="load" />
    <SkeletonList v-else-if="loading" />
    <div v-else class="grid grid-cols-1 items-start gap-5 lg:grid-cols-2">
      <div class="space-y-5">
        <section>
          <h2 class="section-title mb-1.5">{{ t('expenses.balances') }}</h2>
          <ul class="card divide-y divide-line">
            <li v-for="balance in sortedBalances" :key="balance.user.id" class="list-row">
              <UserAvatar :name="balance.user.displayName" :color="balance.user.avatarColor" size="sm" />
              <span class="min-w-0 flex-1 truncate text-sm">
                {{ balance.user.displayName }}
                <span v-if="!isMember(balance.user.id)" class="text-[13px] text-muted">({{ t('expenses.formerMember') }})</span>
              </span>
              <span
                class="text-sm font-medium tabular-nums"
                :class="balance.cents > 0 ? 'text-accent-text' : balance.cents < 0 ? 'text-danger' : 'text-muted'"
              >
                {{ balance.cents > 0 ? '+' : '' }}{{ format.money(balance.cents) }}
              </span>
            </li>
          </ul>
        </section>

        <section>
          <div class="mb-1.5 flex items-center justify-between gap-2">
            <h2 class="section-title">{{ t('expenses.settleUp') }}</h2>
            <AppButton variant="ghost" @click="settlementOpen = true">{{ t('expenses.recordPayment') }}</AppButton>
          </div>
          <p v-if="store.suggestions.length === 0" class="card px-4 py-3 text-sm text-muted">{{ t('expenses.allEven') }}</p>
          <ul v-else class="card divide-y divide-line">
            <li v-for="payment in store.suggestions" :key="`${payment.fromUserId}-${payment.toUserId}`" class="px-4 py-3">
              <p class="flex items-center gap-1.5 text-sm">
                <span class="truncate">{{ nameOf(payment.fromUserId) }}</span>
                <ArrowRight class="size-3.5 shrink-0 text-muted" aria-hidden="true" />
                <span class="truncate">{{ nameOf(payment.toUserId) }}</span>
                <span class="ml-auto font-medium tabular-nums">{{ format.money(payment.amountCents) }}</span>
              </p>
              <p v-if="beerFor(payment)" class="mt-1 text-[13px] text-warn">
                {{ t('expenses.beerAsked', { name: nameOf(payment.toUserId), count: beerFor(payment)!.beers }) }}
              </p>
              <div class="mt-2 flex flex-wrap gap-2">
                <template v-if="beerFor(payment) && payment.toUserId === auth.user?.id">
                  <AppButton variant="primary" @click="beerReceived(payment)">{{ t('expenses.beerReceived') }}</AppButton>
                  <AppButton @click="cancelBeer(payment)">{{ t('expenses.beerCancel') }}</AppButton>
                </template>
                <AppButton v-else @click="markPaid(payment)">{{ t('expenses.markPaid') }}</AppButton>
                <template v-if="payment.toUserId === auth.user?.id && !beerFor(payment)">
                  <AppButton @click="askBeer(payment)">{{ t('expenses.askBeer') }}</AppButton>
                  <AppButton variant="ghost" @click="forgive(payment)">{{ t('expenses.forgive') }}</AppButton>
                </template>
              </div>
            </li>
          </ul>
        </section>
      </div>

      <section>
        <h2 class="section-title mb-1.5">{{ t('expenses.history') }}</h2>
        <EmptyState
          v-if="store.history.length === 0"
          :icon="ReceiptEuro"
          :title="t('expenses.emptyTitle')"
          :text="t('expenses.emptyText')"
          class="card"
        />
        <ul v-else class="card divide-y divide-line">
          <li v-for="entry in store.history" :key="entry.key" class="list-row">
            <template v-if="entry.kind === 'expense'">
              <UserAvatar :name="entry.expense.paidBy.displayName" :color="entry.expense.paidBy.avatarColor" size="sm" />
              <div class="min-w-0 flex-1">
                <p class="flex justify-between gap-2 text-sm font-medium">
                  <span class="truncate">{{ entry.expense.description }}</span>
                  <span class="tabular-nums">{{ format.money(entry.expense.amountCents) }}</span>
                </p>
                <p class="truncate text-[13px] text-muted">
                  {{ format.date(entry.date) }} ·
                  {{
                    t(
                      'expenses.paidByLine',
                      { name: entry.expense.paidBy.displayName, count: entry.expense.participants.length },
                      entry.expense.participants.length,
                    )
                  }}
                </p>
                <p v-if="myShare(entry.expense) !== undefined" class="truncate text-[13px] text-muted">
                  {{ t('expenses.yourShare', { amount: format.money(myShare(entry.expense)!) }) }}
                </p>
              </div>
              <AppMenu
                v-if="canManage(entry.expense.createdById)"
                :label="t('common.actions')"
                :items="[
                  { label: t('common.edit'), action: () => openEdit(entry.expense) },
                  { label: t('common.delete'), action: () => removeExpense(entry.expense), danger: true },
                ]"
              />
              <span v-else class="w-6 shrink-0" aria-hidden="true" />
            </template>
            <template v-else>
              <span class="inline-flex size-6 shrink-0 items-center justify-center rounded-full bg-accent-soft text-accent-text">
                <Handshake class="size-3.5" aria-hidden="true" />
              </span>
              <div class="min-w-0 flex-1">
                <p class="flex justify-between gap-2 text-sm">
                  <span class="truncate">{{ settlementText(entry.settlement) }}</span>
                  <span class="font-medium tabular-nums">{{ format.money(entry.settlement.amountCents) }}</span>
                </p>
                <p class="truncate text-[13px] text-muted">
                  {{ format.date(entry.date) }}<template v-if="entry.settlement.note"> · {{ entry.settlement.note }}</template>
                </p>
              </div>
              <AppMenu
                v-if="canManage(entry.settlement.createdById)"
                :label="t('common.actions')"
                :items="[{ label: t('common.delete'), action: () => removeSettlement(entry.settlement), danger: true }]"
              />
              <span v-else class="w-6 shrink-0" aria-hidden="true" />
            </template>
          </li>
        </ul>
      </section>
    </div>

    <ExpenseFormModal v-model:open="expenseOpen" :expense="editing" @saved="load" />
    <SettlementFormModal v-model:open="settlementOpen" @saved="load" />
  </div>
</template>
