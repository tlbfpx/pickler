import request from './request'
import type { ApiResponse, RankingPageVO, Season } from '@/types'

/** 赛季列表（按 type 过滤） */
export const listSeasons = (type: 'STAR' | 'PARTY') => {
  return request.get<unknown, ApiResponse<Season[]>>('/seasons', { params: { type } })
}

/** 新建赛季（默认 status=ARCHIVED） */
export const createSeason = (data: {
  type: 'STAR' | 'PARTY'
  code: string
  name: string
  startDate: string
  endDate: string
}) => {
  return request.post<unknown, ApiResponse<Season>>('/seasons', data)
}

/** 切换赛季为当前（同 type 旧 CURRENT→ARCHIVED） */
export const activateSeason = (id: number) => {
  return request.post<unknown, ApiResponse<void>>(`/seasons/${id}/activate`)
}

/** 赛季排名查询（当前/归档统一返回 RankingPageVO） */
export const getSeasonRankings = (
  id: number,
  params: { tier?: string; page: number; size: number }
) => {
  return request.get<unknown, ApiResponse<RankingPageVO>>(
    `/seasons/${id}/rankings`,
    { params }
  )
}
