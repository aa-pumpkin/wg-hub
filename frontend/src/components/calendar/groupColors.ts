import type { CalendarGroup } from '@/lib/calendar'

/** Chores, WG events and other dates are told apart by color everywhere in the calendar. */
export const GROUP_STYLE: Record<CalendarGroup, { dot: string; pill: string }> = {
  CHORE: {
    dot: 'bg-[var(--accent)]',
    pill: 'bg-accent-soft text-accent-text',
  },
  WG: {
    dot: 'bg-[#8b7fd6]',
    pill: 'bg-[var(--note-lilac)] text-fg',
  },
  OTHER: {
    dot: 'bg-[#d9a23b]',
    pill: 'bg-[var(--note-yellow)] text-fg',
  },
}
