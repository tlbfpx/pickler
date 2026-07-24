import { describe, it, expect, beforeEach, vi } from 'vitest'

// 同 utils/index.test.ts 的策略:mock dict store 让 label()/color() 回传 code,
// 个别用例需要字典命中时通过 mockReturnValueOnce 覆盖。
const dictStoreMock = {
  label: vi.fn(),
  color: vi.fn(),
  has: vi.fn()
}
vi.mock('@/stores/dict', () => ({
  useDictStore: () => dictStoreMock
}))

import {
  ALLOWED_TRANSITIONS,
  getAllowedTargets,
  formatStatus,
  statusColor,
  statusTooltip,
  type EventStatus
} from './eventStatus'

beforeEach(() => {
  dictStoreMock.label.mockReset()
  dictStoreMock.color.mockReset()
  dictStoreMock.label.mockImplementation((_c: string, code: string) => code)
  dictStoreMock.color.mockImplementation((_c: string, code: string) => code)
  dictStoreMock.has.mockReset()
  // color() 默认回传 code — 让 statusColor 的 c !== '#6B7280' 分支可控
})

// ============ ALLOWED_TRANSITIONS / getAllowedTargets ============
describe('ALLOWED_TRANSITIONS', () => {
  it('is defined and complete for all 6 statuses', () => {
    expect(Object.keys(ALLOWED_TRANSITIONS).sort()).toEqual(
      ['CANCELLED', 'COMPLETED', 'DRAFT', 'FULL', 'IN_PROGRESS', 'OPEN'].sort()
    )
  })

  it('DRAFT → OPEN/CANCELLED', () => {
    expect(ALLOWED_TRANSITIONS.DRAFT).toEqual(['OPEN', 'CANCELLED'])
  })

  it('OPEN → FULL/IN_PROGRESS/CANCELLED', () => {
    expect(ALLOWED_TRANSITIONS.OPEN).toEqual(['FULL', 'IN_PROGRESS', 'CANCELLED'])
  })

  it('FULL → OPEN/IN_PROGRESS/CANCELLED', () => {
    expect(ALLOWED_TRANSITIONS.FULL).toEqual(['OPEN', 'IN_PROGRESS', 'CANCELLED'])
  })

  it('IN_PROGRESS → COMPLETED/CANCELLED', () => {
    expect(ALLOWED_TRANSITIONS.IN_PROGRESS).toEqual(['COMPLETED', 'CANCELLED'])
  })

  it('COMPLETED → [] (terminal)', () => {
    expect(ALLOWED_TRANSITIONS.COMPLETED).toEqual([])
  })

  it('CANCELLED → [] (terminal)', () => {
    expect(ALLOWED_TRANSITIONS.CANCELLED).toEqual([])
  })
})

describe('getAllowedTargets', () => {
  it('returns the array for known statuses', () => {
    expect(getAllowedTargets('DRAFT')).toEqual(['OPEN', 'CANCELLED'])
    expect(getAllowedTargets('OPEN')).toEqual(['FULL', 'IN_PROGRESS', 'CANCELLED'])
    expect(getAllowedTargets('FULL')).toEqual(['OPEN', 'IN_PROGRESS', 'CANCELLED'])
    expect(getAllowedTargets('IN_PROGRESS')).toEqual(['COMPLETED', 'CANCELLED'])
    expect(getAllowedTargets('COMPLETED')).toEqual([])
    expect(getAllowedTargets('CANCELLED')).toEqual([])
  })

  it('returns [] for unknown status (defensive)', () => {
    expect(getAllowedTargets('UNKNOWN' as EventStatus)).toEqual([])
    expect(getAllowedTargets('' as EventStatus)).toEqual([])
  })
})

