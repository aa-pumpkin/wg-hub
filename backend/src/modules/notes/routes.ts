import type { Prisma } from '@prisma/client'
import type { FastifyInstance, FastifyRequest } from 'fastify'
import { z } from 'zod'
import { forbidden, notFound } from '../../lib/errors.js'
import { live } from '../../lib/live.js'
import { prisma } from '../../lib/prisma.js'
import { userSummary } from '../../lib/users.js'
import { idParam, parse } from '../../lib/validate.js'
import { assertOwnerOrAdmin, currentHousehold, currentUser } from '../../middleware/auth.js'

const color = z.enum(['DEFAULT', 'YELLOW', 'GREEN', 'BLUE', 'PINK', 'LILAC'])
const content = z.string().trim().min(1).max(2000)

const createSchema = z.object({
  content,
  color: color.default('DEFAULT'),
  pinned: z.boolean().default(false),
})
const updateSchema = z.object({ content: content.optional(), color: color.optional() })
const pinSchema = z.object({ pinned: z.boolean() })
const archiveSchema = z.object({ archived: z.boolean() })
const listQuery = z.object({ archived: z.enum(['true', 'false']).default('false') })

export const noteInclude = { author: userSummary } satisfies Prisma.NoteInclude

export async function noteRoutes(app: FastifyInstance) {
  const householdId = (request: FastifyRequest) => currentHousehold(request).id

  async function findNote(request: FastifyRequest) {
    const { id } = parse(idParam, request.params)
    const note = await prisma.note.findFirst({ where: { id, householdId: householdId(request) } })
    if (!note) throw notFound()
    return note
  }

  app.get('/notes', async (request) => {
    const { archived } = parse(listQuery, request.query)
    const notes = await prisma.note.findMany({
      where: {
        householdId: householdId(request),
        archivedAt: archived === 'true' ? { not: null } : null,
      },
      include: noteInclude,
      orderBy: [{ pinned: 'desc' }, { createdAt: 'desc' }],
      take: 200,
    })
    return { notes }
  })

  app.post('/notes', async (request, reply) => {
    const body = parse(createSchema, request.body)
    const note = await prisma.note.create({
      data: { ...body, householdId: householdId(request), authorId: currentUser(request).id },
      include: noteInclude,
    })
    live.publish(householdId(request), 'notes')
    return reply.code(201).send({ note })
  })

  // Text belongs to its author only, not even an admin rewrites someone's words.
  app.patch('/notes/:id', async (request) => {
    const existing = await findNote(request)
    if (existing.authorId !== currentUser(request).id) throw forbidden()
    const body = parse(updateSchema, request.body)
    const note = await prisma.note.update({
      where: { id: existing.id },
      data: body,
      include: noteInclude,
    })
    live.publish(householdId(request), 'notes')
    return { note }
  })

  // Pinning is a shared-board action: anyone can flag something as important.
  app.post('/notes/:id/pin', async (request) => {
    const existing = await findNote(request)
    const { pinned } = parse(pinSchema, request.body)
    const note = await prisma.note.update({
      where: { id: existing.id },
      data: { pinned },
      include: noteInclude,
    })
    live.publish(householdId(request), 'notes')
    return { note }
  })

  app.post('/notes/:id/archive', async (request) => {
    const existing = await findNote(request)
    assertOwnerOrAdmin(request, existing.authorId)
    const { archived } = parse(archiveSchema, request.body)
    const note = await prisma.note.update({
      where: { id: existing.id },
      data: { archivedAt: archived ? new Date() : null, pinned: archived ? false : existing.pinned },
      include: noteInclude,
    })
    live.publish(householdId(request), 'notes')
    return { note }
  })

  app.delete('/notes/:id', async (request, reply) => {
    const existing = await findNote(request)
    assertOwnerOrAdmin(request, existing.authorId)
    await prisma.note.delete({ where: { id: existing.id } })
    live.publish(householdId(request), 'notes')
    return reply.code(204).send()
  })
}
