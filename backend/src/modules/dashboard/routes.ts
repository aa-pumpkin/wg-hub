import type { FastifyInstance } from 'fastify'
import { addDays, dateToDb, startOfDayInstant, todayIn } from '../../lib/dates.js'
import { prisma } from '../../lib/prisma.js'
import { currentHousehold, currentUser } from '../../middleware/auth.js'
import { sundayOf } from '../chores/recurrence.js'
import { householdProgress } from '../gamification/service.js'
import { eventInclude, presentEvent } from '../calendar/routes.js'
import { occurrenceInclude, presentOccurrence } from '../chores/routes.js'
import { ensureOccurrences } from '../chores/service.js'
import { householdBalances } from '../expenses/service.js'
import { noteInclude } from '../notes/routes.js'
import { presentItem, shoppingInclude } from '../shopping/routes.js'

const UPCOMING_DAYS = 14

export async function dashboardRoutes(app: FastifyInstance) {
  app.get('/dashboard', async (request) => {
    const household = currentHousehold(request)
    const where = { householdId: household.id }
    await ensureOccurrences(household.id, household.timezone)

    const today = todayIn(household.timezone)
    const todayDb = dateToDb(today)
    const upcomingEnd = addDays(today, UPCOMING_DAYS)
    const weekEndDb = dateToDb(sundayOf(today))
    const mainThisWeek = { kind: 'MAIN', archivedAt: null } as const

    const [
      todaysTasks,
      upcomingTasks,
      shoppingItems,
      shoppingCount,
      events,
      notes,
      balances,
    ] = await Promise.all([
      // Today and overdue, plus what was finished today so progress stays visible.
      prisma.choreAssignment.findMany({
        where: {
          ...where,
          OR: [
            { status: 'PENDING', dueDate: { lte: todayDb } },
            { status: 'COMPLETED', dueDate: todayDb },
            // Main tasks are for the whole week, so they are on the list from Monday on.
            { status: { not: 'SKIPPED' }, dueDate: { gte: todayDb, lte: weekEndDb }, chore: mainThisWeek },
          ],
        },
        include: occurrenceInclude,
        orderBy: { dueDate: 'asc' },
      }),
      prisma.choreAssignment.findMany({
        where: {
          ...where,
          status: 'PENDING',
          dueDate: { gt: todayDb, lte: dateToDb(upcomingEnd) },
          // Main tasks of later weeks are in the plan, not a list of upcoming things.
          chore: { kind: 'EXTRA' },
        },
        include: occurrenceInclude,
        orderBy: { dueDate: 'asc' },
        take: 5,
      }),
      prisma.shoppingItem.findMany({
        where: { ...where, status: 'PENDING' },
        include: shoppingInclude,
        orderBy: { createdAt: 'desc' },
        take: 6,
      }),
      prisma.shoppingItem.count({ where: { ...where, status: 'PENDING' } }),
      prisma.calendarEvent.findMany({
        where: {
          ...where,
          startsAt: {
            gte: startOfDayInstant(today, household.timezone),
            lt: startOfDayInstant(upcomingEnd, household.timezone),
          },
        },
        include: eventInclude,
        orderBy: { startsAt: 'asc' },
        take: 5,
      }),
      prisma.note.findMany({
        where: { ...where, archivedAt: null },
        include: noteInclude,
        orderBy: [{ pinned: 'desc' }, { createdAt: 'desc' }],
        take: 4,
      }),
      householdBalances(household.id),
    ])
    const progress = await householdProgress(household.id, household.timezone)

    return {
      today,
      tasks: todaysTasks.map(presentOccurrence),
      upcomingTasks: upcomingTasks.map(presentOccurrence),
      shopping: { items: shoppingItems.map(presentItem), pendingCount: shoppingCount },
      events: events.map(presentEvent),
      notes,
      myBalanceCents: balances.get(currentUser(request).id) ?? 0,
      myProgress: progress.find((entry) => entry.user.id === currentUser(request).id) ?? null,
      myRank: progress.findIndex((entry) => entry.user.id === currentUser(request).id) + 1,
    }
  })
}
