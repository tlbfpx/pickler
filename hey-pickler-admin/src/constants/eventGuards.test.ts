import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import type { Event } from '@/types'
import {
  canEditEvent,
  canDeleteEvent,
  canCancelEvent,
  canEditPoints,
  canViewRegistrations,
  canIssuePoints,
  canRecordScore,
  canResetScore,
  canCheckIn,
  canWithdraw,
  canBulkCheckIn,
  canBulkCheckInByEvent,
  canStartGrouping,
  canUnlockGroups,
  canLockGroups,
  canGenerateMatches,
  isTeamEvent,
  canCreateTeam,
  canConfirmTeam,
  canDeclineTeam,
  canDissolveTeam
} from './eventGuards'

// 一个固定时刻,让 isTeamEvent 的 deadline 检查有可控锚点
const FROZEN_NOW = new Date('2026-07-24T12:00:00Z').getTime()

function mkEvent(overrides: Partial<Event> = {}): Event {
  return {
    id: 1,
    title: 't',
    status: 'OPEN',
    format: 'DOUBLES',
    groupingLocked: false,
    ...overrides
  } as Event
}

beforeEach(() => {
  vi.useFakeTimers()
  vi.setSystemTime(FROZEN_NOW)
})

afterEach(() => {
  vi.useRealTimers()
})

// ============ Event-level ============
describe('canEditEvent', () => {
  it('null → false', () => {
    expect(canEditEvent(null)).toBe(false)
  })

  it('CANCELLED → false', () => {
    expect(canEditEvent(mkEvent({ status: 'CANCELLED' }))).toBe(false)
  })

  it.each(['DRAFT', 'OPEN', 'FULL', 'IN_PROGRESS', 'COMPLETED'])('status=%s → true', (status) => {
    expect(canEditEvent(mkEvent({ status }))).toBe(true)
  })
})

describe('canDeleteEvent', () => {
  it('null → false', () => {
    expect(canDeleteEvent(null)).toBe(false)
  })

  it('OPEN → false (only DRAFT allowed)', () => {
    expect(canDeleteEvent(mkEvent({ status: 'OPEN' }))).toBe(false)
  })

  it('CANCELLED → false', () => {
    expect(canDeleteEvent(mkEvent({ status: 'CANCELLED' }))).toBe(false)
  })

  it('DRAFT → true', () => {
    expect(canDeleteEvent(mkEvent({ status: 'DRAFT' }))).toBe(true)
  })
})

describe('canCancelEvent', () => {
  it('null → false', () => {
    expect(canCancelEvent(null)).toBe(false)
  })

  it('COMPLETED → false', () => {
    expect(canCancelEvent(mkEvent({ status: 'COMPLETED' }))).toBe(false)
  })

  it('CANCELLED → false', () => {
    expect(canCancelEvent(mkEvent({ status: 'CANCELLED' }))).toBe(false)
  })

  it.each(['DRAFT', 'OPEN', 'FULL', 'IN_PROGRESS'])('status=%s → true', (status) => {
    expect(canCancelEvent(mkEvent({ status }))).toBe(true)
  })
})

describe('canEditPoints', () => {
  it('null → false', () => {
    expect(canEditPoints(null)).toBe(false)
  })

  it('CANCELLED → false', () => {
    expect(canEditPoints(mkEvent({ status: 'CANCELLED' }))).toBe(false)
  })

  it('COMPLETED → false', () => {
    expect(canEditPoints(mkEvent({ status: 'COMPLETED' }))).toBe(false)
  })

  it.each(['DRAFT', 'OPEN', 'FULL', 'IN_PROGRESS'])('status=%s → true', (status) => {
    expect(canEditPoints(mkEvent({ status }))).toBe(true)
  })
})

describe('canViewRegistrations', () => {
  it('null → false', () => {
    expect(canViewRegistrations(null)).toBe(false)
  })

  it('OPEN → true', () => {
    expect(canViewRegistrations(mkEvent({ status: 'OPEN' }))).toBe(true)
  })

  it('FULL → true', () => {
    expect(canViewRegistrations(mkEvent({ status: 'FULL' }))).toBe(true)
  })

  it.each(['DRAFT', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'])('status=%s → false', (status) => {
    expect(canViewRegistrations(mkEvent({ status }))).toBe(false)
  })
})

