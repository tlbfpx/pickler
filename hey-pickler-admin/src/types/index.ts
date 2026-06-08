// User types
export interface User {
  id: number
  phone: string
  nickname: string
  avatar: string
  tier: 'LEGEND' | 'SUPER' | 'SHINING'
  totalPoints: number
  isBanned: boolean
  banReason: string | null
  banExpiresAt: string | null
  createdAt: string
}

export interface UserListResponse {
  users: User[]
  total: number
}

export interface BanUserRequest {
  reason: string
  durationDays: number
}

// Event types
export interface Event {
  id: number
  type: 'STAR' | 'PARTY'
  title: string
  description: string
  location: string
  eventDate: string
  registrationDeadline: string
  maxParticipants: number
  currentParticipants: number
  status: 'UPCOMING' | 'ONGOING' | 'COMPLETED' | 'CANCELLED'
  fee: number
  createdAt: string
}

export interface EventListResponse {
  events: Event[]
  total: number
}

export interface CreateEventRequest {
  type: 'STAR' | 'PARTY'
  title: string
  description: string
  location: string
  eventDate: string
  registrationDeadline: string
  maxParticipants: number
  fee: number
}

export interface UpdateEventRequest extends CreateEventRequest {
  id: number
}

// Ranking types
export interface RankingEntry {
  userId: number
  nickname: string
  avatar: string
  tier: 'LEGEND' | 'SUPER' | 'SHINING'
  totalPoints: number
  rank: number
}

export interface RankingListResponse {
  rankings: RankingEntry[]
  type: 'STAR' | 'PARTY'
}

export interface PointEntryRecord {
  userId: number
  points: number
  reason: string
}

export interface EnterPointsRequest {
  records: PointEntryRecord[]
}

// Banner types
export interface Banner {
  id: number
  title: string
  imageUrl: string
  linkUrl: string
  order: number
  isActive: boolean
  createdAt: string
}

export interface CreateBannerRequest {
  title: string
  imageUrl: string
  linkUrl: string
  order: number
  isActive: boolean
}

export interface UpdateBannerRequest extends CreateBannerRequest {
  id: number
}

// Admin types
export interface Admin {
  id: number
  username: string
  role: 'SUPER_ADMIN' | 'ADMIN'
  createdAt: string
}

export interface CreateAdminRequest {
  username: string
  password: string
  role: 'SUPER_ADMIN' | 'ADMIN'
}

export interface UpdateAdminRequest {
  username?: string
  password?: string
  role?: 'SUPER_ADMIN' | 'ADMIN'
}

// Auth types
export interface LoginRequest {
  username: string
  password: string
}

export interface LoginResponse {
  token: string
  admin: Admin
}

// Dashboard stats
export interface DashboardStats {
  totalUsers: number
  activeEvents: number
  recentRegistrations: number
}

// File upload
export interface FileUploadResponse {
  url: string
}

// API Response wrapper
export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

// Pagination params
export interface PageParams {
  page?: number
  size?: number
  keyword?: string
  type?: string
  status?: string
}
