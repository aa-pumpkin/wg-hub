import { createHash, randomBytes } from 'node:crypto'
import { AppError } from '../../lib/errors.js'
import { prisma } from '../../lib/prisma.js'
import { hashPassword } from '../users/service.js'

const VALID_HOURS = 24
const hashToken = (token: string) => createHash('sha256').update(token).digest('hex')

/** A new link replaces any earlier one that was not used yet. */
export async function createPasswordReset(userId: string, createdById: string) {
  const token = randomBytes(32).toString('base64url')
  const expiresAt = new Date(Date.now() + VALID_HOURS * 60 * 60 * 1000)
  await prisma.$transaction([
    prisma.passwordResetToken.deleteMany({ where: { userId, usedAt: null } }),
    prisma.passwordResetToken.create({
      data: { tokenHash: hashToken(token), userId, createdById, expiresAt },
    }),
  ])
  return { token, expiresAt }
}

/** Sets the password and logs out every device. Returns the user, or throws if invalid. */
export async function usePasswordReset(token: string, newPassword: string) {
  const passwordHash = await hashPassword(newPassword)
  return prisma.$transaction(async (tx) => {
    const { count } = await tx.passwordResetToken.updateMany({
      where: { tokenHash: hashToken(token), usedAt: null, expiresAt: { gt: new Date() } },
      data: { usedAt: new Date() },
    })
    if (count !== 1) throw new AppError(410, 'reset_invalid')
    const { userId } = await tx.passwordResetToken.findUniqueOrThrow({
      where: { tokenHash: hashToken(token) },
    })
    await tx.session.deleteMany({ where: { userId } })
    return tx.user.update({ where: { id: userId }, data: { passwordHash } })
  })
}
