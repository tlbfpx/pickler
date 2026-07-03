import request from './request'
import type { LoginRequest, LoginResponse, ApiResponse } from '@/types'

export const login = (data: LoginRequest) => {
  return request.post<unknown, ApiResponse<LoginResponse>>('/auth/login', data)
}

// NOTE: Backend does not have a logout endpoint
// Just clear token from local storage
export const logout = () => {
  localStorage.removeItem('admin_token')
  localStorage.removeItem('admin_info')
  return Promise.resolve()
}
