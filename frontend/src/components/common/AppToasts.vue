<script setup lang="ts">
import { CheckCircle2, CircleAlert } from 'lucide-vue-next'
import { nextTick, onMounted, ref, watch } from 'vue'
import { useUiStore } from '@/stores/ui'

const ui = useUiStore()
const host = ref<HTMLElement>()

/** Re-entering the top layer keeps toasts above any dialog opened after them. */
function raise() {
  const element = host.value
  if (!element?.showPopover) return
  if (element.matches(':popover-open')) element.hidePopover()
  element.showPopover()
}

onMounted(raise)
watch(
  () => ui.toasts.length,
  () => nextTick(raise),
)
</script>

<template>
  <div
    ref="host"
    popover="manual"
    class="pointer-events-none fixed inset-x-0 top-auto bottom-[calc(5rem+env(safe-area-inset-bottom))] m-0 flex w-full flex-col items-center gap-2 bg-transparent p-0 md:bottom-6"
    role="status"
    aria-live="polite"
  >
    <TransitionGroup
      enter-active-class="transition duration-150"
      enter-from-class="translate-y-2 opacity-0"
      leave-active-class="transition duration-100"
      leave-to-class="opacity-0"
    >
      <button
        v-for="toast in ui.toasts"
        :key="toast.id"
        type="button"
        class="pointer-events-auto flex max-w-[min(90vw,26rem)] items-center gap-2 rounded-xl px-3.5 py-2.5 text-left text-sm shadow-lg"
        :class="toast.kind === 'error' ? 'bg-danger text-bg' : 'bg-fg text-bg'"
        @click="ui.dismissToast(toast.id)"
      >
        <CircleAlert v-if="toast.kind === 'error'" class="size-4 shrink-0" aria-hidden="true" />
        <CheckCircle2 v-else class="size-4 shrink-0" aria-hidden="true" />
        {{ toast.message }}
      </button>
    </TransitionGroup>
  </div>
</template>
