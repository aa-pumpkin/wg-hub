import type { FastifyInstance, FastifyRequest } from 'fastify'
import { randomBytes } from 'node:crypto'
import { z } from 'zod'
import { env } from '../../config/env.js'
import { isValidTimeZone } from '../../lib/dates.js'
import { conflict, notFound, AppError } from '../../lib/errors.js'
import { live } from '../../lib/live.js'
import { prisma } from '../../lib/prisma.js'
import { idParam, optionalText, parse, shortText } from '../../lib/validate.js'
import {
  currentHousehold,
  assertOwnerOrAdmin,
  currentUser,
  requireAdmin,
  requireAuth,
} from '../../middleware/auth.js'
import { applyFreeze, regenerateMainPlan, replanAround } from '../chores/service.js'
import { createPasswordReset } from '../auth/reset.js'
import { removeMember } from './service.js'

const timezone = z.string().refine(isValidTimeZone, { message: 'invalid_timezone' })
const icon = z.string().trim().min(1).max(16)

const createSchema = z.object({
  name: shortText(80),
  address: optionalText(200),
  icon: icon.optional(),
  timezone: timezone.optional(),
})
const updateSchema = z.object({
  name: shortText(80).optional(),
  address: optionalText(200),
  icon: icon.optional(),
  timezone: timezone.optional(),
  beerPriceCents: z.number().int().min(1).max(10_000).optional(),
  dishwasherTarget: z.number().int().min(1).max(200).nullable().optional(),
})
const roleSchema = z.object({ role: z.enum(['ADMIN', 'MEMBER']) })
const freezeSchema = z.object({ frozen: z.boolean() })
const invitationSchema = z.object({
  /** null means the link stays valid until revoked. */
  expiresInDays: z.number().int().min(1).max(365).nullable(),
})
const codeParam = z.object({ code: z.string().min(1).max(64) })

const memberSelect = {
  role: true,
  joinedAt: true,
  frozenAt: true,
  user: { select: { id: true, displayName: true, username: true, avatarColor: true } },
} as const

function presentMember(member: {
  role: string
  joinedAt: Date
  frozenAt: Date | null
  user: { id: string; displayName: string; username: string; avatarColor: string }
}) {
  return {
    ...member.user,
    role: member.role,
    joinedAt: member.joinedAt,
    frozen: member.frozenAt !== null,
  }
}

function isInvitationUsable(invitation: { revokedAt: Date | null; expiresAt: Date | null }) {
  return !invitation.revokedAt && (!invitation.expiresAt || invitation.expiresAt > new Date())
}

