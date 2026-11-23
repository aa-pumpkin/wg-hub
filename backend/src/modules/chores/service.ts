import type { Chore, Prisma } from '@prisma/client'
import { addDays, dateFromDb, dateToDb, todayIn } from '../../lib/dates.js'
import { prisma } from '../../lib/prisma.js'
import { mondayOf, occurrenceDates, periodEnd, sundayOf, weeksBetween } from './recurrence.js'
import { circleAssignee, pickAssignee } from './rotation.js'

/** How far ahead occurrences exist by default. Calendar views may ask for more. */
export const DEFAULT_HORIZON_DAYS = 56
export const MAX_HORIZON_DAYS = 400

async function currentWorkload(
  tx: Prisma.TransactionClient,
  householdId: string,
  from: string,
  until: string,
) {
  const rows = await tx.choreAssignment.groupBy({
    by: ['assigneeId'],
    where: {
      householdId,
      status: 'PENDING',
      dueDate: { gte: dateToDb(from), lt: dateToDb(until) },
    },
    _count: true,
  })
  return new Map(rows.flatMap((row) => (row.assigneeId ? [[row.assigneeId, row._count]] : [])))
}

/** Everything needed to place a main task in the circle. Sets week 0 on first use. */
async function loadCircle(tx: Prisma.TransactionClient, chore: Chore, today: string) {
  const household = await tx.household.findUniqueOrThrow({ where: { id: chore.householdId } })
  let anchor = household.rotationAnchor && dateFromDb(household.rotationAnchor)
  if (!anchor) {
    anchor = mondayOf(today)
    await tx.household.update({
      where: { id: household.id },
      data: { rotationAnchor: dateToDb(anchor) },
    })
  }
  const [members, tasks] = await Promise.all([
    tx.householdMember.findMany({
      where: { householdId: household.id, rotationPosition: { not: null }, frozenAt: null },
      orderBy: { rotationPosition: 'asc' },
      select: { userId: true },
    }),
    tx.chore.findMany({
      where: { householdId: household.id, kind: 'MAIN', archivedAt: null },
      orderBy: [{ position: 'asc' }, { createdAt: 'asc' }],
      select: { id: true },
    }),
  ])
  return {
    anchor,
    circle: members.map((member) => member.userId),
    taskIndex: Math.max(
      tasks.findIndex((task) => task.id === chore.id),
      0,
    ),
  }
}

/**
 * Creates the missing occurrences of one chore up to `until`. Runs in a transaction with
 * the chore row locked, so concurrent requests cannot generate a diverging rotation.
 */
