import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { pinia } from '../stores/pinia'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    redirect: '/groups',
  },
  {
    path: '/login',
    name: 'login',
    component: () => import('../pages/auth/LoginPage.vue'),
    meta: { guestOnly: true },
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('../pages/auth/RegisterPage.vue'),
    meta: { guestOnly: true },
  },
  {
    path: '/account/security',
    name: 'account-security',
    component: () => import('../pages/account/AccountSecurityPage.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/groups',
    name: 'groups',
    component: () => import('../pages/group/GroupsPage.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/documents',
    name: 'documents',
    component: () => import('../pages/document/DocumentPage.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/qa',
    name: 'qa',
    component: () => import('../pages/qa/QaPage.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/assistant',
    name: 'assistant',
    component: () => import('../pages/assistant/AssistantPage.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/admin',
    component: () => import('../pages/admin/AdminLayout.vue'),
    meta: { requiresAuth: true, requiresAdmin: true },
    children: [
      {
        path: '',
        redirect: '/admin/overview',
      },
      {
        path: 'overview',
        name: 'admin-overview',
        component: () => import('../pages/admin/AdminOverviewPage.vue'),
      },
      {
        path: 'users',
        name: 'admin-users',
        component: () => import('../pages/admin/users/AdminUsersPage.vue'),
      },
      {
        path: 'users/:userId',
        name: 'admin-user-detail',
        component: () => import('../pages/admin/users/AdminUserDetailPage.vue'),
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/groups',
  },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
})

router.beforeEach(async (to) => {
  const authStore = useAuthStore(pinia)
  const guestOnly = to.matched.some((record) => record.meta.guestOnly)
  const requiresAuth = to.matched.some((record) => record.meta.requiresAuth)

  if (!guestOnly && !requiresAuth) {
    return true
  }

  const currentUser = await authStore.bootstrap()

  if (guestOnly) {
    return currentUser === null ? true : authStore.resolveLandingPath(readRedirect(to.query.redirect))
  }

  if (currentUser === null) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  const roleRedirect = authStore.resolveRedirectForPath(to.path)
  return roleRedirect === null || roleRedirect === to.path ? true : roleRedirect
})

function readRedirect(redirect: unknown) {
  if (Array.isArray(redirect)) {
    return typeof redirect[0] === 'string' ? redirect[0] : null
  }
  return typeof redirect === 'string' ? redirect : null
}

export default router
