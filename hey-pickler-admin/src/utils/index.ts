import { useDictStore } from '@/stores/dict'

export const formatDate = (date: string): string => {
  const d = new Date(date)
  return d.toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

export const formatTier = (tier: string): string => {
  // 段位 6 档（与后端 TierName 对齐），见 @/constants/terms
  const tierMap: Record<string, string> = {
    BRONZE: '青铜',
    SILVER: '白银',
    GOLD: '黄金',
    PLATINUM: '铂金',
    DIAMOND: '钻石',
    MASTER: '王者'
  }
  return tierMap[tier] || tier
}

// ============ event_status / event_type / event_format ============
// 字典 dict store 优先，缺失/未命中时回退原硬编码 map（保证离线 & 字典未加载时仍可用）

const FALLBACK_STATUS_LABEL: Record<string, string> = {
  DRAFT: '草稿',
  OPEN: '报名中',
  FULL: '名额已满',
  IN_PROGRESS: '进行中',
  COMPLETED: '已结束',
  CANCELLED: '已取消'
}

const FALLBACK_STATUS_COLOR: Record<string, string> = {
  DRAFT: '#9CA3AF',
  OPEN: '#10B981',
  FULL: '#F59E0B',
  IN_PROGRESS: '#3B82F6',
  COMPLETED: '#6B7280',
  CANCELLED: '#EF4444'
}

const FALLBACK_TYPE_LABEL: Record<string, string> = {
  STAR: '竞技赛事',
  PARTY: '社交活动'
}

const FALLBACK_TYPE_COLOR: Record<string, string> = {
  STAR: '#F59E0B',
  PARTY: '#8B5CF6'
}

const FALLBACK_FORMAT_LABEL: Record<string, string> = {
  SINGLES: '单打',
  DOUBLES: '双打',
  MIXED: '混打'
}

const FALLBACK_FORMAT_COLOR: Record<string, string> = {
  SINGLES: '#3B82F6',
  DOUBLES: '#10B981',
  MIXED: '#EC4899'
}

export const formatEventStatus = (status: string): string => {
  const l = useDictStore().label('event_status', status)
  return l && l !== status ? l : (FALLBACK_STATUS_LABEL[status] || status)
}

export const formatEventType = (type: string): string => {
  const l = useDictStore().label('event_type', type)
  return l && l !== type ? l : (FALLBACK_TYPE_LABEL[type] || type)
}

export const getEventStatusColor = (status: string): string => {
  const store = useDictStore()
  return store.has('event_status', status) ? store.color('event_status', status) : (FALLBACK_STATUS_COLOR[status] || '#6B7280')
}

export const getEventTypeColor = (type: string): string => {
  const store = useDictStore()
  return store.has('event_type', type) ? store.color('event_type', type) : (FALLBACK_TYPE_COLOR[type] || '#6B7280')
}

export const formatEventFormat = (format: string | null | undefined): string => {
  if (!format) return '单打' // backend default
  const l = useDictStore().label('event_format', format)
  return l && l !== format ? l : (FALLBACK_FORMAT_LABEL[format] || format)
}

export const getEventFormatColor = (format: string | null | undefined): string => {
  const key = format || 'SINGLES'
  const store = useDictStore()
  return store.has('event_format', key) ? store.color('event_format', key) : (FALLBACK_FORMAT_COLOR[key] || '#6B7280')
}

// ============ 段位 / 角色（非本期字典联动范围，保持原样） ============

export const getTierColor = (tier: string): string => {
  // 段位 6 档展示色（仅前端展示用）
  const colorMap: Record<string, string> = {
    BRONZE: '#A56C2C',
    SILVER: '#9CA3AF',
    GOLD: '#E6A23C',
    PLATINUM: '#409EFF',
    DIAMOND: '#9C27B0',
    MASTER: '#EF4444'
  }
  return colorMap[tier] || '#6B7280'
}

export const getAdminRoleColor = (role: string): string => {
  const colorMap: Record<string, string> = {
    SUPER_ADMIN: '#EF4444',
    ADMIN: '#3B82F6',
    OPERATOR: '#10B981'
  }
  return colorMap[role] || '#6B7280'
}

export const formatAdminRole = (role: string): string => {
  const roleMap: Record<string, string> = {
    SUPER_ADMIN: '超级管理员',
    ADMIN: '管理员',
    OPERATOR: '运营人员'
  }
  return roleMap[role] || role
}
