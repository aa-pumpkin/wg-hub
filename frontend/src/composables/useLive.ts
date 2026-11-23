import { onMounted, onUnmounted } from 'vue'
import type { LiveModule } from '@/types'

type Listener = () => void

const listeners = new Map<LiveModule, Set<Listener>>()
let source: EventSource | null = null
let wasConnected = false

function notify(module: LiveModule) {
  listeners.get(module)?.forEach((listener) => listener())
}

function notifyAll() {
  listeners.forEach((set) => set.forEach((listener) => listener()))
}

/** Opens the household's event stream. The layout calls this; views use useLive. */
export function connectLive(householdId: string) {
  disconnectLive()
  wasConnected = false
  source = new EventSource(`/api/households/${householdId}/stream`)
  source.onopen = () => {
    // Events may have been missed while the connection was down.
    if (wasConnected) notifyAll()
    wasConnected = true
  }
  source.onmessage = (message: MessageEvent<string>) => {
    const { module } = JSON.parse(message.data) as { module: LiveModule }
    notify(module)
  }
}

export function disconnectLive() {
  source?.close()
  source = null
}

/** Phones often suspend background tabs: catch up when the app becomes visible again. */
document.addEventListener('visibilitychange', () => {
  if (document.visibilityState === 'visible' && source) notifyAll()
})

/** Calls `refresh` (debounced) whenever one of the modules changes on the server. */
export function useLive(modules: LiveModule[], refresh: () => void) {
  let timer: ReturnType<typeof setTimeout> | undefined
  const listener = () => {
    clearTimeout(timer)
    timer = setTimeout(refresh, 150)
  }
  onMounted(() => {
    for (const module of modules) {
      const set = listeners.get(module) ?? new Set<Listener>()
      set.add(listener)
      listeners.set(module, set)
    }
  })
  onUnmounted(() => {
    clearTimeout(timer)
    for (const module of modules) listeners.get(module)?.delete(listener)
  })
}
