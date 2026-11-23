import type { Prisma } from '@prisma/client'
import type { FastifyInstance, FastifyRequest } from 'fastify'
import { z } from 'zod'
import { dateFromDb, dateToDb, todayIn, addDays } from '../../lib/dates.js'
import { badRequest, conflict, forbidden, notFound } from '../../lib/errors.js'
import { live } from '../../lib/live.js'
import { prisma } from '../../lib/prisma.js'
import { userSummary } from '../../lib/users.js'
import { idList, idParam, isoDate, optionalText, parse, shortText } from '../../lib/validate.js'
import {
  assertMembers,
  assertOwnerOrAdmin,
  currentHousehold,
  currentUser,
  requireAdmin,
} from '../../middleware/auth.js'
import { levelFor, pointsFor } from '../gamification/rules.js'
import { memberPoints } from '../gamification/service.js'
import { mondayOf, sundayOf } from './recurrence.js'
import {
  DEFAULT_HORIZON_DAYS,
  ensureOccurrences,
  generateOccurrences,
  regenerateFutureOccurrences,
  regenerateMainPlan,
} from './service.js'

const PLAN_WEEKS = 4

const category = z.enum(['KITCHEN', 'BATHROOM', 'TRASH', 'FLOORS', 'LAUNDRY', 'OTHER'])
const recurrence = z.enum(['NONE', 'DAILY', 'WEEKLY', 'BIWEEKLY', 'MONTHLY'])

const points = z.number().int().min(0).max(1000)

export const DEFAULT_POINTS = { MAIN: 10, EXTRA: 20 } as const

const createSchema = z.object({
  kind: z.enum(['MAIN', 'EXTRA']).default('EXTRA'),
  points: points.optional(),
  title: shortText(120),
  description: optionalText(1000),
  category: category.default('OTHER'),
  recurrence: recurrence.default('NONE'),
  startDate: isoDate,
  rotation: idList.default([]),
})
const updateSchema = z.object({
  points: points.optional(),
  title: shortText(120).optional(),
  description: optionalText(1000),
  category: category.optional(),
  recurrence: recurrence.optional(),
  startDate: isoDate.optional(),
  rotation: idList.optional(),
})
const occurrenceQuery = z.object({
  from: isoDate.optional(),
  to: isoDate.optional(),
  status: z.enum(['PENDING', 'COMPLETED', 'SKIPPED']).optional(),
  assigneeId: z.string().max(64).optional(),
  choreId: z.string().max(64).optional(),
  limit: z.coerce.number().int().min(1).max(500).default(200),
})
const circleSchema = z.object({ userIds: idList })
const shiftSchema = z.object({ weeks: z.union([z.literal(-1), z.literal(1)]) })
const reassignSchema = z.object({ assigneeId: z.string().min(1).max(64).nullable() })

export const occurrenceInclude = {
  chore: {
    select: {
      title: true,
      description: true,
      category: true,
      recurrence: true,
      kind: true,
      points: true,
    },
  },
  assignee: userSummary,
  completion: {
    select: { completedAt: true, points: true, completedBy: userSummary },
  },
} satisfies Prisma.ChoreAssignmentInclude

type OccurrenceRow = Prisma.ChoreAssignmentGetPayload<{ include: typeof occurrenceInclude }>

export function presentOccurrence(row: OccurrenceRow) {
  return {
    id: row.id,
    choreId: row.choreId,
    dueDate: dateFromDb(row.dueDate),
    status: row.status,
    title: row.chore.title,
    description: row.chore.description,
    category: row.chore.category,
    recurrence: row.chore.recurrence,
    kind: row.chore.kind,
    points: row.chore.points,
    assignee: row.assignee,
    completedAt: row.completion?.completedAt ?? null,
    completedBy: row.completion?.completedBy ?? null,
    awardedPoints: row.completion?.points ?? null,
  }
}

