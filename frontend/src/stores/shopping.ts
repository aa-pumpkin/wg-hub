import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { shoppingService } from '@/services/modules'
import type { ShoppingItem } from '@/types'

export const useShoppingStore = defineStore('shopping', () => {
  const items = ref<ShoppingItem[]>([])
  const loadedFor = ref('')

  const pending = computed(() => items.value.filter((item) => item.status === 'PENDING'))
  const bought = computed(() => items.value.filter((item) => item.status === 'BOUGHT'))
  const forWg = computed(() => pending.value.filter((item) => !item.personal))
  const personal = computed(() => pending.value.filter((item) => item.personal))

  async function load(householdId: string) {
    items.value = await shoppingService.list(householdId)
    loadedFor.value = householdId
  }

  /** Optimistic toggle between pending and bought. */
  async function toggle(householdId: string, item: ShoppingItem) {
    const status = item.status === 'PENDING' ? 'BOUGHT' : 'PENDING'
    const before = items.value
    items.value = before.map((other) => (other.id === item.id ? { ...other, status } : other))
    try {
      const updated = await shoppingService.setStatus(householdId, item.id, status)
      items.value = items.value.map((other) => (other.id === item.id ? updated : other))
    } catch (error) {
      items.value = before
      throw error
    }
  }

  async function remove(householdId: string, id: string) {
    await shoppingService.remove(householdId, id)
    items.value = items.value.filter((item) => item.id !== id)
  }

  async function clearBought(householdId: string) {
    await shoppingService.clearBought(householdId)
    items.value = pending.value
  }

  return { items, loadedFor, pending, bought, forWg, personal, load, toggle, remove, clearBought }
})
