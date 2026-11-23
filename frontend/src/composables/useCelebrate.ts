import { useI18n } from 'vue-i18n'
import { useUiStore } from '@/stores/ui'
import type { CompletionResult } from '@/types'

/** A small reward after finishing a task. */
export function useCelebrate() {
  const { t } = useI18n()
  const ui = useUiStore()
  return (result: CompletionResult) => {
    if (result.earned > 0) ui.toast(t('points.earned', { points: result.earned }))
    if (result.leveledUp) {
      ui.toast(
        t('points.levelUp', {
          level: result.level,
          title: t(`points.levels.l${Math.min(result.level, 10)}`),
        }),
      )
    }
  }
}
