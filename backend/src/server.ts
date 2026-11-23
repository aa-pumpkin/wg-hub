import { buildApp } from './app.js'
import { env } from './config/env.js'
import { prisma } from './lib/prisma.js'

const app = await buildApp()

const shutdown = async () => {
  // Open event streams never end by themselves.
  const timeout = setTimeout(() => process.exit(0), 3000)
  timeout.unref()
  await app.close()
  await prisma.$disconnect()
  process.exit(0)
}
process.on('SIGTERM', shutdown)
process.on('SIGINT', shutdown)

await app.listen({ port: env.PORT, host: '0.0.0.0' })
