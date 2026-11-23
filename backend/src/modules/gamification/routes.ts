import type { FastifyInstance } from 'fastify'
import { currentHousehold } from '../../middleware/auth.js'
import { householdProgress } from './service.js'

export async function gamificationRoutes(app: FastifyInstance) {
  app.get('/leaderboard', async (request) => {
    const household = currentHousehold(request)
    return { members: await householdProgress(household.id, household.timezone) }
  })
}
