// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach } from 'vitest'

// jsdom 未注入时 polyfill localStorage(同 request.test.ts 兜底)
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

// mock ./request 模块,避免触发其拦截器(E2E 在 request.test.ts 已测过)
const requestPost = vi.fn()
vi.mock('./request', () => ({
  default: { post: (...args: unknown[]) => requestPost(...args) }
}))

beforeEach(() => {
  requestPost.mockReset()
  globalThis.localStorage.clear()
})

import { login, logout } from './auth'

describe('login', () => {
  it('calls request.post with url and data', () => {
    requestPost.mockReturnValue(Promise.resolve({ code: 0, data: null }))
    const payload = { username: 'admin', password: 'admin123' }
    login(payload)
    expect(requestPost).toHaveBeenCalledTimes(1)
    expect(requestPost).toHaveBeenCalledWith('/auth/login', payload)
  })

  it('forwards the request promise result (callers await)', async () => {
    const fakeRes = { code: 0, data: { token: 'tkn', admin: { id: 1, username: 'admin' } } }
    requestPost.mockReturnValue(Promise.resolve(fakeRes))
    const result = await login({ username: 'u', password: 'p' })
    expect(result).toBe(fakeRes)
  })
})

describe('logout', () => {
  it('removes admin_token from localStorage', async () => {
    globalThis.localStorage.setItem('admin_token', 'tkn-1')
    globalThis.localStorage.setItem('admin_info', 'info-1')
    globalThis.localStorage.setItem('unrelated', 'keep')
    await logout()
    expect(globalThis.localStorage.getItem('admin_token')).toBeNull()
  })

  it('removes admin_info from localStorage', async () => {
    globalThis.localStorage.setItem('admin_token', 'tkn-2')
    globalThis.localStorage.setItem('admin_info', 'info-2')
    await logout()
    expect(globalThis.localStorage.getItem('admin_info')).toBeNull()
  })

  it('does NOT touch other localStorage keys', async () => {
    globalThis.localStorage.setItem('admin_token', 'tkn-3')
    globalThis.localStorage.setItem('admin_info', 'info-3')
    globalThis.localStorage.setItem('unrelated_user_pref', 'keep-me')
    await logout()
    expect(globalThis.localStorage.getItem('unrelated_user_pref')).toBe('keep-me')
  })

  it('resolves successfully (does not throw)', async () => {
    await expect(logout()).resolves.toBeUndefined()
  })

  it('works even when localStorage keys are absent (idempotent)', async () => {
    // 没有 token/info 也跑 logout,不应抛
    await expect(logout()).resolves.toBeUndefined()
  })
})