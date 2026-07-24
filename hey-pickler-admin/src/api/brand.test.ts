// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach } from 'vitest'

const requestGet = vi.fn()
const requestPut = vi.fn()
vi.mock('./request', () => ({
  default: {
    get: (...args: unknown[]) => requestGet(...args),
    put: (...args: unknown[]) => requestPut(...args)
  }
}))

beforeEach(() => {
  requestGet.mockReset()
  requestPut.mockReset()
})

import { getBrand, updateBrand } from './brand'

describe('getBrand', () => {
  it('calls request.get with url only, no params', () => {
    requestGet.mockReturnValue(Promise.resolve({ code: 0, data: null }))
    getBrand()
    expect(requestGet).toHaveBeenCalledTimes(1)
    expect(requestGet).toHaveBeenCalledWith('/brand')
  })

  it('forwards the response promise', async () => {
    const fake = {
      code: 0,
      data: { appName: 'Pickler', slogan: 'Play!', logoUrl: 'https://x.com/l.png', primaryColor: '#409eff' }
    }
    requestGet.mockReturnValue(Promise.resolve(fake))
    const result = await getBrand()
    expect(result).toBe(fake)
  })
})

describe('updateBrand', () => {
  it('calls request.put with url and full payload', () => {
    requestPut.mockReturnValue(Promise.resolve({ code: 0, data: null }))
    const payload = { appName: 'X', slogan: 's', logoUrl: 'u', primaryColor: '#000' }
    updateBrand(payload)
    expect(requestPut).toHaveBeenCalledTimes(1)
    expect(requestPut).toHaveBeenCalledWith('/brand', payload)
  })

  it('forwards the response promise', async () => {
    const fake = {
      code: 0,
      data: { appName: 'X', slogan: '', logoUrl: '', primaryColor: '#fff' }
    }
    requestPut.mockReturnValue(Promise.resolve(fake))
    const result = await updateBrand({ appName: 'X', slogan: '', logoUrl: '', primaryColor: '#fff' })
    expect(result).toBe(fake)
  })
})