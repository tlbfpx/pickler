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

const mocks = vi.hoisted(() => ({
  getDictBundle: vi.fn(),
  getDictVersion: vi.fn()
}))
vi.mock('@/api/dict', () => ({
  getDictBundle: (...args: unknown[]) => mocks.getDictBundle(...args),
  getDictVersion: (...args: unknown[]) => mocks.getDictVersion(...args)
}))

beforeEach(() => {
  setActivePinia(createPinia())
  globalThis.localStorage.clear()
  mocks.getDictBundle.mockReset()
  mocks.getDictVersion.mockReset()
})

// import store 模块(顶层 init 跑 readCache)
import { useDictStore } from './dict'

// ============ Helper: 测试用 dict 数据 ============
function makeItem(overrides: Partial<{ itemKey: string; itemLabel: string; itemColor: string | null; sort: number; status: string; extraJson: string | null }> = {}) {
  return {
    itemKey: 'OPEN',
    itemLabel: '报名中',
    itemColor: '#10B981',
    sort: 1,
    status: 'ENABLED',
    extraJson: null,
    ...overrides
  }
}

// ============ Initial state ============
describe('useDictStore — initial state', () => {
  it('bundle starts empty when no cache', () => {
    const store = useDictStore()
    expect(store.bundle).toEqual({})
  })

  it('version starts at 0', () => {
    const store = useDictStore()
    expect(store.version).toBe(0)
  })

  it('loaded starts false', () => {
    const store = useDictStore()
    expect(store.loaded).toBe(false)
  })

  it('with cached bundle in localStorage → init reads it', async () => {
    globalThis.localStorage.setItem('dict_bundle', JSON.stringify({
      version: 5,
      dicts: { event_status: [makeItem({ itemKey: 'OPEN' })] },
      ts: Date.now()
    }))
    vi.resetModules()
    const re = await import('./dict')
    setActivePinia(createPinia())
    const store = re.useDictStore()
    expect(store.version).toBe(5)
    expect(store.bundle.event_status).toHaveLength(1)
  })

  it('malformed cached JSON → catch → 空 bundle', async () => {
    globalThis.localStorage.setItem('dict_bundle', 'NOT-JSON{{')
    vi.resetModules()
    const re = await import('./dict')
    setActivePinia(createPinia())
    const store = re.useDictStore()
    expect(store.bundle).toEqual({})
    expect(store.version).toBe(0)
  })
})

// ============ items / item / label / color / has ============
describe('items / item / label / color / has', () => {
  function setupWithItems(items: any[]) {
    const store = useDictStore()
    store.bundle = { event_status: items }
    return store
  }

  it('items filters out DISABLED + sorts by sort asc', () => {
    const store = setupWithItems([
      makeItem({ itemKey: 'A', sort: 3 }),
      makeItem({ itemKey: 'B', sort: 1, status: 'DISABLED' }),
      makeItem({ itemKey: 'C', sort: 2 })
    ])
    // DISABLED B 过滤掉 → [A(3), C(2)] → 按 sort 升序 → [C(2), A(3)]
    const items = store.items('event_status')
    expect(items.map(i => i.itemKey)).toEqual(['C', 'A'])
  })

  it('items returns [] for unknown code', () => {
    const store = useDictStore()
    expect(store.items('UNKNOWN')).toEqual([])
  })

  it('items returns [] when bundle has code but all items DISABLED', () => {
    const store = setupWithItems([
      makeItem({ status: 'DISABLED' }),
      makeItem({ status: 'DISABLED' })
    ])
    expect(store.items('event_status')).toEqual([])
  })

  it('item finds ENABLED by itemKey', () => {
    const store = setupWithItems([
      makeItem({ itemKey: 'OPEN' }),
      makeItem({ itemKey: 'CLOSED', status: 'DISABLED' })
    ])
    expect(store.item('event_status', 'OPEN')?.itemKey).toBe('OPEN')
    // DISABLED 跳过
    expect(store.item('event_status', 'CLOSED')).toBeUndefined()
  })

  it('item returns undefined for unknown key', () => {
    const store = setupWithItems([makeItem({ itemKey: 'OPEN' })])
    expect(store.item('event_status', 'UNKNOWN')).toBeUndefined()
  })

  it('item returns undefined for unknown code', () => {
    const store = useDictStore()
    expect(store.item('UNKNOWN', 'X')).toBeUndefined()
  })

  it('label returns itemLabel when found, else raw key', () => {
    const store = setupWithItems([makeItem({ itemKey: 'OPEN', itemLabel: '报名中' })])
    expect(store.label('event_status', 'OPEN')).toBe('报名中')
    expect(store.label('event_status', 'MISSING')).toBe('MISSING')
  })

  it('color returns itemColor or default grey #6B7280', () => {
    const store = setupWithItems([makeItem({ itemKey: 'OPEN', itemColor: '#123456' })])
    expect(store.color('event_status', 'OPEN')).toBe('#123456')
    expect(store.color('event_status', 'MISSING')).toBe('#6B7280')
  })

  it('color with null itemColor still uses default', () => {
    const store = setupWithItems([makeItem({ itemKey: 'OPEN', itemColor: null })])
    expect(store.color('event_status', 'OPEN')).toBe('#6B7280')
  })

  it('has returns true/false', () => {
    const store = setupWithItems([makeItem({ itemKey: 'OPEN' })])
    expect(store.has('event_status', 'OPEN')).toBe(true)
    expect(store.has('event_status', 'MISSING')).toBe(false)
  })
})

