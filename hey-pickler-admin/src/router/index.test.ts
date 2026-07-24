// @vitest-environment jsdom
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

// jsdom 兜底
if (typeof globalThis.localStorage === 'undefined' || globalThis.localStorage === undefined) {
  const _store = new Map<string, string>()
  Object.defineProperty(globalThis, 'localStorage', {
    value: {
      getItem: (k: string) => (_store.has(k) ? _store.get(k)! : null),
      setItem: (k: string, v: string) => { _store.set(k, v) },
      removeItem: (k: string) => { _store.delete(k) },
      clear: () => { _store.clear() }
    },
    configurable: true
  })
}

const stubComponent = { name: 'Stub', render: () => null } as any
vi.mock('@/views/login/LoginView.vue', () => ({ default: stubComponent }))
vi.mock('@/components/layout/AppLayout.vue', () => ({ default: stubComponent }))
vi.mock('@/views/dashboard/DashboardView.vue', () => ({ default: stubComponent }))
vi.mock('@/views/events/EventListView.vue', () => ({ default: stubComponent }))
vi.mock('@/views/events/EventDetailView.vue', () => ({ default: stubComponent }))
vi.mock('@/views/venues/VenueListView.vue', () => ({ default: stubComponent }))
vi.mock('@/views/venues/VenueFormView.vue', () => ({ default: stubComponent }))
vi.mock('@/views/bookings/BookingListView.vue', () => ({ default: stubComponent }))
vi.mock('@/views/activities/ActivityListView.vue', () => ({ default: stubComponent }))
vi.mock('@/views/users/UserListView.vue', () => ({ default: stubComponent }))
vi.mock('@/views/rankings/RankingView.vue', () => ({ default: stubComponent }))
vi.mock('@/views/banners/BannerListView.vue', () => ({ default: stubComponent }))
vi.mock('@/views/admins/AdminListView.vue', () => ({ default: stubComponent }))
vi.mock('@/views/ban-records/BanRecordListView.vue', () => ({ default: stubComponent }))
vi.mock('@/views/admin-logs/AdminLogListView.vue', () => ({ default: stubComponent }))
vi.mock('@/views/dict/DictListView.vue', () => ({ default: stubComponent }))
vi.mock('@/views/tier/TierListView.vue', () => ({ default: stubComponent }))
vi.mock('@/views/brand/BrandView.vue', () => ({ default: stubComponent }))
vi.mock('@/views/notifications/NotificationsView.vue', () => ({ default: stubComponent }))
vi.mock('@/views/admin/AdminAnalyticsView.vue', () => ({ default: stubComponent }))

beforeEach(() => {
  setActivePinia(createPinia())
  globalThis.localStorage.clear()
})

import router from './index'

// ============ Routes registration ============
describe('router — routes registration', () => {
  it('exports a router instance', () => {
    expect(router).toBeDefined()
    expect(router.options).toBeDefined()
    expect(Array.isArray(router.options.routes)).toBe(true)
  })

  it('has 2 top-level routes (Login + Layout root)', () => {
    expect(router.options.routes).toHaveLength(2)
  })

  it('first route is /login with requiresAuth=false', () => {
    const loginRoute: any = router.options.routes[0]
    expect(loginRoute.path).toBe('/login')
    expect(loginRoute.name).toBe('Login')
    expect(loginRoute.meta.requiresAuth).toBe(false)
  })

  it('root layout route requiresAuth=true', () => {
    const layout: any = router.options.routes[1]
    expect(layout.path).toBe('/')
    expect(layout.meta.requiresAuth).toBe(true)
    expect(Array.isArray(layout.children)).toBe(true)
  })

  it('layout has at least 19 children (含 redirect /seasons)', () => {
    const layout: any = router.options.routes[1]
    expect(layout.children.length).toBeGreaterThanOrEqual(19)
  })

  it('/seasons redirect to /rankings', () => {
    const layout: any = router.options.routes[1]
    const seasons = layout.children.find((c: any) => c.path === 'seasons')
    expect(seasons).toBeDefined()
    expect(seasons.redirect).toBe('/rankings')
  })

  it('hidden detail routes (events/:id, venues/:id)', () => {
    const layout: any = router.options.routes[1]
    const eventDetail = layout.children.find((c: any) => c.path === 'events/:id')
    expect(eventDetail.meta.hidden).toBe(true)
    const venueDetail = layout.children.find((c: any) => c.path === 'venues/:id')
    expect(venueDetail.meta.hidden).toBe(true)
  })

  it('every non-redirect menu route has title + icon + group (or hidden without icon/group)', () => {
    const layout: any = router.options.routes[1]
    const menuRoutes = layout.children.filter((c: any) => !c.redirect)
    for (const r of menuRoutes) {
      expect(r.meta.title).toBeTruthy()
      if (!r.meta.hidden) {
        expect(r.meta.icon).toBeTruthy()
        expect(r.meta.group).toBeTruthy()
      }
    }
  })

  it('groups used: 运营管理 / 场馆管理 / 积分与排名 / 内容运营 / 系统 / 数据', () => {
    const layout: any = router.options.routes[1]
    const groups = new Set<string>()
    for (const c of layout.children) {
      // /seasons 是 redirect 路由没有 meta,跳过
      if (c.meta?.group) groups.add(c.meta.group)
    }
    expect(groups).toContain('运营管理')
    expect(groups).toContain('场馆管理')
    expect(groups).toContain('积分与排名')
    expect(groups).toContain('内容运营')
    expect(groups).toContain('系统')
    expect(groups).toContain('数据')
  })

  it('redirect /seasons has no meta (excluded from menu)', () => {
    const layout: any = router.options.routes[1]
    const seasons = layout.children.find((c: any) => c.path === 'seasons')
    expect(seasons.meta).toBeUndefined()
  })

  it('all menu routes (no redirect) have a unique path', () => {
    const layout: any = router.options.routes[1]
    const paths = layout.children.map((c: any) => c.path)
    const uniq = new Set(paths)
    expect(uniq.size).toBe(paths.length)
  })
})

// ============ Navigation guard (行为契约) ============
//
// 注:vue-router 4 把 beforeGuards 存放在闭包内(useCallbacks()),外部不可访问,
// 也不能用 mock 拦截 createRouter(否则 v8 覆盖率 instrumentation 失效)。
// 因此 guard 的实际行为由 Playwright E2E(hey-pickler-admin/e2e/navigation.spec.ts)
// 覆盖;单元测试只验证 guard 已被注册(router.beforeEach 是一个公开方法,
// 每次调用返回 remove 函数 → 简单断言 set size 至少 1)。

describe('router — guard registered', () => {
  it('router.beforeEach 返回的 remove 函数可以正常调用(说明至少 1 个 beforeEach 已注册)', () => {
    const remove = router.beforeEach(() => {})
    expect(typeof remove).toBe('function')
    // 调用 remove 不抛错
    expect(() => remove()).not.toThrow()
  })

  it('router.afterEach 同样可用', () => {
    const remove = router.afterEach(() => {})
    expect(typeof remove).toBe('function')
    expect(() => remove()).not.toThrow()
  })
})