// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach } from 'vitest'

const requestGet = vi.fn()
const requestPost = vi.fn()
const requestPut = vi.fn()
const requestDelete = vi.fn()
vi.mock('./request', () => ({
  default: {
    get: (...args: unknown[]) => requestGet(...args),
    post: (...args: unknown[]) => requestPost(...args),
    put: (...args: unknown[]) => requestPut(...args),
    delete: (...args: unknown[]) => requestDelete(...args)
  }
}))

beforeEach(() => {
  requestGet.mockReset()
  requestPost.mockReset()
  requestPut.mockReset()
  requestDelete.mockReset()
})

import {
  getVenueList,
  getVenueDetail,
  createVenue,
  updateVenue,
  deleteVenue,
  replaceBusinessHours,
  addContact,
  updateContact,
  deleteContact,
  getCourts,
  createCourt,
  updateCourt,
  deleteCourt,
  replacePricingBands,
  getPricingBands,
  copyPricingBands
} from './venues'

const ok = { code: 0, data: null }

// ============ Venue ============
describe('getVenueList', () => {
  it('with all filters (page, size, keyword, status)', () => {
    requestGet.mockReturnValue(Promise.resolve(ok))
    getVenueList({ page: 1, size: 10, keyword: 'pickler', status: 'OPEN' })
    expect(requestGet).toHaveBeenCalledWith('/venues', {
      params: { page: 1, size: 10, keyword: 'pickler', status: 'OPEN' }
    })
  })

  it('with empty params object', () => {
    requestGet.mockReturnValue(Promise.resolve(ok))
    getVenueList({})
    expect(requestGet).toHaveBeenCalledWith('/venues', { params: {} })
  })

  it('forwards response', async () => {
    const fake = { code: 0, data: { list: [], total: 0, page: 1, size: 10 } }
    requestGet.mockReturnValue(Promise.resolve(fake))
    expect(await getVenueList({ page: 1, size: 10 })).toBe(fake)
  })
})

describe('getVenueDetail', () => {
  it('calls request.get with /venues/{id}', () => {
    requestGet.mockReturnValue(Promise.resolve(ok))
    getVenueDetail(7)
    expect(requestGet).toHaveBeenCalledWith('/venues/7')
  })
  it('forwards response', async () => {
    const fake = { code: 0, data: { id: 7, name: 'A' } as any }
    requestGet.mockReturnValue(Promise.resolve(fake))
    expect(await getVenueDetail(1)).toBe(fake)
  })
})

describe('createVenue', () => {
  it('calls request.post with /venues + payload', () => {
    requestPost.mockReturnValue(Promise.resolve(ok))
    createVenue({ name: 'V', address: 'A' } as any)
    expect(requestPost).toHaveBeenCalledWith('/venues', { name: 'V', address: 'A' })
  })
  it('forwards response', async () => {
    requestPost.mockReturnValue(Promise.resolve(ok))
    expect(await createVenue({} as any)).toBe(ok)
  })
})

describe('updateVenue', () => {
  it('calls request.put with /venues/{id} + payload', () => {
    requestPut.mockReturnValue(Promise.resolve(ok))
    updateVenue(7, { name: 'V2' } as any)
    expect(requestPut).toHaveBeenCalledWith('/venues/7', { name: 'V2' })
  })
  it('forwards response', async () => {
    requestPut.mockReturnValue(Promise.resolve(ok))
    expect(await updateVenue(1, {} as any)).toBe(ok)
  })
})

describe('deleteVenue', () => {
  it('calls request.delete with /venues/{id}', () => {
    requestDelete.mockReturnValue(Promise.resolve(ok))
    deleteVenue(7)
    expect(requestDelete).toHaveBeenCalledWith('/venues/7')
  })
  it('forwards response', async () => {
    requestDelete.mockReturnValue(Promise.resolve(ok))
    expect(await deleteVenue(1)).toBe(ok)
  })
})

describe('replaceBusinessHours', () => {
  it('calls request.put with /venues/{id}/business-hours + { hours }', () => {
    requestPut.mockReturnValue(Promise.resolve(ok))
    const hours = [{ dayOfWeek: 1, openTime: '09:00', closeTime: '21:00' }]
    replaceBusinessHours(7, hours)
    expect(requestPut).toHaveBeenCalledWith('/venues/7/business-hours', { hours })
  })
  it('forwards response', async () => {
    requestPut.mockReturnValue(Promise.resolve(ok))
    expect(await replaceBusinessHours(1, [])).toBe(ok)
  })
})

// ============ Contacts ============
describe('addContact', () => {
  it('calls request.post with /venues/{venueId}/contacts + payload', () => {
    requestPost.mockReturnValue(Promise.resolve(ok))
    addContact(7, { type: 'PHONE', value: '13800000000' })
    expect(requestPost).toHaveBeenCalledWith('/venues/7/contacts', {
      type: 'PHONE',
      value: '13800000000'
    })
  })
  it('forwards response', async () => {
    requestPost.mockReturnValue(Promise.resolve(ok))
    expect(await addContact(1, { type: 'PHONE', value: 'x' })).toBe(ok)
  })
})

