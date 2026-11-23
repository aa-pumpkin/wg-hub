export type Role = 'ADMIN' | 'MEMBER'
export const LOCALES = ['de', 'en', 'ru'] as const
export type Locale = (typeof LOCALES)[number]

export interface UserSummary {
  id: string
  displayName: string
  avatarColor: string
}

export interface User extends UserSummary {
  username: string
  locale: Locale
}

export interface HouseholdListItem {
  id: string
  name: string
  icon: string
  role: Role
  memberCount: number
}

export interface Household {
  id: string
  name: string
  address: string | null
  icon: string
  timezone: string
  beerPriceCents: number
  dishwasherTarget: number | null
}

export interface Member extends UserSummary {
  username: string
  role: Role
  joinedAt: string
  frozen: boolean
}

export interface MemberStats {
  open: Record<string, number>
  completed: Record<string, number>
}

export interface Invitation {
  id: string
  code: string
  expiresAt: string | null
  useCount: number
  createdAt: string
}

export interface InvitationPreview {
  householdName: string
  householdIcon: string
  memberCount: number
}

export const RECURRENCES = ['NONE', 'DAILY', 'WEEKLY', 'BIWEEKLY', 'MONTHLY'] as const
export type Recurrence = (typeof RECURRENCES)[number]

export type ChoreCategory = 'KITCHEN' | 'BATHROOM' | 'TRASH' | 'FLOORS' | 'LAUNDRY' | 'OTHER'

export type ChoreKind = 'MAIN' | 'EXTRA'

export type OccurrenceStatus = 'PENDING' | 'COMPLETED' | 'SKIPPED'

export interface Chore {
  id: string
  title: string
  description: string | null
  category: ChoreCategory
  kind: ChoreKind
  points: number
  position: number
  recurrence: Recurrence
  startDate: string
  createdById: string
  rotation: UserSummary[]
}

export interface ChoreInput {
  title: string
  description: string
  recurrence: Recurrence
  startDate: string
  rotation: string[]
  kind?: ChoreKind
  points?: number
}

/** One due date of a chore. */
export interface Occurrence {
  id: string
  choreId: string
  dueDate: string
  status: OccurrenceStatus
  title: string
  description: string | null
  category: ChoreCategory
  recurrence: Recurrence
  kind: ChoreKind
  points: number
  assignee: UserSummary | null
  completedAt: string | null
  completedBy: UserSummary | null
  awardedPoints: number | null
}

export interface CompletionResult {
  occurrence: Occurrence
  earned: number
  level: number
  leveledUp: boolean
}

export interface ChorePlan {
  circle: { user: UserSummary; frozen: boolean }[]
  weeks: { dueDate: string; items: Occurrence[] }[]
}

export interface MemberProgress {
  user: UserSummary
  points: number
  monthPoints: number
  weekPoints: number
  completed: number
  level: number
  levelFloor: number
  levelNext: number
  streak: number
  badges: string[]
}

export const SHOPPING_UNITS = ['pcs', 'l', 'kg', 'g', 'pack'] as const

export interface ShoppingItem {
  id: string
  name: string
  quantity: number | null
  unit: string | null
  note: string | null
  personal: boolean
  expense: { id: string; amountCents: number } | null
  status: 'PENDING' | 'BOUGHT'
  createdBy: UserSummary
  boughtBy: UserSummary | null
  boughtAt: string | null
  createdAt: string
}

export interface ShoppingItemInput {
  name: string
  quantity?: number | null
  unit?: string
  note?: string
  personal?: boolean
}

export const EVENT_CATEGORIES = ['WG', 'CHORE', 'OTHER'] as const
export type EventCategory = (typeof EVENT_CATEGORIES)[number]

export interface CalendarEvent {
  id: string
  title: string
  description: string | null
  category: EventCategory
  startsAt: string
  endsAt: string | null
  allDay: boolean
  createdBy: UserSummary
  participants: UserSummary[]
}

export interface EventInput {
  title: string
  description: string
  category: EventCategory
  startsAt: string
  endsAt: string | null
  allDay: boolean
  participantIds: string[]
}

export const NOTE_COLORS = ['DEFAULT', 'YELLOW', 'GREEN', 'BLUE', 'PINK', 'LILAC'] as const
export type NoteColor = (typeof NOTE_COLORS)[number]

export interface Note {
  id: string
  content: string
  color: NoteColor
  pinned: boolean
  archivedAt: string | null
  createdAt: string
  updatedAt: string
  author: UserSummary
}

export interface Expense {
  id: string
  description: string
  amountCents: number
  date: string
  splitMode: 'EQUAL' | 'CUSTOM'
  paidBy: UserSummary
  createdById: string
  participants: { user: UserSummary; shareCents: number }[]
}

export interface ExpenseInput {
  description: string
  amountCents: number
  paidById: string
  date: string
  splitMode: 'EQUAL' | 'CUSTOM'
  participants: { userId: string; shareCents?: number }[]
}

export interface Settlement {
  id: string
  amountCents: number
  date: string
  note: string | null
  kind: 'PAYMENT' | 'FORGIVEN' | 'BEER'
  beers: number | null
  fromUser: UserSummary
  toUser: UserSummary
  createdById: string
}

export interface SettlementInput {
  fromUserId: string
  toUserId: string
  amountCents: number
  date: string
  note?: string
  kind?: 'PAYMENT' | 'FORGIVEN'
}

export interface BeerRequest {
  id: string
  fromUserId: string
  toUserId: string
  amountCents: number
  beers: number
}

export interface DishwasherState {
  target: number | null
  total: number
  strikePenalty: number
  cycle: number
  crownHolderId: string | null
  lastCycle: { winner: UserSummary; struck: UserSummary[] } | null
  members: {
    user: UserSummary
    frozen: boolean
    count: number
    crowns: number
    strikes: number
  }[]
}

export interface Balance {
  user: UserSummary
  cents: number
}

export interface SuggestedPayment {
  fromUserId: string
  toUserId: string
  amountCents: number
}

export interface Dashboard {
  today: string
  tasks: Occurrence[]
  upcomingTasks: Occurrence[]
  shopping: { items: ShoppingItem[]; pendingCount: number }
  events: CalendarEvent[]
  notes: Note[]
  myBalanceCents: number
  myProgress: MemberProgress | null
  myRank: number
}

export type LiveModule =
  | 'chores'
  | 'shopping'
  | 'calendar'
  | 'notes'
  | 'expenses'
  | 'members'
  | 'household'
  | 'dishwasher'
