// @vitest-environment jsdom
import { describe, it, expect, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useAppStore } from './app'

beforeEach(() => {
  setActivePinia(createPinia())
})

describe('useAppStore', () => {
  it('initial sidebarCollapsed = false', () => {
    const store = useAppStore()
    expect(store.sidebarCollapsed).toBe(false)
  })

  it('toggleSidebar flips false → true', () => {
    const store = useAppStore()
    store.toggleSidebar()
    expect(store.sidebarCollapsed).toBe(true)
  })

  it('toggleSidebar flips true → false', () => {
    const store = useAppStore()
    store.toggleSidebar() // false → true
    store.toggleSidebar() // true → false
    expect(store.sidebarCollapsed).toBe(false)
  })

  it('each store instance is independent (per active pinia)', () => {
    const store1 = useAppStore()
    store1.toggleSidebar()
    expect(store1.sidebarCollapsed).toBe(true)
    // 重新 setActivePinia 后,新 store 从初始状态开始
    setActivePinia(createPinia())
    const store2 = useAppStore()
    expect(store2.sidebarCollapsed).toBe(false)
  })
})