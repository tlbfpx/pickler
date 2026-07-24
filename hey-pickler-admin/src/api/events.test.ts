// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach } from 'vitest'

const requestGet = vi.fn()
const requestPost = vi.fn()
const requestPut = vi.fn()
const requestDelete = vi.fn()
const requestPatch = vi.fn()
vi.mock('./request', () => ({
  default: {
    get: (...args: unknown[]) => requestGet(...args),
    post: (...args: unknown[]) => requestPost(...args),
    put: (...args: unknown[]) => requestPut(...args),
    delete: (...args: unknown[]) => requestDelete(...args),
    patch: (...args: unknown[]) => requestPatch(...args)
  }
}))

beforeEach(() => {
  requestGet.mockReset()
  requestPost.mockReset()
  requestPut.mockReset()
  requestDelete.mockReset()
  requestPatch.mockReset()
})

import {
  getEventList,
  getEventDetail,
  getEventParticipants,
  createEvent,
  updateEvent,
  deleteEvent,
  changeEventStatus,
  getEventRegistrations,
  updateRegistrationStatus,
  getEventSummary,
  bulkCheckIn,
  getEventPlacements
} from './events'

const ok = { code: 0, data: null }

describe('getEventList', () => {
  it('calls request.get with url + PageParams', () => {
    requestGet.mockReturnValue(Promise.resolve(ok))
    getEventList({ page: 1, size: 10 })
    expect(requestGet).toHaveBeenCalledWith('/events', { params: { page: 1, size: 10 } })
  })

  it('forwards response', async () => {
    const fake = { code: 0, data: { list: [], total: 0, page: 1, size: 10 } }
    requestGet.mockReturnValue(Promise.resolve(fake))
    expect(await getEventList({ page: 1, size: 10 })).toBe(fake)
  })
})

describe('getEventDetail', () => {
  it('calls request.get with /events/{id}', () => {
    requestGet.mockReturnValue(Promise.resolve(ok))
    getEventDetail(7)
    expect(requestGet).toHaveBeenCalledWith('/events/7')
  })
  it('forwards response', async () => {
    const fake = { code: 0, data: { id: 7, title: 'A' } as any }
    requestGet.mockReturnValue(Promise.resolve(fake))
    expect(await getEventDetail(7)).toBe(fake)
  })
})

describe('getEventParticipants', () => {
  it('calls request.get with /events/{eventId}/participants', () => {
    requestGet.mockReturnValue(Promise.resolve(ok))
    getEventParticipants(7)
    expect(requestGet).toHaveBeenCalledWith('/events/7/participants')
  })
  it('forwards response', async () => {
    const fake = { code: 0, data: [{ userId: 1 }] }
    requestGet.mockReturnValue(Promise.resolve(fake))
    expect(await getEventParticipants(7)).toBe(fake)
  })
})

describe('createEvent', () => {
  it('calls request.post with url + payload', () => {
    requestPost.mockReturnValue(Promise.resolve(ok))
    const payload = { title: 'New', type: 'STAR' as const, format: 'SINGLES' as const, fee: 100 }
    createEvent(payload)
    expect(requestPost).toHaveBeenCalledWith('/events', payload)
  })
  it('forwards response', async () => {
    const fake = { code: 0, data: { id: 100 } }
    requestPost.mockReturnValue(Promise.resolve(fake))
    expect(await createEvent({} as any)).toBe(fake)
  })
})

describe('updateEvent', () => {
  it('calls request.put with /events/{id} + payload', () => {
    requestPut.mockReturnValue(Promise.resolve(ok))
    updateEvent(7, { title: 'Updated' })
    expect(requestPut).toHaveBeenCalledWith('/events/7', { title: 'Updated' })
  })
  it('forwards response', async () => {
    requestPut.mockReturnValue(Promise.resolve(ok))
    expect(await updateEvent(1, {} as any)).toBe(ok)
  })
})

