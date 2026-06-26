## Context

Spec 1 produced format/team/grouping; Spec 2 added match play + standings. With standings computed, the missing piece is connecting placement to points. Currently `point_record` is only written by:
- Manual admin entry (`enterPoints` with `source=MANUAL`)
- Registration bonus (`source=REGISTRATION` from `PointChangeListener`)
- Check-in bonus (`source=CHECK_IN`)

`PointSource.PLACEMENT` enum exists but no code path produces it. Admin currently has to manually map rank → user → points after each event.

Spec 3 automates this: when an event transitions to COMPLETED, the system reads its per-event rank → points table, computes the final standings, and writes one `point_record` row per participant with `source=PLACEMENT`.

## Goals / Non-Goals

**Goals:**
- Admin configures per-event rank → points table before completion.
- On `MatchService.complete(eventId)`, points are issued atomically in the same transaction.
- Doubles teams split the placement points 50/50 (each member gets half).
- Default table loaded from `application.yml` when no per-event override exists.
- New admin endpoints `PUT/GET /api/admin/events/{id}/placement-points`.

**Non-Goals:**
- Cross-event/cross-group ranking (only within the event).
- Per-tier (STAR/PARTY) differential tables (single table per event; admins can set different tables per event).
- Manual re-issue after completion (one shot — re-issue is admin manual `enterPoints`).
- Player point-balance queries in this spec (already covered by RankingService).

## Decisions

### D1. JSON column for the rank → points table

**Decision: store as a JSON map `{rank: points}` in a single row per event.**

Why:
- Map access is O(1) at issuance; no JOIN.
- Admin edits the map as a single JSON document via `PUT /placement-points` body `{points: {...}}`.
- Schema stays minimal: one column.

Alternative rejected: separate `placement_point` table `(event_id, rank, points)` — more SQL for the same data.

### D2. Default table from `application.yml`

**Decision: `hey-pickler.placement.defaultPoints` Map<Integer, Integer>; admin overrides per event.**

Why:
- New events get a sensible default without admin having to remember.
- Override covers atypical events (championships, friendlies).

Alternative rejected: hard-coded in Java — not configurable without redeploy.

### D3. Issuance inside MatchService.complete transaction

**Decision: `PlacementService.issue(eventId)` is called from inside `MatchService.complete`'s `@Transactional` boundary.**

Why:
- Atomic: if any point write fails (DB issue, race), the COMPLETED transition rolls back too. No "completed but no points" inconsistency.

### D4. Doubles split 50/50

**Decision: when the participant is a team, each team member's `point_record.points = tablePoints / 2` (integer division). Each member gets a separate row.**

Why:
- Fair vs singles (a 2-person team shouldn't double the points vs two singles competing separately).
- Round-down handled by rounding UP one of the two rows if `tablePoints` is odd (so the two halves sum back to the original; e.g., 101 → 51+50).

### D5. Ranking source = current group standings

**Decision: read final standings from `MatchService.standings(eventId)`, then flatten across groups by global rank (P1 across all groups, then P2, etc.).**

Why:
- Spec 2 already produces per-group standings; we just flatten + assign points from the table.
- For doubles, identify each placement's slot A/B → team members → both get half.

### D6. Idempotency guard

**Decision: `issue` checks `SELECT COUNT(*) FROM point_record WHERE event_id=? AND source='PLACEMENT'`; if > 0, throw `INVALID_STATUS_TRANSITION` ("already issued").**

Why:
- Admin shouldn't double-issue by re-completing (no re-issue is supported).
- The completion path itself is idempotent on the match-state check, but the placement write needs its own check.

## Risks / Trade-offs

- [R1] Re-completion via manual event status change → already prevented (status transitions table excludes COMPLETED → IN_PROGRESS). 
- [R2] Admin edits `placement_points` AFTER completion → API requires event not COMPLETED to update. 
- [R3] Odd-point table values (e.g., 101) split unevenly → mitigated by D4 (51+50). 
- [R4] Large events (256 participants × 32 groups × ranks) → still < 256 `point_record` writes; cheap. 
- [R5] JSON deserialization of admin-supplied points map → use Jackson `Map<Integer, Integer>`; reject negatives via `@PositiveOrZero`.

## Migration Plan

1. **V14** `V14__placement_points.sql` — `event_placement_points (event_id PK, points JSON NOT NULL, updated_at)`.
2. **Code** — entity + mapper + service + controller + integration test.
3. **Config** — `application.yml` default points table.
4. **Deploy order** — V14 migration first (additive), then code (idempotent at boot).
5. **Rollback** — V14 is a new table; deletion is safe. Existing events without placement rows remain unaffected.

## Open Questions

- **OQ1**: Should PLACEMENT points respect the user's tier/season filter (only count toward current season tier)? — MVP yes (uses `season_code` from current season).
- **OQ2**: Admin override of PLACEMENT records? — MVP: admin can use existing `enterPoints` with `source=ADJUST` to correct errors.