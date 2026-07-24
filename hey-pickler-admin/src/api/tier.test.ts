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

import { getTierConfig, updateTierConfig } from './tier'

const ok = { code: 0, data: null }

describe('getTierConfig', () => {
  it('calls request.get with track in url', () => {
    requestGet.mockReturnValue(Promise.resolve(ok))
    getTierConfig('STAR')
    expect(requestGet).toHaveBeenCalledTimes(1)
    expect(requestGet).toHaveBeenCalledWith('/tier/STAR')
  })

  it('handles different track', () => {
    requestGet.mockReturnValue(Promise.resolve(ok))
    getTierConfig('PARTY')
    expect(requestGet).toHaveBeenCalledWith('/tier/PARTY')
  })

  it('forwards response', async () => {
    const fake = {
      code: 0,
      data: [{ track: 'STAR', tierCode: 'BRONZE', tierName: '青铜', tierColor: '#CD7F32', threshold: 0, icon: null, sort: 1 }]
    }
    requestGet.mockReturnValue(Promise.resolve(fake))
    expect(await getTierConfig('STAR')).toBe(fake)
  })
})

describe('updateTierConfig', () => {
  it('calls request.put with track + items array', () => {
    requestPut.mockReturnValue(Promise.resolve(ok))
    const items = [
      { tierCode: 'BRONZE', tierName: '青铜', tierColor: '#CD7F32', threshold: 0, icon: null },
      { tierCode: 'SILVER', tierName: '白银', tierColor: '#C0C0C0', threshold: 100, icon: null }
    ]
    updateTierConfig('STAR', items)
    expect(requestPut).toHaveBeenCalledTimes(1)
    expect(requestPut).toHaveBeenCalledWith('/tier/STAR', items)
  })

  it('handles different track', () => {
    requestPut.mockReturnValue(Promise.resolve(ok))
    updateTierConfig('PARTY', [])
    expect(requestPut).toHaveBeenCalledWith('/tier/PARTY', [])
  })

  it('forwards response', async () => {
    requestPut.mockReturnValue(Promise.resolve(ok))
    expect(await updateTierConfig('STAR', [])).toBe(ok)
  })
})