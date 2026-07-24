// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach } from 'vitest'

const requestGet = vi.fn()
vi.mock('./request', () => ({
  default: { get: (...args: unknown[]) => requestGet(...args) }
}))

beforeEach(() => {
  requestGet.mockReset()
})

import {
  getDashboardStats,
  getDashboardTrends,
  getDashboardTopEvents,
  getDashboardAttendance,
  getDashboardCompare
} from './dashboard'

const ok = { code: 0, data: null }

describe('getDashboardStats', () => {
  it('calls request.get with url only', () => {
    requestGet.mockReturnValue(Promise.resolve(ok))
    getDashboardStats()
    expect(requestGet).toHaveBeenCalledTimes(1)
    expect(requestGet).toHaveBeenCalledWith('/dashboard')
  })

  it('forwards the response promise', async () => {
    const fake = { code: 0, data: {} as any }
    requestGet.mockReturnValue(Promise.resolve(fake))
    expect(await getDashboardStats()).toBe(fake)
  })
})

describe('getDashboardTrends', () => {
  it('forwards full params', () => {
    requestGet.mockReturnValue(Promise.resolve(ok))
    getDashboardTrends({ range: '30d', from: '2026-07-01', to: '2026-07-24', no_cache: 1 })
    expect(requestGet).toHaveBeenCalledWith('/dashboard/trends', {
      params: { range: '30d', from: '2026-07-01', to: '2026-07-24', no_cache: 1 }
    })
  })

  it('forwards empty params object', () => {
    requestGet.mockReturnValue(Promise.resolve(ok))
    getDashboardTrends({})
    expect(requestGet).toHaveBeenCalledWith('/dashboard/trends', { params: {} })
  })

  it('forwards the response promise', async () => {
    const fake = { code: 0, data: { range: '30d', buckets: [] } }
    requestGet.mockReturnValue(Promise.resolve(fake))
    expect(await getDashboardTrends({ range: '30d' })).toBe(fake)
  })
})

describe('getDashboardTopEvents', () => {
  it('forwards all params including limit', () => {
    requestGet.mockReturnValue(Promise.resolve(ok))
    getDashboardTopEvents({
      metric: 'registrations',
      range: '30d',
      from: '2026-07-01',
      to: '2026-07-24',
      limit: 10,
      no_cache: 1
    })
    expect(requestGet).toHaveBeenCalledWith('/dashboard/top-events', {
      params: {
        metric: 'registrations',
        range: '30d',
        from: '2026-07-01',
        to: '2026-07-24',
        limit: 10,
        no_cache: 1
      }
    })
  })

  it('forwards empty params object', () => {
    requestGet.mockReturnValue(Promise.resolve(ok))
    getDashboardTopEvents({})
    expect(requestGet).toHaveBeenCalledWith('/dashboard/top-events', { params: {} })
  })

  it('forwards the response promise', async () => {
    const fake = { code: 0, data: [{ eventId: 1, title: 'A', value: 50, metric: 'registrations' }] }
    requestGet.mockReturnValue(Promise.resolve(fake))
    expect(await getDashboardTopEvents({ metric: 'registrations' })).toBe(fake)
  })
})

describe('getDashboardAttendance', () => {
  it('forwards full params', () => {
    requestGet.mockReturnValue(Promise.resolve(ok))
    getDashboardAttendance({ range: '30d', from: '2026-07-01', to: '2026-07-24', no_cache: 1 })
    expect(requestGet).toHaveBeenCalledWith('/dashboard/attendance', {
      params: { range: '30d', from: '2026-07-01', to: '2026-07-24', no_cache: 1 }
    })
  })

  it('forwards empty params object', () => {
    requestGet.mockReturnValue(Promise.resolve(ok))
    getDashboardAttendance({})
    expect(requestGet).toHaveBeenCalledWith('/dashboard/attendance', { params: {} })
  })

  it('forwards the response promise', async () => {
    const fake = { code: 0, data: { range: '30d', registered: 100, checkedIn: 80, noShowRate: 0.2 } }
    requestGet.mockReturnValue(Promise.resolve(fake))
    expect(await getDashboardAttendance({ range: '30d' })).toBe(fake)
  })
})

describe('getDashboardCompare', () => {
  it('forwards full params (metric required, others optional)', () => {
    requestGet.mockReturnValue(Promise.resolve(ok))
    getDashboardCompare({ metric: 'users', currentRange: 'thisMonth', previousRange: 'lastMonth', no_cache: 1 })
    expect(requestGet).toHaveBeenCalledWith('/dashboard/compare', {
      params: { metric: 'users', currentRange: 'thisMonth', previousRange: 'lastMonth', no_cache: 1 }
    })
  })

  it('forwards the response promise', async () => {
    const fake = { code: 0, data: { metric: 'users', current: 100, previous: 80, deltaAbs: 20, deltaPct: 0.25 } }
    requestGet.mockReturnValue(Promise.resolve(fake))
    expect(await getDashboardCompare({ metric: 'users' })).toBe(fake)
  })
})