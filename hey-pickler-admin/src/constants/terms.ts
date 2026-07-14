/**
 * 积分体系术语（字典驱动，fallback 保留原硬编码）
 *
 * 后端字段 STAR/PARTY 保持不变，仅前端文案展示用。
 * - STAR  → 竞技赛事 / 战力 / 战力段位
 * - PARTY → 社交活动 / 活力 / 活力段位
 *
 * 段位 6 档（与后端 TierName 对齐）：
 *   BRONZE 青铜 / SILVER 白银 / GOLD 黄金
 *   PLATINUM 铂金 / DIAMOND 钻石 / MASTER 王者
 *
 * 段位色 Chunk4 起改由后端 VO 装配（tierColor / starTierColor / partyTierColor /
 * tierColorMap），前端不再持有 TIER_COLOR 色表；TIER_NAME/formatTierName 仅作
 * tier_code→中文名兜底（离线或缺字段场景）。
 */

import { useDictStore } from '@/stores/dict'

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

const FALLBACK_TERMS: Record<string, PointsTypeTerms> = {
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
}

const FALLBACK_POINT_SOURCE_LABEL: Record<string, string> = {
  MANUAL: '管理员手动',
  ADJUST: '系统纠错',
  PLACEMENT: '名次发分',
  REGISTRATION: '报名',
  CHECK_IN: '签到',
  REDEEM: '兑换'
}

/**
 * 取积分体系术语集合。
 * 字典 track_term 的 extraJson 携带 pointsName/tierName/rankingName/unit；
 * 命中则字典优先，否则回退 FALLBACK_TERMS。
 */
export function getTerms(type: string | null | undefined): PointsTypeTerms {
  if (!type) return FALLBACK_TERMS.STAR
  const store = useDictStore()
  const meta = store.meta('track_term', type) as
    | { pointsName?: string; tierName?: string; rankingName?: string; unit?: string }
    | null
  if (meta && (meta.pointsName || meta.tierName || meta.rankingName)) {
    const typeLabel = store.label('event_type', type)
    return {
      type: typeLabel && typeLabel !== type ? typeLabel : FALLBACK_TERMS[type]?.type || type,
      points: meta.pointsName || FALLBACK_TERMS[type]?.points || type,
      tier: meta.tierName || FALLBACK_TERMS[type]?.tier || type,
      ranking: meta.rankingName || FALLBACK_TERMS[type]?.ranking || type
    }
  }
  return FALLBACK_TERMS[type] || { type, points: type, tier: type, ranking: type }
}

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
 * 取积分类型对应文案（事件类型名）；未命中回退原值（用于类型展示）。
 * 等价于 getTerms(type).type，但直接读 event_type 字典更快。
 */
export function getPointsTypeLabel(type: string | null | undefined): string {
  if (!type) return '-'
  const l = useDictStore().label('event_type', type)
  if (l && l !== type) return l
  return FALLBACK_TERMS[type]?.type || type
}

/** 取积分来源文案；未命中回退原值。 */
export function getPointSourceLabel(source: string | null | undefined): string {
  if (!source) return '-'
  const l = useDictStore().label('point_source', source)
  if (l && l !== source) return l
  return FALLBACK_POINT_SOURCE_LABEL[source] || source
}
