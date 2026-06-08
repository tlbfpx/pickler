import request from './request'
import type { FileUploadResponse, ApiResponse } from '@/types'

export const uploadFile = (file: File) => {
  const formData = new FormData()
  formData.append('file', file)
  return request.post<any, ApiResponse<FileUploadResponse>>('/files/upload', formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}
