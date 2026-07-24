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
  generateMatches,
  getEventMatches,
  getEventStandings,
  submitMatchScore,
  resetMatch,
  completeEvent
} from './matches'

const ok = { code: 0, data: null }

describe('generateMatches', () => {
  it('calls request.post to /events/{eventId}/matches/generate', () => {
    requestPost.mockReturnValue(Promise.resolve(ok))
    generateMatches(7)
    expect(requestPost).toHaveBeenCalledWith('/events/7/matches/generate')
  })
  it('forwards response', async () => {
    const fake = { code: 0, data: [{ id: 1 }] }
    requestPost.mockReturnValue(Promise.resolve(fake))
    expect(await generateMatches(1)).toBe(fake)
  })
})

describe('getEventMatches', () => {
  it('calls request.get to /events/{eventId}/matches', () => {
    requestGet.mockReturnValue(Promise.resolve(ok))
    getEventMatches(7)
    expect(requestGet).toHaveBeenCalledWith('/events/7/matches')
  })
  it('forwards response', async () => {
    const fake = { code: 0, data: [[{ id: 1 }]] }
    requestGet.mockReturnValue(Promise.resolve(fake))
    expect(await getEventMatches(1)).toBe(fake)
  })
})

describe('getEventStandings', () => {
  it('calls request.get to /events/{eventId}/standings', () => {
    requestGet.mockReturnValue(Promise.resolve(ok))
    getEventStandings(7)
    expect(requestGet).toHaveBeenCalledWith('/events/7/standings')
  })
  it('forwards response', async () => {
    const fake = { code: 0, data: [[{ groupIndex: 0, userId: 1 } as any]] }
    requestGet.mockReturnValue(Promise.resolve(fake))
    expect(await getEventStandings(1)).toBe(fake)
  })
})

describe('submitMatchScore', () => {
  it('calls request.post with /matches/{matchId}/score + { games }', () => {
    requestPost.mockReturnValue(Promise.resolve(ok))
    submitMatchScore(7, [{ game: 1, a: 21, b: 15 }, { game: 2, a: 21, b: 18 }])
    expect(requestPost).toHaveBeenCalledWith('/matches/7/score', {
      games: [{ game: 1, a: 21, b: 15 }, { game: 2, a: 21, b: 18 }]
    })
  })
  it('forwards response', async () => {
    requestPost.mockReturnValue(Promise.resolve(ok))
    expect(await submitMatchScore(1, [{ game: 1, a: 21, b: 0 }])).toBe(ok)
  })
})

describe('resetMatch', () => {
  it('calls request.post to /matches/{matchId}/reset', () => {
    requestPost.mockReturnValue(Promise.resolve(ok))
    resetMatch(7)
    expect(requestPost).toHaveBeenCalledWith('/matches/7/reset')
  })
  it('forwards response', async () => {
    requestPost.mockReturnValue(Promise.resolve(ok))
    expect(await resetMatch(1)).toBe(ok)
  })
})

describe('completeEvent', () => {
  it('calls request.post to /events/{eventId}/complete', () => {
    requestPost.mockReturnValue(Promise.resolve(ok))
    completeEvent(7)
    expect(requestPost).toHaveBeenCalledWith('/events/7/complete')
  })
  it('forwards response', async () => {
    requestPost.mockReturnValue(Promise.resolve(ok))
    expect(await completeEvent(1)).toBe(ok)
  })
})