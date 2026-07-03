import request from './request'
import type { ApiResponse, MatchItem, StandingRow, GameScore } from '@/types'

export const generateMatches = (eventId: number) =>
  request.post<unknown, ApiResponse<MatchItem[]>>(`/events/${eventId}/matches/generate`)

export const getEventMatches = (eventId: number) =>
  request.get<unknown, ApiResponse<MatchItem[][]>>(`/events/${eventId}/matches`)

export const getEventStandings = (eventId: number) =>
  request.get<unknown, ApiResponse<StandingRow[][]>>(`/events/${eventId}/standings`)

export const submitMatchScore = (matchId: number, games: GameScore[]) =>
  request.post<unknown, ApiResponse<void>>(`/matches/${matchId}/score`, { games })

export const resetMatch = (matchId: number) =>
  request.post<unknown, ApiResponse<void>>(`/matches/${matchId}/reset`)

export const completeEvent = (eventId: number) =>
  request.post<unknown, ApiResponse<void>>(`/events/${eventId}/complete`)
