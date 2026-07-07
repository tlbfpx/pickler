# Spec: EventSummaryVO 与 getEventSummary

## EventSummaryVO

A new read-only view object aggregating per-event operational metrics.

### Fields

- `eventId: Long` (req) — Event identifier
- `title: String` (req) — Event display title
- `type: String` (req) — SINGLES | DOUBLES | MIXED
- `status: String` (req) — current lifecycle status
- `eventTime: LocalDateTime` (req) — scheduled play time
- `maxParticipants: Integer` (opt) — may be null if unset
- `currentParticipants: Integer` (req) — already on Event entity
- `fillRate: Double` (req) — currentParticipants / maxParticipants; 0.0 if max is null

- `registration: RegistrationCountVO` (req) — never null, fields default 0
  - `registered: Integer`
  - `checkedIn: Integer`
  - `withdrawn: Integer`
  - `checkInRate: Double` — checkedIn / registered; 0.0 if registered is 0

- `teams: TeamCountVO` (req) — never null
  - `pending: Integer`
  - `confirmed: Integer`

- `matches: MatchCountVO` (req) — never null
  - `scheduled: Integer`
  - `inProgress: Integer`
  - `completed: Integer`

- `fees: FeeSummaryVO` (req) — never null
  - `totalCollected: Long`
  - `currency: String` — always "CNY" in v1

- `transitionableStatuses: List<String>` (req) — never null, may be empty

### Invariants

- `fillRate ∈ [0.0, 1.0]` when `maxParticipants > 0`
- `fillRate = 0.0` when `maxParticipants` is null
- `checkInRate ∈ [0.0, 1.0]` when `registered > 0`
- `checkInRate = 0.0` when `registered = 0`

## EventService.getEventSummary(Long eventId)

Returns the operational summary for an event.

### Behavior

- **Input**: eventId (positive Long)
- **Output**: `EventSummaryVO`
- **Throws**: `BizException(NOT_FOUND)` when eventId does not exist or is soft-deleted
- **Side effects**: None (read-only)

### Algorithm

1. Look up `event = eventMapper.selectById(eventId)`; throw NOT_FOUND if null or `deletedAt != null`
2. Compute `registration = countRegistrationsByStatus(eventId)`:
   - Group registrations by status (REGISTERED, CHECKED_IN, WITHDRAWN), defaulting missing statuses to 0
3. Compute `teams = countTeamsByStatus(eventId)`:
   - Group teams by status (PENDING, CONFIRMED), defaulting missing statuses to 0
4. Compute `matches = countMatchesByStatus(eventId)`:
   - Group matches by status (SCHEDULED, IN_PROGRESS, COMPLETED), defaulting missing statuses to 0
5. Compute `fillRate = maxParticipants > 0 ? (double) currentParticipants / maxParticipants : 0.0`
6. Compute `checkInRate = registered > 0 ? (double) checkedIn / registered : 0.0`
7. Compute `transitionableStatuses = StatusTransitionValidator.getAllowedTargets(event.status)`
8. Compute `totalCollected = count of registrations where status != WITHDRAWN, multiplied by event.fee`
9. Return `EventSummaryVO`

## AdminEventController.eventSummary(Long id)

REST endpoint wrapper for `EventService.getEventSummary(id)`.

### Behavior

- **Path**: `GET /api/admin/events/{id}/summary`
- **Auth**: `@RequireRole({SUPER_ADMIN, ADMIN, OPERATOR})` (matches existing event endpoints)
- **Response**: `Result<EventSummaryVO>` (success), `Result.fail(NOT_FOUND)` (404)
- **Side effects**: None
