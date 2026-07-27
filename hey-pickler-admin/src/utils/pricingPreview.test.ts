// @vitest-environment jsdom
import { describe, it, expect } from 'vitest'
import {
  nextWeekdayDate,
  nextWeekendDate,
  formatHoursLine,
  isBandDead
} from './pricingPreview'
import type { BusinessHour, CourtPricingBand } from '@/types'

// 2026-07-27 = 周一;07-25=周六;07-26=周日;08-01=周六(手工校验过的锚点)
const MON = new Date(2026, 6, 27)
const SAT = new Date(2026, 6, 25)
const SUN = new Date(2026, 6, 26)
const NEXT_SAT = new Date(2026, 7, 1)

const fullWeek: BusinessHour[] = [0, 1, 2, 3, 4, 5, 6].map(dow => ({
  dayOfWeek: dow,
  openTime: '09:00',
  closeTime: '22:00'
}))

describe('nextWeekdayDate', () => {
  it('returns same day when from is already a weekday (Mon)', () => {
    expect(nextWeekdayDate(MON)).toBe('2026-07-27')
  })
  it('rolls Sat → next Mon', () => {
    expect(nextWeekdayDate(SAT)).toBe('2026-07-27')
  })
  it('rolls Sun → next Mon', () => {
    expect(nextWeekdayDate(SUN)).toBe('2026-07-27')
  })
})

describe('nextWeekendDate', () => {
  it('rolls Mon → next Sat', () => {
    expect(nextWeekendDate(MON)).toBe('2026-08-01')
  })
  it('returns same day when from is already a weekend (Sat)', () => {
    expect(nextWeekendDate(NEXT_SAT)).toBe('2026-08-01')
  })
  it('returns same day when from is Sun', () => {
    expect(nextWeekendDate(SUN)).toBe('2026-07-26')
  })
})

describe('formatHoursLine', () => {
  it('empty hours → placeholder', () => {
    expect(formatHoursLine([])).toBe('未配置营业时间')
  })
  it('full week 09-22 → 周一..周日 一行,顺序正确', () => {
    const line = formatHoursLine(fullWeek)
    expect(line).toContain('周一 09:00-22:00')
    expect(line).toContain('周日 09:00-22:00')
    expect(line.startsWith('周一 ')).toBe(true)
    expect(line.endsWith('周日 09:00-22:00')).toBe(true)
    expect(line.split(' · ').length).toBe(7)
  })
  it('day with missing closeTime → 休息', () => {
    const hours: BusinessHour[] = [
      { dayOfWeek: 1, openTime: '09:00', closeTime: '22:00' },
      { dayOfWeek: 0 } // 周日无时间
    ]
    expect(formatHoursLine(hours)).toContain('周日 休息')
    expect(formatHoursLine(hours)).toContain('周一 09:00-22:00')
  })
  it('trims seconds off HH:mm:ss', () => {
    const hours: BusinessHour[] = [{ dayOfWeek: 1, openTime: '09:00:00', closeTime: '22:00:00' }]
    expect(formatHoursLine(hours)).toContain('周一 09:00-22:00')
  })
})

describe('isBandDead', () => {
  const baseBand = (over: Partial<CourtPricingBand>): CourtPricingBand => ({
    dayType: 'WEEKDAY',
    startTime: '09:00',
    endTime: '12:00',
    price: 80,
    ...over
  })

  it('empty hours → false (no hours to judge)', () => {
    expect(isBandDead(baseBand({ startTime: '23:00', endTime: '23:30' }), [])).toBe(false)
  })

  it('band inside weekday hours → false', () => {
    expect(isBandDead(baseBand({ startTime: '09:00', endTime: '12:00' }), fullWeek)).toBe(false)
  })

  it('band fully outside operating window → true (dead)', () => {
    // 营业 09-22,band 23:00-23:30 完全在外
    expect(isBandDead(baseBand({ startTime: '23:00', endTime: '23:30' }), fullWeek)).toBe(true)
  })

  it('band partially overlapping a weekday → false', () => {
    // band 08:00-10:00 与 09:00-22:00 在 [09:00,10:00) 相交
    expect(isBandDead(baseBand({ startTime: '08:00', endTime: '10:00' }), fullWeek)).toBe(false)
  })

  it('WEEKDAY band with only weekend hours configured → true', () => {
    const weekendOnly: BusinessHour[] = [
      { dayOfWeek: 6, openTime: '09:00', closeTime: '22:00' },
      { dayOfWeek: 0, openTime: '09:00', closeTime: '22:00' }
    ]
    expect(isBandDead(baseBand({ dayType: 'WEEKDAY', startTime: '09:00', endTime: '22:00' }), weekendOnly)).toBe(true)
  })

  it('WEEKEND band intersecting Sat hours → false', () => {
    const weekendOnly: BusinessHour[] = [{ dayOfWeek: 6, openTime: '09:00', closeTime: '22:00' }]
    expect(
      isBandDead(baseBand({ dayType: 'WEEKEND', startTime: '10:00', endTime: '12:00' }), weekendOnly)
    ).toBe(false)
  })

  it('malformed band (start>=end) → false (do not flag mid-edit)', () => {
    expect(isBandDead(baseBand({ startTime: '12:00', endTime: '12:00' }), fullWeek)).toBe(false)
  })

  it('malformed band startTime (empty / no-colon / NaN) → false', () => {
    expect(isBandDead(baseBand({ startTime: '', endTime: '12:00' } as any), fullWeek)).toBe(false)
    expect(isBandDead(baseBand({ startTime: 'abc', endTime: '12:00' } as any), fullWeek)).toBe(false)
    expect(isBandDead(baseBand({ startTime: 'ab:cd', endTime: '12:00' } as any), fullWeek)).toBe(false)
  })

  it('skips malformed business-hours entries (continue branch) → treated as no intersection', () => {
    // 周一 openTime 畸形 → toMinutes -1 → 该天被 continue;band 仅能与周一周二相交,
    // 周二被剔除后只剩畸形周一 → 死带
    const hours: BusinessHour[] = [
      { dayOfWeek: 1, openTime: 'abc', closeTime: '22:00' },
      { dayOfWeek: 2, openTime: '', closeTime: '' }
    ]
    expect(isBandDead(baseBand({ dayType: 'WEEKDAY', startTime: '09:00', endTime: '12:00' }), hours)).toBe(true)
  })
})
