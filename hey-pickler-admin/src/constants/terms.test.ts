import { describe, it, expect, beforeEach, vi, type Mock } from 'vitest'

// per-test 完全 fresh mock + useDictStore 整体替换。
// 因为 vitest 在 coverage 模式下可能复用 module instance,
// 用 .mockReturnValueOnce 队列在跨 test 时不可靠;改用 ref + beforeEach 重建。
const dictStoreMockRef: { current: {
  label: Mock
  color: Mock
  has: Mock
  meta: Mock
} | null } = { current: null }

vi.mock('@/stores/dict', () => ({
  useDictStore: () => dictStoreMockRef.current
}))

import { getTerms, formatTierName, getPointsTypeLabel, getPointSourceLabel, TIER_NAME } from './terms'

const d = () => dictStoreMockRef.current!

function freshMock() {
  return {
    label: vi.fn(),
    color: vi.fn(),
    has: vi.fn(),
    meta: vi.fn()
  }
}

beforeEach(() => {
  const m = freshMock()
  // 默认:label() 回传 code(label === input → 触发 fallback 分支)
  m.label.mockImplementation((_c: string, code: string) => code)
  // 默认:meta() 返回 null(走 fallback 分支)
  m.meta.mockReturnValue(null)
  dictStoreMockRef.current = m
})

// ============ TIER_NAME (data only, 断言完整性) ============
describe('TIER_NAME', () => {
  it('contains 6 tier codes', () => {
    expect(Object.keys(TIER_NAME).sort()).toEqual(
      ['BRONZE', 'DIAMOND', 'GOLD', 'MASTER', 'PLATINUM', 'SILVER'].sort()
    )
  })

  it.each([
    ['BRONZE', '青铜'],
    ['SILVER', '白银'],
    ['GOLD', '黄金'],
    ['PLATINUM', '铂金'],
    ['DIAMOND', '钻石'],
    ['MASTER', '王者']
  ])('%s → %s', (code, label) => {
    expect(TIER_NAME[code]).toBe(label)
  })
})

