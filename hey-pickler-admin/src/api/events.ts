import request from './request'
import type { EventListResponse, CreateEventRequest, UpdateEventRequest, ApiResponse, PageParams } from '@/types'

export const getEventList = (params: PageParams) => {
  return request.get<any, ApiResponse<EventListResponse>>('/events', { params })
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
