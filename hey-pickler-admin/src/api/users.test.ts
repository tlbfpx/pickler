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
  getUserList,
  getUserDetail,
  getUserPoints,
  getUserEvents,
  banUser,
  unbanUser
} from './users'

const ok = { code: 0, data: null }

describe('getUserList', () => {
  it('with PageParams only', () => {
    requestGet.mockReturnValue(Promise.resolve(ok))
    getUserList({ page: 1, size: 10 })
    expect(requestGet).toHaveBeenCalledWith('/users', { params: { page: 1, size: 10 } })
  })

  it('with city filter', () => {
    requestGet.mockReturnValue(Promise.resolve(ok))
    getUserList({ page: 1, size: 10, city: 'Shanghai' })
    expect(requestGet).toHaveBeenCalledWith('/users', {
      params: { page: 1, size: 10, city: 'Shanghai' }
    })
  })

  it('forwards response', async () => {
    const fake = { code: 0, data: { list: [], total: 0, page: 1, size: 10 } }
    requestGet.mockReturnValue(Promise.resolve(fake))
    expect(await getUserList({ page: 1, size: 10 })).toBe(fake)
  })
})

describe('getUserDetail', () => {
  it('calls request.get with /users/{id}', () => {
    requestGet.mockReturnValue(Promise.resolve(ok))
    getUserDetail(7)
    expect(requestGet).toHaveBeenCalledWith('/users/7')
  })
  it('forwards response', async () => {
    const fake = { code: 0, data: { id: 7, nickname: 'A' } as any }
    requestGet.mockReturnValue(Promise.resolve(fake))
    expect(await getUserDetail(7)).toBe(fake)
  })
})

describe('getUserPoints', () => {
  it('with required page + size only', () => {
    requestGet.mockReturnValue(Promise.resolve(ok))
    getUserPoints(7, { page: 1, size: 10 })
    expect(requestGet).toHaveBeenCalledWith('/users/7/points', {
      params: { page: 1, size: 10 }
    })
  })

  it('with type filter', () => {
    requestGet.mockReturnValue(Promise.resolve(ok))
    getUserPoints(7, { type: 'STAR', page: 1, size: 10 })
    expect(requestGet).toHaveBeenCalledWith('/users/7/points', {
      params: { type: 'STAR', page: 1, size: 10 }
    })
  })

  it('forwards response', async () => {
    const fake = { code: 0, data: { list: [], total: 0, page: 1, size: 10 } }
    requestGet.mockReturnValue(Promise.resolve(fake))
    expect(await getUserPoints(7, { page: 1, size: 10 })).toBe(fake)
  })
})

describe('getUserEvents', () => {
  it('with required page + size only', () => {
    requestGet.mockReturnValue(Promise.resolve(ok))
    getUserEvents(7, { page: 1, size: 10 })
    expect(requestGet).toHaveBeenCalledWith('/users/7/events', {
      params: { page: 1, size: 10 }
    })
  })

  it('with type filter', () => {
    requestGet.mockReturnValue(Promise.resolve(ok))
    getUserEvents(7, { type: 'PARTY', page: 1, size: 10 })
    expect(requestGet).toHaveBeenCalledWith('/users/7/events', {
      params: { type: 'PARTY', page: 1, size: 10 }
    })
  })

  it('forwards response', async () => {
    const fake = { code: 0, data: { list: [], total: 0, page: 1, size: 10 } }
    requestGet.mockReturnValue(Promise.resolve(fake))
    expect(await getUserEvents(7, { page: 1, size: 10 })).toBe(fake)
  })
})

describe('banUser', () => {
  it('with reason only', () => {
    requestPost.mockReturnValue(Promise.resolve(ok))
    banUser(7, { reason: '违规' })
    expect(requestPost).toHaveBeenCalledWith('/users/7/ban', { reason: '违规' })
  })

  it('with reason + days', () => {
    requestPost.mockReturnValue(Promise.resolve(ok))
    banUser(7, { reason: 'spam', days: 7 })
    expect(requestPost).toHaveBeenCalledWith('/users/7/ban', { reason: 'spam', days: 7 })
  })

  it('forwards response', async () => {
    requestPost.mockReturnValue(Promise.resolve(ok))
    expect(await banUser(1, { reason: 'x' })).toBe(ok)
  })
})

describe('unbanUser', () => {
  it('calls request.post to /users/{id}/unban', () => {
    requestPost.mockReturnValue(Promise.resolve(ok))
    unbanUser(7)
    expect(requestPost).toHaveBeenCalledWith('/users/7/unban')
  })
  it('forwards response', async () => {
    requestPost.mockReturnValue(Promise.resolve(ok))
    expect(await unbanUser(1)).toBe(ok)
  })
})