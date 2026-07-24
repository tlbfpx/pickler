// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach } from 'vitest'

// ---- jsdom polyfill 兜底:某些 node 环境跑测试时 localStorage 未注入。手动注入一份。 ----
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

// ---- vi.hoisted 让 vi.mock 工厂可引用顶层 mock 对象 ----
const mockRefs = vi.hoisted(() => {
  const requestInterceptors = { use: vi.fn() }
  const responseInterceptors = { use: vi.fn() }
  const elMessageError = vi.fn()
  const routerPush = vi.fn()
  const mockAxiosInstance = {
    interceptors: {
      request: requestInterceptors,
      response: responseInterceptors
    }
  }
  return { requestInterceptors, responseInterceptors, elMessageError, routerPush, mockAxiosInstance }
})

vi.mock('axios', () => ({
  default: {
    create: () => mockRefs.mockAxiosInstance
  }
}))

vi.mock('element-plus', () => ({
  ElMessage: { error: (...args: unknown[]) => mockRefs.elMessageError(...args) }
}))

vi.mock('@/router', () => ({
  default: { push: (...args: unknown[]) => mockRefs.routerPush(...args) }
}))

beforeEach(() => {
  mockRefs.elMessageError.mockReset()
  mockRefs.routerPush.mockReset()
  globalThis.localStorage.clear()
})

// 现在才 import — 模块顶层执行会调用我们的 mock axios.create 并注册拦截器
import request from './request'

// 抽出拦截回调(只在第一次 import 时注册过一次,后续跨 test 不变)
const requestOnFulfilled = mockRefs.requestInterceptors.use.mock.calls[0][0] as (cfg: any) => any
const requestOnRejected = mockRefs.requestInterceptors.use.mock.calls[0][1] as (err: any) => any
const responseOnFulfilled = mockRefs.responseInterceptors.use.mock.calls[0][0] as (res: any) => any
const responseOnRejected = mockRefs.responseInterceptors.use.mock.calls[0][1] as (err: any) => Promise<any>

describe('module exports', () => {
  it('exports the axios instance', () => {
    expect(request).toBe(mockRefs.mockAxiosInstance)
  })
})

describe('request interceptor — Authorization header', () => {
  it('token present + config has headers → sets Bearer', () => {
    localStorage.setItem('admin_token', 'xyz')
    const cfg: any = { headers: {} }
    const out = requestOnFulfilled(cfg)
    expect(out.headers.Authorization).toBe('Bearer xyz')
  })

  it('token present + config has no headers → does not set (no throw)', () => {
    localStorage.setItem('admin_token', 'xyz')
    const cfg: any = {}
    const out = requestOnFulfilled(cfg)
    expect(out.headers).toBeUndefined()
  })

  it('token missing → headers unchanged', () => {
    const cfg: any = { headers: { 'X-Foo': 'bar' } }
    const out = requestOnFulfilled(cfg)
    expect(out.headers.Authorization).toBeUndefined()
    expect(out.headers['X-Foo']).toBe('bar')
  })

  it('returns the same config object', () => {
    const cfg: any = { headers: {} }
    const out = requestOnFulfilled(cfg)
    expect(out).toBe(cfg)
  })

  it('onRejected → rejects with the same error', async () => {
    const err = new Error('config error')
    await expect(requestOnRejected(err)).rejects.toBe(err)
  })
})

describe('response interceptor — success', () => {
  it('returns response.data on success', () => {
    const res = { data: { hello: 'world' } }
    expect(responseOnFulfilled(res)).toEqual({ hello: 'world' })
  })

  it('returns response.data even when data is null', () => {
    const res = { data: null }
    expect(responseOnFulfilled(res)).toBeNull()
  })
})

