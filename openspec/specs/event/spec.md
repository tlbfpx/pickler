# event Specification

## Purpose
TBD - created by archiving change hey-pickler-backend. Update Purpose after archive.
## Requirements
### Requirement: List events for app
The system SHALL provide an endpoint (`GET /api/app/events`) that returns a paginated list of events filtered by `type` (STAR/PARTY), `status`, with results ordered by event_time descending. Only events with `deleted_at IS NULL` SHALL be returned.

#### Scenario: List STAR events
- **WHEN** a GET request is sent to `/api/app/events?type=STAR&page=1&size=20`
- **THEN** the system SHALL return paginated STAR events with cover, title, time, location, registration status, participant count

#### Scenario: Default pagination
- **WHEN** no `page` or `size` parameters are provided
- **THEN** the system SHALL default to `page=1` and `size=20`

### Requirement: Get event detail for app
The system SHALL provide an endpoint (`GET /api/app/events/{id}`) that returns full event details including description, rules, prizes, and the current user's registration status.

#### Scenario: View event detail
- **WHEN** an authenticated user sends a GET request to `/api/app/events/{id}`
- **THEN** the system SHALL return full event info plus `myRegistrationStatus` (null if not registered, otherwise REGISTERED/CHECKED_IN/WITHDRAWN)

#### Scenario: Event not found or deleted
- **WHEN** the event ID does not exist or has been soft-deleted
- **THEN** the system SHALL return `{ "code": 404, "message": "赛事不存在" }`

### Requirement: Register for event
The system SHALL provide an endpoint (`POST /api/app/events/{id}/register`) for authenticated users to register. The system SHALL atomically increment `current_participants` only if it is less than `max_participants`.

#### Scenario: Successful registration
- **WHEN** an authenticated user sends a POST to register for an OPEN event with available slots
- **THEN** the system SHALL create a `registration` record with `status=REGISTERED`, atomically increment `current_participants`
- **AND** return `{ "code": 0 }`

#### Scenario: Event full
- **WHEN** `current_participants` equals `max_participants`
- **THEN** the system SHALL return `{ "code": 1003, "message": "报名已满" }`

#### Scenario: Duplicate registration
- **WHEN** the user already has a registration for this event (any status except WITHDRAWN)
- **THEN** the system SHALL return `{ "code": 1004, "message": "重复报名" }`

#### Scenario: Registration deadline passed
- **WHEN** the current time is after `registration_deadline`
- **THEN** the system SHALL return `{ "code": 1005, "message": "报名已截止" }`

#### Scenario: Doubles registration requires partner
- **WHEN** `match_type` is DOUBLES or MIXED
- **AND** `partner_id` is not provided
- **THEN** the system SHALL return `{ "code": 1001, "message": "双打必须指定搭档" }`

#### Scenario: Doubles partner also registered
- **WHEN** `match_type` is DOUBLES and `partner_id` is provided
- **AND** the partner is not already registered for this event
- **THEN** the system SHALL validate the partner exists and create the registration with `partner_id`

### Requirement: Cancel registration
The system SHALL provide an endpoint (`POST /api/app/events/{id}/cancel`) for users to withdraw before the registration deadline.

#### Scenario: Successful cancellation
- **WHEN** an authenticated user sends a POST to cancel before `registration_deadline`
- **AND** the user has a REGISTERED status
- **THEN** the system SHALL set registration `status=WITHDRAWN`, atomically decrement `current_participants`
- **AND** return `{ "code": 0 }`

#### Scenario: Cancellation after deadline
- **WHEN** the current time is after `registration_deadline`
- **THEN** the system SHALL return `{ "code": 1005, "message": "报名已截止，无法取消" }`

#### Scenario: Not registered
- **WHEN** the user has no registration for this event
- **THEN** the system SHALL return `{ "code": 404, "message": "未找到报名记录" }`

### Requirement: Admin list events
The system SHALL provide an endpoint (`GET /api/admin/events`) that returns a paginated list of events with filters for `type`, `status`, and date range. Includes soft-deleted events.

