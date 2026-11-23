import { afterEach, describe, expect, it, vi } from 'vitest'
import { mondayOf, occurrenceDate, occurrenceDates, periodEnd, sundayOf, weeksBetween } from '../src/modules/chores/recurrence.js'
import { roundWinner, struckMembers } from '../src/modules/dishwasher/rules.js'
import { circleAssignee, pickAssignee } from '../src/modules/chores/rotation.js'
import { computeStreak, earnedBadges, levelFor, pointsFor } from '../src/modules/gamification/rules.js'
import { beersFor, computeBalances, suggestPayments } from '../src/modules/expenses/balances.js'
import { splitEqually, validateCustomShares } from '../src/modules/expenses/split.js'
import { todayIn, wallClockToInstant } from '../src/lib/dates.js'

describe('recurrence', () => {
  it('spaces weekly and biweekly occurrences correctly', () => {
    expect(occurrenceDates('2026-01-05', 'WEEKLY', '2026-01-01', '2026-01-20')).toEqual([
      '2026-01-05',
      '2026-01-12',
      '2026-01-19',
    ])
    expect(occurrenceDates('2026-01-05', 'BIWEEKLY', '2026-01-10', '2026-02-05')).toEqual([
      '2026-01-19',
      '2026-02-02',
    ])
  })

  it('keeps a monthly chore on its day after a short month', () => {
    expect(occurrenceDate('2026-01-31', 'MONTHLY', 1)).toBe('2026-02-28')
    expect(occurrenceDate('2026-01-31', 'MONTHLY', 2)).toBe('2026-03-31')
  })

  it('is unaffected by daylight saving changes', () => {
    // Clocks change in Germany on 2026-03-29.
    expect(occurrenceDates('2026-03-28', 'DAILY', '2026-03-28', '2026-03-30')).toEqual([
      '2026-03-28',
      '2026-03-29',
      '2026-03-30',
    ])
  })

  it('gives a one-off task a single date, inside the range only', () => {
    expect(occurrenceDates('2026-01-05', 'NONE', '2026-01-01', '2026-12-31')).toEqual(['2026-01-05'])
    expect(occurrenceDates('2026-01-05', 'NONE', '2026-02-01', '2026-12-31')).toEqual([])
  })

  it('computes period ends', () => {
    expect(periodEnd('2026-01-05', 'WEEKLY')).toBe('2026-01-12')
    expect(periodEnd('2026-01-31', 'MONTHLY')).toBe('2026-02-28')
  })
})

describe('rotation', () => {
  const people = ['anna', 'max', 'lisa']
  const none = new Map<string, number>()

  it('moves to the next person and wraps around', () => {
    expect(pickAssignee(people, 'anna', none)).toBe('max')
    expect(pickAssignee(people, 'lisa', none)).toBe('anna')
  })

  it('starts with the least busy person', () => {
    expect(pickAssignee(people, null, new Map([['anna', 1]]))).toBe('max')
    expect(
      pickAssignee(people, null, new Map([['anna', 1], ['max', 1]])),
    ).toBe('lisa')
  })

  it('recovers when the previous assignee left the rotation', () => {
    expect(pickAssignee(['max', 'lisa'], 'anna', new Map([['max', 2]]))).toBe('lisa')
  })

  it('handles a fixed owner and an empty rotation', () => {
    expect(pickAssignee(['max'], 'max', none)).toBe('max')
    expect(pickAssignee([], 'max', none)).toBeNull()
  })

  it('distributes evenly over a full cycle', () => {
    const counts = new Map<string, number>()
    let previous: string | null = null
    for (let week = 0; week < 30; week++) {
      previous = pickAssignee(people, previous, none)!
      counts.set(previous, (counts.get(previous) ?? 0) + 1)
    }
    expect([...counts.values()]).toEqual([10, 10, 10])
  })
})

