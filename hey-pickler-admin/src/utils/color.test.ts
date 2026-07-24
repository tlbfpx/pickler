import { describe, it, expect, beforeEach } from 'vitest'
import { mixHex, applyTheme, parseHex, toHex, toHexByte } from './color'

describe('parseHex', () => {
  it('parses 6-digit hex without leading #', () => {
    expect(parseHex('ff8800')).toEqual({ r: 255, g: 136, b: 0 })
  })

  it('parses 6-digit hex with leading #', () => {
    expect(parseHex('#ff8800')).toEqual({ r: 255, g: 136, b: 0 })
  })

  it('parses mixed-case hex', () => {
    expect(parseHex('#Ff88aA')).toEqual({ r: 255, g: 136, b: 170 })
  })

  it('trims surrounding whitespace', () => {
    expect(parseHex('  #abcdef  ')).toEqual({ r: 171, g: 205, b: 239 })
  })

  it('returns null for empty string', () => {
    expect(parseHex('')).toBeNull()
  })

  it('returns null for null/undefined coerced to empty', () => {
    // @ts-expect-error 测试运行时 fallback
    expect(parseHex(null)).toBeNull()
    // @ts-expect-error 测试运行时 fallback
    expect(parseHex(undefined)).toBeNull()
  })

  it('returns null for invalid length (3 digits)', () => {
    expect(parseHex('#fff')).toBeNull()
  })

  it('returns null for non-hex characters', () => {
    expect(parseHex('#zzzzzz')).toBeNull()
  })

  it('returns null for 8-digit hex (alpha)', () => {
    expect(parseHex('#ffaabbcc')).toBeNull()
  })
})

describe('toHexByte', () => {
  it('formats 0 as "00"', () => {
    expect(toHexByte(0)).toBe('00')
  })

  it('formats 255 as "ff"', () => {
    expect(toHexByte(255)).toBe('ff')
  })

  it('pads single digit with leading zero', () => {
    expect(toHexByte(1)).toBe('01')
    expect(toHexByte(15)).toBe('0f')
  })

  it('rounds 127.5 to 128', () => {
    expect(toHexByte(127.5)).toBe('80')
  })

  it('rounds 127.4 to 127', () => {
    expect(toHexByte(127.4)).toBe('7f')
  })

  it('clamps negative to 0', () => {
    expect(toHexByte(-10)).toBe('00')
  })

  it('clamps above 255 to ff', () => {
    expect(toHexByte(300)).toBe('ff')
  })
})

describe('toHex', () => {
  it('combines rgb into #rrggbb lowercase', () => {
    expect(toHex({ r: 255, g: 136, b: 0 })).toBe('#ff8800')
  })

  it('pads single-digit bytes', () => {
    expect(toHex({ r: 1, g: 2, b: 3 })).toBe('#010203')
  })
})

describe('mixHex', () => {
  it('returns c1 when c1 is invalid', () => {
    expect(mixHex('not-a-color', '#ff0000', 0.5)).toBe('not-a-color')
  })

  it('returns c1 when c2 is invalid', () => {
    expect(mixHex('#000000', 'nope', 0.5)).toBe('#000000')
  })

  it('mixes 50/50 black and white to grey', () => {
    // 0.5 * 255 = 127.5 → rounds to 128 (0x80)
    expect(mixHex('#000000', '#ffffff', 0.5)).toBe('#808080')
  })

  it('weight=0 returns c1 exactly', () => {
    expect(mixHex('#ff0000', '#00ff00', 0)).toBe('#ff0000')
  })

  it('weight=1 returns c2 exactly', () => {
    expect(mixHex('#ff0000', '#00ff00', 1)).toBe('#00ff00')
  })

  it('clamps weight < 0 to 0', () => {
    expect(mixHex('#ff0000', '#00ff00', -1)).toBe('#ff0000')
  })

  it('clamps weight > 1 to 1', () => {
    expect(mixHex('#ff0000', '#00ff00', 5)).toBe('#00ff00')
  })

  it('mixes primary color toward white at 30%', () => {
    // weight=0.3 means 30% white blended into c1=#409eff (Element Plus primary)
    // r = 0x40 * 0.7 + 0xff * 0.3 = 64*0.7 + 255*0.3 = 44.8 + 76.5 = 121.3 → 121 (0x79)
    // g = 0x9e * 0.7 + 0xff * 0.3 = 158*0.7 + 255*0.3 = 110.6 + 76.5 = 187.1 → 187 (0xbb)
    // b = 0xff * 0.7 + 0xff * 0.3 = 255 → 0xff
    expect(mixHex('#409eff', '#ffffff', 0.3)).toBe('#79bbff')
  })
})

describe('applyTheme', () => {
  beforeEach(() => {
    // 每个用例前清掉 :root 的主题变量,避免污染
    const root = document.documentElement
    root.style.removeProperty('--el-color-primary')
    for (let n = 1; n <= 9; n++) root.style.removeProperty(`--el-color-primary-light-${n}`)
    root.style.removeProperty('--el-color-primary-dark-2')
  })

  it('sets --el-color-primary to input hex (lowercased)', () => {
    applyTheme('#FF8800')
    expect(document.documentElement.style.getPropertyValue('--el-color-primary')).toBe('#ff8800')
  })

  it('sets light-1 through light-9 as mix toward white', () => {
    applyTheme('#000000')
    // weight n/10: light-1 = 10% white, light-9 = 90% white
    // r: 0*(1-w) + 255*w; w=0.1 → 25.5 → 26 (0x1a); w=0.9 → 229.5 → 230 (0xe6)
    expect(document.documentElement.style.getPropertyValue('--el-color-primary-light-1')).toBe('#1a1a1a')
    expect(document.documentElement.style.getPropertyValue('--el-color-primary-light-9')).toBe('#e6e6e6')
  })

  it('sets dark-2 as mix toward black at 20%', () => {
    applyTheme('#ffffff')
    // r: 255*0.8 + 0*0.2 = 204 (0xcc)
    expect(document.documentElement.style.getPropertyValue('--el-color-primary-dark-2')).toBe('#cccccc')
  })

  it('silently skips invalid hex (no throw, no setProperty)', () => {
    expect(() => applyTheme('not-a-color')).not.toThrow()
    expect(document.documentElement.style.getPropertyValue('--el-color-primary')).toBe('')
  })

  it('silently skips empty string', () => {
    expect(() => applyTheme('')).not.toThrow()
  })
})