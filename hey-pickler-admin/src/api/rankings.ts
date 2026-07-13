import request from './request'
import type { EnterPointsRequest, ApiResponse, RankingPageVO } from '@/types'

export interface RankingQuery {
  type: 'STAR' | 'PARTY'
  page?: number
  size?: number
  keyword?: string
  tier?: string
}

/** 排名工作台：榜单分页 + 段位分布 + 当前赛季元信息 */
export const getRankings = (q: RankingQuery) => {
  return request.get<unknown, ApiResponse<RankingPageVO>>(`/rankings/${q.type}`, {
    params: { page: q.page, size: q.size, keyword: q.keyword, tier: q.tier }
  })
}

export const enterPoints = (data: EnterPointsRequest) => {
  return request.post<unknown, ApiResponse<void>>('/rankings/points', data)
}

export const refreshRankings = (type: 'STAR' | 'PARTY') => {
  return request.post<unknown, ApiResponse<void>>('/rankings/refresh', { type })
}

/** 撤销单条积分记录（写 ADJUST 补偿行，仅限 MANUAL/ADJUST） */
export const revertPointRecord = (recordId: number) => {
  return request.post<unknown, ApiResponse<void>>(`/rankings/points/${recordId}/revert`)
}
