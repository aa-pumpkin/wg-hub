import type { Prisma } from '@prisma/client'
import type { FastifyInstance, FastifyRequest } from 'fastify'
import { z } from 'zod'
import { notFound } from '../../lib/errors.js'
import { live } from '../../lib/live.js'
import { prisma } from '../../lib/prisma.js'
import { userSummary } from '../../lib/users.js'
import { idParam, optionalText, parse, shortText } from '../../lib/validate.js'
import { dateToDb, todayIn } from '../../lib/dates.js'
import { assertOwnerOrAdmin, currentHousehold, currentUser } from '../../middleware/auth.js'
import { splitEqually } from '../expenses/split.js'

const category = z.enum([
  'PRODUCE',
  'DAIRY',
  'MEAT',
  'BAKERY',
  'PANTRY',
  'FROZEN',
  'DRINKS',
  'HOUSEHOLD',
  'HYGIENE',
  'OTHER',
])
const quantity = z.number().positive().max(99_999).multipleOf(0.01).nullish()

const createSchema = z.object({
  name: shortText(120),
  quantity,
  unit: optionalText(20),
  category: category.default('OTHER'),
  note: optionalText(300),
  personal: z.boolean().default(false),
})
const updateSchema = z.object({
  name: shortText(120).optional(),
  quantity,
  unit: optionalText(20),
  category: category.optional(),
  note: optionalText(300),
  personal: z.boolean().optional(),
})
const statusSchema = z.object({
  status: z.enum(['PENDING', 'BOUGHT']),
  /** What it cost. Without a price it is simply ticked off and nobody owes anything. */
  priceCents: z.number().int().positive().max(100_000_000).optional(),
})

export const shoppingInclude = {
  createdBy: userSummary,
  boughtBy: userSummary,
  expense: { select: { id: true, amountCents: true } },
} satisfies Prisma.ShoppingItemInclude

type Row = Prisma.ShoppingItemGetPayload<{ include: typeof shoppingInclude }>

export function presentItem(item: Row) {
  return {
    id: item.id,
    name: item.name,
    quantity: item.quantity === null ? null : item.quantity.toNumber(),
    unit: item.unit,
    category: item.category,
    note: item.note,
    personal: item.personal,
    expense: item.expense,
    status: item.status,
    createdBy: item.createdBy,
    boughtBy: item.boughtBy,
    boughtAt: item.boughtAt,
    createdAt: item.createdAt,
  }
}

async function sharesFor(
  tx: Prisma.TransactionClient,
  item: { personal: boolean; createdById: string },
  buyerId: string,
  priceCents: number,
  householdId: string,
) {
  if (item.personal) {
    // Bought for yourself means no debt; a requester who left the WG can no longer settle.
    if (item.createdById === buyerId) return []
    const requester = await tx.householdMember.findUnique({
      where: { householdId_userId: { householdId, userId: item.createdById } },
    })
    return requester ? [{ userId: item.createdById, shareCents: priceCents }] : []
  }
  const members = await tx.householdMember.findMany({
    where: { householdId, OR: [{ frozenAt: null }, { userId: buyerId }] },
    orderBy: { joinedAt: 'asc' },
    select: { userId: true },
  })
  return splitEqually(
    priceCents,
    members.map((member) => member.userId),
  )
}

export async function shoppingRoutes(app: FastifyInstance) {
  const householdId = (request: FastifyRequest) => currentHousehold(request).id

  async function assertItem(request: FastifyRequest) {
    const { id } = parse(idParam, request.params)
    const item = await prisma.shoppingItem.findFirst({
      where: { id, householdId: householdId(request) },
      select: { id: true },
    })
    if (!item) throw notFound()
    return item.id
  }

  app.get('/shopping', async (request) => {
    const items = await prisma.shoppingItem.findMany({
      where: { householdId: householdId(request) },
      include: shoppingInclude,
      orderBy: [{ boughtAt: 'desc' }, { createdAt: 'asc' }],
    })
    return { items: items.map(presentItem) }
  })

  app.post('/shopping', async (request, reply) => {
    const body = parse(createSchema, request.body)
    const item = await prisma.shoppingItem.create({
      data: { ...body, householdId: householdId(request), createdById: currentUser(request).id },
      include: shoppingInclude,
    })
    live.publish(householdId(request), 'shopping')
    return reply.code(201).send({ item: presentItem(item) })
  })

  app.patch('/shopping/:id', async (request) => {
    const id = await assertItem(request)
    const body = parse(updateSchema, request.body)
    const item = await prisma.shoppingItem.update({
      where: { id },
      data: body,
      include: shoppingInclude,
    })
    live.publish(householdId(request), 'shopping')
    return { item: presentItem(item) }
  })

  /**
   * Whoever ticks an item off paid for it. With a price this creates the expense in one step:
   * a WG item is split between everyone who is not frozen, a personal one is owed by the
   * person who asked for it. Putting it back on the list takes the expense away again.
   */
  app.post('/shopping/:id/status', async (request) => {
    const id = await assertItem(request)
    const { status, priceCents } = parse(statusSchema, request.body)
    const household = currentHousehold(request)
    const buyerId = currentUser(request).id
    await prisma.$transaction(async (tx) => {
      const item = await tx.shoppingItem.findUniqueOrThrow({ where: { id } })
      if (status === item.status) return

      if (status === 'PENDING') {
        // Someone else's money: only they or an admin may cancel it.
        if (item.expenseId) assertOwnerOrAdmin(request, item.boughtById ?? '')
        await tx.shoppingItem.update({
          where: { id },
          data: { status, boughtAt: null, boughtById: null, expenseId: null },
        })
        if (item.expenseId) await tx.expense.delete({ where: { id: item.expenseId } })
        return
      }

      const shares = priceCents
        ? await sharesFor(tx, item, buyerId, priceCents, household.id)
        : []
      const expense =
        shares.length > 0
          ? await tx.expense.create({
              data: {
                householdId: household.id,
                description: item.name,
                amountCents: priceCents!,
                paidById: buyerId,
                createdById: buyerId,
                date: dateToDb(todayIn(household.timezone)),
                participants: { create: shares },
              },
            })
          : null
      await tx.shoppingItem.update({
        where: { id },
        data: {
          status,
          boughtAt: new Date(),
          boughtById: buyerId,
          expenseId: expense?.id ?? null,
        },
      })
    })
    live.publish(household.id, 'shopping')
    live.publish(household.id, 'expenses')
    return {
      item: presentItem(
        await prisma.shoppingItem.findUniqueOrThrow({ where: { id }, include: shoppingInclude }),
      ),
    }
  })

  app.delete('/shopping/:id', async (request, reply) => {
    const id = await assertItem(request)
    await prisma.shoppingItem.delete({ where: { id } })
    live.publish(householdId(request), 'shopping')
    return reply.code(204).send()
  })

  app.post('/shopping/clear-bought', async (request) => {
    const { count } = await prisma.shoppingItem.deleteMany({
      where: { householdId: householdId(request), status: 'BOUGHT' },
    })
    live.publish(householdId(request), 'shopping')
    return { count }
  })
}