/** Routes outside a household: listing, creating, and public invitation lookup. */
export async function householdRootRoutes(app: FastifyInstance) {
  app.get('/households', { preHandler: requireAuth }, async (request) => {
    const memberships = await prisma.householdMember.findMany({
      where: { userId: currentUser(request).id },
      include: { household: { include: { _count: { select: { members: true } } } } },
      orderBy: { joinedAt: 'asc' },
    })
    return {
      households: memberships.map(({ household, role }) => ({
        id: household.id,
        name: household.name,
        icon: household.icon,
        role,
        memberCount: household._count.members,
      })),
    }
  })

  app.post('/households', { preHandler: requireAuth }, async (request, reply) => {
    const body = parse(createSchema, request.body)
    const household = await prisma.household.create({
      data: {
        ...body,
        members: { create: { userId: currentUser(request).id, role: 'ADMIN', rotationPosition: 0 } },
      },
    })
    return reply.code(201).send({ household })
  })

  const invitationLimit = {
    rateLimit: { max: env.RATE_LIMIT_AUTH_MAX, timeWindow: '1 minute' },
  }

  // Public, so someone without an account sees what they are about to join.
  app.get('/invitations/:code', { config: invitationLimit }, async (request) => {
    const { code } = parse(codeParam, request.params)
    const invitation = await prisma.householdInvitation.findUnique({
      where: { code },
      include: {
        household: { select: { name: true, icon: true, _count: { select: { members: true } } } },
      },
    })
    if (!invitation || !isInvitationUsable(invitation)) throw new AppError(410, 'invitation_invalid')
    return {
      invitation: {
        householdName: invitation.household.name,
        householdIcon: invitation.household.icon,
        memberCount: invitation.household._count.members,
      },
    }
  })

  app.post(
    '/invitations/:code/accept',
    { config: invitationLimit, preHandler: requireAuth },
    async (request) => {
      const { code } = parse(codeParam, request.params)
      const userId = currentUser(request).id
      const result = await prisma.$transaction(async (tx) => {
        const invitation = await tx.householdInvitation.findUnique({ where: { code } })
        if (!invitation || !isInvitationUsable(invitation)) {
          throw new AppError(410, 'invitation_invalid')
        }
        const existing = await tx.householdMember.findUnique({
          where: { householdId_userId: { householdId: invitation.householdId, userId } },
        })
        if (!existing) {
          const last = await tx.householdMember.aggregate({
            where: { householdId: invitation.householdId },
            _max: { rotationPosition: true },
          })
          await tx.householdMember.create({
            data: {
              householdId: invitation.householdId,
              userId,
              role: 'MEMBER',
              rotationPosition: (last._max.rotationPosition ?? -1) + 1,
            },
          })
          await tx.householdInvitation.update({
            where: { id: invitation.id },
            data: { useCount: { increment: 1 } },
          })
        }
        return { householdId: invitation.householdId, joined: !existing }
      })
      if (result.joined) {
        // The current week stays as announced; the new person joins from next week.
        const { timezone } = await prisma.household.findUniqueOrThrow({
          where: { id: result.householdId },
        })
        await regenerateMainPlan(result.householdId, timezone, 'next-week')
      }
      const householdId = result.householdId
      live.publish(householdId, 'members')
      live.publish(householdId, 'chores')
      return { householdId }
    },
  )
}

