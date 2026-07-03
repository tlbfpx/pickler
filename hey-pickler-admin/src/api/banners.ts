import request from './request'
import type { Banner, CreateBannerRequest, UpdateBannerRequest, ApiResponse } from '@/types'

export const getBannerList = () => {
  return request.get<unknown, ApiResponse<Banner[]>>('/banners')
}

export const createBanner = (data: CreateBannerRequest) => {
  return request.post<unknown, ApiResponse<void>>('/banners', data)
}

export const updateBanner = (id: number, data: UpdateBannerRequest) => {
  return request.put<unknown, ApiResponse<void>>(`/banners/${id}`, data)
}

export const deleteBanner = (id: number) => {
  return request.delete<unknown, ApiResponse<void>>(`/banners/${id}`)
}
