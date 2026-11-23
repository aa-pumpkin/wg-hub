export type LiveModule =
  | 'chores'
  | 'shopping'
  | 'calendar'
  | 'notes'
  | 'expenses'
  | 'members'
  | 'household'
  | 'dishwasher'

interface Subscriber {
  userId: string
  send: (module: LiveModule) => void
  close: () => void
}

/**
 * In-process pub/sub behind the server-sent events stream. Events only say which
 * module changed; clients then refetch through the regular permission-checked API.
 * A single backend instance is enough for this app. Scaling out would mean swapping
 * this for Postgres LISTEN/NOTIFY.
 */
class LiveBus {
  private readonly subscribers = new Map<string, Set<Subscriber>>()

  subscribe(householdId: string, subscriber: Subscriber): () => void {
    const set = this.subscribers.get(householdId) ?? new Set<Subscriber>()
    set.add(subscriber)
    this.subscribers.set(householdId, set)
    return () => {
      set.delete(subscriber)
      if (set.size === 0) this.subscribers.delete(householdId)
    }
  }

  publish(householdId: string, module: LiveModule): void {
    this.subscribers.get(householdId)?.forEach((subscriber) => subscriber.send(module))
  }

  /** Ends the streams of a user who no longer belongs to the household. */
  disconnect(householdId: string, userId: string): void {
    this.subscribers.get(householdId)?.forEach((subscriber) => {
      if (subscriber.userId === userId) subscriber.close()
    })
  }
}

export const live = new LiveBus()
