import { describe, it, expect, beforeEach, vi } from 'vitest'

// Mock dict store 让 utils/index.ts 的所有 useDictStore() 调用可控。
// 通过 mock return:每次 useDictStore() 都返回同一个 store-like object,
// 我们用 spyOn 改写它的 label/color/has 方法。
const dictStoreMock = {
  label: vi.fn(),
  color: vi.fn(),
  has: vi.fn()
}
vi.mock('@/stores/dict', () => ({
  useDictStore: () => dictStoreMock
}))

import {
  formatDate,
  formatTier,
  formatEventStatus,
  formatEventType,
  getEventStatusColor,
  getEventTypeColor,
  formatEventFormat,
  getEventFormatColor,
  getAdminRoleColor,
  formatAdminRole
} from './index'

beforeEach(() => {
  dictStoreMock.label.mockReset()
  dictStoreMock.color.mockReset()
  dictStoreMock.has.mockReset()
  // 默认 mock:label() 直接回传 code,触发 fallback 分支(label === code → 用 FALLBACK_*);
  // 个别用例需要"字典命中"时再 .mockReturnValueOnce(...) 覆盖。
  dictStoreMock.label.mockImplementation((_category: string, code: string) => code)
  dictStoreMock.color.mockImplementation((_category: string, code: string) => code)
  dictStoreMock.has.mockReturnValue(false)
})

describe('formatDate', () => {
  it('formats ISO date as zh-CN localized', () => {
    // 注意:toLocaleDateString 输出在 jsdom 中含 zh-CN-u-ca-gregory + 2-digit hour
    // 我们只校验关键字段,不锁死空格(规避 ICU 版本差异)
    const out = formatDate('2026-07-24T14:30:00')
    expect(out).toContain('2026')
    expect(out).toContain('07')
    expect(out).toContain('24')
    expect(out).toContain('14')
    expect(out).toContain('30')
  })
})

describe('formatTier', () => {
  it.each([
    ['BRONZE', '青铜'],
    ['SILVER', '白银'],
    ['GOLD', '黄金'],
    ['PLATINUM', '铂金'],
    ['DIAMOND', '钻石'],
    ['MASTER', '王者']
  ])('maps %s → %s', (code, label) => {
    expect(formatTier(code)).toBe(label)
  })

  it('returns input unchanged for unknown code', () => {
    expect(formatTier('CUSTOM')).toBe('CUSTOM')
    expect(formatTier('')).toBe('')
  })
})

describe('formatEventStatus', () => {
  it('returns dict label when dict has the entry (label !== code)', () => {
    dictStoreMock.label.mockReturnValue('报名中')
    expect(formatEventStatus('OPEN')).toBe('报名中')
    expect(dictStoreMock.label).toHaveBeenCalledWith('event_status', 'OPEN')
  })

  it('returns fallback when dict label equals code (not loaded)', () => {
    dictStoreMock.label.mockReturnValue('OPEN')
    expect(formatEventStatus('OPEN')).toBe('报名中') // FALLBACK_STATUS_LABEL['OPEN']
  })

  it('returns dict label override for COMPLETED', () => {
    dictStoreMock.label.mockReturnValue('已完赛')
    expect(formatEventStatus('COMPLETED')).toBe('已完赛')
  })

  it('returns raw status when both dict and fallback miss', () => {
    dictStoreMock.label.mockReturnValue('UNKNOWN')
    expect(formatEventStatus('UNKNOWN')).toBe('UNKNOWN')
  })

  it('covers each fallback branch', () => {
    // 默认 beforeEach:label() 返回 code → fallback 命中
    expect(formatEventStatus('DRAFT')).toBe('草稿')
    expect(formatEventStatus('FULL')).toBe('名额已满')
    expect(formatEventStatus('IN_PROGRESS')).toBe('进行中')
    expect(formatEventStatus('CANCELLED')).toBe('已取消')
  })
})

describe('formatEventType', () => {
  it('returns dict label when dict returns custom label', () => {
    dictStoreMock.label.mockReturnValue('竞技')
    expect(formatEventType('STAR')).toBe('竞技')
  })

  it('returns fallback when dict label equals code', () => {
    // 默认 beforeEach 已让 label() 返回 code
    expect(formatEventType('STAR')).toBe('竞技赛事')
    expect(formatEventType('PARTY')).toBe('社交活动')
  })

  it('returns raw type when neither dict nor fallback hit', () => {
    dictStoreMock.label.mockReturnValue('XYZ')
    expect(formatEventType('XYZ')).toBe('XYZ')
  })
})

