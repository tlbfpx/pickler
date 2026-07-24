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

import { getAdminList, createAdmin, updateAdmin, resetAdminPassword, deleteAdmin } from './admins'

const ok = { code: 0, data: null }

describe('getAdminList', () => {
  it('calls request.get with url and params', () => {
    requestGet.mockReturnValue(Promise.resolve(ok))
    getAdminList({ page: 1, size: 10 })
    expect(requestGet).toHaveBeenCalledTimes(1)
    expect(requestGet).toHaveBeenCalledWith('/admin-users', { params: { page: 1, size: 10 } })
  })

  it('forwards the response promise', async () => {
    const fake = { code: 0, data: { list: [], total: 0, page: 1, size: 10 } }
    requestGet.mockReturnValue(Promise.resolve(fake))
    const result = await getAdminList({ page: 2, size: 20 })
    expect(result).toBe(fake)
  })
})

describe('createAdmin', () => {
  it('calls request.post with url and payload', () => {
    requestPost.mockReturnValue(Promise.resolve(ok))
    const payload = { username: 'newadmin', password: 'pwd', role: 'ADMIN' as const }
    createAdmin(payload)
    expect(requestPost).toHaveBeenCalledTimes(1)
    expect(requestPost).toHaveBeenCalledWith('/admin-users', payload)
  })

  it('forwards the response promise', async () => {
    const fake = { code: 0, data: null, message: 'ok' }
    requestPost.mockReturnValue(Promise.resolve(fake))
    const result = await createAdmin({ username: 'a', password: 'b', role: 'OPERATOR' as const })
    expect(result).toBe(fake)
  })
})

describe('updateAdmin', () => {
  it('calls request.put with url containing id and payload', () => {
    requestPut.mockReturnValue(Promise.resolve(ok))
    const payload = { username: 'updated' }
    updateAdmin(42, payload)
    expect(requestPut).toHaveBeenCalledTimes(1)
    expect(requestPut).toHaveBeenCalledWith('/admin-users/42', payload)
  })

  it('handles different id values', () => {
    requestPut.mockReturnValue(Promise.resolve(ok))
    updateAdmin(999, { role: 'SUPER_ADMIN' as const })
    expect(requestPut).toHaveBeenCalledWith('/admin-users/999', { role: 'SUPER_ADMIN' })
  })

  it('forwards the response promise', async () => {
    const fake = { code: 0, data: null }
    requestPut.mockReturnValue(Promise.resolve(fake))
    const result = await updateAdmin(1, { username: 'x' })
    expect(result).toBe(fake)
  })
})

describe('resetAdminPassword', () => {
  it('calls request.post to /admin-users/{id}/reset-password with { newPassword }', () => {
    requestPost.mockReturnValue(Promise.resolve(ok))
    resetAdminPassword(7, 'newSecret')
    expect(requestPost).toHaveBeenCalledTimes(1)
    expect(requestPost).toHaveBeenCalledWith('/admin-users/7/reset-password', { newPassword: 'newSecret' })
  })

  it('forwards the response promise', async () => {
    requestPost.mockReturnValue(Promise.resolve(ok))
    const result = await resetAdminPassword(1, 'pwd')
    expect(result).toBe(ok)
  })
})

describe('deleteAdmin', () => {
  it('calls request.delete with url containing id, no body', () => {
    requestDelete.mockReturnValue(Promise.resolve(ok))
    deleteAdmin(13)
    expect(requestDelete).toHaveBeenCalledTimes(1)
    expect(requestDelete).toHaveBeenCalledWith('/admin-users/13')
  })

  it('handles different id values', () => {
    requestDelete.mockReturnValue(Promise.resolve(ok))
    deleteAdmin(456)
    expect(requestDelete).toHaveBeenCalledWith('/admin-users/456')
  })

  it('forwards the response promise', async () => {
    const fake = { code: 0, data: null }
    requestDelete.mockReturnValue(Promise.resolve(fake))
    const result = await deleteAdmin(1)
    expect(result).toBe(fake)
  })
})