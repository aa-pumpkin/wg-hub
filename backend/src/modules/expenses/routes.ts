import type { Prisma } from '@prisma/client'
import type { FastifyInstance, FastifyRequest } from 'fastify'
import { z } from 'zod'
import { dateFromDb, dateToDb, todayIn } from '../../lib/dates.js'
import { badRequest, forbidden, notFound } from '../../lib/errors.js'
import { live } from '../../lib/live.js'
import { prisma } from '../../lib/prisma.js'
import { userSummary } from '../../lib/users.js'
import { idParam, isoDate, optionalText, parse, shortText } from '../../lib/validate.js'
import {
  assertMembers,
  assertOwnerOrAdmin,
  currentHousehold,
  currentUser,
} from '../../middleware/auth.js'
import { beersFor, suggestPayments } from './balances.js'
import { householdBalances } from './service.js'
import { splitEqually, validateCustomShares } from './split.js'

const cents = z.number().int().positive().max(100_000_000)

const expenseSchema = z.object({
  description: shortText(120),
  amountCents: cents,
  paidById: z.string().min(1).max(64),
  date: isoDate,
  participants: z
    .array(
      z.object({
        userId: z.string().min(1).max(64),
        /** Required for custom splits, ignored for equal ones. */
        shareCents: z.number().int().min(0).max(100_000_000).optional(),
      }),
    )
    .min(1)
    .max(100)
    .refine((list) => new Set(list.map((p) => p.userId)).size === list.length, {
      message: 'duplicate',
    }),
  splitMode: z.enum(['EQUAL', 'CUSTOM']).default('EQUAL'),
})
const settlementSchema = z.object({
  fromUserId: z.string().min(1).max(64),
  toUserId: z.string().min(1).max(64),
  amountCents: cents,
  date: isoDate,
  note: optionalText(200),
  /** Forgiving a debt, unlike recording a payment, is the creditor's call alone. */
  kind: z.enum(['PAYMENT', 'FORGIVEN']).default('PAYMENT'),
})
const beerRequestSchema = z.object({
  fromUserId: z.string().min(1).max(64),
  amountCents: cents,
})

const expenseInclude = {
  paidBy: userSummary,
  participants: { include: { user: userSummary } },
} satisfies Prisma.ExpenseInclude

type ExpenseRow = Prisma.ExpenseGetPayload<{ include: typeof expenseInclude }>

function presentExpense(expense: ExpenseRow) {
  return {
    id: expense.id,
    description: expense.description,
    amountCents: expense.amountCents,
    date: dateFromDb(expense.date),
    splitMode: expense.splitMode,
    paidBy: expense.paidBy,
    createdById: expense.createdById,
    participants: expense.participants.map((participant) => ({
      user: participant.user,
      shareCents: participant.shareCents,
    })),
  }
}

function resolveShares(body: z.output<typeof expenseSchema>) {
  if (body.splitMode === 'EQUAL') {
    return splitEqually(
      body.amountCents,
      body.participants.map((participant) => participant.userId),
    )
  }
  return validateCustomShares(
    body.amountCents,
    body.participants.map((participant) => {
      if (participant.shareCents === undefined) throw badRequest('shares_do_not_add_up')
      return { userId: participant.userId, shareCents: participant.shareCents }
    }),
  )
}

