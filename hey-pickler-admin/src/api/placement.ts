import request from './request'
import type { ApiResponse, PlacementPointsVO, PlacementPointsRequest } from '@/types'

export const getPlacementPoints = (eventId: number) => {
  return request.get<any, ApiResponse<PlacementPointsVO>>(
    `/events/${eventId}/placement-points`
  )
}

export const setPlacementPoints = (eventId: number, data: PlacementPointsRequest) => {
  return request.put<any, ApiResponse<void>>(
    `/events/${eventId}/placement-points`,
    data
  )
}