describe('canIssuePoints', () => {
  it('null → false', () => {
    expect(canIssuePoints(null)).toBe(false)
  })

  it('IN_PROGRESS → true', () => {
    expect(canIssuePoints(mkEvent({ status: 'IN_PROGRESS' }))).toBe(true)
  })

  it.each(['DRAFT', 'OPEN', 'FULL', 'COMPLETED', 'CANCELLED'])('status=%s → false', (status) => {
    expect(canIssuePoints(mkEvent({ status }))).toBe(false)
  })
})

// ============ Match-level ============
describe('canRecordScore', () => {
  it('no status → true (not completed)', () => {
    expect(canRecordScore({})).toBe(true)
  })

  it('COMPLETED → false', () => {
    expect(canRecordScore({ status: 'COMPLETED' })).toBe(false)
  })

  it('SCHEDULED → true', () => {
    expect(canRecordScore({ status: 'SCHEDULED' })).toBe(true)
  })
})

describe('canResetScore', () => {
  it('no status → false', () => {
    expect(canResetScore({})).toBe(false)
  })

  it('COMPLETED → true', () => {
    expect(canResetScore({ status: 'COMPLETED' })).toBe(true)
  })

  it('SCHEDULED → false', () => {
    expect(canResetScore({ status: 'SCHEDULED' })).toBe(false)
  })
})

// ============ Registration-level ============
describe('canCheckIn', () => {
  it('REGISTERED → true', () => {
    expect(canCheckIn({ status: 'REGISTERED' })).toBe(true)
  })

  it.each(['CHECKED_IN', 'WITHDRAWN'])('status=%s → false', (status) => {
    expect(canCheckIn({ status })).toBe(false)
  })
})

describe('canWithdraw', () => {
  it('WITHDRAWN → false', () => {
    expect(canWithdraw({ status: 'WITHDRAWN' })).toBe(false)
  })

  it.each(['REGISTERED', 'CHECKED_IN'])('status=%s → true', (status) => {
    expect(canWithdraw({ status })).toBe(true)
  })
})

describe('canBulkCheckIn', () => {
  it('empty array → false', () => {
    expect(canBulkCheckIn([])).toBe(false)
  })

  it('all REGISTERED → true', () => {
    expect(canBulkCheckIn([{ status: 'REGISTERED' }, { status: 'REGISTERED' }])).toBe(true)
  })

  it('mixed statuses → false', () => {
    expect(canBulkCheckIn([{ status: 'REGISTERED' }, { status: 'WITHDRAWN' }])).toBe(false)
  })

  it('all WITHDRAWN → false', () => {
    expect(canBulkCheckIn([{ status: 'WITHDRAWN' }, { status: 'WITHDRAWN' }])).toBe(false)
  })
})

describe('canBulkCheckInByEvent', () => {
  it('null → false', () => {
    expect(canBulkCheckInByEvent(null)).toBe(false)
  })

  it('OPEN → true', () => {
    expect(canBulkCheckInByEvent(mkEvent({ status: 'OPEN' }))).toBe(true)
  })

  it('FULL → true', () => {
    expect(canBulkCheckInByEvent(mkEvent({ status: 'FULL' }))).toBe(true)
  })

  it.each(['DRAFT', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'])('status=%s → false', (status) => {
    expect(canBulkCheckInByEvent(mkEvent({ status }))).toBe(false)
  })
})

