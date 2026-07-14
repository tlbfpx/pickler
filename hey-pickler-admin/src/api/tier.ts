import request from './request'
import type { ApiResponse } from '@/types'

export interface TierConfigVO {
  track: string
  tierCode: string
  tierName: string
  tierColor: string
  threshold: number
  icon: string | null
  sort: number
}

export interface TierItemUpdateRequest {
  tierCode: string
  tierName: string
  tierColor: string
  threshold: number
  icon: string | null
}

export const getTierConfig = (track: string) =>
  request.get<unknown, ApiResponse<TierConfigVO[]>>(`/tier/${track}`)

export const updateTierConfig = (track: string, items: TierItemUpdateRequest[]) =>
  request.put<unknown, ApiResponse<void>>(`/tier/${track}`, items)
