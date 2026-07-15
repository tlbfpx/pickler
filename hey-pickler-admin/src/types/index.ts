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
  /** 后端返回的中文段位名（优先展示） */
  starTierName?: string
  /** 后端返回的中文段位名（优先展示） */
  partyTierName?: string
  /** 后端返回的段位色（TierResolver.colorFor，优先展示） */
  starTierColor?: string
  /** 后端返回的段位色（TierResolver.colorFor，优先展示） */
  partyTierColor?: string
  /** 后端返回的段位图标（TierResolver.iconFor） */
  starTierIcon?: string
  /** 后端返回的段位图标（TierResolver.iconFor） */
  partyTierIcon?: string
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
  registrationDeadline: string | null
  location: string | null
  maxParticipants: number | null
  currentParticipants: number
  fee: number
  status: 'DRAFT' | 'OPEN' | 'FULL' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED'
  minPoints?: number
  format?: 'SINGLES' | 'DOUBLES' | 'MIXED' | null
  groupingLocked?: boolean
  createdByUsername?: string | null
}

export interface CreateEventRequest {
  type: 'STAR' | 'PARTY'
  title: string
  description?: string
  bannerUrl?: string
  rules?: string
  location: string
  eventTime: string
  registrationDeadline: string
  maxParticipants: number
  fee: number
  prizes?: string
  minPoints?: number
  format?: 'SINGLES' | 'DOUBLES' | 'MIXED'
  status?: 'DRAFT' | 'OPEN' | 'FULL' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED'
}

export interface UpdateEventRequest extends CreateEventRequest {
  id: number
}

// ==================== Registration Types ====================

export interface Registration {
  id: number
  userId: number
  nickname: string | null
  avatarUrl: string | null
  city: string | null
  matchType: string
  partnerId: number | null
  partnerNickname: string | null
  status: string
  createdAt: string
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
  /** 后端返回的中文段位名（优先展示） */
  tierName?: string
  /** 后端返回的段位色（TierResolver.colorFor，优先展示） */
  tierColor?: string
  /** 后端返回的段位图标（TierResolver.iconFor） */
  tierIcon?: string
}

export interface PointEntryRecord {
  userId: number
  points: number
  reason: string
}

export interface EnterPointsRequest {
  eventId?: number | null
  type?: string
  records: PointEntryRecord[]
}

// ==================== Season Types ====================

export interface Season {
  id: number
  type: 'STAR' | 'PARTY'
  code: string
  name: string
  startDate: string
  endDate: string
  /** CURRENT | ARCHIVED */
  status: string
  createdAt: string
}

export interface SeasonRankingEntry {
  rank: number
  /** 排名变化（↑/↓）；归档快照保留刷新时的值 */
  change: number
  userId: number
  nickname: string | null
  avatarUrl: string | null
  city: string | null
  points: number
  tier: string
  /** 后端返回的中文段位名（优先展示） */
  tierName?: string
  /** 后端返回的段位色（TierResolver.colorFor，优先展示） */
  tierColor?: string
  /** 后端返回的段位图标（TierResolver.iconFor） */
  tierIcon?: string
}

/**
 * 排名工作台响应：榜单分页 + 段位分布 + 赛季元信息。
 * 当前赛季与归档赛季查询统一返回此结构，前端按 seasonStatus 决定是否禁用写操作。
 */
export interface RankingPageVO {
  page: PageResult<RankingEntry>
  /** 段位分布 {BRONZE: 12, ...}，仅含有行的段位，缺失段位前端补 0 */
  tierDistribution: Record<string, number>
  /** 段位色映射 {BRONZE: #A56C2C, ...}，当前 track 全 6 档，供前端染色图例/徽章 */
  tierColorMap?: Record<string, string>
  /** 段位名映射（per-track，见习..传奇球友） */
  tierNameMap?: Record<string, string>
  /** 段位图标映射（per-track） */
  tierIconMap?: Record<string, string>
  seasonCode: string
  seasonName: string | null
  seasonStatus: 'CURRENT' | 'ARCHIVED'
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
  bannedUsers: number
  newUsersWeek: number
  totalEvents: number
  openEvents: number
  inProgressEvents: number
  totalRegistrations: number
  recentRegistrationsCount: number
  totalRevenue: number
  weeklyRevenue: number
  starTierDistribution: Record<string, number>
  partyTierDistribution: Record<string, number>
  /** 段位色映射（STAR 轨 BRONZE..MASTER），供前端 pie 染色 */
  starTierColorMap?: Record<string, string>
  /** 段位色映射（PARTY 轨 BRONZE..MASTER），供前端 pie 染色 */
  partyTierColorMap?: Record<string, string>
  /** 段位名映射（STAR 轨 per-track，供 pie/标签，避免单套 TIER_NAME fallback） */
  starTierNameMap?: Record<string, string>
  /** 段位名映射（PARTY 轨 per-track，见习..传奇球友） */
  partyTierNameMap?: Record<string, string>
  eventTypes: Record<string, number>
  dailyNewUsers: Array<{ date: string; count: number }>
  dailyRegistrations: Array<{ date: string; count: number }>
  dailyNewEvents: Array<{ date: string; count: number }>
  dailyCompletionRate: Array<{ date: string; rate: number }>
  recentRegistrations: Array<{
    id: number
    nickname: string | null
    eventTitle: string
    matchType: string
    status: string
    createdAt: string
  }>
  upcomingEvents: Array<{
    id: number
    title: string
    type: string
    eventTime: string | null
    location: string | null
    currentParticipants: number
    maxParticipants: number | null
    status: string
  }>
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
  location?: string
  startTime?: string
  endTime?: string
  matchType?: string
  /** Loop-v18 — server-supported sort key, e.g. event_time_asc/desc,
   *  created_at_asc/desc, current_participants_asc/desc. */
  sort?: string
}

// ==================== File Upload ====================

export interface FileUploadResponse {
  url: string
}

// ==================== Placement Points Types ====================

export interface PlacementPointsVO {
  /** 后端以 JSON 反序列化：{ 1: 100, 2: 60, ... }。前端用 Record<string, number> 表示。 */
  points: Record<string, number>
  /** 'default' = 用 application.yml 默认值；'custom' = 赛事已自定义。 */
  source: 'default' | 'custom'
}

export interface PlacementPointsRequest {
  points: Record<string, number>
}

// ==================== Match & Standing Types ====================

export interface GameScore { game: number; a: number; b: number }
export interface MatchItem {
  id: number; eventId: number; groupId: number
  slotAUserId: number | null; slotATeamId: number | null
  slotBUserId: number | null; slotBTeamId: number | null
  slotADisplayName: string | null; slotBDisplayName: string | null
  status: 'SCHEDULED' | 'IN_PROGRESS' | 'COMPLETED'
  games: GameScore[]; gamesWonA: number | null; gamesWonB: number | null
  submittedAt: string | null; completedAt: string | null
}
export interface StandingRow {
  participantKey: number | null; rank: number | null; wins: number | null; losses: number | null
  gamesFor: number | null; gamesAgainst: number | null; displayName: string | null
}

// ==================== Loop-v15 types ====================
export * from './event-summary'
export * from './bulk-check-in'
