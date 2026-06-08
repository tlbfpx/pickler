import request from './request'
import type { LoginRequest, LoginResponse, ApiResponse } from '@/types'

export const login = (data: LoginRequest) => {
  return request.post<any, ApiResponse<LoginResponse>>('/auth/login', data)
}

export const logout = () => {
  return request.post<any, ApiResponse<void>>('/auth/logout')
}
