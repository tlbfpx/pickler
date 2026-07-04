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

// 状态色含义：鼠标 hover 状态 tag 时展示，给用户解释色块语义
export const STATUS_TOOLTIP: Record<EventStatus, string> = {
  DRAFT: '草稿：赛事已创建但未发布，选手暂不可报名',
  OPEN: '报名中：正在接受选手报名',
  FULL: '名额已满：已达人数上限，不再接受新报名',
  IN_PROGRESS: '进行中：比赛已开始，正在录入比分',
  COMPLETED: '已结束：比赛已完结，可配置积分表',
  CANCELLED: '已取消：该赛事已被取消，不再生效'
}
export const statusTooltip = (s: string): string => STATUS_TOOLTIP[s as EventStatus] || ''