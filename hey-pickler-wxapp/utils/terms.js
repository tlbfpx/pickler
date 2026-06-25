/**
 * 积分体系术语常量（后端字段 STAR/PARTY 不变，仅前端文案）
 *
 * 后端双积分体系：
 *   STAR  → 竞技赛事 / 战力 / 战力段位
 *   PARTY → 社交活动 / 活力 / 活力段位
 *
 * 比赛形式（match-grouping Spec 1）：
 *   SINGLES 单打 / DOUBLES 双打 / MIXED 混打
 *
 * 段位 6 档（与后端 TierProperties.names 一致）：
 *   BRONZE 青铜 / SILVER 白银 / GOLD 黄金
 *   PLATINUM 铂金 / DIAMOND 钻石 / MASTER 王者
 */
const TERMS = {
  STAR: { type: '竞技赛事', points: '战力', tier: '战力段位' },
  PARTY: { type: '社交活动', points: '活力', tier: '活力段位' }
}

// 比赛形式 → 展示文本 / 颜色（与后端 EventFormat 对齐）
const FORMAT = {
  SINGLES: { label: '单打', color: '#3B82F6' },
  DOUBLES: { label: '双打', color: '#10B981' },
  MIXED: { label: '混打', color: '#EC4899' }
}

// 队伍状态（与后端 TeamStatus 对齐）
const TEAM_STATUS = {
  PENDING: '待确认',
  CONFIRMED: '已确认'
}

// tier 英文 key → 中文（与后端 TierProperties.names 一致）
const TIER_NAME = {
  BRONZE: '青铜',
  SILVER: '白银',
  GOLD: '黄金',
  PLATINUM: '铂金',
  DIAMOND: '钻石',
  MASTER: '王者'
}

export default { TERMS, TIER_NAME, FORMAT, TEAM_STATUS }
export { TERMS, TIER_NAME, FORMAT, TEAM_STATUS }