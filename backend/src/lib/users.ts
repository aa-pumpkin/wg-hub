/** The public fields of a user, for embedding in API responses. */
export const userSummary = {
  select: { id: true, displayName: true, avatarColor: true },
} as const
