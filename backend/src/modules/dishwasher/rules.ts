export interface RoundMember {
  userId: string
  joinedAt: Date
  frozen: boolean
}

/**
 * Who gets a strike when a round ends: anyone who did not empty the dishwasher even once.
 * Being away (frozen) or having joined during the round is not their fault.
 */
export function struckMembers(
  members: readonly RoundMember[],
  counts: ReadonlyMap<string, number>,
  roundStart: Date | null,
): string[] {
  return members
    .filter((member) => !member.frozen)
    .filter((member) => !roundStart || member.joinedAt <= roundStart)
    .filter((member) => (counts.get(member.userId) ?? 0) === 0)
    .map((member) => member.userId)
}

/** What a strike costs: the same as one main task. */
export const STRIKE_PENALTY = 10

/**
 * Who gets the crown when the round's total is reached: whoever emptied it most. On a tie,
 * whoever got to that number first. `entries` are the round's magnets, oldest first.
 */
export function roundWinner(entries: readonly string[], eligible: ReadonlySet<string>): string | null {
  const counts = new Map<string, number>()
  let winner: string | null = null
  let best = 0
  for (const userId of entries) {
    if (!eligible.has(userId)) continue
    const count = (counts.get(userId) ?? 0) + 1
    counts.set(userId, count)
    // Strictly more, so a tie stays with the one who got there first.
    if (count > best) {
      best = count
      winner = userId
    }
  }
  return winner
}
