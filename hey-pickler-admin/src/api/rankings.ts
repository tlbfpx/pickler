import request from './request'
import type { RankingListResponse, EnterPointsRequest, ApiResponse } from '@/types'

export const getRankings = (type: 'STAR' | 'PARTY') => {
  return request.get<any, ApiResponse<RankingListResponse>>(`/rankings/${type.toLowerCase()}`)
}

export const enterPoints = (data: EnterPointsRequest) => {
  return request.post<any, ApiResponse<void>>('/rankings/points', data)
}

export const refreshRankings = (type: 'STAR' | 'PARTY') => {
  return request.post<any, ApiResponse<void>>('/rankings/refresh', { type })
}
