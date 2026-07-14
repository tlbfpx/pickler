/**
 * 报名/比赛形式 文案工具（字典驱动，fallback 保留原硬编码）
 *
 * - formatRegStatus: 报名状态 code → 中文名（读 registration_status 字典）
 * - formatMatchType: 比赛形式 code → 中文名（matchType 复用 event_format 字典）
 *
 * cell tag 统一改用 <DictTag>（hex color），不再需要 ElementPlus type-enum 映射。
 * 若某处确实无法用 DictTag（例如 select option、CSV 导出、动态组合文案），
 * 调用方自行使用本文件的 format* 函数 + fallback。
 */

import { useDictStore } from '@/stores/dict'

const FALLBACK_REG: Record<string, string> = {
  REGISTERED: '已报名',
  CHECKED_IN: '已签到',
  WITHDRAWN: '已退出'
}

const FALLBACK_FMT: Record<string, string> = {
  SINGLES: '单打',
  DOUBLES: '双打',
  MIXED: '混打'
}

/** 报名状态 code→名（读 registration_status 字典） */
export const formatRegStatus = (s: string | null | undefined): string => {
  if (!s) return '-'
  const store = useDictStore()
  const l = store.label('registration_status', s)
  return l && l !== s ? l : FALLBACK_REG[s] || s
}

/** 比赛形式 code→名（matchType 复用 event_format 字典） */
export const formatMatchType = (s: string | null | undefined): string => {
  if (!s) return '单打'
  const store = useDictStore()
  const l = store.label('event_format', s)
  return l && l !== s ? l : FALLBACK_FMT[s] || s
}
