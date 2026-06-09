import request from './request'
import type { DashboardStats, ApiResponse } from '@/types'

export const getDashboardStats = () => {
  return request.get<any, ApiResponse<DashboardStats>>('/dashboard')
}