#### Scenario: Filter events by status
- **WHEN** an admin sends a GET request to `/api/admin/events?status=OPEN`
- **THEN** the system SHALL return paginated OPEN events

### Requirement: Admin create event
The system SHALL provide an endpoint (`POST /api/admin/events`) for admins to create a new event with all required fields. The system SHALL set `status` to `DRAFT` by default and `created_by` to the current admin's ID.

#### Scenario: Create event
- **WHEN** an admin sends a POST with `{ "type": "STAR", "title": "...", "location": "...", "eventTime": "...", ... }`
- **THEN** the system SHALL create the event with `status=DRAFT` and return `{ "code": 0, "data": { "id": <id> } }`

#### Scenario: Missing required fields
- **WHEN** required fields (title, type, eventTime, location) are missing
- **THEN** the system SHALL return `{ "code": 1001, "message": "参数校验失败" }`

### Requirement: Admin update event
The system SHALL provide an endpoint (`PUT /api/admin/events/{id}`) for admins to update event details.

#### Scenario: Update event
- **WHEN** an admin sends a PUT request with updated fields
- **THEN** the system SHALL update the event record and return `{ "code": 0 }`

### Requirement: Admin soft delete event
The system SHALL provide an endpoint (`DELETE /api/admin/events/{id}`) that sets `deleted_at` to the current timestamp instead of removing the record.

#### Scenario: Soft delete event
- **WHEN** an admin sends a DELETE request for an event
- **THEN** the system SHALL set `deleted_at` to current timestamp
- **AND** the event SHALL no longer appear in app queries

### Requirement: Event status transitions
The system SHALL enforce valid status transitions: DRAFT → OPEN → FULL/IN_PROGRESS → COMPLETED/CANCELLED.

#### Scenario: Open a draft event
- **WHEN** an admin updates event status from DRAFT to OPEN
- **THEN** the system SHALL allow the transition

#### Scenario: Invalid transition
- **WHEN** an admin attempts to change status from COMPLETED back to OPEN
- **THEN** the system SHALL return `{ "code": 1001, "message": "无效的状态变更" }`

### Requirement: Event transitions to IN_PROGRESS on first match generation

The system SHALL transition `event.status` to `IN_PROGRESS` when matches are first generated for an event (via `POST /api/admin/events/{id}/matches/generate`). Once `IN_PROGRESS`, register/cancel/reassign operations SHALL be rejected with `INVALID_STATUS_TRANSITION` (the grouping is already locked at this point, but the contract is explicitly stated for downstream consumers).

#### Scenario: Status moves to IN_PROGRESS on first match generation
- **WHEN** admin generates matches on an event
- **THEN** event status is `IN_PROGRESS`

### Requirement: Event completion gates match operations

Once `event.status = COMPLETED`, the system SHALL treat the event as final. No further match score submissions or admin resets are permitted.

#### Scenario: No more score submission after COMPLETED
- **WHEN** a participant tries to submit a score for a match in a COMPLETED event
- **THEN** the system rejects with 1006 INVALID_STATUS_TRANSITION

#### Scenario: No more resets after COMPLETED
- **WHEN** admin tries to reset a match in a COMPLETED event
- **THEN** the system rejects with 1006 INVALID_STATUS_TRANSITION

### Requirement: Per-event placement table editable only before COMPLETED

The admin endpoint for editing the placement table (`PUT /api/admin/events/{id}/placement-points`) MUST refuse updates once the event is COMPLETED.

#### Scenario: Editing placement table on a non-completed event
- **WHEN** admin PUTs a placement-points body on an event with status in {DRAFT, OPEN, FULL, IN_PROGRESS}
- **THEN** the system persists the new table

#### Scenario: Editing placement table on COMPLETED event
- **WHEN** admin PUTs placement-points on a COMPLETED event
- **THEN** the system returns 1006 INVALID_STATUS_TRANSITION

