// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach } from 'vitest'

const requestGet = vi.fn()
vi.mock('./request', () => ({
  default: { get: (...args: unknown[]) => requestGet(...args) }
}))

beforeEach(() => {
  requestGet.mockReset()
})

import { getOperationLogs, type OperationLogQuery } from './admin-logs'

describe('getOperationLogs', () => {
  it('calls request.get with url and required params only (page, size)', () => {
    requestGet.mockReturnValue(Promise.resolve({ code: 0, data: null }))
    getOperationLogs({ page: 1, size: 10 })
    expect(requestGet).toHaveBeenCalledTimes(1)
    expect(requestGet).toHaveBeenCalledWith('/operation-logs', { params: { page: 1, size: 10 } })
  })

  it('forwards all optional filters when provided', () => {
    requestGet.mockReturnValue(Promise.resolve({ code: 0, data: null }))
    const fullQuery: OperationLogQuery = {
      page: 2,
      size: 20,
      operatorId: 7,
      module: 'event',
      action: 'create',
      status: 1,
      startTime: '2026-07-01T00:00:00Z',
      endTime: '2026-07-24T23:59:59Z'
    }
    getOperationLogs(fullQuery)
    expect(requestGet).toHaveBeenCalledWith('/operation-logs', { params: fullQuery })
  })

  it('partial filters — only operatorId provided', () => {
    requestGet.mockReturnValue(Promise.resolve({ code: 0, data: null }))
    getOperationLogs({ page: 1, size: 10, operatorId: 42 })
    expect(requestGet).toHaveBeenCalledWith('/operation-logs', {
      params: { page: 1, size: 10, operatorId: 42 }
    })
  })

  it('partial filters — only module/action provided', () => {
    requestGet.mockReturnValue(Promise.resolve({ code: 0, data: null }))
    getOperationLogs({ page: 1, size: 10, module: 'auth', action: 'login' })
    expect(requestGet).toHaveBeenCalledWith('/operation-logs', {
      params: { page: 1, size: 10, module: 'auth', action: 'login' }
    })
  })

  it('partial filters — only time range provided', () => {
    requestGet.mockReturnValue(Promise.resolve({ code: 0, data: null }))
    getOperationLogs({ page: 1, size: 10, startTime: '2026-07-01', endTime: '2026-07-24' })
    expect(requestGet).toHaveBeenCalledWith('/operation-logs', {
      params: { page: 1, size: 10, startTime: '2026-07-01', endTime: '2026-07-24' }
    })
  })

  it('forwards the response promise', async () => {
    const fake = {
      code: 0,
      data: { list: [{ id: 1, operatorName: 'admin' } as any], total: 1, page: 1, size: 10 }
    }
    requestGet.mockReturnValue(Promise.resolve(fake))
    const result = await getOperationLogs({ page: 1, size: 10 })
    expect(result).toBe(fake)
  })
})