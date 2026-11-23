import { createHash, randomBytes } from 'node:crypto'
import type { Household, Role, User } from '@prisma/client'
import type { FastifyReply, FastifyRequest } from 'fastify'
import { forbidden, notFound, unauthorized } from '../lib/errors.js'
import { prisma } from '../lib/prisma.js'

const COOKIE = 'wghub_session'
const SESSION_DAYS = 30
const DAY_MS = 24 * 60 * 60 * 1000

declare module 'fastify' {
  interface FastifyRequest {
    user?: User
    household?: Household
    role?: Role
  }
}

const hashToken = (token: string) => createHash('sha256').update(token).digest('hex')

export async function startSession(request: FastifyRequest, reply: FastifyReply, userId: string) {
  const token = randomBytes(32).toString('base64url')
  const expiresAt = new Date(Date.now() + SESSION_DAYS * DAY_MS)
  await prisma.session.create({ data: { tokenHash: hashToken(token), userId, expiresAt } })
  // Clean up expired sessions opportunistically; login is rare enough to afford it.
  await prisma.session.deleteMany({ where: { expiresAt: { lt: new Date() } } })
  reply.setCookie(COOKIE, token, {
    path: '/',
    httpOnly: true,
    sameSite: 'lax',
    secure: request.protocol === 'https',
    expires: expiresAt,
  })
}

export async function endSession(request: FastifyRequest, reply: FastifyReply) {
  const token = request.cookies[COOKIE]
  if (token) await prisma.session.deleteMany({ where: { tokenHash: hashToken(token) } })
  reply.clearCookie(COOKIE, { path: '/' })
}

/** Logs a user out everywhere except the session making this request. */
export async function endOtherSessions(request: FastifyRequest, userId: string) {
  const token = request.cookies[COOKIE] ?? ''
  await prisma.session.deleteMany({ where: { userId, tokenHash: { not: hashToken(token) } } })
}

export async function requireAuth(request: FastifyRequest) {
  const token = request.cookies[COOKIE]
  if (!token) throw unauthorized()
  const session = await prisma.session.findUnique({
    where: { tokenHash: hashToken(token) },
    include: { user: true },
  })
  if (!session || session.expiresAt < new Date()) throw unauthorized()
  request.user = session.user
  // Sliding expiry, written at most once a day.
  const expiresAt = new Date(Date.now() + SESSION_DAYS * DAY_MS)
  if (expiresAt.getTime() - session.expiresAt.getTime() > DAY_MS) {
    await prisma.session.update({ where: { id: session.id }, data: { expiresAt } })
  }
}

/**
 * The tenant boundary. Answers 404 rather than 403 for non-members, so a household's
 * existence is not revealed.
 */
export async function requireMember(request: FastifyRequest) {
  await requireAuth(request)
  const { householdId } = request.params as { householdId: string }
  const membership = await prisma.householdMember.findUnique({
    where: { householdId_userId: { householdId, userId: request.user!.id } },
    include: { household: true },
  })
  if (!membership) throw notFound()
  request.household = membership.household
  request.role = membership.role
}

export async function requireAdmin(request: FastifyRequest) {
  if (request.role !== 'ADMIN') throw forbidden()
}

/** Accessors for route handlers behind requireMember / requireAuth. */
export const currentUser = (request: FastifyRequest) => request.user!
export const currentHousehold = (request: FastifyRequest) => request.household!

/** Throws unless the actor created the resource or is an admin. */
export function assertOwnerOrAdmin(request: FastifyRequest, ownerId: string) {
  if (request.role !== 'ADMIN' && ownerId !== request.user!.id) throw forbidden()
}

/** Throws unless all given users currently belong to the household. */
export async function assertMembers(householdId: string, userIds: string[]) {
  if (userIds.length === 0) return
  const unique = [...new Set(userIds)]
  const count = await prisma.householdMember.count({
    where: { householdId, userId: { in: unique } },
  })
  if (count !== unique.length) throw notFound()
}
