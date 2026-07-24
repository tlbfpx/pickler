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
  getRankings,
  enterPoints,
  refreshRankings,
  revertPointRecord
} from './rankings'

const ok = { code: 0, data: null }

describe('getRankings', () => {
  it('STAR + full query (page, size, keyword, tier)', () => {
    requestGet.mockReturnValue(Promise.resolve(ok))
    getRankings({ type: 'STAR', page: 1, size: 10, keyword: 'pickler', tier: 'GOLD' })
    expect(requestGet).toHaveBeenCalledWith('/rankings/STAR', {
      params: { page: 1, size: 10, keyword: 'pickler', tier: 'GOLD' }
    })
  })

  it('PARTY + minimal query (only type)', () => {
    requestGet.mockReturnValue(Promise.resolve(ok))
    getRankings({ type: 'PARTY' })
    expect(requestGet).toHaveBeenCalledWith('/rankings/PARTY', {
      params: { page: undefined, size: undefined, keyword: undefined, tier: undefined }
    })
  })

  it('partial query — only keyword', () => {
    requestGet.mockReturnValue(Promise.resolve(ok))
    getRankings({ type: 'STAR', keyword: 'pickler' })
    expect(requestGet).toHaveBeenCalledWith('/rankings/STAR', {
      params: { page: undefined, size: undefined, keyword: 'pickler', tier: undefined }
    })
  })

  it('forwards response', async () => {
    const fake = { code: 0, data: { list: [], total: 0, page: 1, size: 10 } as any }
    requestGet.mockReturnValue(Promise.resolve(fake))
    expect(await getRankings({ type: 'STAR' })).toBe(fake)
  })
})

describe('enterPoints', () => {
  it('calls request.post to /rankings/points with payload', () => {
    requestPost.mockReturnValue(Promise.resolve(ok))
    const payload = { userId: 7, points: 100, source: 'MANUAL' as const, reason: 'award' }
    enterPoints(payload)
    expect(requestPost).toHaveBeenCalledWith('/rankings/points', payload)
  })

  it('forwards response', async () => {
    requestPost.mockReturnValue(Promise.resolve(ok))
    expect(await enterPoints({} as any)).toBe(ok)
  })
})

describe('refreshRankings', () => {
  it('STAR → post /rankings/refresh with { type: "STAR" }', () => {
    requestPost.mockReturnValue(Promise.resolve(ok))
    refreshRankings('STAR')
    expect(requestPost).toHaveBeenCalledWith('/rankings/refresh', { type: 'STAR' })
  })

  it('PARTY → post /rankings/refresh with { type: "PARTY" }', () => {
    requestPost.mockReturnValue(Promise.resolve(ok))
    refreshRankings('PARTY')
    expect(requestPost).toHaveBeenCalledWith('/rankings/refresh', { type: 'PARTY' })
  })

  it('forwards response', async () => {
    requestPost.mockReturnValue(Promise.resolve(ok))
    expect(await refreshRankings('STAR')).toBe(ok)
  })
})

describe('revertPointRecord', () => {
  it('calls request.post to /rankings/points/{recordId}/revert', () => {
    requestPost.mockReturnValue(Promise.resolve(ok))
    revertPointRecord(7)
    expect(requestPost).toHaveBeenCalledWith('/rankings/points/7/revert')
  })

  it('forwards response', async () => {
    requestPost.mockReturnValue(Promise.resolve(ok))
    expect(await revertPointRecord(1)).toBe(ok)
  })
})