describe('main task circle', () => {
  const circle = ['anna', 'max', 'lisa']
  const week = (index: number, tasks = 3) =>
    Array.from({ length: tasks }, (_, task) => circleAssignee(circle, task, index))

  it('moves every task to the next person each week and comes back round', () => {
    expect(week(0)).toEqual(['anna', 'max', 'lisa'])
    expect(week(1)).toEqual(['lisa', 'anna', 'max'])
    expect(week(2)).toEqual(['max', 'lisa', 'anna'])
    expect(week(3)).toEqual(week(0))
  })

  it('never gives anyone two tasks while someone has none, when counts match', () => {
    for (let index = -10; index < 30; index++) {
      expect(new Set(week(index)).size).toBe(3)
    }
  })

  it('stays fair with more tasks than people, and fewer', () => {
    const count = new Map<string, number>()
    for (let index = 0; index < 30; index++) {
      for (const person of week(index, 4)) count.set(person!, (count.get(person!) ?? 0) + 1)
    }
    expect([...count.values()]).toEqual([40, 40, 40])
    expect(week(0, 2)).toEqual(['anna', 'max'])
    expect(week(1, 2)).toEqual(['lisa', 'anna'])
  })

  it('handles an empty circle', () => {
    expect(circleAssignee([], 0, 5)).toBeNull()
  })

  it('counts weeks Monday to Sunday', () => {
    expect(mondayOf('2026-11-08')).toBe('2026-11-02')
    expect(sundayOf('2026-11-02')).toBe('2026-11-08')
    expect(weeksBetween('2026-11-02', '2026-11-08')).toBe(0)
    expect(weeksBetween('2026-11-02', '2026-11-09')).toBe(1)
    expect(weeksBetween('2026-11-09', '2026-11-02')).toBe(-1)
    // Across the DST change at the end of March.
    expect(weeksBetween('2026-03-23', '2026-03-30')).toBe(1)
  })
})

describe('points and levels', () => {
  it('halves late work, rounding up', () => {
    expect(pointsFor(10, '2026-01-10', '2026-01-10')).toBe(10)
    expect(pointsFor(10, '2026-01-10', '2026-01-11')).toBe(5)
    expect(pointsFor(15, '2026-01-10', '2026-01-11')).toBe(8)
  })

  it('finds levels at their exact thresholds', () => {
    expect(levelFor(0)).toEqual({ level: 1, floor: 0, next: 50 })
    expect(levelFor(49).level).toBe(1)
    expect(levelFor(50)).toEqual({ level: 2, floor: 50, next: 150 })
    expect(levelFor(150).level).toBe(3)
    expect(levelFor(300).level).toBe(4)
  })

  const weeks = (...results: ('done' | 'missed' | 'neutral')[]) =>
    results.map((result, index) => ({
      dueDate: `2026-01-${String(4 + index * 7).padStart(2, '0')}`,
      result,
    }))

  it('counts weeks in a row back from the latest', () => {
    expect(computeStreak(weeks('done', 'done', 'done'), '2026-02-01')).toBe(3)
    expect(computeStreak(weeks('done', 'missed', 'done'), '2026-02-01')).toBe(1)
    expect(computeStreak(weeks('missed'), '2026-02-01')).toBe(0)
  })

  it('is not interrupted by skipped weeks or the week in progress', () => {
    expect(computeStreak(weeks('done', 'neutral', 'done'), '2026-02-01')).toBe(2)
    // The last week (due 18th) is still open on the 15th.
    expect(computeStreak(weeks('done', 'done', 'missed'), '2026-01-15')).toBe(2)
  })

  it('needs every task of a week', () => {
    const tasks = [
      { dueDate: '2026-01-04', result: 'done' as const },
      { dueDate: '2026-01-04', result: 'missed' as const },
    ]
    expect(computeStreak(tasks, '2026-02-01')).toBe(0)
  })

  it('awards badges', () => {
    expect(earnedBadges({ completed: 0, extras: 0, streak: 0, level: 1 })).toEqual([])
    expect(earnedBadges({ completed: 10, extras: 5, streak: 4, level: 5 })).toEqual([
      'first',
      'ten',
      'helper',
      'streak4',
      'level5',
    ])
  })
})

describe('expense splitting', () => {
  it('matches the example from the spec', () => {
    expect(splitEqually(1800, ['max', 'anna', 'lisa']).map((s) => s.shareCents)).toEqual([
      600, 600, 600,
    ])
  })

  it('never loses a cent', () => {
    for (const amount of [1, 2, 100, 1000, 1001, 9999]) {
      for (const people of [1, 2, 3, 7]) {
        const ids = Array.from({ length: people }, (_, i) => String(i))
        const shares = splitEqually(amount, ids)
        expect(shares.reduce((sum, s) => sum + s.shareCents, 0)).toBe(amount)
        const values = shares.map((s) => s.shareCents)
        expect(Math.max(...values) - Math.min(...values)).toBeLessThanOrEqual(1)
      }
    }
  })

  it('rejects custom shares that do not add up', () => {
    const shares = [
      { userId: 'a', shareCents: 500 },
      { userId: 'b', shareCents: 400 },
    ]
    expect(validateCustomShares(900, shares)).toHaveLength(2)
    expect(() => validateCustomShares(1000, shares)).toThrow('shares_do_not_add_up')
  })
})

