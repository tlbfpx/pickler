// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach } from 'vitest'

const requestGet = vi.fn()
const requestPut = vi.fn()
vi.mock('./request', () => ({
  default: {
    get: (...args: unknown[]) => requestGet(...args),
    put: (...args: unknown[]) => requestPut(...args)
  }
}))

beforeEach(() => {
  requestGet.mockReset()
  requestPut.mockReset()
})

import {
  getDictList,
  getDictItems,
  updateDictItems,
  getDictBundle,
  getDictVersion
} from './dict'

const ok = { code: 0, data: null }

describe('getDictList', () => {
  it('calls request.get with url only', () => {
    requestGet.mockReturnValue(Promise.resolve(ok))
    getDictList()
    expect(requestGet).toHaveBeenCalledTimes(1)
    expect(requestGet).toHaveBeenCalledWith('/dict')
  })

  it('forwards the response promise', async () => {
    const fake = { code: 0, data: [{ dictCode: 'event_status' }] }
    requestGet.mockReturnValue(Promise.resolve(fake))
    expect(await getDictList()).toBe(fake)
  })
})

describe('getDictItems', () => {
  it('calls request.get with url containing dictCode', () => {
    requestGet.mockReturnValue(Promise.resolve(ok))
    getDictItems('event_status')
    expect(requestGet).toHaveBeenCalledWith('/dict/event_status/items')
  })

  it('handles different dictCode', () => {
    requestGet.mockReturnValue(Promise.resolve(ok))
    getDictItems('event_type')
    expect(requestGet).toHaveBeenCalledWith('/dict/event_type/items')
  })

  it('forwards the response promise', async () => {
    const fake = { code: 0, data: [{ itemKey: 'OPEN' }] }
    requestGet.mockReturnValue(Promise.resolve(fake))
    expect(await getDictItems('event_status')).toBe(fake)
  })
})

describe('updateDictItems', () => {
  it('calls request.put with url + items array', () => {
    requestPut.mockReturnValue(Promise.resolve(ok))
    const items = [{ itemKey: 'OPEN', itemLabel: '报名中', itemColor: '#10B981', sort: 1, status: 'ACTIVE' }]
    updateDictItems('event_status', items)
    expect(requestPut).toHaveBeenCalledTimes(1)
    expect(requestPut).toHaveBeenCalledWith('/dict/event_status/items', items)
  })

  it('empty items array', () => {
    requestPut.mockReturnValue(Promise.resolve(ok))
    updateDictItems('event_status', [])
    expect(requestPut).toHaveBeenCalledWith('/dict/event_status/items', [])
  })

  it('forwards the response promise', async () => {
    requestPut.mockReturnValue(Promise.resolve(ok))
    expect(await updateDictItems('event_status', [])).toBe(ok)
  })
})

describe('getDictBundle', () => {
  it('calls request.get with url only', () => {
    requestGet.mockReturnValue(Promise.resolve(ok))
    getDictBundle()
    expect(requestGet).toHaveBeenCalledTimes(1)
    expect(requestGet).toHaveBeenCalledWith('/dict/bundle')
  })

  it('forwards the response promise', async () => {
    const fake = { code: 0, data: { version: 42, dicts: {} } }
    requestGet.mockReturnValue(Promise.resolve(fake))
    expect(await getDictBundle()).toBe(fake)
  })
})

describe('getDictVersion', () => {
  it('calls request.get with url only', () => {
    requestGet.mockReturnValue(Promise.resolve(ok))
    getDictVersion()
    expect(requestGet).toHaveBeenCalledTimes(1)
    expect(requestGet).toHaveBeenCalledWith('/dict/version')
  })

  it('forwards the response promise', async () => {
    const fake = { code: 0, data: { version: 42 } }
    requestGet.mockReturnValue(Promise.resolve(fake))
    expect(await getDictVersion()).toBe(fake)
  })
})