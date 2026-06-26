## MODIFIED Requirements

### Requirement: COMPLETED transition triggers placement issuance

When `MatchService.complete(eventId)` transitions an event to `COMPLETED`, the system SHALL also call `PlacementService.issue(eventId)` in the same transaction. The COMPLETED status is persisted ONLY if placement issuance succeeds.

#### Scenario: Successful completion issues points
- **WHEN** admin completes an event and issuance succeeds
- **THEN** event.status = COMPLETED and point_record rows with source=PLACEMENT are written

#### Scenario: Issuance failure rolls back COMPLETED
- **WHEN** admin completes an event and issuance throws (e.g., race with a duplicate row)
- **THEN** event.status does NOT become COMPLETED (transaction rolls back)

### Requirement: Per-event placement table editable only before COMPLETED

The existing endpoint for editing the placement table MUST refuse updates once the event is COMPLETED.

#### Scenario: Editing placement table on a non-completed event
- **WHEN** admin PUTs a placement-points body on an event with status in {DRAFT, OPEN, FULL, IN_PROGRESS}
- **THEN** the system persists the new table

#### Scenario: Editing placement table on COMPLETED event
- **WHEN** admin PUTs placement-points on a COMPLETED event
- **THEN** the system returns 1006 INVALID_STATUS_TRANSITION

### Requirement: Event completion accepts only one attempt

The existing completion logic in `MatchService.complete` MUST short-circuit if the event is already COMPLETED (return success without re-issuing) — this spec reinforces that re-completion is a no-op, not a re-issue.

#### Scenario: Re-completion is a no-op
- **WHEN** admin posts completion on an event that is already COMPLETED
- **THEN** the system returns success without issuing new placement rows (and without error)