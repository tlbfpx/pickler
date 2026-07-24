// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach } from 'vitest'

const requestGet = vi.fn()
const requestDelete = vi.fn()
vi.mock('./request', () => ({
  default: {
    get: (...args: unknown[]) => requestGet(...args),
    delete: (...args: unknown[]) => requestDelete(...args)
  }
}))

beforeEach(() => {
  requestGet.mockReset()
  requestDelete.mockReset()
})

import { getBanRecords, deleteBanRecord } from './ban-records'

describe('getBanRecords', () => {
  it('required params only (page, size)', () => {
    requestGet.mockReturnValue(Promise.resolve({ code: 0, data: null }))
    getBanRecords({ page: 1, size: 10 })
    expect(requestGet).toHaveBeenCalledTimes(1)
    expect(requestGet).toHaveBeenCalledWith('/ban-records', { params: { page: 1, size: 10 } })
  })

  it('all optional filters provided (userId + action)', () => {
    requestGet.mockReturnValue(Promise.resolve({ code: 0, data: null }))
    getBanRecords({ page: 2, size: 20, userId: 99, action: 'BAN' })
    expect(requestGet).toHaveBeenCalledWith('/ban-records', {
      params: { page: 2, size: 20, userId: 99, action: 'BAN' }
    })
  })

  it('only userId provided', () => {
    requestGet.mockReturnValue(Promise.resolve({ code: 0, data: null }))
    getBanRecords({ page: 1, size: 10, userId: 5 })
    expect(requestGet).toHaveBeenCalledWith('/ban-records', {
      params: { page: 1, size: 10, userId: 5 }
    })
  })

  it('only action provided', () => {
    requestGet.mockReturnValue(Promise.resolve({ code: 0, data: null }))
    getBanRecords({ page: 1, size: 10, action: 'UNBAN' })
    expect(requestGet).toHaveBeenCalledWith('/ban-records', {
      params: { page: 1, size: 10, action: 'UNBAN' }
    })
  })

  it('forwards the response promise', async () => {
    const fake = { code: 0, data: { list: [], total: 0, page: 1, size: 10 } }
    requestGet.mockReturnValue(Promise.resolve(fake))
    const result = await getBanRecords({ page: 1, size: 10 })
    expect(result).toBe(fake)
  })
})

describe('deleteBanRecord', () => {
  it('calls request.delete with url containing id', () => {
    requestDelete.mockReturnValue(Promise.resolve({ code: 0, data: null }))
    deleteBanRecord(13)
    expect(requestDelete).toHaveBeenCalledTimes(1)
    expect(requestDelete).toHaveBeenCalledWith('/ban-records/13')
  })

  it('handles different id values', () => {
    requestDelete.mockReturnValue(Promise.resolve({ code: 0, data: null }))
    deleteBanRecord(456)
    expect(requestDelete).toHaveBeenCalledWith('/ban-records/456')
  })

  it('forwards the response promise', async () => {
    const fake = { code: 0, data: null }
    requestDelete.mockReturnValue(Promise.resolve(fake))
    const result = await deleteBanRecord(1)
    expect(result).toBe(fake)
  })
})