describe('deleteEvent', () => {
  it('calls request.delete with /events/{id}', () => {
    requestDelete.mockReturnValue(Promise.resolve(ok))
    deleteEvent(7)
    expect(requestDelete).toHaveBeenCalledWith('/events/7')
  })
  it('forwards response', async () => {
    requestDelete.mockReturnValue(Promise.resolve(ok))
    expect(await deleteEvent(1)).toBe(ok)
  })
})

describe('changeEventStatus', () => {
  it('calls request.patch with /events/{id}/status + { status }', () => {
    requestPatch.mockReturnValue(Promise.resolve(ok))
    changeEventStatus(7, 'OPEN')
    expect(requestPatch).toHaveBeenCalledWith('/events/7/status', { status: 'OPEN' })
  })
  it('forwards response', async () => {
    requestPatch.mockReturnValue(Promise.resolve(ok))
    expect(await changeEventStatus(1, 'OPEN')).toBe(ok)
  })
})

describe('getEventRegistrations', () => {
  it('calls request.get with /events/{eventId}/registrations + PageParams', () => {
    requestGet.mockReturnValue(Promise.resolve(ok))
    getEventRegistrations(7, { page: 1, size: 10 })
    expect(requestGet).toHaveBeenCalledWith('/events/7/registrations', {
      params: { page: 1, size: 10 }
    })
  })
  it('forwards response', async () => {
    const fake = { code: 0, data: { list: [], total: 0, page: 1, size: 10 } }
    requestGet.mockReturnValue(Promise.resolve(fake))
    expect(await getEventRegistrations(7, { page: 1, size: 10 })).toBe(fake)
  })
})

describe('updateRegistrationStatus', () => {
  it('calls request.patch with /events/{eventId}/registrations/{regId}/status + { status }', () => {
    requestPatch.mockReturnValue(Promise.resolve(ok))
    updateRegistrationStatus(7, 42, 'CHECKED_IN')
    expect(requestPatch).toHaveBeenCalledWith('/events/7/registrations/42/status', {
      status: 'CHECKED_IN'
    })
  })
  it('forwards response', async () => {
    requestPatch.mockReturnValue(Promise.resolve(ok))
    expect(await updateRegistrationStatus(1, 2, 'OPEN')).toBe(ok)
  })
})

describe('getEventSummary', () => {
  it('calls request.get with /events/{eventId}/summary', () => {
    requestGet.mockReturnValue(Promise.resolve(ok))
    getEventSummary(7)
    expect(requestGet).toHaveBeenCalledWith('/events/7/summary')
  })
  it('forwards response', async () => {
    const fake = { code: 0, data: { eventId: 7, totalRegistrations: 5 } as any }
    requestGet.mockReturnValue(Promise.resolve(fake))
    expect(await getEventSummary(7)).toBe(fake)
  })
})

describe('bulkCheckIn', () => {
  it('calls request.post with /events/{eventId}/registrations/bulk-check-in + { registrationIds }', () => {
    requestPost.mockReturnValue(Promise.resolve(ok))
    bulkCheckIn(7, [1, 2, 3])
    expect(requestPost).toHaveBeenCalledWith(
      '/events/7/registrations/bulk-check-in',
      { registrationIds: [1, 2, 3] }
    )
  })
  it('forwards response', async () => {
    const fake = { code: 0, data: { successCount: 3, failedCount: 0 } as any }
    requestPost.mockReturnValue(Promise.resolve(fake))
    expect(await bulkCheckIn(7, [1])).toBe(fake)
  })
})

describe('getEventPlacements', () => {
  it('calls request.get with /events/{id}/placements', () => {
    requestGet.mockReturnValue(Promise.resolve(ok))
    getEventPlacements(7)
    expect(requestGet).toHaveBeenCalledWith('/events/7/placements')
  })
  it('forwards response', async () => {
    const fake = { code: 0, data: [{ rank: 1, userId: 5 }] }
    requestGet.mockReturnValue(Promise.resolve(fake))
    expect(await getEventPlacements(7)).toBe(fake)
  })
})