import request from './request'
import type { UserListResponse, BanUserRequest, ApiResponse, PageParams } from '@/types'

export const getUserList = (params: PageParams) => {
  return request.get<any, ApiResponse<UserListResponse>>('/users', { params })
}

export const banUser = (id: number, data: BanUserRequest) => {
  return request.put<any, ApiResponse<void>>(`/users/${id}/ban`, data)
}

export const unbanUser = (id: number) => {
  return request.put<any, ApiResponse<void>>(`/users/${id}/unban`)
}
