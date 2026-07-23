import request from './request'
import type {
  BookingAdmin,
  BookingQuery,
  BookingForceCancelRequest,
  ApiResponse,
  PageResult
} from '@/types'

export const getBookingList = (params: BookingQuery) =>
  request.get<unknown, ApiResponse<PageResult<BookingAdmin>>>('/bookings', { params })

export const getBookingDetail = (id: number) =>
  request.get<unknown, ApiResponse<BookingAdmin>>(`/bookings/${id}`)

export const completeBooking = (id: number) =>
  request.post<unknown, ApiResponse<void>>(`/bookings/${id}/complete`)

export const noShowBooking = (id: number) =>
  request.post<unknown, ApiResponse<void>>(`/bookings/${id}/no-show`)

export const forceCancelBooking = (id: number, body?: BookingForceCancelRequest) =>
  request.post<unknown, ApiResponse<void>>(`/bookings/${id}/cancel`, body ?? {})