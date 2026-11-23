import { DateTime } from 'luxon'
import { dateFromDb, dateToDb, todayIn } from '../../lib/dates.js'
import { prisma } from '../../lib/prisma.js'
import { userSummary } from '../../lib/users.js'
import { computeStreak, earnedBadges, levelFor, type StreakWeek } from './rules.js'

const STREAK_LOOKBACK_DAYS = 400

export async function memberPoints(householdId: string, userId: string) {
  const { _sum } = await prisma.choreCompletion.aggregate({
    where: { completedById: userId, assignment: { householdId } },
    _sum: { points: true },
  })
  const penalties = await prisma.dishwasherStrike.aggregate({
    where: { userId, cycle: { householdId } },
    _sum: { points: true },
  })
  return (_sum.points ?? 0) - (penalties._sum.points ?? 0)
}

/** Points, level, streak and badges of everyone currently in the WG, best first. */
export async function householdProgress(householdId: string, timezone: string) {
  const today = todayIn(timezone)
  const now = DateTime.now().setZone(timezone)
  const monthStart = now.startOf('month').toJSDate()
  const weekStart = now.startOf('week').toJSDate()
  const lookback = dateToDb(now.minus({ days: STREAK_LOOKBACK_DAYS }).toISODate()!)

  const [members, completions, mainRows, strikes] = await Promise.all([
    prisma.householdMember.findMany({
      where: { householdId },
      select: { user: userSummary },
      orderBy: { joinedAt: 'asc' },
    }),
    prisma.choreCompletion.findMany({
      where: { assignment: { householdId } },
      select: {
        completedById: true,
        points: true,
        completedAt: true,
        assignment: { select: { chore: { select: { kind: true } } } },
      },
    }),
    prisma.choreAssignment.findMany({
      where: {
        householdId,
        chore: { kind: 'MAIN' },
        assigneeId: { not: null },
        dueDate: { gte: lookback, lte: dateToDb(today) },
      },
      select: {
        assigneeId: true,
        dueDate: true,
        status: true,
        completion: { select: { completedById: true, completedAt: true } },
      },
    }),
    prisma.dishwasherStrike.findMany({
      where: { cycle: { householdId } },
      select: { userId: true, points: true, cycle: { select: { finishedAt: true } } },
    }),
  ])
  const thisWeek = await prisma.choreAssignment.findMany({
    where: {
      householdId,
      chore: { kind: 'MAIN' },
      assigneeId: { not: null },
      dueDate: { gt: dateToDb(today), lte: dateToDb(now.endOf('week').toISODate()!) },
    },
    select: {
      assigneeId: true,
      dueDate: true,
      status: true,
      completion: { select: { completedById: true, completedAt: true } },
    },
  })

  const progress = members.map(({ user }) => {
    const mine = completions.filter((completion) => completion.completedById === user.id)
    const sum = (list: typeof mine) => list.reduce((total, item) => total + item.points, 0)
    const myStrikes = strikes.filter((strike) => strike.userId === user.id)
    const lost = (from: Date) =>
      myStrikes
        .filter((strike) => strike.cycle.finishedAt >= from)
        .reduce((total, strike) => total + strike.points, 0)
    const points = sum(mine) - lost(new Date(0))
    const weeks: StreakWeek[] = [...mainRows, ...thisWeek]
      .filter((row) => row.assigneeId === user.id)
      .map((row) => {
        const dueDate = dateFromDb(row.dueDate)
        if (row.status === 'SKIPPED') return { dueDate, result: 'neutral' as const }
        if (row.status === 'PENDING' || !row.completion) return { dueDate, result: 'missed' as const }
        if (row.completion.completedById !== user.id) return { dueDate, result: 'neutral' as const }
        const doneOn = DateTime.fromJSDate(row.completion.completedAt).setZone(timezone).toISODate()!
        return { dueDate, result: doneOn <= dueDate ? ('done' as const) : ('missed' as const) }
      })
    const streak = computeStreak(weeks, today)
    const level = levelFor(points)
    const extras = mine.filter((item) => item.assignment.chore.kind === 'EXTRA').length
    return {
      user,
      points,
      monthPoints: sum(mine.filter((item) => item.completedAt >= monthStart)) - lost(monthStart),
      weekPoints: sum(mine.filter((item) => item.completedAt >= weekStart)) - lost(weekStart),
      completed: mine.length,
      level: level.level,
      levelFloor: level.floor,
      levelNext: level.next,
      streak,
      badges: earnedBadges({
        completed: mine.length,
        extras,
        streak,
        level: level.level,
      }),
    }
  })
  return progress.sort(
    (a, b) => b.points - a.points || a.user.displayName.localeCompare(b.user.displayName),
  )
}
