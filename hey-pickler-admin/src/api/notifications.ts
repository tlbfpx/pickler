import request from './request'
import type { ApiResponse } from '@/types'

// Mirrors the backend Notification entity.
export interface NotificationItem {
  id: number
  userId: number
  type: string
  title: string
  content: string | null
  linkUrl: string | null
  readFlag: number
  createdAt: string
}

export interface NotificationPage {
  list: NotificationItem[]
  total: number
  page: number
  size: number
}

/** GET /notifications?page&size */
export const getNotifications = (params: { page?: number; size?: number }) => {
  return request.get<unknown, ApiResponse<NotificationPage>>('/notifications', { params })
}

/** GET /notifications/unread-count */
export const getUnreadCount = () => {
  return request.get<unknown, ApiResponse<{ count: number }>>('/notifications/unread-count')
}

/** POST /notifications/{id}/read?userId=userId */
export const markNotificationRead = (id: number) => {
  return request.post<unknown, ApiResponse<{ updated: boolean }>>(`/notifications/${id}/read`)
}

/** POST /notifications/read-all */
export const markAllNotificationsRead = () => {
  return request.post<unknown, ApiResponse<{ updated: number }>>('/notifications/read-all')
}