// ============ getTerms ============
describe('getTerms', () => {
  it('null → FALLBACK_TERMS.STAR', () => {
    expect(getTerms(null)).toEqual({
      type: '竞技赛事',
      points: '战力',
      tier: '战力段位',
      ranking: '战力排名'
    })
  })

  it('undefined → FALLBACK_TERMS.STAR', () => {
    expect(getTerms(undefined)).toEqual(getTerms(null))
  })

  it('empty string → FALLBACK_TERMS.STAR (because !type)', () => {
    expect(getTerms('')).toEqual(getTerms(null))
  })

  it('STAR + no meta → FALLBACK_TERMS.STAR', () => {
    expect(getTerms('STAR')).toEqual({
      type: '竞技赛事',
      points: '战力',
      tier: '战力段位',
      ranking: '战力排名'
    })
  })

  it('STAR + meta with only tierName+rankingName (pointsName missing) → points falls back to FALLBACK', () => {
    // 覆盖 line 71-72 第二分支:FALLBACK_TERMS[type]?.X 命中
    d().meta.mockReturnValue({
      tierName: '排位',
      rankingName: '榜'
    })
    expect(getTerms('STAR')).toEqual({
      type: '竞技赛事',
      points: '战力', // ← from FALLBACK (since meta.pointsName is undefined)
      tier: '排位',
      ranking: '榜'
    })
  })

  it('STAR + meta with only pointsName+rankingName (tierName missing) → tier falls back to FALLBACK', () => {
    d().meta.mockReturnValue({
      pointsName: 'P',
      rankingName: 'R'
    })
    expect(getTerms('STAR')).toEqual({
      type: '竞技赛事',
      points: 'P',
      tier: '战力段位', // ← from FALLBACK
      ranking: 'R'
    })
  })

  it('PARTY + no meta → FALLBACK_TERMS.PARTY', () => {
    expect(getTerms('PARTY')).toEqual({
      type: '社交活动',
      points: '活力',
      tier: '活力段位',
      ranking: '活力排名'
    })
  })

  it('meta with all 3 fields → returns merged (type from dict label)', () => {
    d().meta.mockReturnValue({
      pointsName: '战力值',
      tierName: '战力排位',
      rankingName: '战力榜'
    })
    d().label.mockReturnValue('竞技赛')
    expect(getTerms('STAR')).toEqual({
      type: '竞技赛',
      points: '战力值',
      tier: '战力排位',
      ranking: '战力榜'
    })
  })

  it('meta with only pointsName + tierName, no rankingName → ranking falls back', () => {
    d().meta.mockReturnValue({
      pointsName: '战力值',
      tierName: '战力排位'
    })
    expect(getTerms('STAR')).toEqual({
      type: '竞技赛事',
      points: '战力值',
      tier: '战力排位',
      ranking: '战力排名'
    })
  })

  it('meta with only rankingName → points/tier fall back', () => {
    d().meta.mockReturnValue({
      rankingName: '战力榜'
    })
    expect(getTerms('STAR')).toEqual({
      type: '竞技赛事',
      points: '战力',
      tier: '战力段位',
      ranking: '战力榜'
    })
  })

  it('meta with empty object {} → falls back (no pointsName/tierName/rankingName)', () => {
    d().meta.mockReturnValue({})
    expect(getTerms('STAR')).toEqual({
      type: '竞技赛事',
      points: '战力',
      tier: '战力段位',
      ranking: '战力排名'
    })
  })

  it('meta with all fields empty strings → still treated as truthy (ternary on meta exists)', () => {
    // 实现是 (meta.pointsName || meta.tierName || meta.rankingName) → 空串视为 falsy
    // 所以 empty strings 走 fallback
    d().meta.mockReturnValue({
      pointsName: '',
      tierName: '',
      rankingName: ''
    })
    expect(getTerms('STAR')).toEqual({
      type: '竞技赛事',
      points: '战力',
      tier: '战力段位',
      ranking: '战力排名'
    })
  })

  it('dict label returns same code → type label falls back to FALLBACK_TERMS[type].type', () => {
    d().meta.mockReturnValue({
      pointsName: 'P',
      tierName: 'T',
      rankingName: 'R'
    })
    // 默认 label() 回传 code (=== type),所以 l === type → fallback 类型
    expect(getTerms('STAR').type).toBe('竞技赛事')
  })

  it('dict label returns different → uses dict label for type', () => {
    d().meta.mockReturnValue({
      pointsName: 'P',
      tierName: 'T',
      rankingName: 'R'
    })
    d().label.mockReturnValue('Star Event')
    expect(getTerms('STAR').type).toBe('Star Event')
  })

  it('unknown type + meta + all meta fields → uses type for non-overridden, meta for overridden', () => {
    d().meta.mockReturnValue({
      pointsName: 'P',
      tierName: 'T',
      rankingName: 'R'
    })
    expect(getTerms('XYZ').points).toBe('P')
    expect(getTerms('XYZ').tier).toBe('T')
    expect(getTerms('XYZ').ranking).toBe('R')
  })

  it('unknown type + meta with only rankingName → points/tier fall through to type (XYZ)', () => {
    // 覆盖 lines 71-72 Branch C:
    //   meta.X falsy + FALLBACK_TERMS[type]?.X undefined → use RHS = type
    d().meta.mockReturnValue({ rankingName: 'R' })
    const r = getTerms('XYZ')
    expect(r.points).toBe('XYZ') // ← from RHS
    expect(r.tier).toBe('XYZ')   // ← from RHS
    expect(r.ranking).toBe('R')  // ← from meta
  })

  it('unknown type + meta with only pointsName+tierName → ranking falls through to type (XYZ)', () => {
    // 覆盖 line 73 Branch C:meta.rankingName falsy + FALLBACK[type]?.ranking undefined → type
    d().meta.mockReturnValue({
      pointsName: 'P',
      tierName: 'T'
    })
    const r = getTerms('XYZ')
    expect(r.points).toBe('P')
    expect(r.tier).toBe('T')
    expect(r.ranking).toBe('XYZ') // ← from RHS (line 73 Branch C)
  })

  it('unknown type + no meta → returns {type, points: type, tier: type, ranking: type}', () => {
    expect(getTerms('XYZ')).toEqual({
      type: 'XYZ',
      points: 'XYZ',
      tier: 'XYZ',
      ranking: 'XYZ'
    })
  })
})

