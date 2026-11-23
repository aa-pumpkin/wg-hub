import type { Recurrence } from '@prisma/client'
import { DateTime } from 'luxon'

/**
 * The due date of the n-th occurrence (0-based). Always calculated from the start date,
 * so a monthly chore on the 31st returns to the 31st after a short month instead of
 * drifting to the 28th forever.
 */
export function occurrenceDate(start: string, recurrence: Recurrence, index: number): string {
  const base = DateTime.fromISO(start, { zone: 'utc' })
  switch (recurrence) {
    case 'NONE':
      return start
    case 'DAILY':
      return base.plus({ days: index }).toISODate()!
    case 'WEEKLY':
      return base.plus({ weeks: index }).toISODate()!
    case 'BIWEEKLY':
      return base.plus({ weeks: index * 2 }).toISODate()!
    case 'MONTHLY':
      return base.plus({ months: index }).toISODate()!
  }
}

/** All due dates in [from, until], both inclusive. */
export function occurrenceDates(
  start: string,
  recurrence: Recurrence,
  from: string,
  until: string,
): string[] {
  if (recurrence === 'NONE') return start >= from && start <= until ? [start] : []
  const dates: string[] = []
  for (let index = 0; ; index++) {
    const date = occurrenceDate(start, recurrence, index)
    if (date > until) return dates
    if (date >= from) dates.push(date)
  }
}

/** The end (exclusive) of the period a due date belongs to, used to compare workload. */
export function periodEnd(date: string, recurrence: Recurrence): string {
  const base = DateTime.fromISO(date, { zone: 'utc' })
  switch (recurrence) {
    case 'DAILY':
      return base.plus({ days: 1 }).toISODate()!
    case 'BIWEEKLY':
      return base.plus({ weeks: 2 }).toISODate()!
    case 'MONTHLY':
      return base.plus({ months: 1 }).toISODate()!
    case 'NONE':
    case 'WEEKLY':
      return base.plus({ weeks: 1 }).toISODate()!
  }
}

/** Weeks run Monday to Sunday. */
export const mondayOf = (date: string) =>
  DateTime.fromISO(date, { zone: 'utc' }).startOf('week').toISODate()!

export const sundayOf = (date: string) =>
  DateTime.fromISO(date, { zone: 'utc' }).startOf('week').plus({ days: 6 }).toISODate()!

/** Whole weeks from the anchor's week to the date's week; negative before the anchor. */
export function weeksBetween(anchor: string, date: string): number {
  const from = DateTime.fromISO(mondayOf(anchor), { zone: 'utc' })
  const to = DateTime.fromISO(mondayOf(date), { zone: 'utc' })
  return Math.round(to.diff(from, 'weeks').weeks)
}
