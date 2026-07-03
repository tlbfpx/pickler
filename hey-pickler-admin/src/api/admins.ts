import request from './request'
import type { Admin, CreateAdminRequest, UpdateAdminRequest, ApiResponse, PageParams } from '@/types'

export const getAdminList = (params: PageParams) => {
  return request.get<unknown, ApiResponse<PageResult<Admin>>>('/admin-users', { params })
}

export const createAdmin = (data: CreateAdminRequest) => {
  return request.post<unknown, ApiResponse<void>>('/admin-users', data)
}

export const updateAdmin = (id: number, data: UpdateAdminRequest) => {
  return request.put<unknown, ApiResponse<void>>(`/admin-users/${id}`, data)
}

export const deleteAdmin = (id: number) => {
  return request.delete<unknown, ApiResponse<void>>(`/admin-users/${id}`)
}
