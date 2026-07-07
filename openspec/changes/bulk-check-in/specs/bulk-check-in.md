# Spec: BulkCheckIn API

## BulkCheckInRequest

Request body for the bulk check-in endpoint.

### Fields

- `registrationIds: List<Long>` (req, opt nullable but must be non-empty when present)
  - Min size: 1
  - Max size: 200
  - Each id must be > 0

## BulkCheckInResult

Response body for the bulk check-in endpoint.

### Fields

- `eventId: Long` (req) — echoed from path
- `requested: Integer` (req) — input list size
- `updated: Integer` (req) — count of rows that transitioned to CHECKED_IN
- `skipped: Skipped` (req) — buckets of un-updated rows
  - `notFound: List<Long>` — ids not in registration table for this event
  - `withdrawn: List<Long>` — ids whose current status is WITHDRAWN
- `updatedRegistrationIds: List<Long>` (req) — actual ids updated (subset of input)

### Invariants

- `requested = updated + skipped.notFound.size() + skipped.withdrawn.size() + (alreadyCheckedIn)`
- where `alreadyCheckedIn` = ids already in CHECKED_IN state before this call (not exposed in API for v1)
- `updated = updatedRegistrationIds.size()`

## EventService.bulkCheckIn(Long eventId, List<Long> registrationIds)

Bulk transitions registrations to CHECKED_IN.

### Behavior

- **Input**: `eventId` (positive Long), `registrationIds` (1-200 items, each > 0)
- **Output**: `BulkCheckInResult`
- **Throws**:
  - `BizException(PARAM_ERROR, "...")` when list is null/empty/size>200/any id <= 0
  - `BizException(NOT_FOUND)` when eventId does not exist or is soft-deleted
- **Side effects**:
  - Single transaction (rollback on any RuntimeException)
  - UPDATE: `registration SET status='CHECKED_IN' WHERE id IN (...)` (only on rows with status='REGISTERED')

### Algorithm

1. Validate: `registrationIds != null && !empty && size <= 200 && all > 0`; else throw PARAM_ERROR
2. `requireEvent(eventId)`; throw NOT_FOUND if missing
3. Query current state: `SELECT id, status FROM registration WHERE id IN (ids) AND event_id = eventId`
4. Classify ids:
   - not in result → `skipped.notFound`
   - status='WITHDRAWN' → `skipped.withdrawn`
   - status='CHECKED_IN' → alreadyCheckedIn (not exposed v1)
   - status='REGISTERED' → `toUpdate`
5. If `toUpdate` non-empty: `UPDATE registration SET status='CHECKED_IN' WHERE id IN (toUpdate)`. Single SQL, single round-trip.
6. Build result: `eventId`, `requested=ids.size`, `updated=toUpdate.size`, `skipped`, `updatedRegistrationIds=toUpdate`
7. Return

## AdminEventController.bulkCheckIn

REST endpoint wrapper.

### Behavior

- **Path**: `POST /api/admin/events/{eventId}/registrations/bulk-check-in`
- **Auth**: `@RequireRole({SUPER_ADMIN, ADMIN})`
- **Body validation**: `@Valid` on `BulkCheckInRequest` triggers `@NotNull`/`@Size` checks → 400 PARAM_ERROR
- **Side effects**: None beyond service call
- **Response**: `Result<BulkCheckInResult>` (200) or `Result.fail(...)` (400/404)