export async function choreRoutes(app: FastifyInstance) {
  const householdId = (request: FastifyRequest) => currentHousehold(request).id

  async function findChore(request: FastifyRequest) {
    const { id } = parse(idParam, request.params)
    const chore = await prisma.chore.findFirst({
      where: { id, householdId: householdId(request), archivedAt: null },
    })
    if (!chore) throw notFound()
    return chore
  }

  async function findOccurrence(request: FastifyRequest) {
    const { id } = parse(idParam, request.params)
    const occurrence = await prisma.choreAssignment.findFirst({
      where: { id, householdId: householdId(request) },
    })
    if (!occurrence) throw notFound()
    return occurrence
  }

  const fetchPresented = async (id: string) =>
    presentOccurrence(
      await prisma.choreAssignment.findUniqueOrThrow({
        where: { id },
        include: occurrenceInclude,
      }),
    )

  app.get('/chores', async (request) => {
    const chores = await prisma.chore.findMany({
      where: { householdId: householdId(request), archivedAt: null },
      include: {
        rotation: { orderBy: { position: 'asc' }, include: { user: userSummary } },
      },
      orderBy: { createdAt: 'asc' },
    })
    return {
      chores: chores.map((chore) => ({
        id: chore.id,
        title: chore.title,
        description: chore.description,
        category: chore.category,
        kind: chore.kind,
        points: chore.points,
        position: chore.position,
        recurrence: chore.recurrence,
        startDate: dateFromDb(chore.startDate),
        createdById: chore.createdById,
        rotation: chore.rotation.map((member) => member.user),
      })),
    }
  })

  app.post('/chores', async (request, reply) => {
    const body = parse(createSchema, request.body)
    const household = currentHousehold(request)
    const isAdmin = request.role === 'ADMIN'
    if (body.kind === 'MAIN' && !isAdmin) throw forbidden()
    const isMain = body.kind === 'MAIN'
    const rotation = isMain ? [] : body.rotation
    await assertMembers(household.id, rotation)
    const position = isMain
      ? ((
          await prisma.chore.aggregate({
            where: { householdId: household.id, kind: 'MAIN', archivedAt: null },
            _max: { position: true },
          })
        )._max.position ?? -1) + 1
      : 0
    const chore = await prisma.chore.create({
      data: {
        householdId: household.id,
        createdById: currentUser(request).id,
        kind: body.kind,
        // Only admins hand out points; otherwise anyone could create and tick off free ones.
        points: isAdmin ? (body.points ?? DEFAULT_POINTS[body.kind]) : 0,
        position,
        title: body.title,
        description: body.description,
        category: body.category,
        recurrence: isMain ? 'WEEKLY' : body.recurrence,
        startDate: dateToDb(isMain ? sundayOf(todayIn(household.timezone)) : body.startDate),
        rotation: {
          create: rotation.map((userId, index) => ({ userId, position: index })),
        },
      },
    })
    await generateOccurrences(
      chore.id,
      household.timezone,
      addDays(todayIn(household.timezone), DEFAULT_HORIZON_DAYS),
    )
    live.publish(household.id, 'chores')
    return reply.code(201).send({ id: chore.id })
  })

  app.patch('/chores/:id', async (request) => {
    const chore = await findChore(request)
    assertOwnerOrAdmin(request, chore.createdById)
    if (chore.kind === 'MAIN' && request.role !== 'ADMIN') throw forbidden()
    const parsed = parse(updateSchema, request.body)
    if (parsed.points !== undefined && request.role !== 'ADMIN') throw forbidden()
    // A main task's schedule and people come from the circle, not from the task.
    const body =
      chore.kind === 'MAIN'
        ? { ...parsed, recurrence: undefined, startDate: undefined, rotation: undefined }
        : parsed
    const household = currentHousehold(request)
    if (body.rotation) await assertMembers(household.id, body.rotation)

    const updated = await prisma.$transaction(async (tx) => {
      if (body.rotation) {
        await tx.choreRotationMember.deleteMany({ where: { choreId: chore.id } })
        await tx.choreRotationMember.createMany({
          data: body.rotation.map((userId, position) => ({
            choreId: chore.id,
            userId,
            position,
          })),
        })
      }
      return tx.chore.update({
        where: { id: chore.id },
        data: {
          title: body.title,
          description: body.description,
          category: body.category,
          points: body.points,
          recurrence: body.recurrence,
          startDate: body.startDate ? dateToDb(body.startDate) : undefined,
        },
      })
    })

    const scheduleChanged =
      body.rotation !== undefined ||
      (body.recurrence !== undefined && body.recurrence !== chore.recurrence) ||
      (body.startDate !== undefined && body.startDate !== dateFromDb(chore.startDate))
    if (scheduleChanged) await regenerateFutureOccurrences(updated, household.timezone)

    live.publish(household.id, 'chores')
    return { id: chore.id }
  })

  // Ends the series: open occurrences go away, the history of finished ones stays.
  app.delete('/chores/:id', async (request, reply) => {
    const chore = await findChore(request)
    assertOwnerOrAdmin(request, chore.createdById)
    await prisma.$transaction([
      prisma.choreAssignment.deleteMany({ where: { choreId: chore.id, status: 'PENDING' } }),
      prisma.chore.update({ where: { id: chore.id }, data: { archivedAt: new Date() } }),
    ])
    if (chore.kind === 'MAIN') {
      const household = currentHousehold(request)
      await regenerateMainPlan(household.id, household.timezone, 'this-week')
    }
    live.publish(householdId(request), 'chores')
    return reply.code(204).send()
  })

  /** The main task plan: who sits where in the circle and the next weeks. */
  app.get('/chore-plan', async (request) => {
    const household = currentHousehold(request)
    await ensureOccurrences(household.id, household.timezone)
    const weekEnd = sundayOf(todayIn(household.timezone))
    const [members, tasks, rows] = await Promise.all([
      prisma.householdMember.findMany({
        where: { householdId: household.id },
        orderBy: [{ rotationPosition: { sort: 'asc', nulls: 'last' } }, { joinedAt: 'asc' }],
        select: { frozenAt: true, user: userSummary },
      }),
      prisma.chore.findMany({
        where: { householdId: household.id, kind: 'MAIN', archivedAt: null },
        orderBy: [{ position: 'asc' }, { createdAt: 'asc' }],
        select: { id: true },
      }),
      prisma.choreAssignment.findMany({
        where: {
          householdId: household.id,
          chore: { kind: 'MAIN', archivedAt: null },
          dueDate: { gte: dateToDb(weekEnd), lte: dateToDb(addDays(weekEnd, 7 * (PLAN_WEEKS - 1))) },
        },
        include: occurrenceInclude,
        orderBy: { dueDate: 'asc' },
      }),
    ])
    const order = new Map(tasks.map((task, index) => [task.id, index]))
    const weeks = new Map<string, ReturnType<typeof presentOccurrence>[]>()
    for (const row of rows) {
      const date = dateFromDb(row.dueDate)
      weeks.set(date, [...(weeks.get(date) ?? []), presentOccurrence(row)])
    }
    return {
      circle: members.map((member) => ({
        user: member.user,
        frozen: member.frozenAt !== null,
      })),
      weeks: [...weeks].map(([dueDate, items]) => ({
        dueDate,
        items: items.sort((a, b) => (order.get(a.choreId) ?? 0) - (order.get(b.choreId) ?? 0)),
      })),
    }
  })

  /** Sets the clockwise order, of everyone in the WG. */
  app.put('/chore-plan/circle', { preHandler: requireAdmin }, async (request) => {
    const { userIds } = parse(circleSchema, request.body)
    const household = currentHousehold(request)
    await assertMembers(household.id, userIds)
    const everyone = await prisma.householdMember.count({ where: { householdId: household.id } })
    if (userIds.length !== everyone) throw badRequest('circle_incomplete')
    await prisma.$transaction([
      ...userIds.map((userId, position) =>
        prisma.householdMember.update({
          where: { householdId_userId: { householdId: household.id, userId } },
          data: { rotationPosition: position },
        }),
      ),
    ])
    await regenerateMainPlan(household.id, household.timezone, 'this-week')
    live.publish(household.id, 'chores')
    return { ok: true }
  })

  /** Moves everyone one step along the circle, for example to skip or repeat a week. */
  app.post('/chore-plan/shift', { preHandler: requireAdmin }, async (request) => {
    const { weeks } = parse(shiftSchema, request.body)
    const household = currentHousehold(request)
    const anchor = household.rotationAnchor
      ? dateFromDb(household.rotationAnchor)
      : mondayOf(todayIn(household.timezone))
    await prisma.household.update({
      where: { id: household.id },
      // An earlier week 0 puts this week one step further around the circle.
      data: { rotationAnchor: dateToDb(addDays(anchor, -7 * weeks)) },
    })
    await regenerateMainPlan(household.id, household.timezone, 'this-week')
    live.publish(household.id, 'chores')
    return { ok: true }
  })

  app.get('/chore-occurrences', async (request) => {
    const query = parse(occurrenceQuery, request.query)
    const household = currentHousehold(request)
    await ensureOccurrences(household.id, household.timezone, query.to)
    const occurrences = await prisma.choreAssignment.findMany({
      where: {
        householdId: household.id,
        status: query.status,
        assigneeId: query.assigneeId,
        choreId: query.choreId,
        dueDate: {
          gte: query.from ? dateToDb(query.from) : undefined,
          lte: query.to ? dateToDb(query.to) : undefined,
        },
      },
      include: occurrenceInclude,
      // Finished work is read newest-first (history), open work soonest-first.
      orderBy: { dueDate: query.status === 'PENDING' || !query.status ? 'asc' : 'desc' },
      take: query.limit,
    })
    return { occurrences: occurrences.map(presentOccurrence) }
  })

  // Touches exactly one occurrence, never the rest of the series.
  app.post('/chore-occurrences/:id/complete', async (request) => {
    const occurrence = await findOccurrence(request)
    const userId = currentUser(request).id
    const chore = await prisma.chore.findUniqueOrThrow({ where: { id: occurrence.choreId } })
    const today = todayIn(currentHousehold(request).timezone)
    const due = dateFromDb(occurrence.dueDate)
    // Otherwise weeks of future points could be collected with a few taps.
    if (chore.recurrence !== 'NONE' && due >= addDays(today, 7)) throw conflict('too_early')
    const awarded = pointsFor(chore.points, due, today)
    const before = await memberPoints(householdId(request), userId)
    await prisma.$transaction(async (tx) => {
      const { count } = await tx.choreAssignment.updateMany({
        where: { id: occurrence.id, status: { not: 'COMPLETED' } },
        data: { status: 'COMPLETED' },
      })
      if (count === 1) {
        await tx.choreCompletion.create({
          data: { assignmentId: occurrence.id, completedById: userId, points: awarded },
        })
      }
    })
    live.publish(householdId(request), 'chores')
    const after = await memberPoints(householdId(request), userId)
    return {
      occurrence: await fetchPresented(occurrence.id),
      earned: after - before,
      level: levelFor(after).level,
      leveledUp: levelFor(after).level > levelFor(before).level,
    }
  })

  app.post('/chore-occurrences/:id/reopen', async (request) => {
    const occurrence = await findOccurrence(request)
    await prisma.$transaction([
      prisma.choreCompletion.deleteMany({ where: { assignmentId: occurrence.id } }),
      prisma.choreAssignment.update({
        where: { id: occurrence.id },
        data: { status: 'PENDING' },
      }),
    ])
    live.publish(householdId(request), 'chores')
    return { occurrence: await fetchPresented(occurrence.id) }
  })

  app.post('/chore-occurrences/:id/skip', async (request) => {
    const occurrence = await findOccurrence(request)
    if (occurrence.status === 'COMPLETED') throw conflict('already_completed')
    await prisma.choreAssignment.update({
      where: { id: occurrence.id },
      data: { status: 'SKIPPED' },
    })
    live.publish(householdId(request), 'chores')
    return { occurrence: await fetchPresented(occurrence.id) }
  })

  app.patch('/chore-occurrences/:id', async (request) => {
    const occurrence = await findOccurrence(request)
    const { assigneeId } = parse(reassignSchema, request.body)
    if (assigneeId) await assertMembers(householdId(request), [assigneeId])
    await prisma.choreAssignment.update({
      where: { id: occurrence.id },
      data: { assigneeId },
    })
    live.publish(householdId(request), 'chores')
    return { occurrence: await fetchPresented(occurrence.id) }
  })
}

