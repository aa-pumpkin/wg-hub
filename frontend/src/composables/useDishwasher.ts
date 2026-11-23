import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAction } from '@/composables/useForm'
import { useLive } from '@/composables/useLive'
import { dishwasherService } from '@/services/modules'
import { useAuthStore } from '@/stores/auth'
import { useHouseholdStore } from '@/stores/household'
import { useUiStore } from '@/stores/ui'
import type { DishwasherState } from '@/types'

export function useDishwasher() {
  const { t } = useI18n()
  const household = useHouseholdStore()
  const auth = useAuthStore()
  const ui = useUiStore()
  const act = useAction()

  const state = ref<DishwasherState | null>(null)
  const busy = ref(false)

  const load = () =>
    act(async () => {
      state.value = await dishwasherService.get(household.id)
    })
  onMounted(load)
  useLive(['dishwasher', 'members'], load)

  const mine = computed(() => state.value?.members.find((row) => row.user.id === auth.user?.id))

  async function tap() {
    if (busy.value) return
    busy.value = true
    await act(async () => {
      const result = await dishwasherService.tap(household.id)
      if (result.crown) ui.toast(t('dishwasher.crownWon'))
      else if (result.finished) ui.toast(t('dishwasher.crownOf', { name: result.winnerName ?? '' }))
      await load()
    })
    busy.value = false
  }

  const undo = () =>
    act(async () => {
      await dishwasherService.undo(household.id)
      await load()
    })

  return { state, mine, busy, tap, undo }
}
