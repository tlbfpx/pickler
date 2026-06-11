import request from './request'
import type { Event, CreateEventRequest, UpdateEventRequest, ApiResponse, PageParams } from '@/types'

export interface EventParticipant {
  userId: number
  nickname: string | null
  avatarUrl: string | null
  city: string | null
  matchType: string
  registrationStatus: string
}

export const getEventList = (params: PageParams) => {
  return request.get<any, ApiResponse<PageResult<Event>>>('/events', { params })
}

export const getEventParticipants = (eventId: number) => {
  return request.get<any, ApiResponse<EventParticipant[]>>(`/events/${eventId}/participants`)
}

export const createEvent = (data: CreateEventRequest) => {
  return request.post<any, ApiResponse<void>>('/events', data)
}

export const updateEvent = (id: number, data: UpdateEventRequest) => {
  return request.put<any, ApiResponse<void>>(`/events/${id}`, data)
}

export const deleteEvent = (id: number) => {
  return request.delete<any, ApiResponse<void>>(`/events/${id}`)
}

export const changeEventStatus = (id: number, status: string) => {
  return request.patch<any, ApiResponse<void>>(`/events/${id}/status`, { status })
}