export async function expenseRoutes(app: FastifyInstance) {
  const householdId = (request: FastifyRequest) => currentHousehold(request).id

  async function findExpense(request: FastifyRequest) {
    const { id } = parse(idParam, request.params)
    const expense = await prisma.expense.findFirst({
      where: { id, householdId: householdId(request) },
    })
    if (!expense) throw notFound()
    return expense
  }

  app.get('/expenses', async (request) => {
    const expenses = await prisma.expense.findMany({
      where: { householdId: householdId(request) },
      include: expenseInclude,
      orderBy: [{ date: 'desc' }, { createdAt: 'desc' }],
      take: 500,
    })
    return { expenses: expenses.map(presentExpense) }
  })

  app.post('/expenses', async (request, reply) => {
    const body = parse(expenseSchema, request.body)
    const id = householdId(request)
    const shares = resolveShares(body)
    await assertMembers(id, [body.paidById, ...shares.map((share) => share.userId)])
    // Nested write: the expense and its shares are stored atomically.
    const expense = await prisma.expense.create({
      data: {
        householdId: id,
        description: body.description,
        amountCents: body.amountCents,
        paidById: body.paidById,
        date: dateToDb(body.date),
        splitMode: body.splitMode,
        createdById: currentUser(request).id,
        participants: { create: shares },
      },
      include: expenseInclude,
    })
    live.publish(id, 'expenses')
    return reply.code(201).send({ expense: presentExpense(expense) })
  })

  app.patch('/expenses/:id', async (request) => {
    const existing = await findExpense(request)
    assertOwnerOrAdmin(request, existing.createdById)
    const body = parse(expenseSchema, request.body)
    const id = householdId(request)
    const shares = resolveShares(body)
    await assertMembers(id, [body.paidById, ...shares.map((share) => share.userId)])
    const expense = await prisma.expense.update({
      where: { id: existing.id },
      data: {
        description: body.description,
        amountCents: body.amountCents,
        paidById: body.paidById,
        date: dateToDb(body.date),
        splitMode: body.splitMode,
        participants: { deleteMany: {}, create: shares },
      },
      include: expenseInclude,
    })
    live.publish(id, 'expenses')
    return { expense: presentExpense(expense) }
  })

  app.delete('/expenses/:id', async (request, reply) => {
    const existing = await findExpense(request)
    assertOwnerOrAdmin(request, existing.createdById)
    await prisma.expense.delete({ where: { id: existing.id } })
    live.publish(householdId(request), 'expenses')
    return reply.code(204).send()
  })

  app.get('/balances', async (request) => {
    const id = householdId(request)
    const balances = await householdBalances(id)
    const users = await prisma.user.findMany({
      where: {
        OR: [{ memberships: { some: { householdId: id } } }, { id: { in: [...balances.keys()] } }],
      },
      ...userSummary,
    })
    return {
      balances: users.map((user) => ({ user, cents: balances.get(user.id) ?? 0 })),
      suggestions: suggestPayments(balances),
      beerRequests: await prisma.beerRequest.findMany({
        where: { householdId: id },
        select: { id: true, fromUserId: true, toUserId: true, amountCents: true, beers: true },
      }),
    }
  })

  /** "Pay me in beer instead." The person owed asks; nothing changes until they say it arrived. */
  app.post('/beer-requests', async (request, reply) => {
    const body = parse(beerRequestSchema, request.body)
    const household = currentHousehold(request)
    const toUserId = currentUser(request).id
    if (body.fromUserId === toUserId) throw badRequest('same_person')
    await assertMembers(household.id, [body.fromUserId])
    const beers = beersFor(body.amountCents, household.beerPriceCents)
    const key = { householdId: household.id, fromUserId: body.fromUserId, toUserId }
    const beerRequest = await prisma.beerRequest.upsert({
      where: { householdId_fromUserId_toUserId: key },
      create: { ...key, amountCents: body.amountCents, beers },
      update: { amountCents: body.amountCents, beers },
    })
    live.publish(household.id, 'expenses')
    return reply.code(201).send({ id: beerRequest.id, beers })
  })

  async function findBeerRequest(request: FastifyRequest) {
    const { id } = parse(idParam, request.params)
    const beerRequest = await prisma.beerRequest.findFirst({
      where: { id, householdId: householdId(request) },
    })
    if (!beerRequest) throw notFound()
    if (beerRequest.toUserId !== currentUser(request).id) throw forbidden()
    return beerRequest
  }

  app.post('/beer-requests/:id/received', async (request, reply) => {
    const beerRequest = await findBeerRequest(request)
    const household = currentHousehold(request)
    await prisma.$transaction(async (tx) => {
      await tx.$queryRaw`SELECT id FROM "Household" WHERE id = ${household.id} FOR UPDATE`
      const balances = await householdBalances(household.id, tx)
      // Never more than is still owed: the debt may have shrunk since they asked.
      const amountCents = Math.min(
        beerRequest.amountCents,
        -(balances.get(beerRequest.fromUserId) ?? 0),
        balances.get(beerRequest.toUserId) ?? 0,
      )
      if (amountCents > 0) {
        await tx.expenseSettlement.create({
          data: {
            householdId: household.id,
            fromUserId: beerRequest.fromUserId,
            toUserId: beerRequest.toUserId,
            amountCents,
            beers: beerRequest.beers,
            kind: 'BEER',
            date: dateToDb(todayIn(household.timezone)),
            createdById: beerRequest.toUserId,
          },
        })
      }
      await tx.beerRequest.delete({ where: { id: beerRequest.id } })
    })
    live.publish(household.id, 'expenses')
    return reply.code(204).send()
  })

  app.delete('/beer-requests/:id', async (request, reply) => {
    const beerRequest = await findBeerRequest(request)
    await prisma.beerRequest.delete({ where: { id: beerRequest.id } })
    live.publish(householdId(request), 'expenses')
    return reply.code(204).send()
  })

  app.get('/settlements', async (request) => {
    const settlements = await prisma.expenseSettlement.findMany({
      where: { householdId: householdId(request) },
      include: { fromUser: userSummary, toUser: userSummary },
      orderBy: [{ date: 'desc' }, { createdAt: 'desc' }],
      take: 500,
    })
    return {
      settlements: settlements.map((settlement) => ({
        id: settlement.id,
        amountCents: settlement.amountCents,
        date: dateFromDb(settlement.date),
        note: settlement.note,
        kind: settlement.kind,
        beers: settlement.beers,
        fromUser: settlement.fromUser,
        toUser: settlement.toUser,
        createdById: settlement.createdById,
      })),
    }
  })

  app.post('/settlements', async (request, reply) => {
    const body = parse(settlementSchema, request.body)
    if (body.fromUserId === body.toUserId) throw badRequest('same_person')
    if (body.kind === 'FORGIVEN' && body.toUserId !== currentUser(request).id) throw forbidden()
    const id = householdId(request)
    await assertMembers(id, [body.fromUserId, body.toUserId])
    const settlement = await prisma.expenseSettlement.create({
      data: {
        householdId: id,
        fromUserId: body.fromUserId,
        toUserId: body.toUserId,
        amountCents: body.amountCents,
        date: dateToDb(body.date),
        note: body.note,
        kind: body.kind,
        createdById: currentUser(request).id,
      },
    })
    live.publish(id, 'expenses')
    return reply.code(201).send({ id: settlement.id })
  })

  app.delete('/settlements/:id', async (request, reply) => {
    const { id } = parse(idParam, request.params)
    const settlement = await prisma.expenseSettlement.findFirst({
      where: { id, householdId: householdId(request) },
    })
    if (!settlement) throw notFound()
    assertOwnerOrAdmin(request, settlement.createdById)
    await prisma.expenseSettlement.delete({ where: { id } })
    live.publish(householdId(request), 'expenses')
    return reply.code(204).send()
  })
}
