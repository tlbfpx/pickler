## ADDED Requirements

### Requirement: Event status transitions IN_PROGRESS and COMPLETED

The system SHALL transition event status to `IN_PROGRESS` when matches are generated for an event (via `POST /api/admin/events/{id}/matches/generate`). The system SHALL transition to `COMPLETED` only when all matches are COMPLETED (via explicit `POST /api/admin/events/{id}/complete`). While `IN_PROGRESS`, register/cancel/reassign operations SHALL be rejected (already locked, but explicitly state the contract).

#### Scenario: Status moves to IN_PROGRESS on first match generation
- **WHEN** admin generates matches on an event
- **THEN** event status is `IN_PROGRESS`

#### Scenario: No register/cancel during IN_PROGRESS
- **WHEN** a user attempts to register or cancel for an IN_PROGRESS event
- **THEN** the system rejects with 1006 INVALID_STATUS_TRANSITION

### Requirement: Event completion gates standings consumption

Once `event.status = COMPLETED`, the system SHALL treat the event as final. Final standings (consumed by Spec 3) are derived from match data. No further match score submissions or resets are permitted.

#### Scenario: No more score submission after COMPLETED
- **WHEN** a participant tries to submit a score for a match in a COMPLETED event
- **THEN** the system rejects with 1006 INVALID_STATUS_TRANSITION

#### Scenario: No more resets after COMPLETED
- **WHEN** admin tries to reset a match in a COMPLETED event
- **THEN** the system rejects with 1006 INVALID_STATUS_TRANSITION