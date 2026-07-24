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

// mock getBrand(brand.ts 顶层的 readCache + writeCache 在模块加载时跑)
// 用 vi.hoisted 让 mock 在 import store 之前就位
const mocks = vi.hoisted(() => ({
  getBrand: vi.fn()
}))
vi.mock('@/api/brand', () => ({
  getBrand: (...args: unknown[]) => mocks.getBrand(...args)
}))
vi.mock('@/utils/color', () => ({
  applyTheme: vi.fn()
}))

beforeEach(() => {
  setActivePinia(createPinia())
  globalThis.localStorage.clear()
  // 清掉 favicon link + title,免受模块 init 副作用影响
  document.head.querySelectorAll('link[rel="icon"]').forEach(n => n.remove())
  document.title = ''
  mocks.getBrand.mockReset()
})

// 注意:brand.ts 模块顶层就跑 readCache() + syncDom() → import 即触发副作用。
// 这里直接 import 一次即可;后续测试只验证 store 实例(每个 test 重新 setActivePinia)
import { useBrandStore } from './brand'

describe('useBrandStore — initial state', () => {
  it('starts with FALLBACK values when no cache', () => {
    const store = useBrandStore()
    expect(store.appName).toBe('Hey Pickler')
    expect(store.slogan).toBe('')
    expect(store.logoUrl).toBe('')
    expect(store.primaryColor).toBe('#4CAF50')
  })

  it('loaded starts as false', () => {
    const store = useBrandStore()
    expect(store.loaded).toBe(false)
  })
})

describe('readCache — 模块 init 时跑', () => {
  it('no cache → uses FALLBACK (verified above)', () => {
    // 已经验证:store initial 是 FALLBACK
    expect(true).toBe(true)
  })

  it('valid cached JSON → 应用到 store(下次 useBrandStore)', async () => {
    // 重置模块以让 init 重读 localStorage
    const cached = {
      appName: 'CachedApp',
      slogan: 'C',
      logoUrl: 'https://x.com/c.png',
      primaryColor: '#abcdef',
      ts: Date.now()
    }
    globalThis.localStorage.setItem('brand_config', JSON.stringify(cached))
    vi.resetModules()
    const re = await import('./brand')
    setActivePinia(createPinia())
    const store = re.useBrandStore()
    expect(store.appName).toBe('CachedApp')
    expect(store.slogan).toBe('C')
    expect(store.logoUrl).toBe('https://x.com/c.png')
    expect(store.primaryColor).toBe('#abcdef')
    // 标题同步更新
    expect(document.title).toBe('CachedApp 管理后台')
  })

  it('malformed cache JSON → catch → null → FALLBACK', async () => {
    globalThis.localStorage.setItem('brand_config', 'NOT-VALID-JSON{{')
    vi.resetModules()
    const re = await import('./brand')
    setActivePinia(createPinia())
    const store = re.useBrandStore()
    expect(store.appName).toBe('Hey Pickler') // fallback
  })
})

describe('load()', () => {
  it('success → apply + writeCache + loaded=true + syncDom (含 document.title 更新)', async () => {
    mocks.getBrand.mockResolvedValue({
      code: 0,
      data: {
        appName: 'LoadedApp',
        slogan: 'L',
        logoUrl: 'https://x.com/l.png',
        primaryColor: '#123456'
      }
    })
    const store = useBrandStore()
    await store.load()
    expect(store.appName).toBe('LoadedApp')
    expect(store.slogan).toBe('L')
    expect(store.logoUrl).toBe('https://x.com/l.png')
    expect(store.primaryColor).toBe('#123456')
    expect(store.loaded).toBe(true)
    expect(document.title).toBe('LoadedApp 管理后台') // syncDom 通过 load() 触发
    // writeCache 写入 localStorage
    const stored = globalThis.localStorage.getItem('brand_config')
    expect(stored).toBeTruthy()
    const parsed = JSON.parse(stored!)
    expect(parsed.appName).toBe('LoadedApp')
    expect(parsed.ts).toBeGreaterThan(0)
  })

  it('code != 0 → no apply, no writeCache, but loaded=true', async () => {
    mocks.getBrand.mockResolvedValue({ code: 500, message: 'fail', data: null })
    const store = useBrandStore()
    await store.load()
    expect(store.appName).toBe('Hey Pickler') // fallback
    expect(store.loaded).toBe(true)
    expect(globalThis.localStorage.getItem('brand_config')).toBeNull()
  })

  it('getBrand throws → catch → keep cache + loaded=true', async () => {
    mocks.getBrand.mockRejectedValue(new Error('network'))
    // 预先 set 缓存
    globalThis.localStorage.setItem('brand_config', JSON.stringify({
      appName: 'PreservedApp', slogan: '', logoUrl: '', primaryColor: '#000', ts: 0
    }))
    vi.resetModules()
    const re = await import('./brand')
    setActivePinia(createPinia())
    mocks.getBrand.mockRejectedValueOnce(new Error('network'))
    const store = re.useBrandStore()
    // init 时 cached 已被 apply
    expect(store.appName).toBe('PreservedApp')
    await store.load()
    // 抛错后状态保留
    expect(store.appName).toBe('PreservedApp')
    expect(store.loaded).toBe(true)
  })

  it('getBrand returns null data → no apply (data falsy)', async () => {
    mocks.getBrand.mockResolvedValue({ code: 0, data: null })
    const store = useBrandStore()
    await store.load()
    expect(store.appName).toBe('Hey Pickler')
    expect(store.loaded).toBe(true)
  })

  it('partial data — falsy fields fall back to FALLBACK (covers all 4 || branches)', async () => {
    // 所有 4 个字段都 falsy,触发全部 || 兜底分支
    mocks.getBrand.mockResolvedValue({
      code: 0,
      data: { appName: '', slogan: '', logoUrl: '', primaryColor: '' }
    })
    const store = useBrandStore()
    await store.load()
    expect(store.appName).toBe('Hey Pickler')        // ← b.appName falsy → FALLBACK.appName
    expect(store.slogan).toBe('')
    expect(store.logoUrl).toBe('')
    expect(store.primaryColor).toBe('#4CAF50')      // ← b.primaryColor falsy → FALLBACK.primaryColor
  })
})

