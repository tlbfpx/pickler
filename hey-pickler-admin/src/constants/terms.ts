/**
 * 积分体系术语常量
 *
 * 后端字段 STAR/PARTY 保持不变，仅前端文案展示用。
 * - STAR  → 竞技赛事 / 战力 / 战力段位
 * - PARTY → 社交活动 / 活力 / 活力段位
 *
 * 段位 6 档（与后端 TierName 对齐）：
 *   BRONZE 青铜 / SILVER 白银 / GOLD 黄金
 *   PLATINUM 铂金 / DIAMOND 钻石 / MASTER 王者
 */

export type PointsType = 'STAR' | 'PARTY'

export interface PointsTypeTerms {
  /** 事件类型名（竞技赛事 / 社交活动） */
  type: string
  /** 积分名（战力 / 活力） */
  points: string
  /** 段位名（战力段位 / 活力段位） */
  tier: string
  /** 排名名（战力排名 / 活力排名） */
  ranking: string
}

export const TERMS: Record<PointsType, PointsTypeTerms> = {
  STAR: {
    type: '竞技赛事',
    points: '战力',
    tier: '战力段位',
    ranking: '战力排名'
  },
  PARTY: {
    type: '社交活动',
    points: '活力',
    tier: '活力段位',
    ranking: '活力排名'
  }
} as const

/**
 * 段位英文 key → 中文展示名。
 * 优先使用后端 VO 返回的 tierName/starTierName/partyTierName；
 * 当只有英文 key 时经此映射转中文。
 */
export const TIER_NAME: Record<string, string> = {
  BRONZE: '青铜',
  SILVER: '白银',
  GOLD: '黄金',
  PLATINUM: '铂金',
  DIAMOND: '钻石',
  MASTER: '王者'
}

/**
 * 段位展示色（仅前端展示用，不影响业务逻辑）。
 */
export const TIER_COLOR: Record<string, string> = {
  BRONZE: '#A56C2C',
  SILVER: '#9CA3AF',
  GOLD: '#E6A23C',
  PLATINUM: '#409EFF',
  DIAMOND: '#9C27B0',
  MASTER: '#EF4444'
}

/**
 * 取段位中文展示名。
 * - 优先使用后端返回的中文 tierName（已为中文时直接返回）
 * - 否则按英文 key 查 TIER_NAME
 * - 兜底返回原值
 */
export function formatTierName(tier: string | null | undefined): string {
  if (!tier) return '-'
  return TIER_NAME[tier] || tier
}

/**
 * 取段位展示色，未命中时回退灰色。
 */
export function getTierColor(tier: string | null | undefined): string {
  if (!tier) return '#6B7280'
  return TIER_COLOR[tier] || '#6B7280'
}

/**
 * 取积分类型对应文案；未命中回退原值（用于类型展示）。
 */
export function getPointsTypeLabel(type: string | null | undefined): string {
  if (!type) return '-'
  return TERMS[type as PointsType]?.type || type
}

/**
 * 积分来源英文 key → 中文展示名（与后端 PointSource 枚举对齐）。
 * MANUAL/ADJUST 可撤销；PLACEMENT 等系统来源不可撤销（前端隐藏撤销按钮）。
 */
export const POINT_SOURCE_LABEL: Record<string, string> = {
  MANUAL: '管理员手动',
  ADJUST: '系统纠错',
  PLACEMENT: '名次发分',
  REGISTRATION: '报名',
  CHECK_IN: '签到',
  REDEEM: '兑换'
}

/** 取积分来源文案；未命中回退原值。 */
export function getPointSourceLabel(source: string | null | undefined): string {
  if (!source) return '-'
  return POINT_SOURCE_LABEL[source] || source
}
