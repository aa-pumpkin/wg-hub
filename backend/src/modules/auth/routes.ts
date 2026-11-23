import { hash, verify } from '@node-rs/argon2'
import { Prisma } from '@prisma/client'
import type { FastifyInstance } from 'fastify'
import { z } from 'zod'
import { env } from '../../config/env.js'
import { AppError, conflict } from '../../lib/errors.js'
import { prisma } from '../../lib/prisma.js'
import { parse, shortText } from '../../lib/validate.js'
import { endSession, requireAuth, startSession } from '../../middleware/auth.js'
import { hashPassword, LOCALES, presentUser, randomAvatarColor, username } from '../users/service.js'
import { usePasswordReset } from './reset.js'

const registerSchema = z.object({
  username,
  displayName: shortText(60),
  password: z.string().min(8).max(200),
  locale: z.enum(LOCALES).optional(),
})
const loginSchema = z.object({
  username: z.string().trim().toLowerCase().min(1).max(60),
  password: z.string().min(1).max(200),
})
const resetSchema = z.object({
  token: z.string().min(1).max(100),
  newPassword: z.string().min(8).max(200),
})

// Verified when the login is unknown, so response time does not reveal which logins exist.
const dummyHash = hash('not-a-real-password')

export async function authRoutes(app: FastifyInstance) {
  const limit = { rateLimit: { max: env.RATE_LIMIT_AUTH_MAX, timeWindow: '1 minute' } }

  app.post('/register', { config: limit }, async (request, reply) => {
    const body = parse(registerSchema, request.body)
    try {
      const user = await prisma.user.create({
        data: {
          username: body.username,
          displayName: body.displayName,
          avatarColor: randomAvatarColor(),
          passwordHash: await hashPassword(body.password),
          locale: body.locale ?? 'de',
        },
      })
      await startSession(request, reply, user.id)
      return reply.code(201).send({ user: presentUser(user) })
    } catch (error) {
      if (error instanceof Prisma.PrismaClientKnownRequestError && error.code === 'P2002') {
        throw conflict('username_taken')
      }
      throw error
    }
  })

  app.post('/login', { config: limit }, async (request, reply) => {
    const body = parse(loginSchema, request.body)
    const user = await prisma.user.findUnique({ where: { username: body.username } })
    const valid = await verify(user?.passwordHash ?? (await dummyHash), body.password)
    if (!user || !valid) throw new AppError(401, 'invalid_credentials')
    await startSession(request, reply, user.id)
    return { user: presentUser(user) }
  })

  app.post('/reset', { config: limit }, async (request, reply) => {
    const body = parse(resetSchema, request.body)
    const user = await usePasswordReset(body.token, body.newPassword)
    await startSession(request, reply, user.id)
    return { user: presentUser(user) }
  })

  app.post('/logout', async (request, reply) => {
    await endSession(request, reply)
    return reply.code(204).send()
  })

  app.get('/me', { preHandler: requireAuth }, async (request) => ({
    user: presentUser(request.user!),
  }))
}
