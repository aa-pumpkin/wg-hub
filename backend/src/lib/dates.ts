import { DateTime, IANAZone } from 'luxon'

/**
 * Two kinds of time in this app:
 * - calendar dates ("YYYY-MM-DD", chore due dates, expense dates) are stored as
 *   Postgres DATE and have no time zone. Prisma represents them as UTC midnight.
 * - instants (event start/end) are stored in UTC. The API accepts them as wall-clock
 *   time in the household's zone, so DST is handled here and not in the browser.
 */

export const isValidTimeZone = (zone: string) => IANAZone.isValidZone(zone)

export const todayIn = (zone: string) => DateTime.now().setZone(zone).toISODate()!

export const dateToDb = (date: string) => new Date(`${date}T00:00:00.000Z`)

export const dateFromDb = (date: Date) => date.toISOString().slice(0, 10)

export const addDays = (date: string, days: number) =>
  DateTime.fromISO(date, { zone: 'utc' }).plus({ days }).toISODate()!

/** Converts "YYYY-MM-DDTHH:mm" in the given zone to an instant, or null if invalid. */
export function wallClockToInstant(value: string, zone: string): Date | null {
  const parsed = DateTime.fromISO(value, { zone })
  return parsed.isValid ? parsed.toJSDate() : null
}

/** The instant at which a calendar date begins in the given zone. */
export const startOfDayInstant = (date: string, zone: string) =>
  DateTime.fromISO(date, { zone }).startOf('day').toJSDate()
