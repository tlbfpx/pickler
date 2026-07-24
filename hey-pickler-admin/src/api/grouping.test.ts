// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach } from 'vitest'

const requestGet = vi.fn()
const requestPost = vi.fn()
const requestPut = vi.fn()
vi.mock('./request', () => ({
  default: {
    get: (...args: unknown[]) => requestGet(...args),
    post: (...args: unknown[]) => requestPost(...args),
    put: (...args: unknown[]) => requestPut(...args)
  }
}))

beforeEach(() => {
  requestGet.mockReset()
  requestPost.mockReset()
  requestPut.mockReset()
})

import {
  groupEvent,
  getGroups,
  reassignParticipant,
  lockGroups,
  unlockGroups
} from './grouping'

const ok = { code: 0, data: null }

describe('groupEvent', () => {
  it('calls request.post with strategy + groupCount', () => {
    requestPost.mockReturnValue(Promise.resolve(ok))
    groupEvent(7, 'SERPENTINE', 4)
    expect(requestPost).toHaveBeenCalledWith('/events/7/grouping', {
      strategy: 'SERPENTINE',
      groupCount: 4
    })
  })

  it('handles different strategy', () => {
    requestPost.mockReturnValue(Promise.resolve(ok))
    groupEvent(7, 'RANDOM', 8)
    expect(requestPost).toHaveBeenCalledWith('/events/7/grouping', {
      strategy: 'RANDOM',
      groupCount: 8
    })
  })

  it('handles MANUAL strategy', () => {
    requestPost.mockReturnValue(Promise.resolve(ok))
    groupEvent(7, 'MANUAL', 2)
    expect(requestPost).toHaveBeenCalledWith('/events/7/grouping', {
      strategy: 'MANUAL',
      groupCount: 2
    })
  })

  it('forwards response', async () => {
    const fake = { code: 0, data: [{ id: 1, eventId: 7, groupIndex: 0, name: 'A', assignments: [] }] }
    requestPost.mockReturnValue(Promise.resolve(fake))
    expect(await groupEvent(7, 'RANDOM', 2)).toBe(fake)
  })
})

describe('getGroups', () => {
  it('calls request.get with /events/{eventId}/grouping', () => {
    requestGet.mockReturnValue(Promise.resolve(ok))
    getGroups(7)
    expect(requestGet).toHaveBeenCalledWith('/events/7/grouping')
  })
  it('forwards response', async () => {
    const fake = { code: 0, data: [] }
    requestGet.mockReturnValue(Promise.resolve(fake))
    expect(await getGroups(7)).toBe(fake)
  })
})

describe('reassignParticipant', () => {
  it('calls request.put with assignment id + targetGroupId', () => {
    requestPut.mockReturnValue(Promise.resolve(ok))
    reassignParticipant(7, 42, 99)
    expect(requestPut).toHaveBeenCalledWith('/events/7/grouping/assignments/42', {
      targetGroupId: 99
    })
  })
  it('forwards response', async () => {
    requestPut.mockReturnValue(Promise.resolve(ok))
    expect(await reassignParticipant(1, 2, 3)).toBe(ok)
  })
})

describe('lockGroups', () => {
  it('calls request.post to /events/{eventId}/grouping/lock', () => {
    requestPost.mockReturnValue(Promise.resolve(ok))
    lockGroups(7)
    expect(requestPost).toHaveBeenCalledWith('/events/7/grouping/lock')
  })
  it('forwards response', async () => {
    requestPost.mockReturnValue(Promise.resolve(ok))
    expect(await lockGroups(1)).toBe(ok)
  })
})

describe('unlockGroups', () => {
  it('calls request.post to /events/{eventId}/grouping/unlock', () => {
    requestPost.mockReturnValue(Promise.resolve(ok))
    unlockGroups(7)
    expect(requestPost).toHaveBeenCalledWith('/events/7/grouping/unlock')
  })
  it('forwards response', async () => {
    requestPost.mockReturnValue(Promise.resolve(ok))
    expect(await unlockGroups(1)).toBe(ok)
  })
})