import { verify } from '@node-rs/argon2'
import type { FastifyInstance } from 'fastify'
import { z } from 'zod'
import { env } from '../../config/env.js'
import { AppError, conflict } from '../../lib/errors.js'
import { prisma } from '../../lib/prisma.js'
import { parse, shortText } from '../../lib/validate.js'
import { currentUser, endOtherSessions, requireAuth } from '../../middleware/auth.js'
import { AVATAR_COLORS, hashPassword, LOCALES, presentUser, username } from './service.js'
import { Prisma } from '@prisma/client'

const updateSchema = z.object({
  displayName: shortText(60).optional(),
  username: username.optional(),
  avatarColor: z.enum(AVATAR_COLORS).optional(),
  locale: z.enum(LOCALES).optional(),
})
const passwordSchema = z.object({
  currentPassword: z.string().min(1).max(200),
  newPassword: z.string().min(8).max(200),
})

export async function userRoutes(app: FastifyInstance) {
  app.addHook('preHandler', requireAuth)

  app.patch('/', async (request) => {
    const data = parse(updateSchema, request.body)
    try {
      const user = await prisma.user.update({ where: { id: currentUser(request).id }, data })
      return { user: presentUser(user) }
    } catch (error) {
      if (error instanceof Prisma.PrismaClientKnownRequestError && error.code === 'P2002') {
        throw conflict('username_taken')
      }
      throw error
    }
  })

  app.post(
    '/password',
    { config: { rateLimit: { max: env.RATE_LIMIT_AUTH_MAX, timeWindow: '1 minute' } } },
    async (request, reply) => {
      const body = parse(passwordSchema, request.body)
      const user = currentUser(request)
      if (!(await verify(user.passwordHash, body.currentPassword))) {
        throw new AppError(400, 'wrong_password')
      }
      await prisma.user.update({
        where: { id: user.id },
        data: { passwordHash: await hashPassword(body.newPassword) },
      })
      await endOtherSessions(request, user.id)
      return reply.code(204).send()
    },
  )
}