describe('updateContact', () => {
  it('calls request.put with /venues/contacts/{contactId} + payload', () => {
    requestPut.mockReturnValue(Promise.resolve(ok))
    updateContact(11, { type: 'WECHAT', value: 'wx', sortOrder: 2 })
    expect(requestPut).toHaveBeenCalledWith('/venues/contacts/11', {
      type: 'WECHAT',
      value: 'wx',
      sortOrder: 2
    })
  })
  it('forwards response', async () => {
    requestPut.mockReturnValue(Promise.resolve(ok))
    expect(await updateContact(1, { type: 'PHONE', value: 'x' })).toBe(ok)
  })
})

describe('deleteContact', () => {
  it('calls request.delete with /venues/contacts/{contactId}', () => {
    requestDelete.mockReturnValue(Promise.resolve(ok))
    deleteContact(11)
    expect(requestDelete).toHaveBeenCalledWith('/venues/contacts/11')
  })
  it('forwards response', async () => {
    requestDelete.mockReturnValue(Promise.resolve(ok))
    expect(await deleteContact(1)).toBe(ok)
  })
})

// ============ Courts ============
describe('getCourts', () => {
  it('calls request.get with /courts?venueId={venueId}', () => {
    requestGet.mockReturnValue(Promise.resolve(ok))
    getCourts(7)
    expect(requestGet).toHaveBeenCalledWith('/courts', { params: { venueId: 7 } })
  })
  it('forwards response', async () => {
    const fake = { code: 0, data: [{ id: 1, name: 'A' }] }
    requestGet.mockReturnValue(Promise.resolve(fake))
    expect(await getCourts(1)).toBe(fake)
  })
})

describe('createCourt', () => {
  it('calls request.post with /courts + payload', () => {
    requestPost.mockReturnValue(Promise.resolve(ok))
    createCourt({ venueId: 7, name: 'A' })
    expect(requestPost).toHaveBeenCalledWith('/courts', { venueId: 7, name: 'A' })
  })
  it('forwards response', async () => {
    requestPost.mockReturnValue(Promise.resolve(ok))
    expect(await createCourt({ venueId: 1, name: 'X' })).toBe(ok)
  })
})

describe('updateCourt', () => {
  it('calls request.put with /courts/{id} + payload', () => {
    requestPut.mockReturnValue(Promise.resolve(ok))
    updateCourt(11, { name: 'B' })
    expect(requestPut).toHaveBeenCalledWith('/courts/11', { name: 'B' })
  })
  it('forwards response', async () => {
    requestPut.mockReturnValue(Promise.resolve(ok))
    expect(await updateCourt(1, { name: 'X' })).toBe(ok)
  })
})

describe('deleteCourt', () => {
  it('calls request.delete with /courts/{id}', () => {
    requestDelete.mockReturnValue(Promise.resolve(ok))
    deleteCourt(11)
    expect(requestDelete).toHaveBeenCalledWith('/courts/11')
  })
  it('forwards response', async () => {
    requestDelete.mockReturnValue(Promise.resolve(ok))
    expect(await deleteCourt(1)).toBe(ok)
  })
})

// ============ Pricing Bands ============
describe('replacePricingBands', () => {
  it('calls request.put with /courts/{courtId}/pricing-bands + { bands }', () => {
    requestPut.mockReturnValue(Promise.resolve(ok))
    const bands = [{ dayType: 'WEEKDAY', startTime: '09:00', endTime: '12:00', price: 40 }] as any
    replacePricingBands(11, bands)
    expect(requestPut).toHaveBeenCalledWith('/courts/11/pricing-bands', { bands })
  })
  it('forwards response', async () => {
    requestPut.mockReturnValue(Promise.resolve(ok))
    expect(await replacePricingBands(1, [])).toBe(ok)
  })
})

describe('getPricingBands', () => {
  it('calls request.get with /courts/{courtId}/pricing-bands', () => {
    requestGet.mockReturnValue(Promise.resolve(ok))
    getPricingBands(11)
    expect(requestGet).toHaveBeenCalledWith('/courts/11/pricing-bands')
  })
  it('forwards response', async () => {
    const fake = { code: 0, data: [] }
    requestGet.mockReturnValue(Promise.resolve(fake))
    expect(await getPricingBands(1)).toBe(fake)
  })
})

describe('copyPricingBands', () => {
  it('calls request.post to /courts/{courtId}/pricing-bands/copy with params { from } and undefined body', () => {
    requestPost.mockReturnValue(Promise.resolve(ok))
    copyPricingBands(11, 99)
    expect(requestPost).toHaveBeenCalledWith(
      '/courts/11/pricing-bands/copy',
      undefined,
      { params: { from: 99 } }
    )
  })
  it('forwards response', async () => {
    requestPost.mockReturnValue(Promise.resolve(ok))
    expect(await copyPricingBands(1, 2)).toBe(ok)
  })
})