import request from './request'
import type { ApiResponse } from '@/types'

// ==================== Overview (KPI 趋势图) ====================

export interface DailyCount {
  date: string
  count: number
}

export interface DailyRate {
  date: string
  /** 0-100 百分比（保留 1 位小数） */
  rate: number
}

export interface AnalyticsOverview {
  days: number
  newUsers: DailyCount[]
  newRegistrations: DailyCount[]
  newEvents: DailyCount[]
  completionRate: DailyRate[]
  /** 累计完赛率（不分时段）—— 用于首屏 fallback 显示 */
  overallCompletionRate: number
}

export const getAnalyticsOverview = (days = 30) =>
  request.get<unknown, ApiResponse<AnalyticsOverview>>('/analytics/overview', { params: { days } })

// ==================== Dashboard (分析仪表盘) ====================

export interface AnalyticsTotals {
  users: number
  events: number
  registrations: number
  /** 累计收入（所有有效报名 × 赛事 fee） */
  revenue: number
}

export interface MonthlyTrend {
  /** yyyy-MM */
  month: string
  users: number
  events: number
  registrations: number
}

export interface AnalyticsDashboard {
  totals: AnalyticsTotals
  /** 完赛率：COMPLETED / (非 CANCELLED) × 100 */
  completionRate: number
  /** 报名转化：总报名 / 总赛事 */
  registrationPerEvent: number
  /** 30 天内有效报名 + 创建赛事的去重用户数 */
  activeUsersLast30d: number
  /** 最近 12 个月趋势 */
  byMonth: MonthlyTrend[]
  /** 赛事类型分布（STAR / PARTY） */
  byType: Record<string, number>
  /** 赛事状态分布（DRAFT/OPEN/FULL/IN_PROGRESS/COMPLETED/CANCELLED） */
  byStatus: Record<string, number>
}

export const getAnalyticsDashboard = () =>
  request.get<unknown, ApiResponse<AnalyticsDashboard>>('/analytics/dashboard')