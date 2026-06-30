import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

// Augment vue-router RouteMeta so the admin layout can read menu metadata
// (title / icon / group / hidden) off every route record.
declare module 'vue-router' {
  interface RouteMeta {
    requiresAuth?: boolean
    title?: string
    icon?: string
    group?: string
    hidden?: boolean
  }
}

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
          component: () => import('@/views/dashboard/DashboardView.vue'),
          meta: { title: '工作台', icon: 'DataBoard', group: '运营管理' }
        },
        {
          path: 'events',
          name: 'Events',
          component: () => import('@/views/events/EventListView.vue'),
          meta: { title: '竞技赛事', icon: 'Calendar', group: '运营管理' }
        },
        {
          path: 'events/:id',
          name: 'EventDetail',
          component: () => import('@/views/events/EventDetailView.vue'),
          meta: { title: '赛事详情', hidden: true }
        },
        {
          path: 'activities',
          name: 'Activities',
          component: () => import('@/views/activities/ActivityListView.vue'),
          meta: { title: '社交活动', icon: 'Football', group: '运营管理' }
        },
        {
          path: 'users',
          name: 'Users',
          component: () => import('@/views/users/UserListView.vue'),
          meta: { title: '用户管理', icon: 'User', group: '运营管理' }
        },
        {
          path: 'rankings',
          name: 'Rankings',
          component: () => import('@/views/rankings/RankingView.vue'),
          meta: { title: '排名管理', icon: 'Trophy', group: '积分与赛季' }
        },
        {
          path: 'seasons',
          name: 'Seasons',
          component: () => import('@/views/seasons/SeasonView.vue'),
          meta: { title: '赛季管理', icon: 'Timer', group: '积分与赛季' }
        },
        {
          path: 'banners',
          name: 'Banners',
          component: () => import('@/views/banners/BannerListView.vue'),
          meta: { title: 'Banner 管理', icon: 'Picture', group: '内容运营' }
        },
        {
          path: 'admins',
          name: 'Admins',
          component: () => import('@/views/admins/AdminListView.vue'),
          meta: { title: '管理员管理', icon: 'UserFilled', group: '系统' }
        },
        {
          path: 'ban-records',
          name: 'BanRecords',
          component: () => import('@/views/ban-records/BanRecordListView.vue'),
          meta: { title: '封禁记录', icon: 'Document', group: '系统' }
        },
        {
          path: 'admin-logs',
          name: 'AdminLogs',
          component: () => import('@/views/admin-logs/AdminLogListView.vue'),
          meta: { title: '操作日志', icon: 'List', group: '系统' }
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
