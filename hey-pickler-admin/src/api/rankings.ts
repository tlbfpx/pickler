import request from './request'
import type { RankingEntry, EnterPointsRequest, ApiResponse, PageResult } from '@/types'

export interface RankingQuery {
  type: 'STAR' | 'PARTY'
  page?: number
  size?: number
  keyword?: string
  tier?: string
}

export const getRankings = (q: RankingQuery) => {
  return request.get<any, ApiResponse<PageResult<RankingEntry>>>(`/rankings/${q.type}`, {
    params: { page: q.page, size: q.size, keyword: q.keyword, tier: q.tier }
  })
}

export const enterPoints = (data: EnterPointsRequest) => {
  return request.post<any, ApiResponse<void>>('/rankings/points', data)
}

export const refreshRankings = (type: 'STAR' | 'PARTY') => {
  return request.post<any, ApiResponse<void>>('/rankings/refresh', { type })
}
