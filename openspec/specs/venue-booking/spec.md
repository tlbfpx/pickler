# venue-booking Specification

## Purpose
TBD - created by archiving change add-venue-booking-domain. Update Purpose after archive.
## Requirements
### Requirement: Venue and court configuration (admin)

The system SHALL expose admin endpoints (RBAC: `SUPER_ADMIN` / `ADMIN`) for full CRUD on venues and their courts:

- `venue` entity carries: `id`, `name`, `address`, `description`, `coverImage`, `status` (`OPEN` / `CLOSED`), `softDelete` (`deletedAt`), audit timestamps.
- `court` entity carries: `id`, `venueId`, `name`, `slotMinutes` (e.g., 30/60/90), `dayType` (`ALL` / `WEEKDAY` / `WEEKEND`), `status`, `nameKey` STORED GENERATED COLUMN derived from `LOWER(name)`, soft delete.
- `venue_business_hour`: per-venue per-`dayOfWeek` (1-7) `openTime` / `closeTime` rows; unique key `(venue_id, day_of_week)` (P1 dup-dayOfWeek throws `PARAM_ERROR`).
- `venue_contact`: per-venue contact rows (phone / wechat / email).
- `court_pricing_band`: per-court per-day-type pricing band rows with `startMinute` (00:00 offset minutes), `endMinute` (exclusive), `price` (fen).

The system MUST maintain `uk_venue_court_name(venue_id, name_key)` to prevent duplicate court names per venue (case-insensitive). Soft-delete then re-create same name MUST be allowed (the generated `name_key` matches the soft-deleted row).

#### Scenario: Create a venue with one business hour row per day
- **WHEN** admin POSTs `/api/admin/venues` with `name="Pickler Center"`, `address="..."`, one `businessHours` entry per day-of-week (1..7)
- **THEN** the system creates one `venue` row and seven `venue_business_hour` rows; response code 0

#### Scenario: Duplicate dayOfWeek rejected
- **WHEN** admin POSTs two `businessHours` entries with `dayOfWeek=1`
- **THEN** the system returns `PARAM_ERROR` (1001) citing the dup-dayOfWeek constraint

#### Scenario: Re-create soft-deleted court with same name
- **GIVEN** court `(venueId=V, name="A")` exists with `deletedAt != null`
- **WHEN** admin POSTs a new court with `name="A"` under the same venue
- **THEN** the new court is created (the soft-deleted row does not block uniqueness)

### Requirement: Court pricing band validation

The system SHALL validate `court_pricing_band` rows per court on write so that:
- The day-type effective set follows: weekday = `{WEEKDAY, ALL}`, weekend = `{WEEKEND, ALL}`.
- Bands within a single court+day-type MUST NOT overlap on half-open intervals `[startMinute, endMinute)`.
- Gaps (periods with no band) MUST result in slot rows with `price = null` / un-bookable (the system MUST NOT silently infer a default price).
- A band labeled `specific` (`WEEKDAY` or `WEEKEND`) MUST take precedence over `ALL` for matching slots (specific-over-ALL guard).

The `PricingBandValidator` is a pure utility class invoked from `CourtService.upsertPricingBands` BEFORE persistence; on validation failure the entire request MUST be rejected with `PARAM_ERROR`.

#### Scenario: Valid non-overlapping bands
- **WHEN** admin POSTs bands `[00:00-12:00)` weekday and `[12:00-24:00)` weekday
- **THEN** all bands are persisted; response code 0

#### Scenario: Overlapping bands rejected
- **WHEN** admin POSTs bands `[00:00-12:00)` and `[11:00-13:00)` for the same day-type
- **THEN** the system returns `PARAM_ERROR` (1001) with message identifying the overlap

#### Scenario: Gap leaves slots un-bookable
- **GIVEN** court C has weekday band `[09:00-12:00)` only
- **WHEN** user requests slots at `10:30` and at `08:00`
- **THEN** `10:30` returns `available=true, price=<band.price>`; `08:00` returns `available=false, price=null`

#### Scenario: Specific band overrides ALL
- **GIVEN** court C has ALL band `[00:00-24:00) price=80` and WEEKDAY band `[18:00-22:00) price=120`
- **WHEN** user requests weekday slot at `20:00`
- **THEN** `price = 120` (WEEKDAY specific wins)

