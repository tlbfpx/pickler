// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach } from 'vitest'

const requestGet = vi.fn()
const requestPut = vi.fn()
const requestDelete = vi.fn()
vi.mock('./request', () => ({
  default: {
    get: (...args: unknown[]) => requestGet(...args),
    put: (...args: unknown[]) => requestPut(...args),
    delete: (...args: unknown[]) => requestDelete(...args)
  }
}))

beforeEach(() => {
  requestGet.mockReset()
  requestPut.mockReset()
  requestDelete.mockReset()
})

import {
  getPlacementPoints,
  setPlacementPoints,
  clearPlacementPoints
} from './placement'

const ok = { code: 0, data: null }

describe('getPlacementPoints', () => {
  it('calls request.get to /events/{eventId}/placement-points', () => {
    requestGet.mockReturnValue(Promise.resolve(ok))
    getPlacementPoints(7)
    expect(requestGet).toHaveBeenCalledWith('/events/7/placement-points')
  })
  it('forwards response', async () => {
    const fake = { code: 0, data: { eventId: 7, pointsTable: { '1': 100 } } }
    requestGet.mockReturnValue(Promise.resolve(fake))
    expect(await getPlacementPoints(1)).toBe(fake)
  })
})

describe('setPlacementPoints', () => {
  it('calls request.put with /events/{eventId}/placement-points + data', () => {
    requestPut.mockReturnValue(Promise.resolve(ok))
    const data = { pointsTable: { '1': 100, '2': 80 } }
    setPlacementPoints(7, data)
    expect(requestPut).toHaveBeenCalledWith('/events/7/placement-points', data)
  })
  it('forwards response', async () => {
    requestPut.mockReturnValue(Promise.resolve(ok))
    expect(await setPlacementPoints(1, { pointsTable: {} })).toBe(ok)
  })
})

describe('clearPlacementPoints', () => {
  it('calls request.delete to /events/{eventId}/placement-points', () => {
    requestDelete.mockReturnValue(Promise.resolve(ok))
    clearPlacementPoints(7)
    expect(requestDelete).toHaveBeenCalledWith('/events/7/placement-points')
  })
  it('forwards response', async () => {
    requestDelete.mockReturnValue(Promise.resolve(ok))
    expect(await clearPlacementPoints(1)).toBe(ok)
  })
})