export async function generateOccurrences(choreId: string, timezone: string, until: string) {
  await prisma.$transaction(async (tx) => {
    await tx.$queryRaw`SELECT id FROM "Chore" WHERE id = ${choreId} FOR UPDATE`
    const chore = await tx.chore.findUnique({
      where: { id: choreId },
      include: { rotation: { orderBy: { position: 'asc' } } },
    })
    if (!chore || chore.archivedAt) return

    const generatedUntil = chore.generatedUntil && dateFromDb(chore.generatedUntil)
    if (generatedUntil && generatedUntil >= until) return

    const start = dateFromDb(chore.startDate)
    const today = todayIn(timezone)
    // A one-off task always gets its occurrence. A series only gets future ones: nobody
    // should find weeks of overdue chores after the app sat unused.
    const after = generatedUntil ? addDays(generatedUntil, 1) : start
    const from = chore.recurrence === 'NONE' || after > today ? after : today
    const lastDate = chore.recurrence === 'NONE' && start > until ? start : until
    const dates = occurrenceDates(start, chore.recurrence, from, lastDate)

    const existing = await tx.choreAssignment.findMany({
      where: { choreId },
      orderBy: { dueDate: 'asc' },
      select: { dueDate: true, assigneeId: true },
    })
    const taken = new Set(existing.map((row) => dateFromDb(row.dueDate)))
    let previous = existing.at(-1)?.assigneeId ?? null

    const frozen = await tx.householdMember.findMany({
      where: { householdId: chore.householdId, frozenAt: { not: null } },
      select: { userId: true },
    })
    const frozenIds = new Set(frozen.map((member) => member.userId))
    const rotation = chore.rotation
      .map((member) => member.userId)
      .filter((userId) => !frozenIds.has(userId))
    const main = chore.kind === 'MAIN' ? await loadCircle(tx, chore, today) : null
    const rows: Prisma.ChoreAssignmentCreateManyInput[] = []
    for (const date of dates) {
      if (taken.has(date)) continue
      if (main) {
        rows.push({
          householdId: chore.householdId,
          choreId,
          dueDate: dateToDb(date),
          assigneeId: circleAssignee(main.circle, main.taskIndex, weeksBetween(main.anchor, date)),
        })
        continue
      }
      const needsWorkload =
        rotation.length > 0 && (!previous || !rotation.includes(previous))
      const workload = needsWorkload
        ? await currentWorkload(tx, chore.householdId, date, periodEnd(date, chore.recurrence))
        : new Map<string, number>()
      const assigneeId = pickAssignee(rotation, previous, workload)
      rows.push({
        householdId: chore.householdId,
        choreId,
        dueDate: dateToDb(date),
        assigneeId,
      })
      previous = assigneeId
    }
    if (rows.length > 0) await tx.choreAssignment.createMany({ data: rows, skipDuplicates: true })
    await tx.chore.update({
      where: { id: choreId },
      data: { generatedUntil: dateToDb(lastDate) },
    })
  })
}

/** Makes sure all active series of a household have occurrences up to `until`. */
export async function ensureOccurrences(householdId: string, timezone: string, until?: string) {
  const today = todayIn(timezone)
  const limit = addDays(today, MAX_HORIZON_DAYS)
  const target = until && until > addDays(today, DEFAULT_HORIZON_DAYS) ? until : addDays(today, DEFAULT_HORIZON_DAYS)
  const horizon = target > limit ? limit : target
  const stale = await prisma.chore.findMany({
    where: {
      householdId,
      archivedAt: null,
      recurrence: { not: 'NONE' },
      OR: [{ generatedUntil: null }, { generatedUntil: { lt: dateToDb(horizon) } }],
    },
    select: { id: true },
  })
  for (const chore of stale) await generateOccurrences(chore.id, timezone, horizon)
}

/**
 * Applies a changed schedule or rotation: open occurrences from today on are replaced,
 * while finished ones and anything overdue stay untouched.
 */
export async function regenerateFutureOccurrences(chore: Chore, timezone: string) {
  const today = todayIn(timezone)
  await prisma.$transaction([
    prisma.choreAssignment.deleteMany({
      where: { choreId: chore.id, status: 'PENDING', dueDate: { gte: dateToDb(today) } },
    }),
    prisma.chore.update({ where: { id: chore.id }, data: { generatedUntil: null } }),
  ])
  await generateOccurrences(chore.id, timezone, addDays(today, DEFAULT_HORIZON_DAYS))
}

/**
 * Recalculates the main task plan after the circle or the task list changed. Finished
 * work stays as it is. `from` decides whether the current week changes too.
 */
export async function regenerateMainPlan(
  householdId: string,
  timezone: string,
  from: 'this-week' | 'next-week',
) {
  const today = todayIn(timezone)
  const cutoff = from === 'this-week' ? today : addDays(sundayOf(today), 1)
  const tasks = await prisma.chore.findMany({
    where: { householdId, kind: 'MAIN', archivedAt: null },
    orderBy: { position: 'asc' },
    select: { id: true },
  })
  for (const task of tasks) {
    await prisma.$transaction([
      prisma.choreAssignment.deleteMany({
        where: { choreId: task.id, status: 'PENDING', dueDate: { gte: dateToDb(cutoff) } },
      }),
      prisma.chore.update({ where: { id: task.id }, data: { generatedUntil: null } }),
    ])
    await generateOccurrences(task.id, timezone, addDays(today, DEFAULT_HORIZON_DAYS))
  }
}

