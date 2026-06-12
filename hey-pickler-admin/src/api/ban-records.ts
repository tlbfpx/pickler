import request from './request'
import type { BanRecordItem, ApiResponse, PageResult } from '@/types'

export const getBanRecords = (params: { page: number; size: number; userId?: number; action?: string }) => {
  return request.get<any, ApiResponse<PageResult<BanRecordItem>>>('/ban-records', { params })
}

export const deleteBanRecord = (id: number) => {
  return request.delete<any, ApiResponse<void>>(`/ban-records/${id}`)
}
