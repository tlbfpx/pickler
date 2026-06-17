import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/views/login/LoginView.vue'),
      meta: { requiresAuth: false }
    },
    {
      path: '/',
      component: () => import('@/components/layout/AppLayout.vue'),
      meta: { requiresAuth: true },
      children: [
        {
          path: '',
          name: 'Dashboard',
          component: () => import('@/views/dashboard/DashboardView.vue')
        },
        {
          path: 'users',
          name: 'Users',
          component: () => import('@/views/users/UserListView.vue')
        },
        {
          path: 'events',
          name: 'Events',
          component: () => import('@/views/events/EventListView.vue')
        },
        {
          path: 'activities',
          name: 'Activities',
          component: () => import('@/views/activities/ActivityListView.vue')
        },
        {
          path: 'rankings',
          name: 'Rankings',
          component: () => import('@/views/rankings/RankingView.vue')
        },
        {
          path: 'banners',
          name: 'Banners',
          component: () => import('@/views/banners/BannerListView.vue')
        },
        {
          path: 'admins',
          name: 'Admins',
          component: () => import('@/views/admins/AdminListView.vue')
        },
        {
          path: 'ban-records',
          name: 'BanRecords',
          component: () => import('@/views/ban-records/BanRecordListView.vue')
        },
        {
          path: 'admin-logs',
          name: 'AdminLogs',
          component: () => import('@/views/admin-logs/AdminLogListView.vue')
        }
      ]
    }
  ]
})

// Navigation guard
router.beforeEach((to, from, next) => {
  const authStore = useAuthStore()
  const requiresAuth = to.meta.requiresAuth !== false

  if (requiresAuth && !authStore.isAuthenticated()) {
    next('/login')
  } else if (to.path === '/login' && authStore.isAuthenticated()) {
    next('/')
  } else {
    next()
  }
})

export default router
