import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { householdService, type HouseholdInput } from '@/services/households'
import type { Household, HouseholdListItem, Member, Role } from '@/types'

const LAST_KEY = 'wghub.lastHousehold'

export const useHouseholdStore = defineStore('household', () => {
  const list = ref<HouseholdListItem[]>([])
  const current = ref<Household | null>(null)
  const role = ref<Role>('MEMBER')
  const members = ref<Member[]>([])

  const id = computed(() => current.value?.id ?? '')
  const timezone = computed(() => current.value?.timezone ?? 'Europe/Berlin')
  const isAdmin = computed(() => role.value === 'ADMIN')

  async function loadList() {
    list.value = await householdService.list()
    return list.value
  }

  /** Throws if the household does not exist or the user is not a member. */
  async function open(householdId: string) {
    const detail = await householdService.detail(householdId)
    current.value = detail.household
    role.value = detail.role
    members.value = detail.members
    localStorage.setItem(LAST_KEY, householdId)
  }

  const refresh = () => (current.value ? open(current.value.id) : Promise.resolve())

  function close() {
    current.value = null
    members.value = []
  }

  /** The household to land on after login. */
  async function pickLanding(): Promise<string | null> {
    const households = await loadList()
    const last = localStorage.getItem(LAST_KEY)
    return households.find((h) => h.id === last)?.id ?? households[0]?.id ?? null
  }

  async function create(input: HouseholdInput) {
    const household = await householdService.create(input)
    await loadList()
    return household
  }

  async function update(input: HouseholdInput) {
    current.value = await householdService.update(id.value, input)
    await loadList()
  }

  function reset() {
    list.value = []
    close()
    localStorage.removeItem(LAST_KEY)
  }

  return {
    list,
    current,
    role,
    members,
    id,
    timezone,
    isAdmin,
    loadList,
    open,
    refresh,
    close,
    pickLanding,
    create,
    update,
    reset,
  }
})
