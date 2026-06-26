## Why

Spec 2 produces final group standings on `event.status = COMPLETED`. Currently those ranks are read-only and produce no ranking effect — competitors receive no points for placing. This means the points system has no connection to actual play results; admins must manually award points via the existing `enterPoints` admin endpoint.

This change (Spec 3) closes the loop: when an event is marked completed, the system reads its group standings and writes a `point_record` row per participant per rank using the event's configured placement points table.

## What Changes

- **New `placement_points` table** — per-event rank → points table, configured by admin before completion. Stored as a JSON map keyed by rank. Defaults populated from `application.yml` (`hey-pickler.placement.defaultPoints`).
- **New `PlacementService`** — `issue(eventId)` reads the event's `placement_points`, computes standings, writes `point_record` rows with `source = PLACEMENT`, fires `PointChangeEvent`.
- **MatchService.complete() integration** — when an event transitions to COMPLETED, `PlacementService.issue` runs in the same transaction. If any point write fails, the completion is rolled back.
- **New admin endpoint** `PUT /api/admin/events/{id}/placement-points` to set the table before completion. `GET` returns current.
- **Doubles rule** — placement points are split equally between team members (P1+P2 of a team each get half), recorded as a single point_record per member.
- **No breaking changes**.

## Capabilities

### New Capabilities

- `placement`: per-event rank→points table, issue on COMPLETED, doubles-split rule.

### Modified Capabilities

- `event`: adding the requirement that on `COMPLETED` transition, placement points are issued atomically (delta — currently COMPLETED just sets status).
- `ranking`: clarifying that `point_record.source = PLACEMENT` rows are written by this spec (delta on the input contract declared in Spec 2).

## Impact

- **New entity**: `EventPlacementPoints` (eventId PK, pointsJson, updatedAt).
- **New mapper**: `EventPlacementPointsMapper`.
- **New service**: `PlacementService` + `PlacementServiceImpl`.
- **New dto**: `PlacementPointsRequest` `{points: {1:100, 2:60, 3:30, ...}}`.
- **New VO**: `PlacementPointsVO`.
- **Modified**: `MatchService.complete` (calls PlacementService), `EventService` (no change, but COMPLETED now cascades), `AdminEventController` (add placement endpoints).
- **Migration**: V14 (`V14__placement_points.sql`).
- **New config**: `hey-pickler.placement.defaultPoints` in `application.yml` (e.g., `{1:100, 2:60, 3:30, 4:15}`).
- **Tests**: `PlacementServiceImplTest` (split, table lookup, default fallback), `PlacementIntegrationTest` (complete → points visible).