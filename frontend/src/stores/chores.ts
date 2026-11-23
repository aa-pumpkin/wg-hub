import { defineStore } from 'pinia'
import { ref } from 'vue'
import { choreService } from '@/services/modules'
import type { Chore, Occurrence } from '@/types'
import { useAuthStore } from './auth'

export const useChoreStore = defineStore('chores', () => {
  const series = ref<Chore[]>([])
  const open = ref<Occurrence[]>([])
  const history = ref<Occurrence[]>([])
  const loadedFor = ref('')

  async function load(householdId: string) {
    const [chores, pending, done] = await Promise.all([
      choreService.list(householdId),
      choreService.occurrences(householdId, { status: 'PENDING', limit: '300' }),
      choreService.occurrences(householdId, { status: 'COMPLETED', limit: '50' }),
    ])
    series.value = chores
    open.value = pending
    history.value = done
    loadedFor.value = householdId
  }

  /** Optimistic: the row reacts at once and comes back if the server says no. */
  async function complete(householdId: string, occurrence: Occurrence) {
    const before = open.value
    open.value = before.filter((item) => item.id !== occurrence.id)
    try {
      const result = await choreService.complete(householdId, occurrence.id)
      history.value = [result.occurrence, ...history.value]
      return result
    } catch (error) {
      open.value = before
      throw error
    }
  }

  async function reopen(householdId: string, occurrence: Occurrence) {
    await choreService.act(householdId, occurrence.id, 'reopen')
    await load(householdId)
  }

  async function skip(householdId: string, occurrence: Occurrence) {
    await choreService.act(householdId, occurrence.id, 'skip')
    open.value = open.value.filter((item) => item.id !== occurrence.id)
  }

  async function reassign(householdId: string, occurrence: Occurrence, assigneeId: string | null) {
    const updated = await choreService.reassign(householdId, occurrence.id, assigneeId)
    open.value = open.value.map((item) => (item.id === updated.id ? updated : item))
  }

  function mine(items: readonly Occurrence[]) {
    const userId = useAuthStore().user?.id
    return items.filter((item) => item.assignee?.id === userId)
  }

  return { series, open, history, loadedFor, load, complete, reopen, skip, reassign, mine }
})