// ============ GroupingPanel ============
describe('canStartGrouping', () => {
  it('null → false', () => {
    expect(canStartGrouping(null)).toBe(false)
  })

  it('OPEN + !locked → true', () => {
    expect(canStartGrouping(mkEvent({ status: 'OPEN', groupingLocked: false }))).toBe(true)
  })

  it('OPEN + locked → false (already locked)', () => {
    expect(canStartGrouping(mkEvent({ status: 'OPEN', groupingLocked: true }))).toBe(false)
  })

  it('DRAFT + !locked → false (status not in OPEN/FULL)', () => {
    expect(canStartGrouping(mkEvent({ status: 'DRAFT' }))).toBe(false)
  })

  it('IN_PROGRESS + !locked → false', () => {
    expect(canStartGrouping(mkEvent({ status: 'IN_PROGRESS' }))).toBe(false)
  })
})

describe('canUnlockGroups', () => {
  it('null → false', () => {
    expect(canUnlockGroups(null)).toBe(false)
  })

  it('locked → true', () => {
    expect(canUnlockGroups(mkEvent({ groupingLocked: true }))).toBe(true)
  })

  it('not locked → false', () => {
    expect(canUnlockGroups(mkEvent({ groupingLocked: false }))).toBe(false)
  })
})

describe('canLockGroups', () => {
  it('null → false', () => {
    expect(canLockGroups(null)).toBe(false)
  })

  it('not locked → true', () => {
    expect(canLockGroups(mkEvent({ groupingLocked: false }))).toBe(true)
  })

  it('already locked → false', () => {
    expect(canLockGroups(mkEvent({ groupingLocked: true }))).toBe(false)
  })
})

// ============ MatchesPanel ============
describe('canGenerateMatches', () => {
  it('null → false', () => {
    expect(canGenerateMatches(null, false)).toBe(false)
  })

  it('locked + no matches → true', () => {
    expect(canGenerateMatches(mkEvent({ groupingLocked: true }), false)).toBe(true)
  })

  it('not locked + no matches → false', () => {
    expect(canGenerateMatches(mkEvent({ groupingLocked: false }), false)).toBe(false)
  })

  it('locked + has matches → false (already generated)', () => {
    expect(canGenerateMatches(mkEvent({ groupingLocked: true }), true)).toBe(false)
  })

  it('not locked + has matches → false', () => {
    expect(canGenerateMatches(mkEvent({ groupingLocked: false }), true)).toBe(false)
  })
})

// ============ Team management ============
describe('isTeamEvent', () => {
  it('null → false', () => {
    expect(isTeamEvent(null)).toBe(false)
  })

  it('SINGLES → false (no team)', () => {
    expect(isTeamEvent(mkEvent({ format: 'SINGLES' }))).toBe(false)
  })

  it('OPEN + DOUBLES + !locked + no deadline → true', () => {
    expect(isTeamEvent(mkEvent({ format: 'DOUBLES', status: 'OPEN' }))).toBe(true)
  })

  it('MIXED + FULL + !locked → true', () => {
    expect(isTeamEvent(mkEvent({ format: 'MIXED', status: 'FULL' }))).toBe(true)
  })

  it('DRAFT status → false (not in OPEN/FULL)', () => {
    expect(isTeamEvent(mkEvent({ format: 'DOUBLES', status: 'DRAFT' }))).toBe(false)
  })

  it('IN_PROGRESS → false', () => {
    expect(isTeamEvent(mkEvent({ format: 'DOUBLES', status: 'IN_PROGRESS' }))).toBe(false)
  })

  it('locked → false', () => {
    expect(isTeamEvent(mkEvent({ format: 'DOUBLES', groupingLocked: true }))).toBe(false)
  })

  it('deadline in future → true (still valid)', () => {
    const future = new Date(FROZEN_NOW + 60_000).toISOString()
    expect(isTeamEvent(mkEvent({ format: 'DOUBLES', registrationDeadline: future }))).toBe(true)
  })

  it('deadline in past → false', () => {
    const past = new Date(FROZEN_NOW - 60_000).toISOString()
    expect(isTeamEvent(mkEvent({ format: 'DOUBLES', registrationDeadline: past }))).toBe(false)
  })

  it('deadline exactly now → true (strict <: < not ≤)', () => {
    // 边界实现细节:new Date(d).getTime() < Date.now() 是严格小于;
    // 所以 deadline == now 仍未过期,返回 true。
    const exactly = new Date(FROZEN_NOW).toISOString()
    expect(isTeamEvent(mkEvent({ format: 'DOUBLES', registrationDeadline: exactly }))).toBe(true)
  })
})

