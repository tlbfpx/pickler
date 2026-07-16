// hex 解析 / 混色 + Element Plus 运行时主题（覆盖 --el-color-primary 及其明暗档变量）。

function parseHex(hex: string): { r: number; g: number; b: number } | null {
  const m = /^#?([0-9a-fA-F]{6})$/.exec((hex || '').trim())
  if (!m) return null
  const v = m[1]
  return {
    r: parseInt(v.slice(0, 2), 16),
    g: parseInt(v.slice(2, 4), 16),
    b: parseInt(v.slice(4, 6), 16)
  }
}

function toHexByte(n: number): string {
  return Math.max(0, Math.min(255, Math.round(n))).toString(16).padStart(2, '0')
}

function toHex({ r, g, b }: { r: number; g: number; b: number }): string {
  return `#${toHexByte(r)}${toHexByte(g)}${toHexByte(b)}`
}

/** 将 c1 向 c2 按权重混合，weight 为 c2 占比（0..1）。非法 hex 原样返回 c1。 */
export function mixHex(c1: string, c2: string, weight: number): string {
  const a = parseHex(c1)
  const b = parseHex(c2)
  if (!a || !b) return c1
  const w = Math.max(0, Math.min(1, weight))
  return toHex({
    r: a.r * (1 - w) + b.r * w,
    g: a.g * (1 - w) + b.g * w,
    b: a.b * (1 - w) + b.b * w
  })
}

/**
 * 应用 Element Plus 主色到 :root：--el-color-primary + light-1..9（向白混合，越大越浅）
 * + dark-2（向黑混合 20%）。覆盖后按钮 / 链接 / 选中态等即跟随品牌色。
 * 非法 hex 静默跳过。
 */
export function applyTheme(primary: string): void {
  const hex = parseHex(primary)
  if (!hex) return
  const root = document.documentElement
  root.style.setProperty('--el-color-primary', toHex(hex))
  for (let n = 1; n <= 9; n++) {
    root.style.setProperty(`--el-color-primary-light-${n}`, mixHex(primary, '#ffffff', n / 10))
  }
  root.style.setProperty('--el-color-primary-dark-2', mixHex(primary, '#000000', 0.2))
}
