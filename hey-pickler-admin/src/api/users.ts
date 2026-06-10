import request from './request'
import type { PageResult, ApiResponse, PageParams } from '@/types'

export interface User {
  id: number
  nickname: string | null
  avatarUrl: string | null
  city: string | null
  phone: string | null
  starPoints: number
  partyPoints: number
  starTier: string
  partyTier: string
  status: 'NORMAL' | 'BANNED'
  createdAt: string
}

export interface PointRecord {
  id: number
  eventId: number | null
  eventTitle: string | null
  type: string
  points: number
  reason: string | null
  createdAt: string
}

export interface EventRecord {
  id: number
  title: string
  type: string
  bannerUrl: string | null
  eventTime: string | null
  location: string | null
  status: string
  registrationStatus: string
}

export const getUserList = (params: PageParams & { city?: string }) => {
  return request.get<any, ApiResponse<PageResult<User>>>('/users', { params })
}

export const getUserDetail = (id: number) => {
  return request.get<any, ApiResponse<User>>(`/users/${id}`)
}

export const getUserPoints = (id: number, params: { type?: string; page: number; size: number }) => {
  return request.get<any, ApiResponse<PageResult<PointRecord>>>(`/users/${id}/points`, { params })
}

export const getUserEvents = (id: number, params: { type?: string; page: number; size: number }) => {
  return request.get<any, ApiResponse<PageResult<EventRecord>>>(`/users/${id}/events`, { params })
}

export const banUser = (id: number, data: { reason: string; days?: number }) => {
  return request.post<any, ApiResponse<void>>(`/users/${id}/ban`, data)
}

export const unbanUser = (id: number) => {
  return request.post<any, ApiResponse<void>>(`/users/${id}/unban`)
}
