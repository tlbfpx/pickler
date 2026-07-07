import request from './request'
import type { Event, CreateEventRequest, UpdateEventRequest, Registration, ApiResponse, PageParams, PageResult, EventSummaryVO, BulkCheckInRequest, BulkCheckInResult } from '@/types'

export interface EventParticipant {
  userId: number
  nickname: string | null
  avatarUrl: string | null
  city: string | null
  matchType: string
  registrationStatus: string
}

export interface PlacementDetail {
  rank: number
  userId: number
  nickname: string | null
  points: number
  reason: string
  createdAt: string
}

export const getEventList = (params: PageParams) => {
  return request.get<unknown, ApiResponse<PageResult<Event>>>('/events', { params })
}

export const getEventDetail = (id: number) =>
  request.get<unknown, ApiResponse<Event>>(`/events/${id}`)

export const getEventParticipants = (eventId: number) => {
  return request.get<unknown, ApiResponse<EventParticipant[]>>(`/events/${eventId}/participants`)
}

export const createEvent = (data: CreateEventRequest) => {
  return request.post<unknown, ApiResponse<{ id: number }>>('/events', data)
}

export const updateEvent = (id: number, data: UpdateEventRequest) => {
  return request.put<unknown, ApiResponse<void>>(`/events/${id}`, data)
}

export const deleteEvent = (id: number) => {
  return request.delete<unknown, ApiResponse<void>>(`/events/${id}`)
}

export const changeEventStatus = (id: number, status: string) => {
  return request.patch<unknown, ApiResponse<void>>(`/events/${id}/status`, { status })
}

export const getEventRegistrations = (eventId: number, params: PageParams) => {
  return request.get<unknown, ApiResponse<PageResult<Registration>>>(
    `/events/${eventId}/registrations`, { params }
  )
}

export const updateRegistrationStatus = (eventId: number, registrationId: number, status: string) => {
  return request.patch<unknown, ApiResponse<void>>(
    `/events/${eventId}/registrations/${registrationId}/status`, { status }
  )
}

// Loop-v15 — Loop-v13 backend integration
export const getEventSummary = (eventId: number) =>
  request.get<unknown, ApiResponse<EventSummaryVO>>(`/events/${eventId}/summary`)

// Loop-v15 — Loop-v14 backend integration
export const bulkCheckIn = (eventId: number, registrationIds: number[]) =>
  request.post<BulkCheckInRequest, ApiResponse<BulkCheckInResult>>(
    `/events/${eventId}/registrations/bulk-check-in`,
    { registrationIds }
  )

export const getEventPlacements = (id: number) =>
  request.get<unknown, ApiResponse<PlacementDetail[]>>(`/events/${id}/placements`)
