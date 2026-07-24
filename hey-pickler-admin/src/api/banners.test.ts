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

import { getBannerList, createBanner, updateBanner, deleteBanner } from './banners'

const ok = { code: 0, data: null }

describe('getBannerList', () => {
  it('calls request.get with url only, no params', () => {
    requestGet.mockReturnValue(Promise.resolve(ok))
    getBannerList()
    expect(requestGet).toHaveBeenCalledTimes(1)
    expect(requestGet).toHaveBeenCalledWith('/banners')
  })

  it('forwards the response promise', async () => {
    const fake = { code: 0, data: [{ id: 1, title: 'A' } as any] }
    requestGet.mockReturnValue(Promise.resolve(fake))
    const result = await getBannerList()
    expect(result).toBe(fake)
  })
})

describe('createBanner', () => {
  it('calls request.post with url and payload', () => {
    requestPost.mockReturnValue(Promise.resolve(ok))
    const payload = { title: 'New', imageUrl: 'https://x.com/y.jpg', sortOrder: 1 }
    createBanner(payload)
    expect(requestPost).toHaveBeenCalledTimes(1)
    expect(requestPost).toHaveBeenCalledWith('/banners', payload)
  })

  it('forwards the response promise', async () => {
    requestPost.mockReturnValue(Promise.resolve(ok))
    const result = await createBanner({ title: 'X', imageUrl: 'u', sortOrder: 0 })
    expect(result).toBe(ok)
  })
})

describe('updateBanner', () => {
  it('calls request.put with url containing id and payload', () => {
    requestPut.mockReturnValue(Promise.resolve(ok))
    updateBanner(7, { title: 'Updated' })
    expect(requestPut).toHaveBeenCalledTimes(1)
    expect(requestPut).toHaveBeenCalledWith('/banners/7', { title: 'Updated' })
  })

  it('handles different id values', () => {
    requestPut.mockReturnValue(Promise.resolve(ok))
    updateBanner(999, { sortOrder: 5 })
    expect(requestPut).toHaveBeenCalledWith('/banners/999', { sortOrder: 5 })
  })

  it('forwards the response promise', async () => {
    requestPut.mockReturnValue(Promise.resolve(ok))
    const result = await updateBanner(1, { title: 'x' })
    expect(result).toBe(ok)
  })
})

describe('deleteBanner', () => {
  it('calls request.delete with url containing id, no body', () => {
    requestDelete.mockReturnValue(Promise.resolve(ok))
    deleteBanner(13)
    expect(requestDelete).toHaveBeenCalledTimes(1)
    expect(requestDelete).toHaveBeenCalledWith('/banners/13')
  })

  it('handles different id values', () => {
    requestDelete.mockReturnValue(Promise.resolve(ok))
    deleteBanner(456)
    expect(requestDelete).toHaveBeenCalledWith('/banners/456')
  })

  it('forwards the response promise', async () => {
    requestDelete.mockReturnValue(Promise.resolve(ok))
    const result = await deleteBanner(1)
    expect(result).toBe(ok)
  })
})