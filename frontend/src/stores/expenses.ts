import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { expenseService } from '@/services/modules'
import type { Balance, BeerRequest, Expense, Settlement, SuggestedPayment } from '@/types'

export type HistoryEntry =
  | { kind: 'expense'; date: string; key: string; expense: Expense }
  | { kind: 'settlement'; date: string; key: string; settlement: Settlement }

export const useExpenseStore = defineStore('expenses', () => {
  const expenses = ref<Expense[]>([])
  const settlements = ref<Settlement[]>([])
  const balances = ref<Balance[]>([])
  const suggestions = ref<SuggestedPayment[]>([])
  const beerRequests = ref<BeerRequest[]>([])
  const loadedFor = ref('')

  const history = computed<HistoryEntry[]>(() =>
    [
      ...expenses.value.map(
        (expense): HistoryEntry => ({
          kind: 'expense',
          date: expense.date,
          key: `e-${expense.id}`,
          expense,
        }),
      ),
      ...settlements.value.map(
        (settlement): HistoryEntry => ({
          kind: 'settlement',
          date: settlement.date,
          key: `s-${settlement.id}`,
          settlement,
        }),
      ),
    ].sort((a, b) => b.date.localeCompare(a.date)),
  )

  async function load(householdId: string) {
    const [expenseList, settlementList, balanceData] = await Promise.all([
      expenseService.list(householdId),
      expenseService.settlements(householdId),
      expenseService.balances(householdId),
    ])
    expenses.value = expenseList
    settlements.value = settlementList
    balances.value = balanceData.balances
    suggestions.value = balanceData.suggestions
    beerRequests.value = balanceData.beerRequests
    loadedFor.value = householdId
  }

  return { expenses, settlements, balances, suggestions, beerRequests, history, loadedFor, load }
})
