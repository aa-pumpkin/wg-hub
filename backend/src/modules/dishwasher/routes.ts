import type { FastifyInstance } from 'fastify'
import { conflict } from '../../lib/errors.js'
import { live } from '../../lib/live.js'
import { prisma } from '../../lib/prisma.js'
import { userSummary } from '../../lib/users.js'
import { currentHousehold, currentUser } from '../../middleware/auth.js'
import { roundWinner, STRIKE_PENALTY, struckMembers } from './rules.js'

/**
 * The magnet board for a shared dishwasher. Everyone marks their own emptyings. When the WG
 * together reaches the target, whoever did the most gets the crown, whoever did nothing gets
 * a strike and loses points, and the next round starts from zero.
 */
export async function dishwasherRoutes(app: FastifyInstance) {
  app.get('/dishwasher', async (request) => {
    const household = currentHousehold(request)
    const where = { householdId: household.id }
    const [members, entries, cycles] = await Promise.all([
      prisma.householdMember.findMany({
        where,
        orderBy: { joinedAt: 'asc' },
        select: { frozenAt: true, user: userSummary },
      }),
      prisma.dishwasherEntry.groupBy({
        by: ['userId'],
        where: { ...where, cycle: household.dishwasherCycle },
        _count: true,
      }),
      prisma.dishwasherCycle.findMany({
        where,
        orderBy: { number: 'desc' },
        include: { winner: userSummary, strikes: { include: { user: userSummary } } },
      }),
    ])
    const counts = new Map(entries.map((row) => [row.userId, row._count]))
    const crowns = new Map<string, number>()
    const strikes = new Map<string, number>()
    for (const cycle of cycles) {
      crowns.set(cycle.winnerId, (crowns.get(cycle.winnerId) ?? 0) + 1)
      for (const strike of cycle.strikes) {
        strikes.set(strike.userId, (strikes.get(strike.userId) ?? 0) + 1)
      }
    }
    const last = cycles[0]
    return {
      target: household.dishwasherTarget,
      total: entries.reduce((sum, row) => sum + row._count, 0),
      strikePenalty: STRIKE_PENALTY,
      cycle: household.dishwasherCycle,
      crownHolderId: last?.winnerId ?? null,
      lastCycle: last
        ? { winner: last.winner, struck: last.strikes.map((strike) => strike.user) }
        : null,
      members: members.map(({ user, frozenAt }) => ({
        user,
        frozen: frozenAt !== null,
        count: counts.get(user.id) ?? 0,
        crowns: crowns.get(user.id) ?? 0,
        strikes: strikes.get(user.id) ?? 0,
      })),
    }
  })

  app.post('/dishwasher/entries', async (request, reply) => {
    const { id: householdId } = currentHousehold(request)
    const userId = currentUser(request).id
    const result = await prisma.$transaction(async (tx) => {
      // One at a time, so two people cannot both finish the same round.
      await tx.$queryRaw`SELECT id FROM "Household" WHERE id = ${householdId} FOR UPDATE`
      const household = await tx.household.findUniqueOrThrow({ where: { id: householdId } })
      if (household.dishwasherTarget === null) throw conflict('dishwasher_off')
      const cycle = household.dishwasherCycle
      await tx.dishwasherEntry.create({ data: { householdId, userId, cycle } })

      const entries = await tx.dishwasherEntry.findMany({
        where: { householdId, cycle },
        orderBy: [{ createdAt: 'asc' }, { id: 'asc' }],
        select: { userId: true },
      })
      const counts = new Map<string, number>()
      for (const entry of entries) counts.set(entry.userId, (counts.get(entry.userId) ?? 0) + 1)
      if (entries.length < household.dishwasherTarget) {
        return { count: counts.get(userId) ?? 0, finished: false, crown: false, winnerName: null }
      }

      const members = await tx.householdMember.findMany({
        where: { householdId },
        include: { user: { select: { id: true, displayName: true } } },
      })
      const winnerId = roundWinner(
        entries.map((entry) => entry.userId),
        new Set(members.map((member) => member.userId)),
      )!
      const struck = struckMembers(
        members.map((member) => ({
          userId: member.userId,
          joinedAt: member.joinedAt,
          frozen: member.frozenAt !== null,
        })),
        counts,
        household.dishwasherCycleStart,
      )
      await tx.dishwasherCycle.create({
        data: {
          householdId,
          number: cycle,
          winnerId,
          strikes: { create: struck.map((id) => ({ userId: id, points: STRIKE_PENALTY })) },
        },
      })
      await tx.household.update({
        where: { id: householdId },
        data: { dishwasherCycle: cycle + 1, dishwasherCycleStart: new Date() },
      })
      return {
        count: counts.get(userId) ?? 0,
        finished: true,
        crown: winnerId === userId,
        winnerName: members.find((member) => member.userId === winnerId)!.user.displayName,
      }
    })
    live.publish(householdId, 'dishwasher')
    if (result.finished) live.publish(householdId, 'chores') // Strikes changed the points.
    return reply.code(201).send(result)
  })

  /** Tapped by accident: takes back your own latest magnet of the running round. */
  app.delete('/dishwasher/entries/last', async (request, reply) => {
    const household = currentHousehold(request)
    const last = await prisma.dishwasherEntry.findFirst({
      where: {
        householdId: household.id,
        userId: currentUser(request).id,
        cycle: household.dishwasherCycle,
      },
      orderBy: { createdAt: 'desc' },
    })
    if (!last) throw conflict('nothing_to_undo')
    await prisma.dishwasherEntry.delete({ where: { id: last.id } })
    live.publish(household.id, 'dishwasher')
    return reply.code(204).send()
  })
}
