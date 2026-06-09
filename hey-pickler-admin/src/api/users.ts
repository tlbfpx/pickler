import request from './request'
import type { PageResult, BanRequest, ApiResponse, PageParams } from '@/types'

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

export const getUserList = (params: PageParams) => {
  return request.get<any, ApiResponse<PageResult<User>>>('/users', { params })
}

export const banUser = (id: number, data: BanRequest) => {
  return request.post<any, ApiResponse<void>>(`/users/${id}/ban`, data)
}

export const unbanUser = (id: number) => {
  return request.post<any, ApiResponse<void>>(`/users/${id}/unban`)
}