/** Routes inside a household. Registered behind requireMember. */
export async function householdRoutes(app: FastifyInstance) {
  const householdId = (request: FastifyRequest) => currentHousehold(request).id

  app.get('/', async (request) => {
    const members = await prisma.householdMember.findMany({
      where: { householdId: householdId(request) },
      select: memberSelect,
      orderBy: { joinedAt: 'asc' },
    })
    return {
      household: currentHousehold(request),
      role: request.role,
      members: members.map(presentMember),
    }
  })

  app.patch('/', { preHandler: requireAdmin }, async (request) => {
    const body = parse(updateSchema, request.body)
    const before = currentHousehold(request)
    const household = await prisma.household.update({
      where: { id: before.id },
      data: {
        ...body,
        // Turning the counter on starts a round; people who join later are not struck for it.
        ...(body.dishwasherTarget && before.dishwasherTarget === null
          ? { dishwasherCycleStart: new Date() }
          : {}),
      },
    })
    live.publish(household.id, 'household')
    return { household }
  })

  app.delete('/', { preHandler: requireAdmin }, async (request, reply) => {
    const id = householdId(request)
    await prisma.household.delete({ where: { id } })
    // Everyone still connected refetches, gets a 404 and leaves the deleted WG.
    live.publish(id, 'household')
    return reply.code(204).send()
  })

  app.post('/leave', async (request, reply) => {
    const household = currentHousehold(request)
    await removeMember(household.id, currentUser(request).id, household.timezone)
    return reply.code(204).send()
  })

  app.delete('/members/:id', { preHandler: requireAdmin }, async (request, reply) => {
    const { id } = parse(idParam, request.params)
    const household = currentHousehold(request)
    const member = await prisma.householdMember.findUnique({
      where: { householdId_userId: { householdId: household.id, userId: id } },
    })
    if (!member) throw notFound()
    await removeMember(household.id, id, household.timezone)
    return reply.code(204).send()
  })

  app.patch('/members/:id', { preHandler: requireAdmin }, async (request) => {
    const { id } = parse(idParam, request.params)
    const { role } = parse(roleSchema, request.body)
    const id_ = { householdId: householdId(request), userId: id }
    await prisma.$transaction(async (tx) => {
      const member = await tx.householdMember.findUnique({ where: { householdId_userId: id_ } })
      if (!member) throw notFound()
      if (member.role === 'ADMIN' && role === 'MEMBER') {
        const admins = await tx.householdMember.count({
          where: { householdId: id_.householdId, role: 'ADMIN' },
        })
        if (admins === 1) throw conflict('last_admin')
      }
      await tx.householdMember.update({ where: { householdId_userId: id_ }, data: { role } })
    })
    live.publish(id_.householdId, 'members')
    return { ok: true }
  })

  /** Someone is away for a while: no tasks until they are back. Anyone may do this for themselves. */
  app.post('/members/:id/freeze', async (request) => {
    const { id } = parse(idParam, request.params)
    assertOwnerOrAdmin(request, id)
    const { frozen } = parse(freezeSchema, request.body)
    const household = currentHousehold(request)
    const key = { householdId_userId: { householdId: household.id, userId: id } }
    const member = await prisma.householdMember.findUnique({ where: key })
    if (!member) throw notFound()
    if ((member.frozenAt !== null) !== frozen) {
      await prisma.householdMember.update({
        where: key,
        data: { frozenAt: frozen ? new Date() : null },
      })
      if (frozen) await applyFreeze(household.id, id, household.timezone)
      else await replanAround(household.id, id, household.timezone)
    }
    live.publish(household.id, 'members')
    live.publish(household.id, 'chores')
    return { frozen }
  })

  /**
   * There is no email to send a reset to, so an admin creates a one-time link and hands it
   * over in person. The token itself is only ever returned here, never stored in the clear.
   */
  app.post('/members/:id/password-reset', { preHandler: requireAdmin }, async (request, reply) => {
    const { id } = parse(idParam, request.params)
    const member = await prisma.householdMember.findUnique({
      where: { householdId_userId: { householdId: householdId(request), userId: id } },
    })
    if (!member) throw notFound()
    const token = await createPasswordReset(id, currentUser(request).id)
    return reply.code(201).send(token)
  })

  app.get('/members/stats', async (request) => {
    const id = householdId(request)
    const [open, done] = await Promise.all([
      prisma.choreAssignment.groupBy({
        by: ['assigneeId'],
        where: { householdId: id, status: 'PENDING', chore: { archivedAt: null } },
        _count: true,
      }),
      prisma.choreCompletion.groupBy({
        by: ['completedById'],
        where: { assignment: { householdId: id } },
        _count: true,
      }),
    ])
    return {
      open: Object.fromEntries(open.map((row) => [row.assigneeId, row._count])),
      completed: Object.fromEntries(done.map((row) => [row.completedById, row._count])),
    }
  })

  app.get('/invitations', { preHandler: requireAdmin }, async (request) => {
    const invitations = await prisma.householdInvitation.findMany({
      where: {
        householdId: householdId(request),
        revokedAt: null,
        OR: [{ expiresAt: null }, { expiresAt: { gt: new Date() } }],
      },
      orderBy: { createdAt: 'desc' },
    })
    return { invitations }
  })

  app.post('/invitations', { preHandler: requireAdmin }, async (request, reply) => {
    const { expiresInDays } = parse(invitationSchema, request.body)
    const invitation = await prisma.householdInvitation.create({
      data: {
        householdId: householdId(request),
        createdById: currentUser(request).id,
        code: randomBytes(12).toString('base64url'),
        expiresAt: expiresInDays ? new Date(Date.now() + expiresInDays * 86_400_000) : null,
      },
    })
    return reply.code(201).send({ invitation })
  })

  app.delete('/invitations/:id', { preHandler: requireAdmin }, async (request, reply) => {
    const { id } = parse(idParam, request.params)
    const { count } = await prisma.householdInvitation.updateMany({
      where: { id, householdId: householdId(request), revokedAt: null },
      data: { revokedAt: new Date() },
    })
    if (count === 0) throw notFound()
    return reply.code(204).send()
  })
}
