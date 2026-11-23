import type { Locale, User } from '@/types'
import { api } from './api'

interface UserResponse {
  user: User
}

export const authService = {
  me: () => api.get<UserResponse>('/auth/me').then((r) => r.user),
  login: (body: { username: string; password: string }) =>
    api.post<UserResponse>('/auth/login', body).then((r) => r.user),
  register: (body: { username: string; displayName: string; password: string; locale: Locale }) =>
    api.post<UserResponse>('/auth/register', body).then((r) => r.user),
  resetPassword: (body: { token: string; newPassword: string }) =>
    api.post<UserResponse>('/auth/reset', body).then((r) => r.user),
  logout: () => api.post<void>('/auth/logout'),
  updateProfile: (body: Partial<Pick<User, 'displayName' | 'username' | 'avatarColor' | 'locale'>>) =>
    api.patch<UserResponse>('/me', body).then((r) => r.user),
  changePassword: (body: { currentPassword: string; newPassword: string }) =>
    api.post<void>('/me/password', body),
}