### Requirement: Slot generation algorithm

The system SHALL compute a court's day-slot grid via `SlotCalculator` (pure utility, `@Component` injectable for Clock control) with these invariants:

- Anchor to the venue's `openTime` / `closeTime` for the requested `dayOfWeek`; skip if the venue is closed that day (return empty array).
- Each slot is a half-open interval `[t, t + slotMinutes)` measured in **minutes from local midnight**.
- Midnight guard: if a venue spans midnight (e.g., `openTime=20:00`, `closeTime=02:00`), the second-day portion wraps around; slots after midnight MUST be returned with `slotStart` on the requested `date`.
- The `now` window: slots with `slotStart + slotMinutes <= now(clock)` are filtered out (past slots are not browse-able).
- `matchBand` uses half-open matching + specific-over-ALL precedence; slots without a matching band receive `price = null` / `available = false`.

#### Scenario: Standard 60-minute slots in a 09:00-21:00 venue
- **WHEN** user requests slots for court C (`slotMinutes=60`) on a Wednesday
- **THEN** the response is 12 slots: `09:00`, `10:00`, ..., `20:00`

#### Scenario: Past slots filtered
- **GIVEN** `Clock.now() = 2026-07-24T14:30`
- **WHEN** user requests slots for `2026-07-24`
- **THEN** slots with `slotStart < 14:30` are absent (i.e., `09:00`-`14:00` excluded, `15:00` and later present if venue is open)

#### Scenario: Midnight-spanning venue
- **GIVEN** venue V has Wednesday `openTime=20:00`, `closeTime=02:00` and court C with `slotMinutes=60`
- **WHEN** user requests slots for `2026-07-22` (Wednesday)
- **THEN** slots are `20:00`, `21:00`, `22:00`, `23:00`, `00:00 (2026-07-23)`, `01:00 (2026-07-23)` — 6 slots total, with `slotStart` correctly anchored to local-midnight minutes

### Requirement: Anonymous browse endpoints (P1 read-only)

The system SHALL expose anonymous-readable endpoints under `/api/app/venues` and `/api/app/courts`:

- `GET /api/app/venues?page=&size=` — paginated active venue list (no auth required, `AppAuthFilter` bypasses these prefixes)
- `GET /api/app/venues/{id}` — venue detail (contact + business hours + courts)
- `GET /api/app/venues/{id}/courts` — list courts under a venue
- `GET /api/app/courts/{id}/slots?date=YYYY-MM-DD` — slot grid for a court on a date

All four endpoints MUST be marked `@PublicAnonymousAccess` to satisfy the D9 startup handler-annotation audit. They MUST NOT expose soft-deleted rows or internal admin fields (no `cost`, no `operator`, no `auditLogId`).

#### Scenario: Anonymous list venues
- **WHEN** unauthenticated client calls `GET /api/app/venues`
- **THEN** the system returns paginated active venues with code 0; no JWT required

#### Scenario: Slot grid for a future date
- **WHEN** user calls `GET /api/app/courts/{id}/slots?date=2026-08-01`
- **THEN** the response contains slots with `available` (bool) and `price` (fen or null); past slots filtered

### Requirement: Booking creation (CAS-protected)

The system SHALL expose `POST /api/app/bookings` (auth required: `@RequireAppUser`) creating a `Booking` row plus one `BookingSlot` row per slot the user selected.

The system MUST enforce ALL of the following in a single transaction:

