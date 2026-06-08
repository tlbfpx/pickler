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
  const tierMap: Record<string, string> = {
    LEGEND: '传奇',
    SUPER: '超级',
    SHINING: '新星'
  }
  return tierMap[tier] || tier
}

export const formatEventStatus = (status: string): string => {
  const statusMap: Record<string, string> = {
    UPCOMING: '即将开始',
    ONGOING: '进行中',
    COMPLETED: '已结束',
    CANCELLED: '已取消'
  }
  return statusMap[status] || status
}

export const formatEventType = (type: string): string => {
  const typeMap: Record<string, string> = {
    STAR: '星级赛',
    PARTY: '派对赛'
  }
  return typeMap[type] || type
}

export const getTierColor = (tier: string): string => {
  const colorMap: Record<string, string> = {
    LEGEND: '#F59E0B',
    SUPER: '#8B5CF6',
    SHINING: '#6B7280'
  }
  return colorMap[tier] || '#6B7280'
}

export const getEventStatusColor = (status: string): string => {
  const colorMap: Record<string, string> = {
    UPCOMING: '#3B82F6',
    ONGOING: '#10B981',
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
    ADMIN: '#3B82F6'
  }
  return colorMap[role] || '#6B7280'
}

export const formatAdminRole = (role: string): string => {
  const roleMap: Record<string, string> = {
    SUPER_ADMIN: '超级管理员',
    ADMIN: '管理员'
  }
  return roleMap[role] || role
}
