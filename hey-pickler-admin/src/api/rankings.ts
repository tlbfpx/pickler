import request from './request'
import type { RankingEntry, EnterPointsRequest, ApiResponse, PageParams } from '@/types'

export const getRankings = (params: PageParams & { type: 'STAR' | 'PARTY' }) => {
  return request.get<any, ApiResponse<PageResult<RankingEntry>>>(`/rankings`, { params })
}

export const enterPoints = (data: EnterPointsRequest) => {
  return request.post<any, ApiResponse<void>>('/rankings/points', data)
}

export const refreshRankings = (type: 'STAR' | 'PARTY') => {
  return request.post<any, ApiResponse<void>>('/rankings/refresh', { type })
}
