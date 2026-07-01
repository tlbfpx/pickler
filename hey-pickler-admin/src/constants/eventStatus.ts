// 严格对齐 hey-pickler-server StatusTransitionValidator
export type EventStatus = 'DRAFT' | 'OPEN' | 'FULL' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED'

export const STATUS_LABEL: Record<EventStatus, string> = {
  DRAFT: '草稿', OPEN: '报名中', FULL: '名额已满',
  IN_PROGRESS: '进行中', COMPLETED: '已结束', CANCELLED: '已取消'
}

export const STATUS_COLOR: Record<EventStatus, string> = {
  DRAFT: '#909399', OPEN: '#10B981', FULL: '#F59E0B',
  IN_PROGRESS: '#3B82F6', COMPLETED: '#6B7280', CANCELLED: '#EF4444'
}

export const ALLOWED_TRANSITIONS: Record<EventStatus, EventStatus[]> = {
  DRAFT: ['OPEN', 'CANCELLED'],
  OPEN: ['FULL', 'IN_PROGRESS', 'CANCELLED'],
  FULL: ['OPEN', 'IN_PROGRESS', 'CANCELLED'],
  IN_PROGRESS: ['COMPLETED', 'CANCELLED'],
  COMPLETED: [],
  CANCELLED: []
}

export const getAllowedTargets = (s: EventStatus): EventStatus[] => ALLOWED_TRANSITIONS[s] || []

export const formatStatus = (s: string): string => STATUS_LABEL[s as EventStatus] || s
export const statusColor = (s: string): string => STATUS_COLOR[s as EventStatus] || '#6B7280'