- `booking_no` format: `BK{yyyyMMdd}-{4-digit daily sequence}`, sequence sourced from Redis key `booking:seq:{LocalDate}` (INCR; first 4 digits padded with leading zeros; TTL 24h).
- All requested slots MUST satisfy: `slotStart >= now(clock)` (no past slots) and `slotStart < now + bookingWindow` (where `bookingWindow` is a static future horizon; no further constraint from the spec).
- Each requested slot MUST have a matching pricing band (else `BOOKING_WINDOW_EXCEEDED` 1013).
- The user MUST have fewer than `hey-pickler.booking.max-concurrent` (default 5) non-terminal bookings (status ∈ {`CONFIRMED`}) at the time of insertion (else `USER_BOOKING_LIMIT_EXCEEDED` 1015).
- For every requested `(courtId, slotStart)` pair the system attempts `INSERT INTO booking_slot (court_id, slot_start, booking_id)`. Any duplicate-key violation triggers `SLOT_ALREADY_TAKEN` (1012). The handler MUST inspect the root-cause (`uk_court_slot` index name OR message contains `slot_start`) and reject with 1012; other `DataIntegrityViolation` cases fall back to `PARAM_ERROR` 1001.
- On success the new `Booking` row has `status='CONFIRMED'`, `priceSnapshot` JSON containing `{slotStart: price, ...}` per slot, `userId` from JWT.

#### Scenario: Happy path booking
- **WHEN** user with 0 active bookings requests 3 consecutive slots starting at `2026-08-01T15:00`
- **THEN** the system creates one `Booking` row (status `CONFIRMED`, `booking_no=BK20260801-0001`) and three `BookingSlot` rows; response code 0 with `BookingCreateResultVO {bookingNo, totalPrice}`

#### Scenario: Concurrent user wins one of two slots
- **GIVEN** user A and user B both attempt to book slot `2026-08-01T15:00` on court C
- **WHEN** both requests arrive within milliseconds
- **THEN** one succeeds (response code 0), the other returns `SLOT_ALREADY_TAKEN` (1012); the slot is occupied by exactly one user

#### Scenario: Concurrent booking exceeding max-concurrent
- **GIVEN** user U already has 5 CONFIRMED bookings
- **WHEN** U attempts a new booking
- **THEN** the system returns `USER_BOOKING_LIMIT_EXCEEDED` (1015) without writing any rows

#### Scenario: Booking without matching pricing band
- **WHEN** user requests a slot at a court/time without any pricing band
- **THEN** the system returns `BOOKING_WINDOW_EXCEEDED` (1013)

#### Scenario: Daily sequence increments
- **WHEN** user U1 creates booking at 2026-08-01 and U2 creates a second booking same day
- **THEN** U1 gets `booking_no=BK20260801-0001` and U2 gets `BK20260801-0002`

### Requirement: Booking terminal transitions (CAS-protected)

The system SHALL provide four terminal-state mutations on a `Booking`, each gated by an **atomic CAS** that MUST be the only path for state change:

- `cancelMine(userId)` — `UPDATE booking SET status='CANCELLED', cancelled_at=NOW, cancel_reason='USER' WHERE id=? AND user_id=? AND status='CONFIRMED'`. If `affectedRows==0`, throw `INVALID_STATUS_TRANSITION` (1006).
- `forceCancel(adminId, reason?)` — admin variant; same CAS shape but `user_id` filter dropped; `cancel_reason='ADMIN: <reason or null>'`.
- `complete(bookingId)` (admin) — `UPDATE booking SET status='COMPLETED', completed_at=NOW WHERE id=? AND status='CONFIRMED'`.
- `markNoShow(bookingId)` (admin) — `UPDATE booking SET status='NO_SHOW', completed_at=NOW WHERE id=? AND status='CONFIRMED'`.

For `forceCancel` / `complete` / `markNoShow` the system MUST first call `bookingMapper.selectById(id)` to distinguish:
- `id` not found → `BOOKING_NOT_FOUND` (1016)
- `id` exists but `affectedRows==0` (CAS lost) → `INVALID_STATUS_TRANSITION` (1006)

The system MUST also enforce a user-side cancellation deadline: `cancelMine` rejects with `CANCEL_DEADLINE_PASSED` (1014) when `slotStart - now(clock) < hey-pickler.booking.cancel-deadline-hours` (default 2h).

#### Scenario: User cancels their own booking
- **WHEN** user U calls `cancelMine` on their CONFIRMED booking with `slotStart - now > 2h`
- **THEN** the booking transitions to `CANCELLED`, the corresponding `BookingSlot` rows are physically deleted (freeing the `uk_court_slot` unique key)

