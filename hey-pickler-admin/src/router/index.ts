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
          path: 'venues',
          name: 'Venues',
          component: () => import('@/views/venues/VenueListView.vue'),
          meta: { title: '场馆', icon: 'Location', group: '场馆管理' }
        },
        {
          path: 'venues/:id',
          name: 'VenueDetail',
          component: () => import('@/views/venues/VenueFormView.vue'),
          meta: { title: '场馆编辑', hidden: true }
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
          meta: { title: '积分与排名', icon: 'Trophy', group: '积分与排名' }
        },
        {
          path: 'seasons',
          redirect: '/rankings'
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
        },
        {
          path: 'dict',
          name: 'Dict',
          component: () => import('@/views/dict/DictListView.vue'),
          meta: { title: '字典管理', icon: 'Collection', group: '系统' }
        },
        {
          path: 'tier',
          name: 'Tier',
          component: () => import('@/views/tier/TierListView.vue'),
          meta: { title: '段位管理', icon: 'Medal', group: '系统' }
        },
        {
          path: 'brand',
          name: 'Brand',
          component: () => import('@/views/brand/BrandView.vue'),
          meta: { title: '品牌管理', icon: 'Brush', group: '系统' }
        },
        {
          path: 'notifications',
          name: 'Notifications',
          component: () => import('@/views/notifications/NotificationsView.vue'),
          meta: { title: '通知中心', icon: 'Bell', group: '系统' }
        },
        {
          path: 'analytics',
          name: 'Analytics',
          component: () => import('@/views/admin/AdminAnalyticsView.vue'),
          meta: { title: '数据分析', icon: 'DataAnalysis', group: '数据' }
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
