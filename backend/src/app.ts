import cookie from '@fastify/cookie'
import rateLimit from '@fastify/rate-limit'
import Fastify, { type FastifyError, type FastifyInstance } from 'fastify'
import { ZodError } from 'zod'
import { env } from './config/env.js'
import { AppError } from './lib/errors.js'
import { prisma } from './lib/prisma.js'
import { requireMember } from './middleware/auth.js'
import { csrfProtection } from './middleware/csrf.js'
import { authRoutes } from './modules/auth/routes.js'
import { calendarRoutes } from './modules/calendar/routes.js'
import { choreRoutes } from './modules/chores/routes.js'
import { dashboardRoutes } from './modules/dashboard/routes.js'
import { dishwasherRoutes } from './modules/dishwasher/routes.js'
import { expenseRoutes } from './modules/expenses/routes.js'
import { gamificationRoutes } from './modules/gamification/routes.js'
import { householdRootRoutes, householdRoutes } from './modules/households/routes.js'
import { liveRoutes } from './modules/live/routes.js'
import { noteRoutes } from './modules/notes/routes.js'
import { shoppingRoutes } from './modules/shopping/routes.js'
import { userRoutes } from './modules/users/routes.js'

export async function buildApp(): Promise<FastifyInstance> {
  const app = Fastify({
    logger: env.LOG_LEVEL === 'silent' ? false : { level: env.LOG_LEVEL },
    // The backend is only reachable through nginx, which sets X-Forwarded-*.
    trustProxy: true,
    bodyLimit: 256 * 1024,
  })

  await app.register(cookie)
  // global: false, so only routes that opt in via config.rateLimit are limited.
  await app.register(rateLimit, { global: false })

  app.addHook('onRequest', csrfProtection)

  app.setErrorHandler((error: FastifyError | AppError | ZodError, request, reply) => {
    if (error instanceof ZodError) {
      const fields: Record<string, string> = {}
      for (const issue of error.issues) {
        const key = issue.path.join('.')
        if (key && !fields[key]) fields[key] = issue.code
      }
      return reply.code(400).send({ error: 'validation_error', fields })
    }
    if (error instanceof AppError) {
      return reply.code(error.statusCode).send({ error: error.code })
    }
    if (error.statusCode === 429) return reply.code(429).send({ error: 'too_many_requests' })
    if (error.statusCode && error.statusCode < 500) {
      return reply.code(error.statusCode).send({ error: 'bad_request' })
    }
    request.log.error(error)
    return reply.code(500).send({ error: 'internal_error' })
  })

  app.setNotFoundHandler((_request, reply) => reply.code(404).send({ error: 'not_found' }))

  app.get('/api/health', async (request, reply) => {
    try {
      await prisma.$queryRaw`SELECT 1`
      return { status: 'ok' }
    } catch (error) {
      request.log.error(error)
      return reply.code(503).send({ status: 'unavailable' })
    }
  })

  await app.register(authRoutes, { prefix: '/api/auth' })
  await app.register(userRoutes, { prefix: '/api/me' })
  await app.register(householdRootRoutes, { prefix: '/api' })

  // Everything below is tenant data. The hook here is the single access check,
  // so a new module registered in this block cannot forget it.
  await app.register(
    async (scope) => {
      scope.addHook('preHandler', requireMember)
      await scope.register(householdRoutes)
      await scope.register(dashboardRoutes)
      await scope.register(choreRoutes)
      await scope.register(shoppingRoutes)
      await scope.register(calendarRoutes)
      await scope.register(noteRoutes)
      await scope.register(expenseRoutes)
      await scope.register(gamificationRoutes)
      await scope.register(dishwasherRoutes)
      await scope.register(liveRoutes)
    },
    { prefix: '/api/households/:householdId' },
  )

  return app
}
