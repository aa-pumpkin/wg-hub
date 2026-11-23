import { describe, expect, it } from 'vitest'
import { buildDayMap, eventDays, monthGrid, startOfWeek, weekDays, weekTaskMap } from '../src/lib/calendar'
import type { CalendarEvent, Occurrence } from '../src/types'

describe('calendar grid', () => {
  it('starts weeks on Monday', () => {
    expect(startOfWeek('2026-11-08')).toBe('2026-11-02') // a Sunday
    expect(weekDays('2026-11-04')).toHaveLength(7)
  })

  it('covers the whole month in full weeks', () => {
    const grid = monthGrid('2026-11-15')
    expect(grid.length % 7).toBe(0)
    expect(grid[0]).toBe('2026-10-26')
    expect(grid).toContain('2026-11-30')
  })

  it('handles the month with the DST switch', () => {
    const grid = monthGrid('2026-03-15')
    expect(grid.length % 7).toBe(0)
    expect(new Set(grid).size).toBe(grid.length)
  })
})

const event = (overrides: Partial<CalendarEvent>): CalendarEvent => ({
  id: '1',
  title: 'x',
  description: null,
  category: 'WG',
  startsAt: '2026-11-07T19:00:00.000Z',
  endsAt: null,
  allDay: false,
  createdBy: { id: 'u', displayName: 'U', avatarColor: 'sage' },
  participants: [],
  ...overrides,
})

describe('eventDays', () => {
  it('uses the household zone, not UTC', () => {
    // 23:30 UTC on the 7th is already the 8th in Berlin.
    expect(eventDays(event({ startsAt: '2026-11-07T23:30:00.000Z' }), 'Europe/Berlin')).toEqual([
      '2026-11-08',
    ])
  })

  it('expands multi-day events', () => {
    expect(
      eventDays(
        event({ startsAt: '2026-11-07T10:00:00.000Z', endsAt: '2026-11-09T10:00:00.000Z' }),
        'Europe/Berlin',
      ),
    ).toEqual(['2026-11-07', '2026-11-08', '2026-11-09'])
  })
})

describe('buildDayMap', () => {
  const occurrence: Occurrence = {
    id: 'o',
    choreId: 'c',
    dueDate: '2026-11-07',
    status: 'PENDING',
    title: 'Bad',
    description: null,
    category: 'BATHROOM',
    recurrence: 'WEEKLY',
    kind: 'EXTRA',
    points: 10,
    assignee: null,
    completedAt: null,
    completedBy: null,
    awardedPoints: null,
  }

  it('puts chores first, then events by time, and honours filters', () => {
    const all = new Set(['CHORE', 'WG', 'OTHER'] as const)
    const map = buildDayMap(
      [event({ id: 'late' }), event({ id: 'early', startsAt: '2026-11-07T09:00:00.000Z' })],
      [occurrence],
      'Europe/Berlin',
      all,
    )
    expect(map.get('2026-11-07')!.map((i) => i.time)).toEqual([null, '10:00', '20:00'])

    const onlyChores = buildDayMap([event({})], [occurrence], 'Europe/Berlin', new Set(['CHORE']))
    expect(onlyChores.get('2026-11-07')).toHaveLength(1)
  })

  it('shows main tasks on their week instead of on a day', () => {
    const main: Occurrence = { ...occurrence, kind: 'MAIN', dueDate: '2026-11-08' }
    const chores = new Set(['CHORE'] as const)
    expect(buildDayMap([], [main], 'Europe/Berlin', chores).size).toBe(0)
    expect(weekTaskMap([main, occurrence], chores).get('2026-11-08')).toEqual([main])
    expect(weekTaskMap([main], new Set(['WG'])).size).toBe(0)
  })

  it('hides skipped occurrences', () => {
    const map = buildDayMap(
      [],
      [{ ...occurrence, status: 'SKIPPED' }],
      'Europe/Berlin',
      new Set(['CHORE']),
    )
    expect(map.size).toBe(0)
  })
})
