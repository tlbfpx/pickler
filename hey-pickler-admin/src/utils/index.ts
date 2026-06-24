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

export const formatEventStatus = (status: string): string => {
  const statusMap: Record<string, string> = {
    DRAFT: '草稿',
    OPEN: '报名中',
    FULL: '名额已满',
    IN_PROGRESS: '进行中',
    COMPLETED: '已结束',
    CANCELLED: '已取消'
  }
  return statusMap[status] || status
}

export const formatEventType = (type: string): string => {
  // 双积分体系：STAR→竞技赛事，PARTY→社交活动（字段不变，仅文案）
  const typeMap: Record<string, string> = {
    STAR: '竞技赛事',
    PARTY: '社交活动'
  }
  return typeMap[type] || type
}

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

export const getEventStatusColor = (status: string): string => {
  const colorMap: Record<string, string> = {
    DRAFT: '#9CA3AF',
    OPEN: '#10B981',
    FULL: '#F59E0B',
    IN_PROGRESS: '#3B82F6',
    COMPLETED: '#6B7280',
    CANCELLED: '#EF4444'
  }
  return colorMap[status] || '#6B7280'
}

export const getEventTypeColor = (type: string): string => {
  const colorMap: Record<string, string> = {
    STAR: '#F59E0B',
    PARTY: '#8B5CF6'
  }
  return colorMap[type] || '#6B7280'
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
