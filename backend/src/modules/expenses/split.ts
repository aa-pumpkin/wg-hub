import { badRequest } from '../../lib/errors.js'

export interface Share {
  userId: string
  shareCents: number
}

/**
 * Splits a total evenly. Leftover cents go one each to the first participants, so the
 * shares always add up to exactly the total (10.00 € / 3 = 3.34, 3.33, 3.33).
 */
export function splitEqually(amountCents: number, userIds: readonly string[]): Share[] {
  if (userIds.length === 0) throw badRequest('no_participants')
  const base = Math.floor(amountCents / userIds.length)
  const remainder = amountCents % userIds.length
  return userIds.map((userId, index) => ({
    userId,
    shareCents: base + (index < remainder ? 1 : 0),
  }))
}

/** Validates custom shares: each at least 0, summing to exactly the total. */
export function validateCustomShares(amountCents: number, shares: readonly Share[]): Share[] {
  if (shares.length === 0) throw badRequest('no_participants')
  const sum = shares.reduce((total, share) => total + share.shareCents, 0)
  if (sum !== amountCents) throw badRequest('shares_do_not_add_up')
  return [...shares]
}