// ============ meta ============
describe('meta', () => {
  it('parses extraJson when present and valid', () => {
    const store = useDictStore()
    store.bundle = {
      event_status: [makeItem({ itemKey: 'OPEN', extraJson: '{"pointsName":"P","tierName":"T"}' })]
    }
    const m = store.meta('event_status', 'OPEN')
    expect(m).toEqual({ pointsName: 'P', tierName: 'T' })
  })

  it('returns null when extraJson is null', () => {
    const store = useDictStore()
    store.bundle = { event_status: [makeItem({ itemKey: 'OPEN', extraJson: null })] }
    expect(store.meta('event_status', 'OPEN')).toBeNull()
  })

  it('returns null when item not found', () => {
    const store = useDictStore()
    expect(store.meta('event_status', 'UNKNOWN')).toBeNull()
  })

  it('returns null on JSON.parse failure (catch)', () => {
    const store = useDictStore()
    store.bundle = { event_status: [makeItem({ itemKey: 'OPEN', extraJson: 'NOT-JSON' })] }
    expect(store.meta('event_status', 'OPEN')).toBeNull()
  })
})

// ============ options ============
describe('options', () => {
  it('maps items to { label, value, color, sort }', () => {
    const store = useDictStore()
    store.bundle = {
      event_status: [
        makeItem({ itemKey: 'A', itemLabel: '甲', itemColor: '#111', sort: 2 }),
        makeItem({ itemKey: 'B', itemLabel: '乙', itemColor: null, sort: 1 })
      ]
    }
    const opts = store.options('event_status')
    expect(opts).toEqual([
      { label: '乙', value: 'B', color: '#6B7280', sort: 1 }, // B 排序在前
      { label: '甲', value: 'A', color: '#111', sort: 2 }
    ])
  })

  it('empty for unknown code', () => {
    const store = useDictStore()
    expect(store.options('UNKNOWN')).toEqual([])
  })
})

