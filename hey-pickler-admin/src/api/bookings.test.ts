// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach } from 'vitest'

const requestGet = vi.fn()
const requestPost = vi.fn()
vi.mock('./request', () => ({
  default: {
    get: (...args: unknown[]) => requestGet(...args),
    post: (...args: unknown[]) => requestPost(...args)
  }
}))

beforeEach(() => {
  requestGet.mockReset()
  requestPost.mockReset()
})

import {
  getBookingList,
  getBookingDetail,
  completeBooking,
  noShowBooking,
  forceCancelBooking,
  type BookingQuery
} from './bookings'

const ok = { code: 0, data: null }

describe('getBookingList', () => {
  it('calls request.get with url and full BookingQuery params', () => {
    requestGet.mockReturnValue(Promise.resolve(ok))
    const q: BookingQuery = { page: 1, size: 10, status: 'CONFIRMED', userId: 5 }
    getBookingList(q)
    expect(requestGet).toHaveBeenCalledTimes(1)
    expect(requestGet).toHaveBeenCalledWith('/bookings', { params: q })
  })

  it('forwards the response promise', async () => {
    const fake = { code: 0, data: { list: [], total: 0, page: 1, size: 10 } }
    requestGet.mockReturnValue(Promise.resolve(fake))
    const result = await getBookingList({ page: 1, size: 10 })
    expect(result).toBe(fake)
  })
})

describe('getBookingDetail', () => {
  it('calls request.get with url containing id', () => {
    requestGet.mockReturnValue(Promise.resolve(ok))
    getBookingDetail(42)
    expect(requestGet).toHaveBeenCalledTimes(1)
    expect(requestGet).toHaveBeenCalledWith('/bookings/42')
  })

  it('forwards the response promise', async () => {
    const fake = { code: 0, data: { id: 42, bookingNo: 'BK20260801-0001' } as any }
    requestGet.mockReturnValue(Promise.resolve(fake))
    const result = await getBookingDetail(42)
    expect(result).toBe(fake)
  })
})

describe('completeBooking', () => {
  it('calls request.post to /bookings/{id}/complete, no body', () => {
    requestPost.mockReturnValue(Promise.resolve(ok))
    completeBooking(7)
    expect(requestPost).toHaveBeenCalledTimes(1)
    expect(requestPost).toHaveBeenCalledWith('/bookings/7/complete')
  })

  it('forwards the response promise', async () => {
    requestPost.mockReturnValue(Promise.resolve(ok))
    const result = await completeBooking(1)
    expect(result).toBe(ok)
  })
})

describe('noShowBooking', () => {
  it('calls request.post to /bookings/{id}/no-show, no body', () => {
    requestPost.mockReturnValue(Promise.resolve(ok))
    noShowBooking(7)
    expect(requestPost).toHaveBeenCalledWith('/bookings/7/no-show')
  })

  it('forwards the response promise', async () => {
    requestPost.mockReturnValue(Promise.resolve(ok))
    const result = await noShowBooking(1)
    expect(result).toBe(ok)
  })
})

describe('forceCancelBooking', () => {
  it('without body → sends empty object {} (default)', () => {
    requestPost.mockReturnValue(Promise.resolve(ok))
    forceCancelBooking(7)
    expect(requestPost).toHaveBeenCalledTimes(1)
    expect(requestPost).toHaveBeenCalledWith('/bookings/7/cancel', {})
  })

  it('with body → sends the body', () => {
    requestPost.mockReturnValue(Promise.resolve(ok))
    forceCancelBooking(7, { reason: 'court closed' })
    expect(requestPost).toHaveBeenCalledWith('/bookings/7/cancel', { reason: 'court closed' })
  })

  it('body=undefined → still defaults to empty object', () => {
    requestPost.mockReturnValue(Promise.resolve(ok))
    forceCancelBooking(7, undefined)
    expect(requestPost).toHaveBeenCalledWith('/bookings/7/cancel', {})
  })

  it('forwards the response promise', async () => {
    requestPost.mockReturnValue(Promise.resolve(ok))
    const result = await forceCancelBooking(1)
    expect(result).toBe(ok)
  })
})