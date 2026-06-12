import request from './request'
import type { Admin, CreateAdminRequest, UpdateAdminRequest, ApiResponse, PageParams } from '@/types'

export const getAdminList = (params: PageParams) => {
  return request.get<any, ApiResponse<PageResult<Admin>>>('/admin-users', { params })
}

export const createAdmin = (data: CreateAdminRequest) => {
  return request.post<any, ApiResponse<void>>('/admin-users', data)
}

export const updateAdmin = (id: number, data: UpdateAdminRequest) => {
  return request.put<any, ApiResponse<void>>(`/admin-users/${id}`, data)
}

export const deleteAdmin = (id: number) => {
  return request.delete<any, ApiResponse<void>>(`/admin-users/${id}`)
}
