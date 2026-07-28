import request from './request'
import type {
  Venue,
  VenueDetail,
  CreateVenueRequest,
  Court,
  CourtPricingBand,
  Slot,
  ApiResponse,
  PageResult
} from '@/types'

// ==================== Venue ====================

export const getVenueList = (params: { page?: number; size?: number; keyword?: string; status?: string }) =>
  request.get<unknown, ApiResponse<PageResult<Venue>>>('/venues', { params })

export const getVenueDetail = (id: number) =>
  request.get<unknown, ApiResponse<VenueDetail>>(`/venues/${id}`)

export const createVenue = (data: CreateVenueRequest) =>
  request.post<unknown, ApiResponse<{ id: number }>>('/venues', data)

export const updateVenue = (id: number, data: CreateVenueRequest) =>
  request.put<unknown, ApiResponse<void>>(`/venues/${id}`, data)

export const deleteVenue = (id: number) =>
  request.delete<unknown, ApiResponse<void>>(`/venues/${id}`)

export const replaceBusinessHours = (
  id: number,
  hours: { dayOfWeek: number; openTime?: string; closeTime?: string }[]
) => request.put<unknown, ApiResponse<void>>(`/venues/${id}/business-hours`, { hours })

// ==================== Venue Contacts ====================

export const addContact = (
  venueId: number,
  data: { type: string; value: string; label?: string; sortOrder?: number }
) => request.post<unknown, ApiResponse<{ id: number }>>(`/venues/${venueId}/contacts`, data)

export const updateContact = (
  contactId: number,
  data: { type: string; value: string; label?: string; sortOrder?: number }
) => request.put<unknown, ApiResponse<void>>(`/venues/contacts/${contactId}`, data)

export const deleteContact = (contactId: number) =>
  request.delete<unknown, ApiResponse<void>>(`/venues/contacts/${contactId}`)

// ==================== Courts ====================

export const getCourts = (venueId: number) =>
  request.get<unknown, ApiResponse<Court[]>>('/courts', { params: { venueId } })

export const createCourt = (data: Partial<Court> & { venueId: number; name: string }) =>
  request.post<unknown, ApiResponse<{ id: number }>>('/courts', data)

export const updateCourt = (id: number, data: Partial<Court> & { name: string }) =>
  request.put<unknown, ApiResponse<void>>(`/courts/${id}`, data)

export const deleteCourt = (id: number) =>
  request.delete<unknown, ApiResponse<void>>(`/courts/${id}`)

// ==================== Pricing Bands ====================

export const replacePricingBands = (courtId: number, bands: CourtPricingBand[]) =>
  request.put<unknown, ApiResponse<void>>(`/courts/${courtId}/pricing-bands`, { bands })

export const getPricingBands = (courtId: number) =>
  request.get<unknown, ApiResponse<CourtPricingBand[]>>(`/courts/${courtId}/pricing-bands`)

export const copyPricingBands = (courtId: number, from: number) =>
  request.post<unknown, ApiResponse<void>>(`/courts/${courtId}/pricing-bands/copy`, undefined, {
    params: { from }
  })

/**
 * 某场地某日的可订时段网格(匿名 app 接口,admin 定价预览复用)。
 * 注意:axios 实例 baseURL='/api/admin',此处需 { baseURL: '' } 覆盖以打到 /api/app/*。
 */
export const getCourtSlots = (courtId: number, date: string) =>
  request.get<unknown, ApiResponse<Slot[]>>(`/api/app/courts/${courtId}/slots`, {
    baseURL: '',
    params: { date }
  })