/**
 * Freezing: nobody else's week changes. The frozen person's open main tasks of this week
 * move to someone in the circle, overdue ones become free for anyone, and future work is
 * planned without them.
 */
export async function applyFreeze(householdId: string, userId: string, timezone: string) {
  const today = todayIn(timezone)
  const [members, thisWeek] = await Promise.all([
    prisma.householdMember.findMany({
      where: { householdId, rotationPosition: { not: null }, frozenAt: null },
      orderBy: { rotationPosition: 'asc' },
      select: { userId: true },
    }),
    prisma.choreAssignment.findMany({
      where: {
        householdId,
        assigneeId: userId,
        status: 'PENDING',
        chore: { kind: 'MAIN' },
        dueDate: { gte: dateToDb(today), lte: dateToDb(sundayOf(today)) },
      },
      select: { id: true },
    }),
  ])
  // Whoever has the least to do this week takes over, so nobody ends up with everything.
  const circle = members.map((member) => member.userId)
  const weekRows = await prisma.choreAssignment.groupBy({
    by: ['assigneeId'],
    where: {
      householdId,
      chore: { kind: 'MAIN' },
      dueDate: { gte: dateToDb(today), lte: dateToDb(sundayOf(today)) },
    },
    _count: true,
  })
  const load = new Map(weekRows.map((row) => [row.assigneeId, row._count]))
  for (const row of thisWeek) {
    const next = circle.reduce<string | null>(
      (best, person) =>
        best === null || (load.get(person) ?? 0) < (load.get(best) ?? 0) ? person : best,
      null,
    )
    if (next) load.set(next, (load.get(next) ?? 0) + 1)
    await prisma.choreAssignment.update({ where: { id: row.id }, data: { assigneeId: next } })
  }
  await prisma.choreAssignment.updateMany({
    where: { householdId, assigneeId: userId, status: 'PENDING', dueDate: { lt: dateToDb(today) } },
    data: { assigneeId: null },
  })
  await replanAround(householdId, userId, timezone)
}

/** Future work with the person's new availability. This week's main tasks stay as they are. */
export async function replanAround(householdId: string, userId: string, timezone: string) {
  const extras = await prisma.chore.findMany({
    where: {
      householdId,
      kind: 'EXTRA',
      archivedAt: null,
      OR: [
        { rotation: { some: { userId } } },
        { assignments: { some: { assigneeId: userId, status: 'PENDING' } } },
      ],
    },
  })
  for (const chore of extras) await regenerateFutureOccurrences(chore, timezone)
  await regenerateMainPlan(householdId, timezone, 'next-week')
}

/** Takes a departing member out of all rotations and hands over their open occurrences. */
export async function removeFromChores(householdId: string, userId: string, timezone: string) {
  const affected = await prisma.chore.findMany({
    where: {
      householdId,
      archivedAt: null,
      OR: [
        { rotation: { some: { userId } } },
        { assignments: { some: { assigneeId: userId, status: 'PENDING' } } },
      ],
    },
  })
  await prisma.choreRotationMember.deleteMany({
    where: { userId, chore: { householdId } },
  })
  // Overdue occurrences stay in the list, but nobody is on the hook for them any more.
  await prisma.choreAssignment.updateMany({
    where: {
      householdId,
      assigneeId: userId,
      status: 'PENDING',
      dueDate: { lt: dateToDb(todayIn(timezone)) },
    },
    data: { assigneeId: null },
  })
  for (const chore of affected) {
    if (chore.kind === 'EXTRA') await regenerateFutureOccurrences(chore, timezone)
  }
  await regenerateMainPlan(householdId, timezone, 'this-week')
}
