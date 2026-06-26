## ADDED Requirements

### Requirement: Configure per-event rank → points table

The system SHALL provide admin endpoints `PUT /api/admin/events/{id}/placement-points` (body `{points: {1:100, 2:60, 3:30}}`) and `GET /api/admin/events/{id}/placement-points` (returns `{points: {...}, source: "default" | "custom"}`). The table MUST be set BEFORE the event transitions to COMPLETED; PUT on a COMPLETED event MUST return `INVALID_STATUS_TRANSITION`.

If no per-event table exists when issuance runs, the system MUST fall back to the `hey-pickler.placement.defaultPoints` config. The fallback MUST be visible to the admin via `GET` (returned with `source: "default"`).

All points values MUST be ≥ 0; the system MUST reject negative values with `PARAM_ERROR`.

#### Scenario: Admin sets a custom table
- **WHEN** admin PUTs `{points: {1:100, 2:60, 3:30}}` on a non-completed event
- **THEN** the response is `code: 0` and GET returns `{points: {1:100, 2:60, 3:30}, source: "custom"}`

#### Scenario: PUT on COMPLETED event rejected
- **WHEN** admin PUTs placement-points on a COMPLETED event
- **THEN** the system returns 1006 INVALID_STATUS_TRANSITION

#### Scenario: Default fallback
- **WHEN** admin GETs placement-points on a non-completed event with no per-event row
- **THEN** the response carries `source: "default"` and the values from `application.yml`

#### Scenario: Negative point rejected
- **WHEN** admin PUTs `{points: {1:-10}}`
- **THEN** the system returns 1001 PARAM_ERROR

### Requirement: Issue placement points on COMPLETED transition

The system SHALL issue `point_record` rows with `source = PLACEMENT` when an event transitions to COMPLETED. Issuance MUST happen inside the same `@Transactional` boundary as `MatchService.complete(eventId)`. If issuance fails, the COMPLETED transition MUST be rolled back.

Each issued row MUST carry: `eventId`, `userId` (or team member userId for doubles), `type` (STAR or PARTY — same as event.type), `points` (table lookup), `reason = "PLACEMENT: 赛事《<title>》第<rank>名"`, `source = "PLACEMENT"`, `seasonCode` = current season code, `operatorId` = null (system-issued).

If a `point_record` row with `event_id = X AND source = 'PLACEMENT'` already exists (re-issue attempt), the system MUST throw `INVALID_STATUS_TRANSITION` and not write any new rows.

#### Scenario: 3-player singles event
- **WHEN** an event with 3 singles participants and table `{1:100, 2:60, 3:30}` transitions to COMPLETED
- **THEN** 3 `point_record` rows are written, with points 100, 60, 30 respectively; ranks 1, 2, 3

#### Scenario: Doubles team split
- **WHEN** a doubles event's 1st-place team (members A,B) gets 100 from the table
- **THEN** two rows are written: userId=A points=50, userId=B points=50; if 100 is odd (e.g., 101), one gets ceil-half and the other floor-half (51+50)

#### Scenario: Re-completion rejected
- **WHEN** the completion path runs twice for the same event (e.g., manually retried)
- **THEN** the second attempt throws `INVALID_STATUS_TRANSITION` and does not write new rows

### Requirement: Ranks beyond the table get 0

When the global rank exceeds the configured table (e.g., table has ranks 1..4 but the event has 6 participants), the system MUST write rows with `points = 0` so every participant has a placement record (audit trail). Rows with `points = 0` are still written but produce no point change.

#### Scenario: 6 participants, table 1..4
- **WHEN** standings resolve ranks 1..6 and table only covers 1..4
- **THEN** rows for ranks 5 and 6 are written with points 0

### Requirement: Placement uses current season code

The issued rows SHALL carry `seasonCode = <code of the CURRENT season for the event.type>`. The system queries the `season` table for `status = 'CURRENT' AND type = <event.type>` and uses its `code`. If no current season exists for the type, `seasonCode` SHALL be `null` (rows still written).

#### Scenario: Current STAR season exists
- **WHEN** a STAR event is completed while a STAR season with status CURRENT exists
- **THEN** the issued rows have seasonCode = that season's code

#### Scenario: No current season
- **WHEN** the event completes and no current season exists for its type
- **THEN** the issued rows have seasonCode = null (still written)

### Requirement: Source filter for placement queries

The existing ranking query endpoint `GET /api/admin/rankings/{type}` (RANKING spec) MUST continue to surface placement points (it sums all `point_record` rows by season code). No code change required beyond the issuance path writing the rows.

#### Scenario: Placement row contributes to ranking
- **WHEN** the rankings endpoint sums `point_record.points` by user for the season
- **THEN** placement rows are included in the sum (alongside REGISTRATION / CHECK_IN / MANUAL)