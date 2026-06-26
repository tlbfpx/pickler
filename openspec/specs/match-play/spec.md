# match-play Specification

## Purpose
比赛阶段（赛事已 IN_PROGRESS）：按分组循环赛签表生成 match、参赛者自助记分、组内排名、admin 重置与赛事完成。

## Requirements

### Requirement: Generate round-robin matches on event start

The system SHALL provide an endpoint `POST /api/admin/events/{id}/matches/generate` (admin role) that generates all round-robin matches for every group in the event. The system SHALL reject generation if the event is not `groupingLocked=true` (no matches until the roster is frozen). The endpoint SHALL clear any existing matches for the event first (idempotent re-generation).

The generation algorithm: for a group with N participants, produce N*(N-1)/2 matches (every pair plays exactly once). Each match has two slots (slotA, slotB) populated from the group_assignment rows. Participants are SINGLES users or DOUBLES/MIXED teams; slot columns store user_id OR team_id (mutually exclusive).

#### Scenario: Generate matches for a 4-person singles group
- **WHEN** admin calls generate on an event with one group containing 4 singles assignments
- **THEN** 6 matches are created (4*3/2), all with `status=SCHEDULED`, and the response lists them

#### Scenario: Generate matches for a 3-team doubles group
- **WHEN** admin calls generate on an event with one group containing 3 team assignments
- **THEN** 3 matches are created with `slot_a_team_id` and `slot_b_team_id` populated (no user_id slots)

#### Scenario: Reject generation when grouping is not locked
- **WHEN** admin calls generate on an event where `grouping_locked=false`
- **THEN** the system returns 1006 INVALID_STATUS_TRANSITION and no matches are created

#### Scenario: Idempotent re-generation clears prior matches
- **WHEN** admin calls generate twice on the same event
- **THEN** the second call removes prior matches and re-creates from current group assignments

### Requirement: Submit match score by either participant

The system SHALL provide `POST /api/app/matches/{id}/score` that accepts a JSON body with `games: [{game:1, a:21, b:15}, ...]` (1 to 3 games). The system SHALL validate that the caller is one of the two slot occupants (singles user, or any team member for doubles) OR an admin. After a successful submission, the match `status` becomes `COMPLETED`, `games_won_a`/`games_won_b` are derived server-side from the games JSON, and `submitted_by_user_id`/`submitted_at`/`completed_at` are recorded.

#### Scenario: Valid 2-0 submission by participant
- **WHEN** a participant submits `[{game:1, a:21, b:15}, {game:2, a:21, b:18}]`
- **THEN** match becomes COMPLETED with games_won_a=2, games_won_b=0

#### Scenario: Valid 2-1 submission (full BO3)
- **WHEN** a participant submits 3 games where A wins 2 and B wins 1
- **THEN** match becomes COMPLETED with games_won_a=2, games_won_b=1

#### Scenario: Reject submission by non-participant
- **WHEN** a user who is neither slot_a nor slot_b (and not admin) tries to submit
- **THEN** the system returns 403 FORBIDDEN

#### Scenario: Reject submission on already completed match
- **WHEN** a participant submits to a match already in `COMPLETED`
- **THEN** the system returns 1006 INVALID_STATUS_TRANSITION

### Requirement: Badminton game-score validation

The system SHALL validate every game in the submission against badminton (best-of-3) rules:
- Each game MUST have integer scores ≥ 0 and ≤ 30 for both sides
- The winner of each game MUST have score ≥ 21 AND score >= loser's score + 2
- The match is best-of-3: the first side to win 2 games wins the match
- If scores are inconsistent with these rules, the system returns 1001 PARAM_ERROR with a message identifying the offending game number

#### Scenario: Reject 21-20 (only 1-point margin)
- **WHEN** a game is submitted as `a:21, b:20`
- **THEN** the system rejects with PARAM_ERROR ("第N局比分需领先2分")

#### Scenario: Reject 31-29 (over 30 cap)
- **WHEN** a game is submitted as `a:31, b:29`
- **THEN** the system rejects with PARAM_ERROR ("单局最高30分")

