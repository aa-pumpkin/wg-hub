import type { Prisma } from '@prisma/client'
import { prisma } from '../../lib/prisma.js'
import { computeBalances } from './balances.js'

export async function householdBalances(
  householdId: string,
  db: Prisma.TransactionClient = prisma,
) {
  const [expenses, settlements] = await Promise.all([
    db.expense.findMany({
      where: { householdId },
      select: { paidById: true, amountCents: true, participants: true },
    }),
    db.expenseSettlement.findMany({
      where: { householdId },
      select: { fromUserId: true, toUserId: true, amountCents: true },
    }),
  ])
  return computeBalances(expenses, settlements)
}
