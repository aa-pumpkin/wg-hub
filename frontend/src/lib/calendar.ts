import { DateTime } from 'luxon'
import type { CalendarEvent, EventCategory, Occurrence } from '@/types'

export type CalendarGroup = 'CHORE' | 'WG' | 'OTHER'

export interface CalendarItem {
  key: string
  kind: 'event' | 'occurrence'
  group: CalendarGroup
  title: string
  /** "14:00" in the household's zone, or null for all-day items. */
  time: string | null
  done: boolean
  event?: CalendarEvent
  occurrence?: Occurrence
}

const fromIso = (date: string) => DateTime.fromISO(date, { zone: 'utc' })

export function addDays(date: string, days: number): string {
  return fromIso(date).plus({ days }).toISODate()!
}

export function addMonths(date: string, months: number): string {
  return fromIso(date).plus({ months }).toISODate()!
}

export function startOfWeek(date: string): string {
  return fromIso(date).startOf('week').toISODate()!
}

export function weekDays(date: string): string[] {
  const start = startOfWeek(date)
  return Array.from({ length: 7 }, (_, index) => addDays(start, index))
}

/** The days shown for a month: full Monday-to-Sunday weeks, 4 to 6 rows. */
export function monthGrid(date: string): string[] {
  const month = fromIso(date)
  const first = month.startOf('month').startOf('week')
  const last = month.endOf('month').endOf('week')
  const count = Math.round(last.diff(first, 'days').days)
  return Array.from({ length: count }, (_, index) => first.plus({ days: index }).toISODate()!)
}

/** The calendar days an event covers in the household's zone. */
export function eventDays(event: Pick<CalendarEvent, 'startsAt' | 'endsAt'>, zone: string): string[] {
  const start = DateTime.fromISO(event.startsAt, { zone })
  const end = event.endsAt ? DateTime.fromISO(event.endsAt, { zone }) : start
  const days: string[] = []
  // Bounded so a wrongly entered end date cannot hang the page.
  for (let day = start.startOf('day'); day <= end && days.length < 366; day = day.plus({ days: 1 })) {
    days.push(day.toISODate()!)
  }
  return days
}

/** Groups events and chore occurrences by day: all-day and chores first, then by time. */
export function buildDayMap(
  events: readonly CalendarEvent[],
  occurrences: readonly Occurrence[],
  zone: string,
  visible: ReadonlySet<CalendarGroup>,
): Map<string, CalendarItem[]> {
  const map = new Map<string, CalendarItem[]>()
  const add = (day: string, item: CalendarItem) => {
    const list = map.get(day)
    if (list) list.push(item)
    else map.set(day, [item])
  }

  for (const occurrence of occurrences) {
    // Main tasks are for a whole week, so they are shown on the week, not on a day.
    if (!visible.has('CHORE') || occurrence.status === 'SKIPPED' || occurrence.kind === 'MAIN') continue
    add(occurrence.dueDate, {
      key: `o-${occurrence.id}`,
      kind: 'occurrence',
      group: 'CHORE',
      title: occurrence.title,
      time: null,
      done: occurrence.status === 'COMPLETED',
      occurrence,
    })
  }
  for (const event of events) {
    const group: EventCategory = event.category
    if (!visible.has(group)) continue
    const days = eventDays(event, zone)
    days.forEach((day, index) => {
      add(day, {
        key: `e-${event.id}-${day}`,
        kind: 'event',
        group,
        title: event.title,
        time:
          event.allDay || index > 0
            ? null
            : DateTime.fromISO(event.startsAt, { zone }).toFormat('HH:mm'),
        done: false,
        event,
      })
    })
  }
  for (const list of map.values()) {
    list.sort((a, b) => (a.time ?? '').localeCompare(b.time ?? ''))
  }
  return map
}

/** "YYYY-MM-DDTHH:mm" in the household's zone, for pre-filling the event form. */
export function instantToWallClock(iso: string, zone: string): string {
  return DateTime.fromISO(iso, { zone }).toFormat("yyyy-MM-dd'T'HH:mm")
}

/** Main tasks by the Sunday of their week. */
export function weekTaskMap(
  occurrences: readonly Occurrence[],
  visible: ReadonlySet<CalendarGroup>,
): Map<string, Occurrence[]> {
  const map = new Map<string, Occurrence[]>()
  if (!visible.has('CHORE')) return map
  for (const occurrence of occurrences) {
    if (occurrence.kind !== 'MAIN' || occurrence.status === 'SKIPPED') continue
    map.set(occurrence.dueDate, [...(map.get(occurrence.dueDate) ?? []), occurrence])
  }
  return map
}
