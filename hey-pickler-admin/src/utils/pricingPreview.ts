import type { BusinessHour, CourtPricingBand } from '@/types'

/**
 * 定价预览相关的纯函数(不依赖 Vue 运行时,便于单测)。
 * 时间统一按本地时区(浏览器 = 后端 Asia/Shanghai),与后端 SlotCalculator 的半开区间语义一致。
 */

const DAY_LABELS = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']

/** 周一..周日的 dayOfWeek 序(后端 0=周日..6=周六) */
const DISPLAY_ORDER = [1, 2, 3, 4, 5, 6, 0]

const DAY_TYPES: Record<CourtPricingBand['dayType'], number[]> = {
  WEEKDAY: [1, 2, 3, 4, 5],
  WEEKEND: [0, 6],
  ALL: [0, 1, 2, 3, 4, 5, 6]
}

function pad2(n: number): string {
  return n < 10 ? '0' + n : String(n)
}

function formatYmd(d: Date): string {
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`
}

/** 把 'HH:mm' / 'HH:mm:ss' 折算成自午夜起的分钟数;非法 → -1 */
function toMinutes(t?: string): number {
  if (!t) return -1
  const s = t.slice(0, 5)
  const idx = s.indexOf(':')
  if (idx < 0) return -1
  const h = Number(s.slice(0, idx))
  const m = Number(s.slice(idx + 1))
  if (Number.isNaN(h) || Number.isNaN(m)) return -1
  return h * 60 + m
}

/** 'HH:mm[:ss]' → 'HH:mm' 显示用(slice 对短串安全,无需条件分支;调用方已过滤空值) */
function toHm(t: string): string {
  return t.slice(0, 5)
}

/**
 * 从 `from` 起找最近的星期一-五日期(含 `from` 当天),返回 'YYYY-MM-DD'。
 * 用于"下个工作日"定价预览取数。
 */
export function nextWeekdayDate(from: Date): string {
  const d = new Date(from.getFullYear(), from.getMonth(), from.getDate())
  let guard = 0
  while ((d.getDay() === 0 || d.getDay() === 6) && guard < 14) {
    d.setDate(d.getDate() + 1)
    guard++
  }
  return formatYmd(d)
}

/**
 * 从 `from` 起找最近的周六/周日日期(含 `from` 当天),返回 'YYYY-MM-DD'。
 * 用于"下个周末"定价预览取数。
 */
export function nextWeekendDate(from: Date): string {
  const d = new Date(from.getFullYear(), from.getMonth(), from.getDate())
  let guard = 0
  while (d.getDay() !== 0 && d.getDay() !== 6 && guard < 14) {
    d.setDate(d.getDate() + 1)
    guard++
  }
  return formatYmd(d)
}

/**
 * 把 7 天营业时间压成一行可读字符串(周一..周日顺序)。
 * 空 hours → '未配置营业时间';某天 open/close 缺失 → '休息'。
 */
export function formatHoursLine(hours: BusinessHour[]): string {
  if (!hours || hours.length === 0) return '未配置营业时间'
  const map = new Map<number, BusinessHour>()
  for (const h of hours) map.set(h.dayOfWeek, h)
  return DISPLAY_ORDER.map(dow => {
    const h = map.get(dow)
    const label = DAY_LABELS[dow]
    if (!h || !h.openTime || !h.closeTime) return `${label} 休息`
    return `${label} ${toHm(h.openTime)}-${toHm(h.closeTime)}`
  }).join(' · ')
}

/**
 * 判断一条定价带是否"死带":其 dayType 对应的任一星期里,营业时间 [open,close)
 * 都与 band [start,end) 不相交 → 该带产不出任何可订格子。hours 为空 → 不判定(false)。
 * band 自身 start/end 非法或 start>=end → 不判定(false,避免编辑中误报)。
 */
export function isBandDead(band: CourtPricingBand, hours: BusinessHour[]): boolean {
  if (!hours || hours.length === 0) return false
  const bStart = toMinutes(band.startTime)
  const bEnd = toMinutes(band.endTime)
  if (bStart < 0 || bEnd < 0 || bStart >= bEnd) return false
  for (const dow of DAY_TYPES[band.dayType]) {
    const h = hours.find(x => x.dayOfWeek === dow)
    if (!h) continue
    const o = toMinutes(h.openTime)
    const c = toMinutes(h.closeTime)
    if (o < 0 || c < 0 || o >= c) continue
    // 半开区间相交:bStart < c && o < bEnd
    if (bStart < c && o < bEnd) return false
  }
  return true
}