describe('balances', () => {
  const expense = {
    paidById: 'max',
    amountCents: 1800,
    participants: [
      { userId: 'max', shareCents: 600 },
      { userId: 'anna', shareCents: 600 },
      { userId: 'lisa', shareCents: 600 },
    ],
  }

  it('computes the example from the spec', () => {
    const balances = computeBalances([expense], [])
    expect(Object.fromEntries(balances)).toEqual({ max: 1200, anna: -600, lisa: -600 })
    expect(suggestPayments(balances)).toEqual([
      { fromUserId: 'anna', toUserId: 'max', amountCents: 600 },
      { fromUserId: 'lisa', toUserId: 'max', amountCents: 600 },
    ])
  })

  it('applies settlements', () => {
    const balances = computeBalances(
      [expense],
      [{ fromUserId: 'anna', toUserId: 'max', amountCents: 600 }],
    )
    expect(Object.fromEntries(balances)).toEqual({ max: 600, anna: 0, lisa: -600 })
    expect(suggestPayments(balances)).toHaveLength(1)
  })

  it('suggested payments bring everyone to exactly zero', () => {
    const balances = new Map([
      ['a', 1000],
      ['b', 333],
      ['c', -700],
      ['d', -633],
    ])
    const result = new Map(balances)
    for (const payment of suggestPayments(balances)) {
      result.set(payment.fromUserId, result.get(payment.fromUserId)! + payment.amountCents)
      result.set(payment.toUserId, result.get(payment.toUserId)! - payment.amountCents)
    }
    expect([...result.values()].every((cents) => cents === 0)).toBe(true)
  })
})

describe('the deadline of a main task', () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  // 2026-11-08 is a Sunday. Berlin is one hour ahead of UTC in November.
  const pointsAt = (utc: string) => {
    vi.useFakeTimers({ now: new Date(utc) })
    return pointsFor(10, sundayOf('2026-11-02'), todayIn('Europe/Berlin'))
  }

  it('counts as on time until Sunday 23:59 in the WG, and late from Monday 00:00', () => {
    expect(pointsAt('2026-11-08T22:59:59Z')).toBe(10)
    expect(pointsAt('2026-11-08T23:00:00Z')).toBe(5)
  })

  it('starts the next week on Monday 00:00 in the WG', () => {
    vi.useFakeTimers({ now: new Date('2026-11-08T22:59:59Z') })
    expect(sundayOf(todayIn('Europe/Berlin'))).toBe('2026-11-08')
    vi.setSystemTime(new Date('2026-11-08T23:00:00Z'))
    expect(sundayOf(todayIn('Europe/Berlin'))).toBe('2026-11-15')
  })
})

describe('beer', () => {
  it('rounds up', () => {
    expect(beersFor(600, 150)).toBe(4)
    expect(beersFor(601, 150)).toBe(5)
    expect(beersFor(1, 150)).toBe(1)
  })
})

describe('dishwasher crown', () => {
  const all = new Set(['a', 'b', 'c'])
  it('goes to whoever did the most, not whoever finished', () => {
    expect(roundWinner(['a', 'a', 'b', 'c'], all)).toBe('a')
    expect(roundWinner(['b', 'a', 'a', 'a', 'b'], all)).toBe('a')
  })
  it('on a tie goes to whoever got there first', () => {
    expect(roundWinner(['a', 'b', 'b', 'a'], all)).toBe('b')
  })
  it('skips people who left', () => {
    expect(roundWinner(['x', 'x', 'x', 'a'], all)).toBe('a')
  })
})

describe('dishwasher strikes', () => {
  const start = new Date('2026-01-01')
  const person = (userId: string, extra = {}) => ({ userId, joinedAt: new Date('2025-01-01'), frozen: false, ...extra })

  it('strikes only those who did nothing', () => {
    const counts = new Map([['anna', 3], ['max', 1]])
    expect(struckMembers([person('anna'), person('max'), person('lisa')], counts, start)).toEqual(['lisa'])
  })

  it('spares the frozen and those who joined mid-round', () => {
    const late = person('new', { joinedAt: new Date('2026-02-01') })
    expect(struckMembers([person('f', { frozen: true }), late], new Map(), start)).toEqual([])
  })
})

describe('time zones', () => {
  it('reads wall-clock time in the household zone across DST', () => {
    expect(wallClockToInstant('2026-01-15T14:00', 'Europe/Berlin')!.toISOString()).toBe(
      '2026-01-15T13:00:00.000Z',
    )
    expect(wallClockToInstant('2026-07-15T14:00', 'Europe/Berlin')!.toISOString()).toBe(
      '2026-07-15T12:00:00.000Z',
    )
  })
})
