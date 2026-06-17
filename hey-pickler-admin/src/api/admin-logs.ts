import request from './request'
import type { ApiResponse, PageResult } from '@/types'

export interface OperationLogItem {
  id: number
  operatorId: number | null
  operatorName: string | null
  operatorRole: string
  method: string
  module: string
  action: string
  targetType: string | null
  targetId: string | null
  path: string
  params: string | null
  status: number
  errorCode: number | null
  errorMsg: string | null
  ip: string | null
  userAgent: string | null
  latencyMs: number | null
  createdAt: string
}

export interface OperationLogQuery {
  page: number
  size: number
  operatorId?: number
  module?: string
  action?: string
  status?: number
  startTime?: string
  endTime?: string
}

export const getOperationLogs = (params: OperationLogQuery) => {
  return request.get<unknown, ApiResponse<PageResult<OperationLogItem>>>('/admin/operation-logs', { params })
}
