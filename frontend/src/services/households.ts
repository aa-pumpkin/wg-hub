import type {
  Household,
  HouseholdListItem,
  Invitation,
  InvitationPreview,
  Member,
  MemberStats,
  Role,
} from '@/types'
import { api } from './api'

export interface HouseholdDetail {
  household: Household
  role: Role
  members: Member[]
}

export interface HouseholdInput {
  name: string
  address?: string
  icon?: string
  timezone?: string
  beerPriceCents?: number
  dishwasherTarget?: number | null
}

const base = (id: string) => `/households/${id}`

export const householdService = {
  list: () =>
    api.get<{ households: HouseholdListItem[] }>('/households').then((r) => r.households),
  create: (body: HouseholdInput) =>
    api.post<{ household: Household }>('/households', body).then((r) => r.household),
  detail: (id: string) => api.get<HouseholdDetail>(base(id)),
  update: (id: string, body: HouseholdInput) =>
    api.patch<{ household: Household }>(base(id), body).then((r) => r.household),
  remove: (id: string) => api.delete(base(id)),
  leave: (id: string) => api.post<void>(`${base(id)}/leave`),
  removeMember: (id: string, userId: string) => api.delete(`${base(id)}/members/${userId}`),
  setFrozen: (id: string, userId: string, frozen: boolean) =>
    api.post(`${base(id)}/members/${userId}/freeze`, { frozen }),
  setRole: (id: string, userId: string, role: Role) =>
    api.patch(`${base(id)}/members/${userId}`, { role }),
  createPasswordReset: (id: string, userId: string) =>
    api.post<{ token: string; expiresAt: string }>(`${base(id)}/members/${userId}/password-reset`),
  stats: (id: string) => api.get<MemberStats>(`${base(id)}/members/stats`),
  invitations: (id: string) =>
    api.get<{ invitations: Invitation[] }>(`${base(id)}/invitations`).then((r) => r.invitations),
  createInvitation: (id: string, expiresInDays: number | null) =>
    api
      .post<{ invitation: Invitation }>(`${base(id)}/invitations`, { expiresInDays })
      .then((r) => r.invitation),
  revokeInvitation: (id: string, invitationId: string) =>
    api.delete(`${base(id)}/invitations/${invitationId}`),
  previewInvitation: (code: string) =>
    api.get<{ invitation: InvitationPreview }>(`/invitations/${code}`).then((r) => r.invitation),
  acceptInvitation: (code: string) =>
    api.post<{ householdId: string }>(`/invitations/${code}/accept`).then((r) => r.householdId),
}
