import request from './request'
import type { RankingEntry, EnterPointsRequest, ApiResponse, PageResult } from '@/types'

export const getRankings = (params: { type: 'STAR' | 'PARTY'; page: number; size: number }) => {
  return request.get<any, ApiResponse<PageResult<RankingEntry>>>(`/rankings/${params.type}`)
}

export const enterPoints = (data: EnterPointsRequest) => {
  return request.post<any, ApiResponse<void>>('/rankings/points', data)
}

export const refreshRankings = (type: 'STAR' | 'PARTY') => {
  return request.post<any, ApiResponse<void>>('/rankings/refresh', { type })
}
