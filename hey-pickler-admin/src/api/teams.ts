import request from './request'
import type { ApiResponse } from '@/types'

// Mirrors the backend TeamVO (com.heypickler.vo.TeamVO).
export interface TeamVO {
  id: number
  eventId: number
  member1UserId: number
  member2UserId: number
  member1Name: string | null
  member2Name: string | null
  name: string | null
  /** PENDING | CONFIRMED */
  status: 'PENDING' | 'CONFIRMED' | string
}

/** GET /events/{eventId}/teams */
export const listEventTeams = (eventId: number) => {
  return request.get<unknown, ApiResponse<TeamVO[]>>(`/events/${eventId}/teams`)
}

export interface CreateTeamRequest {
  captainUserId: number
  partnerUserId: number
  name?: string
}

/** POST /events/{eventId}/teams */
export const createTeam = (eventId: number, data: CreateTeamRequest) => {
  return request.post<unknown, ApiResponse<TeamVO>>(`/events/${eventId}/teams`, data)
}

export interface UserScopedTeamRequest {
  /** The user id the admin is acting for (must equal team.member2UserId). */
  userId: number
}

/** POST /teams/{teamId}/confirm */
export const confirmTeam = (teamId: number, data: UserScopedTeamRequest) => {
  return request.post<unknown, ApiResponse<TeamVO>>(`/teams/${teamId}/confirm`, data)
}

/** POST /teams/{teamId}/decline */
export const declineTeam = (teamId: number, data: UserScopedTeamRequest) => {
  return request.post<unknown, ApiResponse<void>>(`/teams/${teamId}/decline`, data)
}

/** DELETE /teams/{teamId} */
export const dissolveTeam = (teamId: number) => {
  return request.delete<unknown, ApiResponse<void>>(`/teams/${teamId}`)
}

export interface TeamInviteVO {
  teamId: number
  eventId: number
  eventTitle: string
  captainName: string
  expiresAt: string
}

/** GET /teams/{teamId}/invite */
export const getTeamInvite = (teamId: number) => {
  return request.get<unknown, ApiResponse<TeamInviteVO>>(`/teams/${teamId}/invite`)
}