describe('response interceptor — error with status code', () => {
  // 401 还要测副作用:clear localStorage + router.push('/login')
  it('400 + msg from server → uses msg', async () => {
    const err = makeError(400, 'invalid input X')
    await expectErrorRejection(err)
    expect(mockRefs.elMessageError).toHaveBeenCalledWith('invalid input X')
  })

  it('400 + no msg → uses fallback', async () => {
    const err = makeError(400)
    await expectErrorRejection(err)
    expect(mockRefs.elMessageError).toHaveBeenCalledWith('请求参数有误，请检查后重试')
  })

  it('401 → fixed message + clears localStorage + router.push("/login")', async () => {
    localStorage.setItem('admin_token', 'tkn')
    localStorage.setItem('admin_info', 'info')
    const err = makeError(401)
    await expectErrorRejection(err)
    expect(mockRefs.elMessageError).toHaveBeenCalledWith('登录已过期，请重新登录')
    expect(localStorage.getItem('admin_token')).toBeNull()
    expect(localStorage.getItem('admin_info')).toBeNull()
    expect(mockRefs.routerPush).toHaveBeenCalledWith('/login')
  })

  it('403 → fixed message', async () => {
    const err = makeError(403)
    await expectErrorRejection(err)
    expect(mockRefs.elMessageError).toHaveBeenCalledWith('没有权限执行此操作')
  })

  it('404 + msg → uses msg', async () => {
    const err = makeError(404, 'user not found')
    await expectErrorRejection(err)
    expect(mockRefs.elMessageError).toHaveBeenCalledWith('user not found')
  })

  it('404 + no msg → fallback', async () => {
    const err = makeError(404)
    await expectErrorRejection(err)
    expect(mockRefs.elMessageError).toHaveBeenCalledWith('请求的资源不存在')
  })

  it('429 → fixed message', async () => {
    const err = makeError(429)
    await expectErrorRejection(err)
    expect(mockRefs.elMessageError).toHaveBeenCalledWith('操作过于频繁，请稍后再试')
  })

  it('500 + msg → uses msg', async () => {
    const err = makeError(500, 'database down')
    await expectErrorRejection(err)
    expect(mockRefs.elMessageError).toHaveBeenCalledWith('database down')
  })

  it('500 + no msg → fallback', async () => {
    const err = makeError(500)
    await expectErrorRejection(err)
    expect(mockRefs.elMessageError).toHaveBeenCalledWith('服务器开小差了，请稍后再试')
  })

  it('unknown status (e.g. 418) + msg → uses msg', async () => {
    const err = makeError(418, 'I am a teapot')
    await expectErrorRejection(err)
    expect(mockRefs.elMessageError).toHaveBeenCalledWith('I am a teapot')
  })

  it('unknown status (e.g. 418) + no msg → fallback', async () => {
    const err = makeError(418)
    await expectErrorRejection(err)
    expect(mockRefs.elMessageError).toHaveBeenCalledWith('请求失败，请稍后重试')
  })

  it('data without message property → msg = "" → fallback', async () => {
    // data 存在但没有 .message 字段
    const err: any = new Error('axios err')
    err.isAxiosError = true
    err.response = { status: 400, data: {} }
    await expectErrorRejection(err)
    expect(mockRefs.elMessageError).toHaveBeenCalledWith('请求参数有误，请检查后重试')
  })
})

describe('response interceptor — error without response', () => {
  it('network error (no error.response) → fallback', async () => {
    const err: any = new Error('Network Error')
    err.isAxiosError = true
    // 故意不设 err.response
    await expectErrorRejection(err)
    expect(mockRefs.elMessageError).toHaveBeenCalledWith('网络连接异常，请检查网络')
  })
})

// ============ helpers ============

function makeError(status: number, message?: string): any {
  const err: any = new Error(`Request failed with status ${status}`)
  err.isAxiosError = true
  err.response = {
    status,
    data: message !== undefined ? { message } : undefined
  }
  return err
}

async function expectErrorRejection(err: any): Promise<void> {
  await expect(responseOnRejected(err)).rejects.toBe(err)
}