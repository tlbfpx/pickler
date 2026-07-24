import { describe, it, expect, vi } from 'vitest'
import { effectScope, ref } from 'vue'

vi.mock('@/stores/dict', () => ({
  useDictStore: () => ({
    options: vi.fn((code: string) => `OPTIONS_FOR_${code}`),
    label: vi.fn((code: string, key: string) => `${code}:${key}`),
    color: vi.fn((code: string, key: string) => `COLOR_${code}_${key}`)
  })
}))

import { useDictOptions, useDictItem } from './useDict'

// computed() 在 Vue 3 中需要 active effect scope 才能 .value;
// 在 test 环境下,包一个 effectScope 让响应式正常生效。

describe('useDictOptions', () => {
  it('returns a computed wrapping store.options(code)', () => {
    const scope = effectScope()
    scope.run(() => {
      const dict = useDictOptions('event_status')
      expect(dict.value).toBe('OPTIONS_FOR_event_status')
    })
    scope.stop()
  })

  it('different code → different computed value', () => {
    const scope = effectScope()
    scope.run(() => {
      const a = useDictOptions('event_status')
      const b = useDictOptions('event_type')
      expect(a.value).toBe('OPTIONS_FOR_event_status')
      expect(b.value).toBe('OPTIONS_FOR_event_type')
    })
    scope.stop()
  })
})

describe('useDictItem', () => {
  it('key is null → returns {label: "-", color: "#6B7280"} without calling store', () => {
    const scope = effectScope()
    scope.run(() => {
      const item = useDictItem('event_status', () => null)
      expect(item.value).toEqual({ label: '-', color: '#6B7280' })
    })
    scope.stop()
  })

  it('key is undefined → returns placeholder', () => {
    const scope = effectScope()
    scope.run(() => {
      const item = useDictItem('event_status', () => undefined)
      expect(item.value).toEqual({ label: '-', color: '#6B7280' })
    })
    scope.stop()
  })

  it('key is empty string → returns placeholder', () => {
    const scope = effectScope()
    scope.run(() => {
      const item = useDictItem('event_status', () => '')
      expect(item.value).toEqual({ label: '-', color: '#6B7280' })
    })
    scope.stop()
  })

  it('keyGetter is a closure returning a const → stable across reads', () => {
    const scope = effectScope()
    scope.run(() => {
      const item = useDictItem('event_status', () => 'OPEN')
      // 同一 keyGetter 返回相同结果,computed 应该缓存稳定值
      expect(item.value).toEqual({
        label: 'event_status:OPEN',
        color: 'COLOR_event_status_OPEN'
      })
      // 再次访问,值一样(无 .value 改动)
      expect(item.value).toEqual({
        label: 'event_status:OPEN',
        color: 'COLOR_event_status_OPEN'
      })
    })
    scope.stop()
  })

  it('keyGetter is a closure returning a ref-tracked value → reactivity works', () => {
    const scope = effectScope()
    scope.run(async () => {
      const r = ref('INITIAL')
      const item = useDictItem('event_status', () => r.value)
      expect(item.value).toEqual({
        label: 'event_status:INITIAL',
        color: 'COLOR_event_status_INITIAL'
      })

      r.value = 'CHANGED'
      // computed 自动重算(因为 keyGetter 闭包读取了 ref.value)
      // await next tick 让调度生效
      await new Promise(resolve => setTimeout(resolve, 0))
      expect(item.value).toEqual({
        label: 'event_status:CHANGED',
        color: 'COLOR_event_status_CHANGED'
      })
    })
    scope.stop()
  })

  it('keyGetter returns ref wrapped string → placeholder', () => {
    // ref<string | null>; null 时走 !k 分支 → placeholder
    const scope = effectScope()
    scope.run(() => {
      const r = ref<string | null>(null)
      const item = useDictItem('event_status', () => r.value)
      expect(item.value).toEqual({ label: '-', color: '#6B7280' })
    })
    scope.stop()
  })
})