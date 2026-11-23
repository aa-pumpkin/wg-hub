import { defineStore } from 'pinia'
import { ref } from 'vue'
import { dashboardService } from '@/services/modules'
import type { Dashboard, OccurrenceStatus } from '@/types'

export const useDashboardStore = defineStore('dashboard', () => {
  const data = ref<Dashboard | null>(null)
  let loadedFor = ''

  async function load(householdId: string) {
    // Never show one WG's data while another one is loading.
    if (loadedFor !== householdId) data.value = null
    const next = await dashboardService.get(householdId)
    data.value = next
    loadedFor = householdId
  }

  function setTaskStatus(id: string, status: OccurrenceStatus) {
    if (!data.value) return
    data.value.tasks = data.value.tasks.map((task) => (task.id === id ? { ...task, status } : task))
  }

  function removeShoppingItem(id: string) {
    if (!data.value) return
    data.value.shopping.items = data.value.shopping.items.filter((item) => item.id !== id)
    data.value.shopping.pendingCount -= 1
  }

  return { data, load, setTaskStatus, removeShoppingItem }
})
