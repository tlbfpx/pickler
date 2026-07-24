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
  getNotifications,
  getUnreadCount,
  markNotificationRead,
  markAllNotificationsRead
} from './notifications'

const ok = { code: 0, data: null }

describe('getNotifications', () => {
  it('with both page + size', () => {
    requestGet.mockReturnValue(Promise.resolve(ok))
    getNotifications({ page: 1, size: 10 })
    expect(requestGet).toHaveBeenCalledWith('/notifications', {
      params: { page: 1, size: 10 }
    })
  })

  it('with only page', () => {
    requestGet.mockReturnValue(Promise.resolve(ok))
    getNotifications({ page: 2 })
    expect(requestGet).toHaveBeenCalledWith('/notifications', { params: { page: 2 } })
  })

  it('with only size', () => {
    requestGet.mockReturnValue(Promise.resolve(ok))
    getNotifications({ size: 20 })
    expect(requestGet).toHaveBeenCalledWith('/notifications', { params: { size: 20 } })
  })

  it('with empty params object', () => {
    requestGet.mockReturnValue(Promise.resolve(ok))
    getNotifications({})
    expect(requestGet).toHaveBeenCalledWith('/notifications', { params: {} })
  })

  it('forwards response', async () => {
    const fake = { code: 0, data: { list: [], total: 0, page: 1, size: 10 } }
    requestGet.mockReturnValue(Promise.resolve(fake))
    expect(await getNotifications({ page: 1, size: 10 })).toBe(fake)
  })
})

describe('getUnreadCount', () => {
  it('calls request.get to /notifications/unread-count', () => {
    requestGet.mockReturnValue(Promise.resolve(ok))
    getUnreadCount()
    expect(requestGet).toHaveBeenCalledWith('/notifications/unread-count')
  })
  it('forwards response', async () => {
    const fake = { code: 0, data: { count: 5 } }
    requestGet.mockReturnValue(Promise.resolve(fake))
    expect(await getUnreadCount()).toBe(fake)
  })
})

describe('markNotificationRead', () => {
  it('calls request.post to /notifications/{id}/read', () => {
    requestPost.mockReturnValue(Promise.resolve(ok))
    markNotificationRead(7)
    expect(requestPost).toHaveBeenCalledWith('/notifications/7/read')
  })
  it('forwards response', async () => {
    const fake = { code: 0, data: { updated: true } }
    requestPost.mockReturnValue(Promise.resolve(fake))
    expect(await markNotificationRead(1)).toBe(fake)
  })
})

describe('markAllNotificationsRead', () => {
  it('calls request.post to /notifications/read-all', () => {
    requestPost.mockReturnValue(Promise.resolve(ok))
    markAllNotificationsRead()
    expect(requestPost).toHaveBeenCalledWith('/notifications/read-all')
  })
  it('forwards response', async () => {
    const fake = { code: 0, data: { updated: 5 } }
    requestPost.mockReturnValue(Promise.resolve(fake))
    expect(await markAllNotificationsRead()).toBe(fake)
  })
})