#### Scenario: Reject invalid BO3 (no one reached 2 wins)
- **WHEN** the games_won_a/b derived from the submission do not sum to a 2-game majority
- **THEN** the system rejects with PARAM_ERROR

### Requirement: Admin can reset a match for replay

The system SHALL provide `POST /api/admin/matches/{id}/reset` (admin role) that resets a COMPLETED match back to `SCHEDULED`, clears `games`, `games_won_a`, `games_won_b`, `submitted_by_user_id`, `submitted_at`, and `completed_at`. The match becomes scoreable again.

#### Scenario: Reset a completed match
- **WHEN** admin resets a COMPLETED match
- **THEN** status returns to SCHEDULED and all score fields are null

### Requirement: List my matches in an event

The system SHALL provide `GET /api/app/events/{id}/matches` returning matches where the caller is a slot occupant (singles: matches a user_id slot; doubles: matches where the caller is member1 or member2 of a team_id slot), regardless of match status, ordered by `match.id` (creation order, approximates schedule).

#### Scenario: List matches for a singles player
- **WHEN** a user is slot_a or slot_b in 3 matches of event X
- **THEN** the response includes those 3 matches with opponent user info

#### Scenario: List matches for a doubles team member
- **WHEN** a user is in a team that plays 2 matches of event X
- **THEN** the response includes both matches with opponent team info

### Requirement: Group standings (real-time)

The system SHALL provide `GET /api/app/events/{id}/standings` returning per-group standings ranked by: (1) wins desc, (2) games-for-minus-against desc, (3) ties share the same rank. Standings are computed live from match data, not persisted. Doubles events count wins/losses per team (not per individual member).

#### Scenario: Standings with clear winner
- **WHEN** group has 3 participants and all matches COMPLETED with wins 2-1-0
- **THEN** the response ranks 2-win participant first, 1-win second, 0-win third

#### Scenario: Doubles team standings
- **WHEN** computing standings for a doubles event
- **THEN** wins/losses are counted per team (not per individual member)

### Requirement: Mark event completed

The system SHALL provide `POST /api/admin/events/{id}/complete` that transitions `event.status` to `COMPLETED` only if all matches in the event are `COMPLETED`. The system SHALL reject if any match is not COMPLETED.

#### Scenario: Complete when all matches finished
- **WHEN** admin calls complete and every match has `status=COMPLETED`
- **THEN** event status becomes COMPLETED

#### Scenario: Reject complete with unfinished matches
- **WHEN** admin calls complete and any match is SCHEDULED or IN_PROGRESS
- **THEN** the system returns PARAM_ERROR listing the unfinished count

#### Scenario: Re-completion is a no-op
- **WHEN** admin posts completion on an event that is already COMPLETED
- **THEN** the system returns success without issuing new placement rows (and without error)

### Requirement: Match status enum and JSON storage

The match entity SHALL have `status` ∈ {`SCHEDULED`, `IN_PROGRESS`, `COMPLETED`}, `games` stored as JSON (e.g., `[{"game":1,"a":21,"b":15}]`), `games_won_a` and `games_won_b` as TINYINT (0..3) derived columns. Slot A and Slot B each have exactly one of `slot_*_user_id` (singles) or `slot_*_team_id` (doubles/mixed) — enforced by application code AND DB CHECK (MySQL ≥ 8.0.16).

#### Scenario: Match row shape
- **WHEN** a match is persisted
- **THEN** it has slot_a_user_id OR slot_a_team_id set (not both, not neither); same for slot B; games JSON is valid JSON array; games_won fields reflect the games

### Requirement: Score endpoints in app (read-side)

The system SHALL expose app-side endpoints. The single mutating app endpoint is `POST /api/app/matches/{id}/score`. Read endpoints MUST include `GET /api/app/events/{id}/matches` (mine) and `GET /api/app/events/{id}/standings` (group tables). Standings are publicly readable (browsable without login). My-matches MUST require auth.

#### Scenario: My matches requires auth, standings does not
- **WHEN** an unauthenticated GET hits `/api/app/events/{id}/standings`
- **THEN** the standings data is returned (browsable)
- **WHEN** an unauthenticated GET hits `/api/app/events/{id}/matches`
- **THEN** the response is 401 (auth required)