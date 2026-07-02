import request from './request'
import type { ApiResponse } from '@/types'

export interface Assignment {
  id: number
  userId: number | null
  teamId: number | null
  displayName: string | null
  seed: number
}

export interface Group {
  id: number
  eventId: number
  groupIndex: number
  name: string
  assignments: Assignment[]
}

export type GroupingStrategy = 'RANDOM' | 'SERPENTINE' | 'MANUAL'

export const groupEvent = (eventId: number, strategy: GroupingStrategy, groupCount: number) => {
  return request.post<unknown, ApiResponse<Group[]>>(`/events/${eventId}/grouping`, {
    strategy,
    groupCount
  })
}

export const getGroups = (eventId: number) => {
  return request.get<unknown, ApiResponse<Group[]>>(`/events/${eventId}/grouping`)
}

export const reassignParticipant = (eventId: number, assignmentId: number, targetGroupId: number) => {
  return request.put<unknown, ApiResponse<void>>(
    `/events/${eventId}/grouping/assignments/${assignmentId}`,
    { targetGroupId }
  )
}

export const lockGroups = (eventId: number) => {
  return request.post<unknown, ApiResponse<void>>(`/events/${eventId}/grouping/lock`)
}

export const unlockGroups = (eventId: number) => {
  return request.post<unknown, ApiResponse<void>>(`/events/${eventId}/grouping/unlock`)
}