#### Scenario: Cancel CAS loses (race with admin force-cancel)
- **GIVEN** admin has just CAS'd booking B to `CANCELLED` (force-cancel)
- **WHEN** user U's `cancelMine(B)` runs
- **THEN** `affectedRows==0`; the system returns `INVALID_STATUS_TRANSITION` (1006) and does NOT delete any `BookingSlot` (B's slots were already deleted by the admin path)

#### Scenario: Cancel past deadline rejected
- **GIVEN** booking B has `slotStart - now == 1h` (within the 2h deadline)
- **WHEN** user U calls `cancelMine(B)`
- **THEN** the system returns `CANCEL_DEADLINE_PASSED` (1014); status unchanged

#### Scenario: Force-cancel on non-existent booking
- **WHEN** admin calls `forceCancel(99999)`
- **THEN** the system returns `BOOKING_NOT_FOUND` (1016)

#### Scenario: Admin completes a CONFIRMED booking
- **WHEN** admin calls `complete(B)` on a CONFIRMED booking
- **THEN** the booking transitions to `COMPLETED` with `completed_at=NOW`; `BookingSlot` rows are NOT deleted (historical occupancy)

### Requirement: Auto-complete scheduler

The system SHALL run a Spring `@Scheduled` bean `BookingStatusScheduler` (configurable via `hey-pickler.booking.complete-cadence`, default `PT5M`) that auto-completes stale CONFIRMED bookings.

Each tick MUST execute:

```
threshold = LocalDateTime.now(clock).minusHours(hey-pickler.booking.complete-grace-hours)
UPDATE booking
   SET status='COMPLETED', completed_at=NOW
 WHERE status='CONFIRMED'
   AND slot_end < :threshold
 ORDER BY id
 LIMIT :hey-pickler.booking.complete-batch-size
```

- `slot_end` MUST be the latest `slot_start + slot_minutes` among that booking's `BookingSlot` rows (or an equivalent precomputed column).
- The `LIMIT` MUST be enforced via `LambdaUpdateWrapper.last("LIMIT N")` to bound per-tick DB write volume.
- The scheduler MUST log two distinct outcomes: "本批完成 N 条" when `N == batchSize` (more work may remain) and "本批完成 N 条(可能还有)" otherwise; both MUST include the row count.
- The `Clock` MUST be injectable so unit tests can freeze `now`.

#### Scenario: Tick auto-completes a stale booking
- **GIVEN** booking B has `slot_end = T`, current time `now = T + 3h`, `complete-grace-hours = 2h`
- **WHEN** the scheduler fires
- **THEN** B transitions to `COMPLETED` with `completed_at=now`

#### Scenario: Idempotent tick on already-COMPLETED rows
- **GIVEN** booking B is already `COMPLETED`
- **WHEN** the scheduler fires
- **THEN** B is unaffected (the CAS-equivalent WHERE clause `status='CONFIRMED'` filters it out)

#### Scenario: Batch size honored
- **GIVEN** 500 stale bookings exist, `complete-batch-size=200`
- **WHEN** the scheduler fires once
- **THEN** at most 200 are transitioned (the rest await the next tick)

### Requirement: List endpoints

The system SHALL expose two list endpoints:

- `GET /api/app/bookings?type=upcoming|history` (auth required) — returns bookings for the JWT user, sorted by `slotStart` ascending (`upcoming`) or descending (`history`); each booking carries the joined court + venue name + price snapshot sum.
- `GET /api/admin/bookings?status=&userId=&courtId=&venueId=&from=&to=&page=&size=` (admin RBAC) — paginated filterable list of all bookings, joined with user nickname + court + venue; default sort by `id DESC` (newest first).

#### Scenario: User upcoming tab
- **GIVEN** user U has CONFIRMED booking B1 (slot `2026-08-01`) and COMPLETED booking B2 (slot `2026-06-01`)
- **WHEN** user calls `GET /api/app/bookings?type=upcoming`
- **THEN** response contains B1 only, sorted ascending by `slotStart`

#### Scenario: Admin filter by status
- **WHEN** admin calls `GET /api/admin/bookings?status=COMPLETED&from=2026-07-01&to=2026-07-31`
- **THEN** the response is paginated, filtered to COMPLETED bookings in that window

### Requirement: Admin booking actions

The system SHALL provide three admin-only actions on `/api/admin/bookings/{id}`:

- `POST /api/admin/bookings/{id}/complete` — invokes `complete(id)`.
- `POST /api/admin/bookings/{id}/no-show` — invokes `markNoShow(id)`.
- `POST /api/admin/bookings/{id}/force-cancel` with body `{reason?: string}` — invokes `forceCancel(adminId, reason)`.

Each action MUST be logged to `operation_log` via the existing `OperationLogAspect` (module `BOOKING`, action derived from the URL suffix). The aspect MUST capture the operator (from request attributes), IP (X-Forwarded-For first hop), and JSON-masked params (truncated to 2000 chars).

#### Scenario: Admin force-cancel with reason
- **WHEN** admin POSTs `/api/admin/bookings/42/force-cancel` with body `{reason: "court closed for maintenance"}`
- **THEN** the booking transitions to `CANCELLED`, `cancel_reason='ADMIN: court closed for maintenance'`, and an `operation_log` row is written with `module=BOOKING, action=FORCE_CANCEL, target_id=42`

#### Scenario: Admin force-cancel without reason
- **WHEN** admin POSTs `/api/admin/bookings/42/force-cancel` with empty body
- **THEN** the booking transitions to `CANCELLED`, `cancel_reason='ADMIN: '`, and the operation log row records the empty reason

#### Scenario: Non-admin user attempts force-cancel
- **WHEN** an `app` role JWT hits `/api/admin/bookings/42/force-cancel`
- **THEN** the system returns 403 FORBIDDEN (AppAuthFilter does not bypass admin endpoints, RoleCheckAspect enforces `@RequireRole({SUPER_ADMIN, ADMIN})`)

### Requirement: Configuration & invariants

The system SHALL read the following booking properties from `hey-pickler.booking.*` (default values shown) on startup:

- `cancel-deadline-hours: 2` — minimum hours before `slotStart` for `cancelMine` to succeed.
- `max-concurrent: 5` — max active (CONFIRMED) bookings per user.
- `complete-grace-hours: 2` — hours after `slotEnd` before the scheduler auto-completes.
- `complete-cadence: PT5M` — `@Scheduled fixedDelay` for the auto-complete job.
- `complete-batch-size: 200` — per-tick LIMIT for the auto-complete UPDATE.
- `initial-delay-seconds: 30` — initial delay before the first scheduler tick.

The system SHALL fail-fast on startup if any of these properties is missing or non-numeric. Property overrides via env vars MUST follow Spring Boot relaxed binding (`HEY_PICKLER_BOOKING_CANCEL_DEADLINE_HOURS`, etc.).

#### Scenario: Property override via env var
- **GIVEN** env `HEY_PICKLER_BOOKING_MAX_CONCURRENT=10`
- **WHEN** the app starts
- **THEN** `BookingProperties.maxConcurrent == 10` and the booking limit check uses 10

### Requirement: Data integrity root-cause classification

The system SHALL distinguish `SLOT_ALREADY_TAKEN` (1012) from generic `PARAM_ERROR` (1001) inside `GlobalExceptionHandler.handleDataIntegrityViolation` based on the root-cause of `DataIntegrityViolationException`:

- IF the violation's root cause mentions the `uk_court_slot` unique index OR the column `slot_start`, THEN return `SLOT_ALREADY_TAKEN` (1012).
- OTHERWISE return `PARAM_ERROR` (1001) — covering V22 `venue_business_hour` dup-dayOfWeek and other FK violations without introducing new error codes.

The classification MUST happen on the unwrapped `SQLException` / `SQLIntegrityConstraintViolationException` cause chain, not on the message text alone (defends against future driver message changes).

#### Scenario: Concurrent insert collision
- **GIVEN** two concurrent inserts target `(courtId=C, slotStart=T)`
- **WHEN** both reach `INSERT INTO booking_slot`
- **THEN** the losing insert throws `DataIntegrityViolationException` whose root cause names `uk_court_slot`; the handler maps it to 1012

#### Scenario: Unrelated integrity violation
- **GIVEN** a request triggers a non-`uk_court_slot` `DataIntegrityViolation` (e.g., FK to venue)
- **WHEN** the handler catches it
- **THEN** the response is `PARAM_ERROR` (1001) with the original message

