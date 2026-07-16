import request from './request'
import type { ApiResponse } from '@/types'

/** 品牌配置 VO（与后端 BrandVO 对齐：app 名 / slogan / logo URL / 主题色）。 */
export interface BrandVO {
  appName: string
  slogan: string | null
  logoUrl: string | null
  primaryColor: string
}

export interface BrandUpdateRequest {
  appName: string
  slogan: string
  logoUrl: string
  primaryColor: string
}

export const getBrand = () =>
  request.get<unknown, ApiResponse<BrandVO>>('/brand')

export const updateBrand = (data: BrandUpdateRequest) =>
  request.put<unknown, ApiResponse<BrandVO>>('/brand', data)
