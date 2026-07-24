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

import { listSeasons, createSeason, activateSeason, getSeasonRankings } from './seasons'

const ok = { code: 0, data: null }

describe('listSeasons', () => {
  it('STAR → params { type: "STAR" }', () => {
    requestGet.mockReturnValue(Promise.resolve(ok))
    listSeasons('STAR')
    expect(requestGet).toHaveBeenCalledWith('/seasons', { params: { type: 'STAR' } })
  })

  it('PARTY → params { type: "PARTY" }', () => {
    requestGet.mockReturnValue(Promise.resolve(ok))
    listSeasons('PARTY')
    expect(requestGet).toHaveBeenCalledWith('/seasons', { params: { type: 'PARTY' } })
  })

  it('forwards response', async () => {
    const fake = { code: 0, data: [{ id: 1, code: '2026H1', name: '2026 H1', status: 'CURRENT' }] }
    requestGet.mockReturnValue(Promise.resolve(fake))
    expect(await listSeasons('STAR')).toBe(fake)
  })
})

describe('createSeason', () => {
  it('calls request.post with full payload', () => {
    requestPost.mockReturnValue(Promise.resolve(ok))
    const payload = { type: 'STAR' as const, code: '2026H1', name: '2026 H1', startDate: '2026-01-01', endDate: '2026-06-30' }
    createSeason(payload)
    expect(requestPost).toHaveBeenCalledWith('/seasons', payload)
  })

  it('forwards response', async () => {
    const fake = { code: 0, data: { id: 1 } }
    requestPost.mockReturnValue(Promise.resolve(fake))
    expect(await createSeason({} as any)).toBe(fake)
  })
})

describe('activateSeason', () => {
  it('calls request.post to /seasons/{id}/activate', () => {
    requestPost.mockReturnValue(Promise.resolve(ok))
    activateSeason(7)
    expect(requestPost).toHaveBeenCalledWith('/seasons/7/activate')
  })
  it('forwards response', async () => {
    requestPost.mockReturnValue(Promise.resolve(ok))
    expect(await activateSeason(1)).toBe(ok)
  })
})

describe('getSeasonRankings', () => {
  it('with required page + size only', () => {
    requestGet.mockReturnValue(Promise.resolve(ok))
    getSeasonRankings(7, { page: 1, size: 10 })
    expect(requestGet).toHaveBeenCalledWith('/seasons/7/rankings', {
      params: { page: 1, size: 10 }
    })
  })

  it('with tier filter', () => {
    requestGet.mockReturnValue(Promise.resolve(ok))
    getSeasonRankings(7, { tier: 'GOLD', page: 2, size: 20 })
    expect(requestGet).toHaveBeenCalledWith('/seasons/7/rankings', {
      params: { tier: 'GOLD', page: 2, size: 20 }
    })
  })

  it('forwards response', async () => {
    const fake = { code: 0, data: { list: [], total: 0, page: 1, size: 10 } as any }
    requestGet.mockReturnValue(Promise.resolve(fake))
    expect(await getSeasonRankings(1, { page: 1, size: 10 })).toBe(fake)
  })
})