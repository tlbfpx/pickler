// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach } from 'vitest'

const requestGet = vi.fn()
vi.mock('./request', () => ({
  default: { get: (...args: unknown[]) => requestGet(...args) }
}))

beforeEach(() => {
  requestGet.mockReset()
})

import { getAnalyticsOverview, getAnalyticsDashboard } from './analytics'

describe('getAnalyticsOverview', () => {
  it('default days = 30 → params { days: 30 }', () => {
    requestGet.mockReturnValue(Promise.resolve({ code: 0, data: null }))
    getAnalyticsOverview()
    expect(requestGet).toHaveBeenCalledTimes(1)
    expect(requestGet).toHaveBeenCalledWith('/analytics/overview', { params: { days: 30 } })
  })

  it('explicit days = 7 → params { days: 7 }', () => {
    requestGet.mockReturnValue(Promise.resolve({ code: 0, data: null }))
    getAnalyticsOverview(7)
    expect(requestGet).toHaveBeenCalledWith('/analytics/overview', { params: { days: 7 } })
  })

  it('explicit days = 90 → params { days: 90 }', () => {
    requestGet.mockReturnValue(Promise.resolve({ code: 0, data: null }))
    getAnalyticsOverview(90)
    expect(requestGet).toHaveBeenCalledWith('/analytics/overview', { params: { days: 90 } })
  })

  it('forwards the response promise', async () => {
    const fake = {
      code: 0,
      data: {
        days: 30,
        newUsers: [{ date: '2026-07-01', count: 5 }],
        newRegistrations: [],
        newEvents: [],
        completionRate: [{ date: '2026-07-01', rate: 80.0 }],
        overallCompletionRate: 80.0
      }
    }
    requestGet.mockReturnValue(Promise.resolve(fake))
    const result = await getAnalyticsOverview(7)
    expect(result).toBe(fake)
  })
})

describe('getAnalyticsDashboard', () => {
  it('calls request.get with url only, no params', () => {
    requestGet.mockReturnValue(Promise.resolve({ code: 0, data: null }))
    getAnalyticsDashboard()
    expect(requestGet).toHaveBeenCalledTimes(1)
    expect(requestGet).toHaveBeenCalledWith('/analytics/dashboard')
  })

  it('forwards the response promise', async () => {
    const fake = {
      code: 0,
      data: {
        totals: { users: 100, events: 5, registrations: 200, revenue: 3000 },
        completionRate: 80.0,
        registrationPerEvent: 40,
        activeUsersLast30d: 80,
        byMonth: [],
        byType: { STAR: 3, PARTY: 2 },
        byStatus: { COMPLETED: 3 }
      }
    }
    requestGet.mockReturnValue(Promise.resolve(fake))
    const result = await getAnalyticsDashboard()
    expect(result).toBe(fake)
  })
})