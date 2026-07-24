// @vitest-environment jsdom
import { describe, it, expect, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useAuthStore } from './auth'

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

// 构造测试 JWT:header.payload.signature 三段,只 payload 段被解析
function makeJwt(expSeconds: number): string {
  const payload = JSON.stringify({ exp: expSeconds })
  // base64url → base64 转换给 atob
  const b64 = btoa(payload).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_')
  return `header.${b64}.signature`
}

beforeEach(() => {
  setActivePinia(createPinia())
  globalThis.localStorage.clear()
})

describe('useAuthStore — initial state', () => {
  it('with no localStorage token → token is null', () => {
    const store = useAuthStore()
    expect(store.token).toBeNull()
  })

  it('with localStorage admin_token → token is initialized from it', () => {
    globalThis.localStorage.setItem('admin_token', 'init-token')
    // 重新 setActivePinia 让 store 重新创建并读 localStorage
    setActivePinia(createPinia())
    const store = useAuthStore()
    expect(store.token).toBe('init-token')
  })

  it('admin starts as null', () => {
    const store = useAuthStore()
    expect(store.admin).toBeNull()
  })
})

describe('setToken', () => {
  it('updates token ref', () => {
    const store = useAuthStore()
    store.setToken('new-token')
    expect(store.token).toBe('new-token')
  })

  it('persists to localStorage admin_token', () => {
    const store = useAuthStore()
    store.setToken('persisted-token')
    expect(globalThis.localStorage.getItem('admin_token')).toBe('persisted-token')
  })
})

describe('setAdmin', () => {
  it('updates admin ref', () => {
    const store = useAuthStore()
    const admin = { id: 1, username: 'admin', role: 'SUPER_ADMIN' as const }
    store.setAdmin(admin)
    // Pinia reactive proxy 包裹原对象,toBe 走 Object.is 失败,改用 toEqual
    expect(store.admin).toEqual(admin)
  })
})

describe('logout', () => {
  it('clears token + admin', () => {
    const store = useAuthStore()
    store.setToken('t')
    store.setAdmin({ id: 1, username: 'u', role: 'ADMIN' as const })
    store.logout()
    expect(store.token).toBeNull()
    expect(store.admin).toBeNull()
  })

  it('clears 3 localStorage keys', () => {
    const store = useAuthStore()
    globalThis.localStorage.setItem('admin_token', 't')
    globalThis.localStorage.setItem('admin_info', 'i')
    globalThis.localStorage.setItem('admin_role', 'r')
    store.logout()
    expect(globalThis.localStorage.getItem('admin_token')).toBeNull()
    expect(globalThis.localStorage.getItem('admin_info')).toBeNull()
    expect(globalThis.localStorage.getItem('admin_role')).toBeNull()
  })

  it('idempotent when nothing to clear', () => {
    const store = useAuthStore()
    expect(() => store.logout()).not.toThrow()
  })
})

describe('isAuthenticated', () => {
  it('no token → false', () => {
    const store = useAuthStore()
    expect(store.isAuthenticated()).toBe(false)
  })

  it('valid non-expired token → true', () => {
    const store = useAuthStore()
    const future = Math.floor(Date.now() / 1000) + 3600 // 1h ahead
    store.setToken(makeJwt(future))
    expect(store.isAuthenticated()).toBe(true)
  })

  it('expired token → false + triggers logout (clears localStorage)', () => {
    const store = useAuthStore()
    globalThis.localStorage.setItem('admin_info', 'leftover')
    globalThis.localStorage.setItem('admin_role', 'leftover')
    const past = Math.floor(Date.now() / 1000) - 60 // 1min ago
    store.setToken(makeJwt(past))
    expect(store.isAuthenticated()).toBe(false)
    expect(store.token).toBeNull()
    expect(globalThis.localStorage.getItem('admin_info')).toBeNull()
    expect(globalThis.localStorage.getItem('admin_role')).toBeNull()
  })

  it('malformed JWT (catch branch) → treated as expired', () => {
    const store = useAuthStore()
    store.setToken('not.a.valid.jwt.at.all')
    expect(store.isAuthenticated()).toBe(false)
  })

  it('token within 30s skew window → still considered expired (skew grace)', () => {
    // 语义:即使 exp 还在未来,提前 30s 算过期(给客户端"提前续签"时间)
    // exp = now+10s: exp*1000 = now+10000; Date.now()+30000 = now+30000.
    // 10000 < 30000 → true → expired
    const store = useAuthStore()
    const withinSkew = Math.floor(Date.now() / 1000) + 10
    store.setToken(makeJwt(withinSkew))
    expect(store.isAuthenticated()).toBe(false)
  })

  it('token beyond 30s skew window but still future → still valid', () => {
    // exp = now+60s: exp*1000 = now+60000; Date.now()+30000 = now+30000.
    // 60000 < 30000 → false → not expired
    const store = useAuthStore()
    const beyondSkew = Math.floor(Date.now() / 1000) + 60
    store.setToken(makeJwt(beyondSkew))
    expect(store.isAuthenticated()).toBe(true)
  })
})

describe('isTokenExpired — direct unit tests (skew behavior)', () => {
  // isTokenExpired 是模块私有函数,通过 isAuthenticated 间接覆盖;
  // 此处为补强边界。
  it('token with valid future exp (1h) → not expired', () => {
    // 直接验证:用 isAuthenticated 作探针(只能间接测私有函数)
    const store = useAuthStore()
    const farFuture = Math.floor(Date.now() / 1000) + 3600
    store.setToken(makeJwt(farFuture))
    expect(store.isAuthenticated()).toBe(true)
  })

  it('token with exp = 0 (epoch 1970) → expired', () => {
    const store = useAuthStore()
    store.setToken(makeJwt(0))
    expect(store.isAuthenticated()).toBe(false)
  })
})

describe('integration: setToken then isAuthenticated', () => {
  it('setToken → isAuthenticated true → logout → isAuthenticated false', () => {
    const store = useAuthStore()
    const future = Math.floor(Date.now() / 1000) + 3600
    store.setToken(makeJwt(future))
    expect(store.isAuthenticated()).toBe(true)
    store.logout()
    expect(store.isAuthenticated()).toBe(false)
    expect(store.token).toBeNull()
  })

  it('uses vi.useFakeTimers not needed; tests use real Date.now()', () => {
    // 占位说明:本套件全部使用真实时钟,因为 token 时效窗口是相对 Date.now() 计算的;
    // 如果改用 vi.useFakeTimers,需要在 beforeEach 设固定时间。
    expect(true).toBe(true)
  })
})