import { describe, it, expect, beforeEach, vi } from 'vitest'

const dictStoreMock = {
  label: vi.fn(),
  color: vi.fn(),
  has: vi.fn()
}
vi.mock('@/stores/dict', () => ({
  useDictStore: () => dictStoreMock
}))

import { formatRegStatus, formatMatchType } from './registration'

beforeEach(() => {
  dictStoreMock.label.mockReset()
  dictStoreMock.label.mockImplementation((_c: string, code: string) => code)
})

// ============ formatRegStatus ============
describe('formatRegStatus', () => {
  it('null → "-" (early return)', () => {
    expect(formatRegStatus(null)).toBe('-')
  })

  it('undefined → "-"', () => {
    expect(formatRegStatus(undefined)).toBe('-')
  })

  it('empty string → "-" (because !s)', () => {
    expect(formatRegStatus('')).toBe('-')
  })

  it('dict returns different label → uses dict label', () => {
    dictStoreMock.label.mockReturnValueOnce('报名成功')
    expect(formatRegStatus('REGISTERED')).toBe('报名成功')
    expect(dictStoreMock.label).toHaveBeenCalledWith('registration_status', 'REGISTERED')
  })

  it('dict returns same code → falls back to FALLBACK_REG', () => {
    expect(formatRegStatus('REGISTERED')).toBe('已报名')
    expect(formatRegStatus('CHECKED_IN')).toBe('已签到')
    expect(formatRegStatus('WITHDRAWN')).toBe('已退出')
  })

  it('unknown code + dict returns same → returns raw code', () => {
    expect(formatRegStatus('UNKNOWN')).toBe('UNKNOWN')
  })

  it('dict returns null → falls through to fallback', () => {
    dictStoreMock.label.mockReturnValueOnce(null as unknown as string)
    expect(formatRegStatus('REGISTERED')).toBe('已报名')
  })
})

// ============ formatMatchType ============
describe('formatMatchType', () => {
  it('null → "单打" (early return; backend default)', () => {
    expect(formatMatchType(null)).toBe('单打')
  })

  it('undefined → "单打"', () => {
    expect(formatMatchType(undefined)).toBe('单打')
  })

  it('empty string → "单打" (because !s)', () => {
    expect(formatMatchType('')).toBe('单打')
  })

  it('dict returns different label → uses dict label', () => {
    dictStoreMock.label.mockReturnValueOnce('男子单打')
    expect(formatMatchType('SINGLES')).toBe('男子单打')
    expect(dictStoreMock.label).toHaveBeenCalledWith('event_format', 'SINGLES')
  })

  it('dict returns same code → falls back to FALLBACK_FMT', () => {
    expect(formatMatchType('SINGLES')).toBe('单打')
    expect(formatMatchType('DOUBLES')).toBe('双打')
    expect(formatMatchType('MIXED')).toBe('混打')
  })

  it('unknown code + dict returns same → returns raw code', () => {
    expect(formatMatchType('FOUR_PLAYER')).toBe('FOUR_PLAYER')
  })

  it('dict returns null → falls through to fallback', () => {
    dictStoreMock.label.mockReturnValueOnce(null as unknown as string)
    expect(formatMatchType('DOUBLES')).toBe('双打')
  })
})