/**
 * Sets a new password from the server's shell. Normally a WG admin creates a reset link in
 * the app; this is for when the admin is the one locked out:
 *   docker compose exec backend node dist/scripts/reset-password.js anna 'new password'
 */
import { prisma } from '../lib/prisma.js'
import { hashPassword } from '../modules/users/service.js'

const [login, password] = process.argv.slice(2)
if (!login || !password || password.length < 8) {
  console.error('Usage: reset-password <username> <new password, at least 8 characters>')
  process.exit(1)
}

const user = await prisma.user.findUnique({ where: { username: login.toLowerCase() } })
if (!user) {
  console.error('No user with that username.')
  process.exit(1)
}
await prisma.user.update({
  where: { id: user.id },
  data: { passwordHash: await hashPassword(password), sessions: { deleteMany: {} } },
})
console.log(`Password updated for ${user.username}; all sessions were logged out.`)
await prisma.$disconnect()