// ============ load ============
describe('load()', () => {
  it('first load (loaded=false) → always fetches bundle', async () => {
    mocks.getDictVersion.mockResolvedValue({ code: 0, data: { version: 1 } })
    mocks.getDictBundle.mockResolvedValue({
      code: 0,
      data: { version: 1, dicts: { event_status: [makeItem()] } }
    })
    const store = useDictStore()
    await store.load()
    expect(store.version).toBe(1)
    expect(store.loaded).toBe(true)
    expect(mocks.getDictBundle).toHaveBeenCalledTimes(1)
  })

  it('serverVersion > local version → fetches bundle (update path)', async () => {
    const store = useDictStore()
    store.version = 1
    store.loaded = true // 模拟之前 load 过
    mocks.getDictVersion.mockResolvedValue({ code: 0, data: { version: 5 } })
    mocks.getDictBundle.mockResolvedValue({
      code: 0,
      data: { version: 5, dicts: { event_status: [makeItem({ itemKey: 'NEW' })] } }
    })
    await store.load()
    expect(store.version).toBe(5)
    expect(store.bundle.event_status[0].itemKey).toBe('NEW')
  })

  it('serverVersion <= local version + loaded=true → no fetch (cache fresh)', async () => {
    const store = useDictStore()
    store.version = 5
    store.loaded = true
    mocks.getDictVersion.mockResolvedValue({ code: 0, data: { version: 3 } })
    await store.load()
    expect(mocks.getDictBundle).not.toHaveBeenCalled()
    expect(store.loaded).toBe(true)
  })

  it('version data missing (undefined) → defaults to 0', async () => {
    mocks.getDictVersion.mockResolvedValue({ code: 0, data: undefined })
    mocks.getDictBundle.mockResolvedValue({
      code: 0,
      data: { version: 0, dicts: { event_status: [makeItem()] } }
    })
    const store = useDictStore()
    await store.load()
    expect(store.version).toBe(0)
    expect(mocks.getDictBundle).toHaveBeenCalled()
  })

  it('getDictBundle code != 0 → no apply, but loaded=true', async () => {
    mocks.getDictVersion.mockResolvedValue({ code: 0, data: { version: 1 } })
    mocks.getDictBundle.mockResolvedValue({ code: 500, message: 'fail', data: null })
    const store = useDictStore()
    await store.load()
    expect(store.bundle).toEqual({}) // 未 apply
    expect(store.loaded).toBe(true)
  })

  it('getDictBundle throws → catch → loaded=true, state unchanged', async () => {
    mocks.getDictVersion.mockRejectedValue(new Error('network'))
    const store = useDictStore()
    await store.load()
    expect(store.loaded).toBe(true)
    expect(store.bundle).toEqual({})
  })

  it('successful load → writeCache persists to localStorage', async () => {
    mocks.getDictVersion.mockResolvedValue({ code: 0, data: { version: 7 } })
    mocks.getDictBundle.mockResolvedValue({
      code: 0,
      data: { version: 7, dicts: { event_status: [makeItem()] } }
    })
    const store = useDictStore()
    await store.load()
    const cached = JSON.parse(globalThis.localStorage.getItem('dict_bundle')!)
    expect(cached.version).toBe(7)
    expect(cached.dicts.event_status).toHaveLength(1)
    expect(cached.ts).toBeGreaterThan(0)
  })
})

// ============ refresh ============
describe('refresh()', () => {
  it('success → applies + writes cache + loaded stays true', async () => {
    mocks.getDictBundle.mockResolvedValue({
      code: 0,
      data: { version: 3, dicts: { event_status: [makeItem()] } }
    })
    const store = useDictStore()
    await store.refresh()
    expect(store.version).toBe(3)
    expect(globalThis.localStorage.getItem('dict_bundle')).toBeTruthy()
  })

  it('code != 0 → no apply', async () => {
    mocks.getDictBundle.mockResolvedValue({ code: 500, data: null })
    const store = useDictStore()
    await store.refresh()
    expect(store.version).toBe(0)
  })

  it('throws → catch (silent)', async () => {
    mocks.getDictBundle.mockRejectedValue(new Error('boom'))
    const store = useDictStore()
    await expect(store.refresh()).resolves.toBeUndefined()
  })

  it('writeCache failure (quota) → silent swallow', async () => {
    const original = globalThis.localStorage.setItem
    globalThis.localStorage.setItem = vi.fn(() => {
      throw new Error('QuotaExceededError')
    }) as any
    mocks.getDictBundle.mockResolvedValue({
      code: 0,
      data: { version: 1, dicts: { event_status: [makeItem()] } }
    })
    const store = useDictStore()
    await expect(store.refresh()).resolves.toBeUndefined()
    expect(store.version).toBe(1) // 仍 apply
    globalThis.localStorage.setItem = original
  })
})