describe('canCreateTeam', () => {
  it('teamEvent=true + no existing team (null) → true', () => {
    expect(canCreateTeam(mkEvent({ format: 'DOUBLES' }), null)).toBe(true)
  })

  it('teamEvent=true + no existing team (undefined) → true', () => {
    expect(canCreateTeam(mkEvent({ format: 'DOUBLES' }), undefined)).toBe(true)
  })

  it('teamEvent=true + existing team → false', () => {
    expect(canCreateTeam(mkEvent({ format: 'DOUBLES' }), { status: 'PENDING' })).toBe(false)
  })

  it('teamEvent=false (SINGLES) → false', () => {
    expect(canCreateTeam(mkEvent({ format: 'SINGLES' }), null)).toBe(false)
  })

  it('teamEvent=false (null event) → false', () => {
    expect(canCreateTeam(null, null)).toBe(false)
  })
})

describe('canConfirmTeam', () => {
  it('teamEvent=true + team PENDING → true', () => {
    expect(canConfirmTeam(mkEvent({ format: 'DOUBLES' }), { status: 'PENDING' })).toBe(true)
  })

  it('teamEvent=true + team CONFIRMED → false (not pending)', () => {
    expect(canConfirmTeam(mkEvent({ format: 'DOUBLES' }), { status: 'CONFIRMED' })).toBe(false)
  })

  it('teamEvent=true + no team (null) → false', () => {
    expect(canConfirmTeam(mkEvent({ format: 'DOUBLES' }), null)).toBe(false)
  })

  it('teamEvent=true + no team (undefined) → false', () => {
    expect(canConfirmTeam(mkEvent({ format: 'DOUBLES' }), undefined)).toBe(false)
  })

  it('teamEvent=false (SINGLES) + team PENDING → false', () => {
    expect(canConfirmTeam(mkEvent({ format: 'SINGLES' }), { status: 'PENDING' })).toBe(false)
  })
})

describe('canDeclineTeam', () => {
  it('teamEvent=true + team PENDING → true', () => {
    expect(canDeclineTeam(mkEvent({ format: 'DOUBLES' }), { status: 'PENDING' })).toBe(true)
  })

  it('teamEvent=true + team CONFIRMED → false', () => {
    expect(canDeclineTeam(mkEvent({ format: 'DOUBLES' }), { status: 'CONFIRMED' })).toBe(false)
  })

  it('teamEvent=true + no team → false', () => {
    expect(canDeclineTeam(mkEvent({ format: 'DOUBLES' }), null)).toBe(false)
  })

  it('teamEvent=false (SINGLES) → false', () => {
    expect(canDeclineTeam(mkEvent({ format: 'SINGLES' }), { status: 'PENDING' })).toBe(false)
  })
})

describe('canDissolveTeam', () => {
  it('teamEvent=true + team CONFIRMED → true', () => {
    expect(canDissolveTeam(mkEvent({ format: 'DOUBLES' }), { status: 'CONFIRMED' })).toBe(true)
  })

  it('teamEvent=true + team PENDING → false', () => {
    expect(canDissolveTeam(mkEvent({ format: 'DOUBLES' }), { status: 'PENDING' })).toBe(false)
  })

  it('teamEvent=true + no team → false', () => {
    expect(canDissolveTeam(mkEvent({ format: 'DOUBLES' }), null)).toBe(false)
  })

  it('teamEvent=false (SINGLES) → false', () => {
    expect(canDissolveTeam(mkEvent({ format: 'SINGLES' }), { status: 'CONFIRMED' })).toBe(false)
  })

  it('teamEvent=false (locked) → false', () => {
    expect(canDissolveTeam(mkEvent({ format: 'DOUBLES', groupingLocked: true }), { status: 'CONFIRMED' })).toBe(false)
  })
})