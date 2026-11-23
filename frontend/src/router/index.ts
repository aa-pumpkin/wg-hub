import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useHouseholdStore } from '@/stores/household'

declare module 'vue-router' {
  interface RouteMeta {
    /** Reachable without login. */
    public?: boolean
    /** Only makes sense while logged out (login, register). */
    guestOnly?: boolean
  }
}

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'home', component: { render: () => null } },
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { public: true, guestOnly: true },
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('@/views/RegisterView.vue'),
      meta: { public: true, guestOnly: true },
    },
    {
      path: '/invite/:code',
      name: 'invite',
      component: () => import('@/views/InviteView.vue'),
      meta: { public: true },
    },
    {
      path: '/reset/:token',
      name: 'reset',
      component: () => import('@/views/ResetView.vue'),
      meta: { public: true },
    },
    { path: '/welcome', name: 'welcome', component: () => import('@/views/WelcomeView.vue') },
    {
      path: '/w/:householdId',
      component: () => import('@/layouts/AppLayout.vue'),
      children: [
        { path: '', redirect: { name: 'dashboard' } },
        { path: 'dashboard', name: 'dashboard', component: () => import('@/views/DashboardView.vue') },
        { path: 'chores', name: 'chores', component: () => import('@/views/ChoresView.vue') },
        { path: 'shopping', name: 'shopping', component: () => import('@/views/ShoppingView.vue') },
        { path: 'calendar', name: 'calendar', component: () => import('@/views/CalendarView.vue') },
        { path: 'notes', name: 'notes', component: () => import('@/views/NotesView.vue') },
        { path: 'expenses', name: 'expenses', component: () => import('@/views/ExpensesView.vue') },
        { path: 'points', name: 'points', component: () => import('@/views/PointsView.vue') },
        { path: 'dishwasher', name: 'dishwasher', component: () => import('@/views/DishwasherView.vue') },
        { path: 'members', name: 'members', component: () => import('@/views/MembersView.vue') },
        { path: 'settings', name: 'settings', component: () => import('@/views/SettingsView.vue') },
        { path: 'profile', name: 'profile', component: () => import('@/views/ProfileView.vue') },
        { path: 'more', name: 'more', component: () => import('@/views/MoreView.vue') },
      ],
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () => import('@/views/NotFoundView.vue'),
      meta: { public: true },
    },
  ],
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  await auth.ensureChecked()

  if (!auth.user && !to.meta.public) {
    return to.name === 'home'
      ? { name: 'login' }
      : { name: 'login', query: { redirect: to.fullPath } }
  }
  if (auth.user && to.meta.guestOnly) return { name: 'home' }
  if (auth.user && to.name === 'home') {
    const householdId = await useHouseholdStore().pickLanding()
    return householdId ? { name: 'dashboard', params: { householdId } } : { name: 'welcome' }
  }
  return true
})
