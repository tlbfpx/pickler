// Loop-v15 — types matching backend EventSummaryVO (PR #33).
// Mirrors /hey-pickler-server/.../vo/EventSummaryVO.java.

export interface RegistrationCountVO {
  registered: number
  checkedIn: number
  withdrawn: number
  checkInRate: number
}

export interface TeamCountVO {
  pending: number
  confirmed: number
}

export interface MatchCountVO {
  scheduled: number
  inProgress: number
  completed: number
}

export interface FeeSummaryVO {
  totalCollected: number
  currency: string
}

export interface EventSummaryVO {
  eventId: number
  title: string
  type: string
  status: string
  eventTime: string
  maxParticipants: number | null
  currentParticipants: number
  fillRate: number
  registration: RegistrationCountVO
  teams: TeamCountVO
  matches: MatchCountVO
  fees: FeeSummaryVO
  transitionableStatuses: string[]
}
