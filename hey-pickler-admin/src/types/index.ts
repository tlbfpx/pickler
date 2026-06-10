// ==================== User Types ====================

export interface User {
  id: number
  nickname: string | null
  avatarUrl: string | null
  city: string | null
  phone: string | null
  starPoints: number
  partyPoints: number
  starTier: string
  partyTier: string
  status: 'NORMAL' | 'BANNED'
  createdAt: string
}

// ==================== Event Types ====================

export interface Event {
  id: number
  type: 'STAR' | 'PARTY'
  title: string
  bannerUrl: string | null
  eventTime: string | null
  location: string | null
  maxParticipants: number | null
  currentParticipants: number
  fee: number
  status: 'DRAFT' | 'OPEN' | 'FULL' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED'
}

export interface CreateEventRequest {
  type: 'STAR' | 'PARTY'
  title: string
  bannerUrl: string
  eventTime: string
  location: string
  maxParticipants: number
  fee: number
}

export interface UpdateEventRequest extends CreateEventRequest {
  id: number
}

// ==================== Banner Types ====================

export interface Banner {
  id: number
  imageUrl: string
  linkUrl: string | null
  sortOrder: number
  status: string
}

export interface CreateBannerRequest {
  imageUrl: string
  linkUrl: string
  sortOrder: number
  status: string
}

export interface UpdateBannerRequest extends CreateBannerRequest {
  id: number
}

// ==================== Ranking Types ====================

export interface RankingEntry {
  rank: number
  change: number
  userId: number
  nickname: string | null
  avatarUrl: string | null
  city: string | null
  points: number
  tier: string
}

export interface PointEntryRecord {
  userId: number
  points: number
  reason: string
}

export interface EnterPointsRequest {
  records: PointEntryRecord[]
}

// ==================== Admin Types ====================

export interface Admin {
  id: number
  username: string
  role: 'SUPER_ADMIN' | 'ADMIN' | 'OPERATOR'
  status: 'ACTIVE' | 'DISABLED'
  createdAt: string
  updatedAt: string
}

export interface CreateAdminRequest {
  username: string
  password: string
  role: 'SUPER_ADMIN' | 'ADMIN' | 'OPERATOR'
}

export interface UpdateAdminRequest {
  role: 'SUPER_ADMIN' | 'ADMIN' | 'OPERATOR'
  status: 'ACTIVE' | 'DISABLED'
}

// ==================== Ban Types ====================

export interface BanRequest {
  reason: string
  days: number
}

export interface BanRecordItem {
  id: number
  userId: number
  userNickname: string | null
  userPhone: string | null
  operatorId: number
  operatorName: string | null
  action: 'BAN' | 'UNBAN'
  reason: string | null
  banUntil: string | null
  createdAt: string
}

// ==================== Auth Types ====================

export interface LoginRequest {
  username: string
  password: string
}

export interface LoginResponse {
  token: string
  role: string
}

// ==================== Dashboard Types ====================

export interface DashboardStats {
  totalUsers: number
  activeEvents: number
  recentRegistrations: number
}

// ==================== Common Types ====================

export interface PageResult<T> {
  total: number
  page: number
  size: number
  list: T[]
}

export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

export interface PageParams {
  page?: number
  size?: number
  keyword?: string
  type?: string
  status?: string
}

// ==================== File Upload ====================

export interface FileUploadResponse {
  url: string
}
