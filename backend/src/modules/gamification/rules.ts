/** Late work still counts, for half the points (rounded up). */
export function pointsFor(points: number, dueDate: string, today: string): number {
  return today <= dueDate ? points : Math.ceil(points / 2)
}

/** Points needed to reach a level: 0, 50, 150, 300, 500, 750… */
export const levelFloor = (level: number) => 25 * level * (level - 1)

export function levelFor(points: number) {
  // Strikes can push the total below zero; the level just stays at the bottom.
  let level = 1
  while (points >= levelFloor(level + 1)) level++
  return { level, floor: levelFloor(level), next: levelFloor(level + 1) }
}

export interface StreakWeek {
  dueDate: string
  /** done: by them, on time. missed: still open or late. neutral: skipped or done by someone else. */
  result: 'done' | 'missed' | 'neutral'
}

/**
 * Weeks in a row in which someone finished all their main tasks on time. A week still in
 * progress can only add to the streak, never break it. Free weeks do not interrupt it.
 */
export function computeStreak(tasks: readonly StreakWeek[], today: string): number {
  const byWeek = new Map<string, StreakWeek['result'][]>()
  for (const task of tasks) {
    byWeek.set(task.dueDate, [...(byWeek.get(task.dueDate) ?? []), task.result])
  }
  let streak = 0
  for (const dueDate of [...byWeek.keys()].sort().reverse()) {
    const results = byWeek.get(dueDate)!
    const missed = results.includes('missed')
    if (missed && dueDate >= today) continue
    if (missed) return streak
    if (results.includes('done')) streak++
  }
  return streak
}

export interface BadgeStats {
  completed: number
  extras: number
  streak: number
  level: number
}

const BADGES: Record<string, (stats: BadgeStats) => boolean> = {
  first: (stats) => stats.completed >= 1,
  ten: (stats) => stats.completed >= 10,
  fifty: (stats) => stats.completed >= 50,
  hundred: (stats) => stats.completed >= 100,
  helper: (stats) => stats.extras >= 5,
  streak4: (stats) => stats.streak >= 4,
  streak12: (stats) => stats.streak >= 12,
  level5: (stats) => stats.level >= 5,
}

export const earnedBadges = (stats: BadgeStats) =>
  Object.keys(BADGES).filter((badge) => BADGES[badge]!(stats))