// ============ formatStatus ============
describe('formatStatus', () => {
  it('empty string → returns as-is (early return)', () => {
    expect(formatStatus('')).toBe('')
  })

  it('dict returns different label → uses dict label', () => {
    dictStoreMock.label.mockReturnValueOnce('开放报名')
    expect(formatStatus('OPEN')).toBe('开放报名')
    expect(dictStoreMock.label).toHaveBeenCalledWith('event_status', 'OPEN')
  })

  it('dict returns same code → falls back to FALLBACK_STATUS_LABEL', () => {
    // 默认 mockImplementation:label() 返回 code,所以 'OPEN' → 'OPEN' (== input) → 走 fallback
    expect(formatStatus('OPEN')).toBe('报名中')
    expect(formatStatus('DRAFT')).toBe('草稿')
    expect(formatStatus('FULL')).toBe('名额已满')
    expect(formatStatus('IN_PROGRESS')).toBe('进行中')
    expect(formatStatus('COMPLETED')).toBe('已结束')
    expect(formatStatus('CANCELLED')).toBe('已取消')
  })

  it('unknown status + dict miss → returns raw status', () => {
    expect(formatStatus('UNKNOWN')).toBe('UNKNOWN')
  })

  it('dict returns null/undefined/empty → falls through to fallback', () => {
    dictStoreMock.label.mockReturnValueOnce(null as unknown as string)
    expect(formatStatus('DRAFT')).toBe('草稿')
  })
})

// ============ statusColor ============
describe('statusColor', () => {
  it('empty string → default grey (early return)', () => {
    expect(statusColor('')).toBe('#6B7280')
  })

  it('dict returns non-default color → uses dict color', () => {
    dictStoreMock.color.mockReturnValueOnce('#abc123')
    expect(statusColor('OPEN')).toBe('#abc123')
    expect(dictStoreMock.color).toHaveBeenCalledWith('event_status', 'OPEN')
  })

  it('dict returns exact default #6B7280 → falls back to FALLBACK_STATUS_COLOR', () => {
    dictStoreMock.color.mockReturnValueOnce('#6B7280')
    expect(statusColor('OPEN')).toBe('#10B981') // OPEN 的 fallback 是绿色
  })

  it('dict returns empty string → falls back to FALLBACK_STATUS_COLOR', () => {
    dictStoreMock.color.mockReturnValueOnce('')
    expect(statusColor('OPEN')).toBe('#10B981')
  })

  it('covers each fallback color', () => {
    // 默认 mockImplementation 让 color() === code;c !== '#6B7280' 比较:code 都会 !=
    // 所以默认走"用 dict color"分支。要走 fallback 必须让 color() 返回 '#6B7280'
    dictStoreMock.color.mockReturnValue('#6B7280')
    expect(statusColor('DRAFT')).toBe('#909399')
    expect(statusColor('OPEN')).toBe('#10B981')
    expect(statusColor('FULL')).toBe('#F59E0B')
    expect(statusColor('IN_PROGRESS')).toBe('#3B82F6')
    expect(statusColor('COMPLETED')).toBe('#6B7280')
    expect(statusColor('CANCELLED')).toBe('#EF4444')
  })

  it('unknown status + dict returns default → returns default grey', () => {
    dictStoreMock.color.mockReturnValueOnce('#6B7280')
    expect(statusColor('UNKNOWN')).toBe('#6B7280')
  })

  it('unknown status + dict returns custom → uses dict color', () => {
    dictStoreMock.color.mockReturnValueOnce('#abcdef')
    expect(statusColor('UNKNOWN')).toBe('#abcdef')
  })
})

// ============ statusTooltip ============
describe('statusTooltip', () => {
  it('returns tooltip for each known status', () => {
    expect(statusTooltip('DRAFT')).toBe('草稿：赛事已创建但未发布，选手暂不可报名')
    expect(statusTooltip('OPEN')).toBe('报名中：正在接受选手报名')
    expect(statusTooltip('FULL')).toBe('名额已满：已达人数上限，不再接受新报名')
    expect(statusTooltip('IN_PROGRESS')).toBe('进行中：比赛已开始，正在录入比分')
    expect(statusTooltip('COMPLETED')).toBe('已结束：比赛已完结，可配置积分表')
    expect(statusTooltip('CANCELLED')).toBe('已取消：该赛事已被取消，不再生效')
  })

  it('returns empty string for unknown status', () => {
    expect(statusTooltip('UNKNOWN')).toBe('')
    expect(statusTooltip('')).toBe('')
  })
})