describe('getEventStatusColor', () => {
  it('returns dict color when store.has() === true', () => {
    dictStoreMock.has.mockReturnValue(true)
    dictStoreMock.color.mockReturnValue('#abcdef')
    expect(getEventStatusColor('OPEN')).toBe('#abcdef')
    expect(dictStoreMock.has).toHaveBeenCalledWith('event_status', 'OPEN')
  })

  it('returns fallback color when store.has() === false', () => {
    dictStoreMock.has.mockReturnValue(false)
    expect(getEventStatusColor('OPEN')).toBe('#10B981')
    expect(getEventStatusColor('DRAFT')).toBe('#9CA3AF')
    expect(getEventStatusColor('FULL')).toBe('#F59E0B')
    expect(getEventStatusColor('IN_PROGRESS')).toBe('#3B82F6')
    expect(getEventStatusColor('COMPLETED')).toBe('#6B7280')
    expect(getEventStatusColor('CANCELLED')).toBe('#EF4444')
  })

  it('returns default grey when status has no fallback and no dict entry', () => {
    dictStoreMock.has.mockReturnValue(false)
    expect(getEventStatusColor('UNKNOWN')).toBe('#6B7280')
  })
})

describe('getEventTypeColor', () => {
  it('returns dict color when store.has() === true', () => {
    dictStoreMock.has.mockReturnValue(true)
    dictStoreMock.color.mockReturnValue('#123456')
    expect(getEventTypeColor('STAR')).toBe('#123456')
  })

  it('returns fallback color when store.has() === false', () => {
    dictStoreMock.has.mockReturnValue(false)
    expect(getEventTypeColor('STAR')).toBe('#F59E0B')
    expect(getEventTypeColor('PARTY')).toBe('#8B5CF6')
    expect(getEventTypeColor('XYZ')).toBe('#6B7280') // default grey
  })
})

describe('formatEventFormat', () => {
  it('returns dict label when dict returns custom label', () => {
    dictStoreMock.label.mockReturnValue('男子单打')
    expect(formatEventFormat('SINGLES')).toBe('男子单打')
  })

  it('returns fallback when dict label equals code', () => {
    // 默认 beforeEach:label() 返回 code → 走 fallback 分支
    expect(formatEventFormat('SINGLES')).toBe('单打')
    expect(formatEventFormat('DOUBLES')).toBe('双打')
    expect(formatEventFormat('MIXED')).toBe('混打')
  })

  it('returns "单打" when format is null/undefined (backend default)', () => {
    dictStoreMock.label.mockReturnValue('SINGLES')
    expect(formatEventFormat(null)).toBe('单打')
    expect(formatEventFormat(undefined)).toBe('单打')
  })

  it('returns raw format when neither dict nor fallback hit', () => {
    dictStoreMock.label.mockReturnValue('XYZ')
    expect(formatEventFormat('XYZ')).toBe('XYZ')
  })
})

describe('getEventFormatColor', () => {
  it('returns dict color for valid format when store.has() === true', () => {
    dictStoreMock.has.mockReturnValue(true)
    dictStoreMock.color.mockReturnValue('#aaaaaa')
    expect(getEventFormatColor('SINGLES')).toBe('#aaaaaa')
  })

  it('returns fallback color when store.has() === false', () => {
    dictStoreMock.has.mockReturnValue(false)
    expect(getEventFormatColor('SINGLES')).toBe('#3B82F6')
    expect(getEventFormatColor('DOUBLES')).toBe('#10B981')
    expect(getEventFormatColor('MIXED')).toBe('#EC4899')
  })

  it('treats null/undefined as SINGLES fallback', () => {
    dictStoreMock.has.mockReturnValue(false)
    expect(getEventFormatColor(null)).toBe('#3B82F6')
    expect(getEventFormatColor(undefined)).toBe('#3B82F6')
  })

  it('returns default grey for unknown format', () => {
    dictStoreMock.has.mockReturnValue(false)
    expect(getEventFormatColor('UNKNOWN')).toBe('#6B7280')
  })
})

describe('getAdminRoleColor', () => {
  it.each([
    ['SUPER_ADMIN', '#EF4444'],
    ['ADMIN', '#3B82F6'],
    ['OPERATOR', '#10B981']
  ])('maps %s → %s', (role, color) => {
    expect(getAdminRoleColor(role)).toBe(color)
  })

  it('returns default grey for unknown role', () => {
    expect(getAdminRoleColor('UNKNOWN')).toBe('#6B7280')
    expect(getAdminRoleColor('')).toBe('#6B7280')
  })
})

describe('formatAdminRole', () => {
  it.each([
    ['SUPER_ADMIN', '超级管理员'],
    ['ADMIN', '管理员'],
    ['OPERATOR', '运营人员']
  ])('maps %s → %s', (role, label) => {
    expect(formatAdminRole(role)).toBe(label)
  })

  it('returns input unchanged for unknown role', () => {
    expect(formatAdminRole('CUSTOM')).toBe('CUSTOM')
  })
})