import '@/assets/main.css'
import { createPinia } from 'pinia'
import { createApp } from 'vue'
import App from './App.vue'
import { i18n } from './i18n'
import { router } from './router'
import { setUnauthorizedHandler } from './services/api'
import { useAuthStore } from './stores/auth'
import { useHouseholdStore } from './stores/household'

const app = createApp(App)
const pinia = createPinia()
app.use(pinia)
app.use(i18n)
app.use(router)

// An expired session sends the person to login and back to where they were.
setUnauthorizedHandler(() => {
  const auth = useAuthStore(pinia)
  if (!auth.user) return
  auth.setUser(null)
  useHouseholdStore(pinia).reset()
  void router.push({ name: 'login', query: { redirect: router.currentRoute.value.fullPath } })
})

// The service worker updates in the background. When a new version takes over, reload, so
// nobody keeps looking at the old one. No controller yet means this is the first visit.
if ('serviceWorker' in navigator && navigator.serviceWorker.controller) {
  navigator.serviceWorker.addEventListener('controllerchange', () => location.reload())
}

// An app left open on a phone only learns about updates if it asks.
document.addEventListener('visibilitychange', () => {
  if (document.visibilityState === 'visible') {
    void navigator.serviceWorker?.getRegistration().then((registration) => registration?.update())
  }
})

app.mount('#app')
