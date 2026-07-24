// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach } from 'vitest'

const requestGet = vi.fn()
const requestPost = vi.fn()
const requestDelete = vi.fn()
vi.mock('./request', () => ({
  default: {
    get: (...args: unknown[]) => requestGet(...args),
    post: (...args: unknown[]) => requestPost(...args),
    delete: (...args: unknown[]) => requestDelete(...args)
  }
}))

beforeEach(() => {
  requestGet.mockReset()
  requestPost.mockReset()
  requestDelete.mockReset()
})

import {
  listEventTeams,
  createTeam,
  confirmTeam,
  declineTeam,
  dissolveTeam,
  getTeamInvite
} from './teams'

const ok = { code: 0, data: null }

describe('listEventTeams', () => {
  it('calls request.get to /events/{eventId}/teams', () => {
    requestGet.mockReturnValue(Promise.resolve(ok))
    listEventTeams(7)
    expect(requestGet).toHaveBeenCalledWith('/events/7/teams')
  })
  it('forwards response', async () => {
    const fake = { code: 0, data: [{ id: 1, status: 'CONFIRMED' }] }
    requestGet.mockReturnValue(Promise.resolve(fake))
    expect(await listEventTeams(1)).toBe(fake)
  })
})

describe('createTeam', () => {
  it('calls request.post with full payload (including optional name)', () => {
    requestPost.mockReturnValue(Promise.resolve(ok))
    const payload = { captainUserId: 1, partnerUserId: 2, name: 'Team A' }
    createTeam(7, payload)
    expect(requestPost).toHaveBeenCalledWith('/events/7/teams', payload)
  })

  it('calls request.post with minimal payload (no name)', () => {
    requestPost.mockReturnValue(Promise.resolve(ok))
    const payload = { captainUserId: 1, partnerUserId: 2 }
    createTeam(7, payload)
    expect(requestPost).toHaveBeenCalledWith('/events/7/teams', payload)
  })

  it('forwards response', async () => {
    const fake = { code: 0, data: { id: 1, status: 'PENDING' } as any }
    requestPost.mockReturnValue(Promise.resolve(fake))
    expect(await createTeam(1, { captainUserId: 1, partnerUserId: 2 })).toBe(fake)
  })
})

describe('confirmTeam', () => {
  it('calls request.post with teamId + UserScopedTeamRequest', () => {
    requestPost.mockReturnValue(Promise.resolve(ok))
    confirmTeam(7, { userId: 2 })
    expect(requestPost).toHaveBeenCalledWith('/teams/7/confirm', { userId: 2 })
  })
  it('forwards response', async () => {
    const fake = { code: 0, data: { id: 7, status: 'CONFIRMED' } as any }
    requestPost.mockReturnValue(Promise.resolve(fake))
    expect(await confirmTeam(1, { userId: 1 })).toBe(fake)
  })
})

describe('declineTeam', () => {
  it('calls request.post with teamId + UserScopedTeamRequest', () => {
    requestPost.mockReturnValue(Promise.resolve(ok))
    declineTeam(7, { userId: 2 })
    expect(requestPost).toHaveBeenCalledWith('/teams/7/decline', { userId: 2 })
  })
  it('forwards response', async () => {
    requestPost.mockReturnValue(Promise.resolve(ok))
    expect(await declineTeam(1, { userId: 1 })).toBe(ok)
  })
})

describe('dissolveTeam', () => {
  it('calls request.delete to /teams/{teamId}', () => {
    requestDelete.mockReturnValue(Promise.resolve(ok))
    dissolveTeam(7)
    expect(requestDelete).toHaveBeenCalledWith('/teams/7')
  })
  it('forwards response', async () => {
    requestDelete.mockReturnValue(Promise.resolve(ok))
    expect(await dissolveTeam(1)).toBe(ok)
  })
})

describe('getTeamInvite', () => {
  it('calls request.get to /teams/{teamId}/invite', () => {
    requestGet.mockReturnValue(Promise.resolve(ok))
    getTeamInvite(7)
    expect(requestGet).toHaveBeenCalledWith('/teams/7/invite')
  })
  it('forwards response', async () => {
    const fake = { code: 0, data: { teamId: 7, eventId: 1, eventTitle: 'A', captainName: 'B', expiresAt: '2026-08-01' } }
    requestGet.mockReturnValue(Promise.resolve(fake))
    expect(await getTeamInvite(1)).toBe(fake)
  })
})