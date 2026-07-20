import request from './request'
import type { DashboardStats, ApiResponse } from '@/types'

// ==================== Loop-v19 Dashboard Phase 1 — 新增 4 endpoint ====================

/** /trends 单桶：users/registrations/revenue/eventsCount 4 条时序同桶 */
export interface DashboardTrendBucket {
  date: string
  users: number
  registrations: number
  revenue: number
  eventsCount: number
}

/** /trends 响应：range + buckets 数组（按天填零，无空缺） */
export interface DashboardTrendVO {
  range: string
  buckets: DashboardTrendBucket[]
}

/** /top-events 单元素 */
export interface TopEventVO {
  eventId: number
  title: string
  /** registrations: 报名数；revenue: 金额（元）；fillRate: 0..1 */
  value: number
  /** echo: registrations | revenue | fillRate */
  metric: string
  maxParticipants: number | null
  currentParticipants: number | null
}

/** /attendance 漏斗 */
export interface AttendanceFunnelVO {
  range: string
  registered: number
  checkedIn: number
  /** 0..1；registered=0 时为 null（避免 NaN） */
  noShowRate: number | null
}

/** /compare 同比/环比。previous=0 时 deltaPct=null */
export interface CompareResultVO {
  metric: string
  current: number
  previous: number
  deltaAbs: number
  /** 0..1 */
  deltaPct: number | null
}

export const getDashboardStats = () => {
  return request.get<unknown, ApiResponse<DashboardStats>>('/dashboard')
}

export const getDashboardTrends = (params: {
  range?: string
  from?: string
  to?: string
  no_cache?: number
}) => {
  return request.get<unknown, ApiResponse<DashboardTrendVO>>('/dashboard/trends', { params })
}

export const getDashboardTopEvents = (params: {
  metric?: string
  range?: string
  from?: string
  to?: string
  limit?: number
  no_cache?: number
}) => {
  return request.get<unknown, ApiResponse<TopEventVO[]>>('/dashboard/top-events', { params })
}

export const getDashboardAttendance = (params: {
  range?: string
  from?: string
  to?: string
  no_cache?: number
}) => {
  return request.get<unknown, ApiResponse<AttendanceFunnelVO>>('/dashboard/attendance', { params })
}

export const getDashboardCompare = (params: {
  metric: string
  currentRange?: string
  previousRange?: string
  no_cache?: number
}) => {
  return request.get<unknown, ApiResponse<CompareResultVO>>('/dashboard/compare', { params })
}