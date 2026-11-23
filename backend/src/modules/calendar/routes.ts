import type { Prisma } from '@prisma/client'
import type { FastifyInstance, FastifyRequest } from 'fastify'
import { z } from 'zod'
import { addDays, dateToDb, startOfDayInstant, wallClockToInstant } from '../../lib/dates.js'
import { badRequest, notFound } from '../../lib/errors.js'
import { live } from '../../lib/live.js'
import { prisma } from '../../lib/prisma.js'
import { userSummary } from '../../lib/users.js'
import { idList, idParam, isoDate, optionalText, parse, shortText } from '../../lib/validate.js'
import {
  assertMembers,
  assertOwnerOrAdmin,
  currentHousehold,
  currentUser,
} from '../../middleware/auth.js'
import { occurrenceInclude, presentOccurrence } from '../chores/routes.js'
import { ensureOccurrences } from '../chores/service.js'

const wallClock = z.string().regex(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/)

const eventSchema = z.object({
  title: shortText(120),
  description: optionalText(2000),
  category: z.enum(['CHORE', 'WG', 'OTHER']).default('WG'),
  /** Wall-clock time in the household's zone. */
  startsAt: wallClock,
  endsAt: wallClock.nullish(),
  allDay: z.boolean().default(false),
  participantIds: idList.default([]),
})
const rangeQuery = z.object({ from: isoDate, to: isoDate })

export const eventInclude = {
  createdBy: userSummary,
  participants: { include: { user: userSummary } },
} satisfies Prisma.CalendarEventInclude

type Row = Prisma.CalendarEventGetPayload<{ include: typeof eventInclude }>

export function presentEvent(event: Row) {
  return {
    id: event.id,
    title: event.title,
    description: event.description,
    category: event.category,
    startsAt: event.startsAt,
    endsAt: event.endsAt,
    allDay: event.allDay,
    createdBy: event.createdBy,
    participants: event.participants.map((participant) => participant.user),
  }
}

function toEventData(body: z.output<typeof eventSchema>, timezone: string) {
  const startsAt = wallClockToInstant(body.startsAt, timezone)
  const endsAt = body.endsAt ? wallClockToInstant(body.endsAt, timezone) : null
  if (!startsAt || (body.endsAt && !endsAt)) throw badRequest('invalid_date')
  if (endsAt && endsAt < startsAt) throw badRequest('end_before_start')
  return {
    title: body.title,
    description: body.description,
    category: body.category,
    allDay: body.allDay,
    startsAt,
    endsAt,
  }
}

export async function calendarRoutes(app: FastifyInstance) {
  async function findEvent(request: FastifyRequest) {
    const { id } = parse(idParam, request.params)
    const event = await prisma.calendarEvent.findFirst({
      where: { id, householdId: currentHousehold(request).id },
    })
    if (!event) throw notFound()
    return event
  }

  /** Events and chore occurrences in [from, to], both calendar dates in the household's zone. */
  app.get('/calendar', async (request) => {
    const { from, to } = parse(rangeQuery, request.query)
    if (from > to) throw badRequest('invalid_range')
    const household = currentHousehold(request)
    await ensureOccurrences(household.id, household.timezone, to)
    const rangeStart = startOfDayInstant(from, household.timezone)
    const rangeEnd = startOfDayInstant(addDays(to, 1), household.timezone)
    const [events, occurrences] = await Promise.all([
      prisma.calendarEvent.findMany({
        where: {
          householdId: household.id,
          startsAt: { lt: rangeEnd },
          OR: [{ startsAt: { gte: rangeStart } }, { endsAt: { gte: rangeStart } }],
        },
        include: eventInclude,
        orderBy: { startsAt: 'asc' },
      }),
      prisma.choreAssignment.findMany({
        where: {
          householdId: household.id,
          dueDate: { gte: dateToDb(from), lte: dateToDb(to) },
        },
        include: occurrenceInclude,
        orderBy: { dueDate: 'asc' },
      }),
    ])
    return {
      events: events.map(presentEvent),
      occurrences: occurrences.map(presentOccurrence),
    }
  })

  app.post('/events', async (request, reply) => {
    const body = parse(eventSchema, request.body)
    const household = currentHousehold(request)
    await assertMembers(household.id, body.participantIds)
    const event = await prisma.calendarEvent.create({
      data: {
        ...toEventData(body, household.timezone),
        householdId: household.id,
        createdById: currentUser(request).id,
        participants: { create: body.participantIds.map((userId) => ({ userId })) },
      },
      include: eventInclude,
    })
    live.publish(household.id, 'calendar')
    return reply.code(201).send({ event: presentEvent(event) })
  })

  app.patch('/events/:id', async (request) => {
    const existing = await findEvent(request)
    assertOwnerOrAdmin(request, existing.createdById)
    const body = parse(eventSchema, request.body)
    const household = currentHousehold(request)
    await assertMembers(household.id, body.participantIds)
    const event = await prisma.calendarEvent.update({
      where: { id: existing.id },
      data: {
        ...toEventData(body, household.timezone),
        participants: {
          deleteMany: {},
          create: body.participantIds.map((userId) => ({ userId })),
        },
      },
      include: eventInclude,
    })
    live.publish(household.id, 'calendar')
    return { event: presentEvent(event) }
  })

  app.delete('/events/:id', async (request, reply) => {
    const existing = await findEvent(request)
    assertOwnerOrAdmin(request, existing.createdById)
    await prisma.calendarEvent.delete({ where: { id: existing.id } })
    live.publish(currentHousehold(request).id, 'calendar')
    return reply.code(204).send()
  })
}