// ============ formatTierName ============
describe('formatTierName', () => {
  it('null → "-"', () => expect(formatTierName(null)).toBe('-'))
  it('undefined → "-"', () => expect(formatTierName(undefined)).toBe('-'))
  it('empty string → "-"', () => expect(formatTierName('')).toBe('-'))

  it.each([
    ['BRONZE', '青铜'],
    ['SILVER', '白银'],
    ['GOLD', '黄金'],
    ['PLATINUM', '铂金'],
    ['DIAMOND', '钻石'],
    ['MASTER', '王者']
  ])('%s → %s', (code, label) => {
    expect(formatTierName(code)).toBe(label)
  })

  it('unknown tier → returns raw value', () => {
    expect(formatTierName('CUSTOM')).toBe('CUSTOM')
  })
})

// ============ getPointsTypeLabel ============
describe('getPointsTypeLabel', () => {
  it('null → "-"', () => expect(getPointsTypeLabel(null)).toBe('-'))
  it('undefined → "-"', () => expect(getPointsTypeLabel(undefined)).toBe('-'))
  it('empty string → "-"', () => expect(getPointsTypeLabel('')).toBe('-'))

  it('dict returns different label → uses dict label', () => {
    d().label.mockReturnValue('赛事类型')
    expect(getPointsTypeLabel('STAR')).toBe('赛事类型')
    expect(d().label).toHaveBeenCalledWith('event_type', 'STAR')
  })

  it('dict returns same code → falls back to FALLBACK_TERMS[type].type', () => {
    expect(getPointsTypeLabel('STAR')).toBe('竞技赛事')
    expect(getPointsTypeLabel('PARTY')).toBe('社交活动')
  })

  it('unknown type + dict returns same → returns raw type', () => {
    expect(getPointsTypeLabel('XYZ')).toBe('XYZ')
  })
})

// ============ getPointSourceLabel ============
describe('getPointSourceLabel', () => {
  it('null → "-"', () => expect(getPointSourceLabel(null)).toBe('-'))
  it('undefined → "-"', () => expect(getPointSourceLabel(undefined)).toBe('-'))
  it('empty string → "-"', () => expect(getPointSourceLabel('')).toBe('-'))

  it('dict returns different label → uses dict label', () => {
    d().label.mockReturnValue('管理员调整')
    expect(getPointSourceLabel('MANUAL')).toBe('管理员调整')
    expect(d().label).toHaveBeenCalledWith('point_source', 'MANUAL')
  })

  it('dict returns same code → falls back to FALLBACK_POINT_SOURCE_LABEL (covers each key)', () => {
    expect(getPointSourceLabel('MANUAL')).toBe('管理员手动')
    expect(getPointSourceLabel('ADJUST')).toBe('系统纠错')
    expect(getPointSourceLabel('PLACEMENT')).toBe('名次发分')
    expect(getPointSourceLabel('REGISTRATION')).toBe('报名')
    expect(getPointSourceLabel('CHECK_IN')).toBe('签到')
    expect(getPointSourceLabel('REDEEM')).toBe('兑换')
  })

  it('unknown source + dict returns same → returns raw source', () => {
    expect(getPointSourceLabel('CUSTOM_SOURCE')).toBe('CUSTOM_SOURCE')
  })
})