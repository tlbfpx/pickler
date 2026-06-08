import request from './request'
import type { Admin, CreateAdminRequest, UpdateAdminRequest, ApiResponse } from '@/types'

export const getAdminList = () => {
  return request.get<any, ApiResponse<Admin[]>>('/admins')
}

export const createAdmin = (data: CreateAdminRequest) => {
  return request.post<any, ApiResponse<void>>('/admins', data)
}

export const updateAdmin = (id: number, data: UpdateAdminRequest) => {
  return request.put<any, ApiResponse<void>>(`/admins/${id}`, data)
}

export const deleteAdmin = (id: number) => {
  return request.delete<any, ApiResponse<void>>(`/admins/${id}`)
}
