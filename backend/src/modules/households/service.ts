import { conflict } from '../../lib/errors.js'
import { live } from '../../lib/live.js'
import { prisma } from '../../lib/prisma.js'
import { removeFromChores } from '../chores/service.js'
import { householdBalances } from '../expenses/service.js'

/**
 * Removes a member and everything that points at them as a future responsibility.
 * Blocked while their balance is open: the debt would otherwise be left with nobody
 * able to settle it. Past records keep their name.
 */
export async function removeMember(householdId: string, userId: string, timezone: string) {
  const balances = await householdBalances(householdId)
  if ((balances.get(userId) ?? 0) !== 0) throw conflict('open_balance')

  await prisma.$transaction(async (tx) => {
    const admins = await tx.householdMember.findMany({
      where: { householdId, role: 'ADMIN' },
      select: { userId: true },
    })
    if (admins.length === 1 && admins[0]!.userId === userId) {
      const others = await tx.householdMember.count({
        where: { householdId, userId: { not: userId } },
      })
      if (others > 0) throw conflict('last_admin')
    }
    await tx.householdMember.delete({ where: { householdId_userId: { householdId, userId } } })
    await tx.calendarEventParticipant.deleteMany({ where: { userId, event: { householdId } } })
  })
  await removeFromChores(householdId, userId, timezone)

  // Nobody left: the household has no way back in, so it goes too.
  const remaining = await prisma.householdMember.count({ where: { householdId } })
  if (remaining === 0) await prisma.household.delete({ where: { id: householdId } })

  live.publish(householdId, 'members')
  live.publish(householdId, 'chores')
  live.disconnect(householdId, userId)
}
