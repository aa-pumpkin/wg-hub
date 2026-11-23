import type {
  Balance,
  BeerRequest,
  DishwasherState,
  CalendarEvent,
  Chore,
  ChorePlan,
  CompletionResult,
  MemberProgress,
  ChoreInput,
  Dashboard,
  EventInput,
  Expense,
  ExpenseInput,
  Note,
  NoteColor,
  Occurrence,
  Settlement,
  SettlementInput,
  ShoppingItem,
  ShoppingItemInput,
  SuggestedPayment,
} from '@/types'
import { api } from './api'

const base = (householdId: string) => `/households/${householdId}`

export const dashboardService = {
  get: (hid: string) => api.get<Dashboard>(`${base(hid)}/dashboard`),
}

export const choreService = {
  list: (hid: string) => api.get<{ chores: Chore[] }>(`${base(hid)}/chores`).then((r) => r.chores),
  create: (hid: string, body: ChoreInput) => api.post<{ id: string }>(`${base(hid)}/chores`, body),
  update: (hid: string, id: string, body: ChoreInput) =>
    api.patch<{ id: string }>(`${base(hid)}/chores/${id}`, body),
  end: (hid: string, id: string) => api.delete(`${base(hid)}/chores/${id}`),
  occurrences: (hid: string, query: Record<string, string>) =>
    api
      .get<{ occurrences: Occurrence[] }>(
        `${base(hid)}/chore-occurrences?${new URLSearchParams(query)}`,
      )
      .then((r) => r.occurrences),
  complete: (hid: string, id: string) =>
    api.post<CompletionResult>(`${base(hid)}/chore-occurrences/${id}/complete`),
  plan: (hid: string) => api.get<ChorePlan>(`${base(hid)}/chore-plan`),
  setCircle: (hid: string, userIds: string[]) =>
    api.put(`${base(hid)}/chore-plan/circle`, { userIds }),
  shift: (hid: string, weeks: 1 | -1) => api.post(`${base(hid)}/chore-plan/shift`, { weeks }),
  act: (hid: string, id: string, action: 'reopen' | 'skip') =>
    api
      .post<{ occurrence: Occurrence }>(`${base(hid)}/chore-occurrences/${id}/${action}`)
      .then((r) => r.occurrence),
  reassign: (hid: string, id: string, assigneeId: string | null) =>
    api
      .patch<{ occurrence: Occurrence }>(`${base(hid)}/chore-occurrences/${id}`, { assigneeId })
      .then((r) => r.occurrence),
}

export const dishwasherService = {
  get: (hid: string) => api.get<DishwasherState>(`${base(hid)}/dishwasher`),
  tap: (hid: string) =>
    api.post<{ count: number; finished: boolean; crown: boolean; winnerName: string | null }>(
      `${base(hid)}/dishwasher/entries`,
    ),
  undo: (hid: string) => api.delete(`${base(hid)}/dishwasher/entries/last`),
}

export const pointsService = {
  leaderboard: (hid: string) =>
    api.get<{ members: MemberProgress[] }>(`${base(hid)}/leaderboard`).then((r) => r.members),
}

export const shoppingService = {
  list: (hid: string) =>
    api.get<{ items: ShoppingItem[] }>(`${base(hid)}/shopping`).then((r) => r.items),
  create: (hid: string, body: ShoppingItemInput) =>
    api.post<{ item: ShoppingItem }>(`${base(hid)}/shopping`, body).then((r) => r.item),
  update: (hid: string, id: string, body: ShoppingItemInput) =>
    api.patch<{ item: ShoppingItem }>(`${base(hid)}/shopping/${id}`, body).then((r) => r.item),
  setStatus: (hid: string, id: string, status: ShoppingItem['status'], priceCents?: number) =>
    api
      .post<{ item: ShoppingItem }>(`${base(hid)}/shopping/${id}/status`, { status, priceCents })
      .then((r) => r.item),
  remove: (hid: string, id: string) => api.delete(`${base(hid)}/shopping/${id}`),
  clearBought: (hid: string) => api.post<{ count: number }>(`${base(hid)}/shopping/clear-bought`),
}

export const calendarService = {
  range: (hid: string, from: string, to: string) =>
    api.get<{ events: CalendarEvent[]; occurrences: Occurrence[] }>(
      `${base(hid)}/calendar?${new URLSearchParams({ from, to })}`,
    ),
  create: (hid: string, body: EventInput) =>
    api.post<{ event: CalendarEvent }>(`${base(hid)}/events`, body).then((r) => r.event),
  update: (hid: string, id: string, body: EventInput) =>
    api.patch<{ event: CalendarEvent }>(`${base(hid)}/events/${id}`, body).then((r) => r.event),
  remove: (hid: string, id: string) => api.delete(`${base(hid)}/events/${id}`),
}

export const noteService = {
  list: (hid: string, archived: boolean) =>
    api.get<{ notes: Note[] }>(`${base(hid)}/notes?archived=${archived}`).then((r) => r.notes),
  create: (hid: string, body: { content: string; color: NoteColor; pinned: boolean }) =>
    api.post<{ note: Note }>(`${base(hid)}/notes`, body).then((r) => r.note),
  update: (hid: string, id: string, body: { content: string; color: NoteColor }) =>
    api.patch<{ note: Note }>(`${base(hid)}/notes/${id}`, body).then((r) => r.note),
  pin: (hid: string, id: string, pinned: boolean) =>
    api.post<{ note: Note }>(`${base(hid)}/notes/${id}/pin`, { pinned }).then((r) => r.note),
  archive: (hid: string, id: string, archived: boolean) =>
    api.post<{ note: Note }>(`${base(hid)}/notes/${id}/archive`, { archived }).then((r) => r.note),
  remove: (hid: string, id: string) => api.delete(`${base(hid)}/notes/${id}`),
}

export const expenseService = {
  list: (hid: string) =>
    api.get<{ expenses: Expense[] }>(`${base(hid)}/expenses`).then((r) => r.expenses),
  create: (hid: string, body: ExpenseInput) => api.post(`${base(hid)}/expenses`, body),
  update: (hid: string, id: string, body: ExpenseInput) =>
    api.patch(`${base(hid)}/expenses/${id}`, body),
  remove: (hid: string, id: string) => api.delete(`${base(hid)}/expenses/${id}`),
  balances: (hid: string) =>
    api.get<{ balances: Balance[]; suggestions: SuggestedPayment[]; beerRequests: BeerRequest[] }>(
      `${base(hid)}/balances`,
    ),
  askForBeer: (hid: string, fromUserId: string, amountCents: number) =>
    api.post(`${base(hid)}/beer-requests`, { fromUserId, amountCents }),
  beerReceived: (hid: string, id: string) => api.post(`${base(hid)}/beer-requests/${id}/received`),
  cancelBeer: (hid: string, id: string) => api.delete(`${base(hid)}/beer-requests/${id}`),
  settlements: (hid: string) =>
    api.get<{ settlements: Settlement[] }>(`${base(hid)}/settlements`).then((r) => r.settlements),
  createSettlement: (hid: string, body: SettlementInput) =>
    api.post(`${base(hid)}/settlements`, body),
  removeSettlement: (hid: string, id: string) => api.delete(`${base(hid)}/settlements/${id}`),
}
