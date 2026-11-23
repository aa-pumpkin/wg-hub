import { randomInt } from 'node:crypto'
import { hash } from '@node-rs/argon2'
import type { User } from '@prisma/client'
import { z } from 'zod'

export const LOCALES = ['de', 'en', 'ru'] as const

/** Logins are case-insensitive: Anna and anna are the same person. */
export const username = z
  .string()
  .trim()
  .toLowerCase()
  .regex(/^[\p{L}\p{N}._-]{3,30}$/u)

export const AVATAR_COLORS = ['sage', 'sky', 'rose', 'amber', 'lilac', 'teal'] as const

/** A starting color, so flatmates are told apart at a glance. Changeable in the profile. */
export const randomAvatarColor = () => AVATAR_COLORS[randomInt(AVATAR_COLORS.length)]!

/** @node-rs/argon2 defaults to Argon2id. Parameters follow the OWASP recommendation. */
export const hashPassword = (password: string) =>
  hash(password, { memoryCost: 19456, timeCost: 2, parallelism: 1 })

export function presentUser(user: User) {
  return {
    id: user.id,
    username: user.username,
    displayName: user.displayName,
    avatarColor: user.avatarColor,
    locale: user.locale,
  }
}
