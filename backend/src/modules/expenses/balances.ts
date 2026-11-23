export interface ExpenseInput {
  paidById: string
  amountCents: number
  participants: readonly { userId: string; shareCents: number }[]
}

export interface SettlementInput {
  fromUserId: string
  toUserId: string
  amountCents: number
}

export interface SuggestedPayment {
  fromUserId: string
  toUserId: string
  amountCents: number
}

/**
 * Net balance per user in cents: positive means the others owe them, negative means
 * they owe. The balances of a household always add up to zero.
 */
export function computeBalances(
  expenses: readonly ExpenseInput[],
  settlements: readonly SettlementInput[],
): Map<string, number> {
  const balances = new Map<string, number>()
  const add = (userId: string, cents: number) =>
    balances.set(userId, (balances.get(userId) ?? 0) + cents)

  for (const expense of expenses) {
    add(expense.paidById, expense.amountCents)
    for (const participant of expense.participants) add(participant.userId, -participant.shareCents)
  }
  for (const settlement of settlements) {
    add(settlement.fromUserId, settlement.amountCents)
    add(settlement.toUserId, -settlement.amountCents)
  }
  return balances
}

/** A short list of payments that brings every balance to zero: largest debts first. */
export function suggestPayments(balances: ReadonlyMap<string, number>): SuggestedPayment[] {
  const byLargest = (a: [string, number], b: [string, number]) =>
    b[1] - a[1] || a[0].localeCompare(b[0])
  const creditors = [...balances].filter(([, cents]) => cents > 0).sort(byLargest)
  const debtors = [...balances]
    .filter(([, cents]) => cents < 0)
    .map(([userId, cents]): [string, number] => [userId, -cents])
    .sort(byLargest)

  const payments: SuggestedPayment[] = []
  let c = 0
  let d = 0
  while (c < creditors.length && d < debtors.length) {
    const creditor = creditors[c]!
    const debtor = debtors[d]!
    const amountCents = Math.min(creditor[1], debtor[1])
    payments.push({ fromUserId: debtor[0], toUserId: creditor[0], amountCents })
    creditor[1] -= amountCents
    debtor[1] -= amountCents
    if (creditor[1] === 0) c++
    if (debtor[1] === 0) d++
  }
  return payments
}

/** Beers to cover a debt: always rounded up, a debt is not settled by a fraction of a beer. */
export const beersFor = (amountCents: number, beerPriceCents: number) =>
  Math.ceil(amountCents / beerPriceCents)
