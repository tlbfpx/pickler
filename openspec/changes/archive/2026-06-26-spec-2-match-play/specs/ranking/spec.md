## ADDED Requirements

### Requirement: Spec 3 consumes Spec 2 match-derived standings

The ranking subsystem SHALL, in a future Spec 3 implementation, consume final per-group standings from Spec 2 to issue points via `source=PLACEMENT`. This declaration defines the input contract for that future work; it does not change ranking storage or behavior in this change.

#### Scenario: Inputs available to Spec 3
- **WHEN** Spec 3 placement logic runs for a COMPLETED event
- **THEN** it SHALL read each group's ranked participants from `GET /api/app/events/{id}/standings` (or its internal equivalent) to determine placement points