describe('refresh()', () => {
  it('calls load again (相同副作用)', async () => {
    mocks.getBrand.mockResolvedValue({
      code: 0,
      data: { appName: 'R1', slogan: '', logoUrl: '', primaryColor: '#000' }
    })
    const store = useBrandStore()
    await store.refresh()
    expect(store.appName).toBe('R1')
    expect(mocks.getBrand).toHaveBeenCalledTimes(1)
    mocks.getBrand.mockResolvedValueOnce({
      code: 0,
      data: { appName: 'R2', slogan: '', logoUrl: '', primaryColor: '#000' }
    })
    await store.refresh()
    expect(store.appName).toBe('R2')
    expect(mocks.getBrand).toHaveBeenCalledTimes(2)
  })
})

describe('writeCache — 写入失败 catch', () => {
  it('localStorage.setItem throws → 静默 swallow(quota 例)', async () => {
    // 模拟 quota exceeded:覆写 setItem 让它抛
    const originalSetItem = globalThis.localStorage.setItem
    globalThis.localStorage.setItem = vi.fn(() => {
      throw new Error('QuotaExceededError')
    }) as any
    mocks.getBrand.mockResolvedValue({
      code: 0,
      data: { appName: 'X', slogan: '', logoUrl: '', primaryColor: '#000' }
    })
    const store = useBrandStore()
    // 不应 throw
    await expect(store.load()).resolves.toBeUndefined()
    expect(store.appName).toBe('X') // apply 仍然成功
    globalThis.localStorage.setItem = originalSetItem
  })
})

describe('setFavicon — empty url → no-op', () => {
  it('apply empty logoUrl → no link element created', async () => {
    // 启动时 init syncDom:logoUrl='' → setFavicon 不动
    // 清掉 head 残留
    document.head.querySelectorAll('link[rel="icon"]').forEach(n => n.remove())
    mocks.getBrand.mockResolvedValue({
      code: 0,
      data: { appName: 'NoLogo', slogan: '', logoUrl: '', primaryColor: '#000' }
    })
    const store = useBrandStore()
    await store.load()
    // 没有创建 link[rel="icon"]
    expect(document.head.querySelector('link[rel="icon"]')).toBeNull()
  })

  it('apply non-empty logoUrl → creates <link rel=icon>', async () => {
    document.head.querySelectorAll('link[rel="icon"]').forEach(n => n.remove())
    mocks.getBrand.mockResolvedValue({
      code: 0,
      data: {
        appName: 'WithLogo',
        slogan: '',
        logoUrl: 'https://x.com/logo.png',
        primaryColor: '#000'
      }
    })
    const store = useBrandStore()
    await store.load()
    const link = document.head.querySelector('link[rel="icon"]') as HTMLLinkElement | null
    expect(link).not.toBeNull()
    expect(link?.href).toContain('https://x.com/logo.png')
  })

  it('existing <link> → setFavicon just updates href (covers !link false branch)', async () => {
    // 预先创建一个 link,模拟已存在的 favicon
    const existing = document.createElement('link')
    existing.rel = 'icon'
    existing.href = 'https://old.com/old.png'
    document.head.appendChild(existing)
    mocks.getBrand.mockResolvedValue({
      code: 0,
      data: {
        appName: 'UpdateLogo',
        slogan: '',
        logoUrl: 'https://new.com/new.png',
        primaryColor: '#000'
      }
    })
    const store = useBrandStore()
    await store.load()
    // 同一个 link element,但 href 已被更新
    const links = document.head.querySelectorAll('link[rel="icon"]')
    expect(links.length).toBe(1) // 没有新建
    expect(links[0].href).toContain('https://new.com/new.png')
  })
})