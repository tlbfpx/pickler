import request from './request'
import type { ApiResponse } from '@/types'

export interface SysDictVO {
  dictCode: string
  dictName: string
  description: string
  status: string
}

export interface SysDictItemVO {
  itemKey: string
  itemLabel: string
  itemColor: string | null
  sort: number
  status: string
  extraJson: string | null
}

export interface DictBundleVO {
  version: number
  dicts: Record<string, SysDictItemVO[]>
}

export interface DictItemUpdateRequest {
  itemKey: string
  itemLabel: string
  itemColor: string | null
  sort: number
  status: string
}

export const getDictList = () =>
  request.get<unknown, ApiResponse<SysDictVO[]>>('/dict')

export const getDictItems = (dictCode: string) =>
  request.get<unknown, ApiResponse<SysDictItemVO[]>>(`/dict/${dictCode}/items`)

export const updateDictItems = (dictCode: string, items: DictItemUpdateRequest[]) =>
  request.put<unknown, ApiResponse<void>>(`/dict/${dictCode}/items`, items)

export const getDictBundle = () =>
  request.get<unknown, ApiResponse<DictBundleVO>>('/dict/bundle')

export const getDictVersion = () =>
  request.get<unknown, ApiResponse<{ version: number }>>('/dict/version')
