# dashboard Specification

## Purpose
管理后台首页（运营仪表盘）的能力契约。提供 KPI 概览、活动趋势、Top 活动排行、出席漏斗、同比环比等运营指标查询接口，支持日期范围选择与 Redis 缓存，确保管理员可基于实时数据决策而无性能抖动。

## Requirements
### Requirement: Dashboard snapshot endpoint (向后兼容 + KPI 增同比环比)

The system SHALL expose `GET /api/admin/dashboard` returning a JSON snapshot with at least the following keys: `totalUsers`, `bannedUsers`, `totalEvents`, `activeEvents`, `finishedEvents`, `totalRegistrations`, `totalRevenue` (in fen / 分), `tierDistribution` (ordered by `BRONZE → SILVER → GOLD → PLATINUM → DIAMOND → MASTER`), `recentEvents` (last N events), and `trends` (30-day daily buckets for users/registrations/events).

In addition, the snapshot SHALL **add** two new sibling **keys** for each window-comparable numeric KPI: `<key>DeltaPct` and `<key>DeltaAbs` (percentage and absolute difference against the prior 30-day window). The original `<key>` remains its raw numeric value — MUST NOT be wrapped or mutated — so the existing `DashboardView.vue` continues to work. For KPIs with no meaningful prior comparison (cumulative totals like `totalUsers`/`totalEvents`/`totalRegistrations`/`totalRevenue`) the system SHALL return `deltaPct = null, deltaAbs = null` — MUST NOT throw or return 500.

The endpoint MUST require one of the roles `{SUPER_ADMIN, ADMIN, OPERATOR}` (via existing `@RequireRole`). The endpoint MUST remain backward-compatible — every existing top-level key MUST remain present and unchanged in shape.

#### Scenario: First-time deploy (no prior window)
- **WHEN** admin calls `GET /api/admin/dashboard` and there is no registration / user before the system started
- **THEN** the response has `code: 0` and every KPI carries `deltaPct: null, deltaAbs: null`

#### Scenario: All existing keys present
- **WHEN** admin calls `GET /api/admin/dashboard`
- **THEN** the response body MUST contain every key documented above and MUST not break the existing `DashboardView.vue` rendering

#### Scenario: RBAC
- **WHEN** a non-admin user (e.g., app role) calls `GET /api/admin/dashboard`
- **THEN** the system returns 403 FORBIDDEN

### Requirement: Time-series trends endpoint

The system SHALL expose `GET /api/admin/dashboard/trends` accepting query parameters:
- `range` — one of `7d | 30d | 90d | thisMonth | lastMonth` (default `30d`)
- `from` / `to` — ISO dates `yyyy-MM-dd`; MUST only be used when `range=custom`

The endpoint SHALL return a JSON response containing:
- `range` — echo of the resolved window
- `buckets` — array of `{date, users, registrations, revenue, eventsCount}` objects, sorted ascending by `date`, length equal to the window size (no gaps; days with zero activity return `0`).

`revenue` SHALL be the sum of `event.fee` for `registration.status = 'REGISTERED'` joined with the event, as a `double` in yuan (元 — matches the existing `totalRevenue` field in the snapshot, which is `BigDecimal` fee rounded to 2 decimals). Cancelled / withdrawn registrations MUST be excluded.

The implementation SHALL use SQL `GROUP BY DATE(created_at)` (or equivalent) to compute all four series in at most 4 round-trips to MySQL. The forbidden pattern of N+1 per-day loops MUST NOT appear.

#### Scenario: 7-day default
- **WHEN** admin calls `GET /api/admin/dashboard/trends?range=7d`
- **THEN** the response contains 7 contiguous `buckets`, sorted by date ascending

#### Scenario: Custom range
- **WHEN** admin calls `GET /api/admin/dashboard/trends?range=custom&from=2026-07-01&to=2026-07-10`
- **THEN** the response contains exactly 10 `buckets` with inclusive endpoints

#### Scenario: Cancelled registration excluded from revenue
- **WHEN** a registration is cancelled (`status = 'WITHDRAWN'`) within the window
- **THEN** its `event.fee` SHALL NOT contribute to the `revenue` of any bucket

#### Scenario: No N+1
- **WHEN** the dashboard snapshot is computed for a 90-day window against ≥ 10 000 registrations
- **THEN** MySQL query log shows at most 4 SQL statements for the trends portion (not 90 × per-day loops)

### Requirement: Top events ranking

The system SHALL expose `GET /api/admin/dashboard/top-events` accepting query parameters:
- `metric` — one of `registrations | revenue | fillRate` (default `registrations`)
- `range` — `7d | 30d | 90d | thisMonth | lastMonth | custom` (default `30d`)
- `from` / `to` — when `range = custom`
- `limit` — integer 1..50, default `10`

The endpoint SHALL return a list of `TopEventVO` objects sorted by the chosen metric descending. Each item contains:
- `eventId`, `title`, `value` (the chosen metric), `metric` (echo), `maxParticipants` (echo), `currentParticipants` (echo).

For `metric = fillRate`, `value = currentParticipants / maxParticipants` (decimal 0..1). Events with `maxParticipants = 0` (unlimited) or `maxParticipants = null` MUST be excluded from the result set entirely (avoids divide-by-zero).

When `metric = revenue`, revenue SHALL use the same definition as in the trends requirement.

#### Scenario: Default metric = registrations
- **WHEN** admin calls `GET /api/admin/dashboard/top-events` with no metric
- **THEN** the response is sorted by `value` descending where `value` is the count of REGISTERED registrations

#### Scenario: fillRate excludes unlimited events
- **GIVEN** event A has `maxParticipants = 0` and 5 registrations
- **AND** event B has `maxParticipants = 10` and 5 registrations
- **WHEN** admin calls `GET /api/admin/dashboard/top-events?metric=fillRate`
- **THEN** event A is absent and event B appears with `value = 0.5`

#### Scenario: Limit honoured
- **WHEN** admin calls `GET /api/admin/dashboard/top-events?limit=5`
- **THEN** the response array length is at most 5

### Requirement: Attendance funnel

The system SHALL expose `GET /api/admin/dashboard/attendance` accepting the same `range` / `from` / `to` parameters as trends. The response SHALL contain:
- `range` — echo
- `registered` — count of `registration.status = 'REGISTERED'` within the window
- `checkedIn` — count of registrations in `REGISTERED` state that have a corresponding `check_in` record within the window
- `noShowRate` — `1 - checkedIn / registered`, decimal 0..1. When `registered = 0`, MUST return `null` (not `NaN` and not throw).

#### Scenario: Normal attendance
- **WHEN** out of 100 REGISTERED registrations within the window, 80 have a `check_in`
- **THEN** the response is `{registered: 100, checkedIn: 80, noShowRate: 0.2}`

#### Scenario: Zero registrations
- **WHEN** the window contains 0 registrations
- **THEN** the response is `{registered: 0, checkedIn: 0, noShowRate: null}`

### Requirement: Compare (同比环比)

The system SHALL expose `GET /api/admin/dashboard/compare` accepting:
- `metric` — one of `users | registrations | revenue | events`
- `currentRange` — one of `7d | thisMonth | lastMonth` (default `thisMonth`)
- `previousRange` — same set (default `lastMonth`)

The endpoint SHALL return a `CompareResultVO` with:
- `metric` — echo
- `current` — numeric value for the current window
- `previous` — numeric value for the prior window
- `deltaAbs` — `current − previous`
- `deltaPct` — `(current − previous) / previous`, decimal. When `previous = 0` and `current = 0`, `deltaPct = null`. When `previous = 0` and `current > 0`, `deltaPct = null` with `deltaAbs = current` (avoid divide-by-zero and avoid "Infinity%").

The system SHALL support only weekly / monthly granularity (no yearly YoY). The natural-month comparison MAY have different day counts (e.g., 28 vs 31) — the percentage is computed against the raw counts; the dashboard UI MUST surface a tooltip explaining "自然月对比，天数可能不同".

#### Scenario: Month over month
- **WHEN** current month has 120 registrations and last month had 100
- **THEN** the response is `{metric: 'registrations', current: 120, previous: 100, deltaAbs: 20, deltaPct: 0.2}`

#### Scenario: Previous zero (avoid divide by zero)
- **WHEN** current = 50 and previous = 0
- **THEN** the response has `deltaPct: null, deltaAbs: 50`

#### Scenario: Both zero
- **WHEN** current = 0 and previous = 0
- **THEN** the response has `deltaPct: null, deltaAbs: 0`

### Requirement: Redis caching + SUPER_ADMIN bypass

The system SHALL cache the response of every endpoint listed above (snapshot / trends / top-events / attendance / compare) in Redis with TTL 5 minutes. Cache keys MUST:
- Use the prefix `heypickler:dashboard:` (matches the existing Redis key namespace `heypickler:…`)
- Embed the resolved range / metric / limit so different queries don't collide

When an admin passes `?no_cache=1` query parameter, the system SHALL bypass the cache (read-through to DB) ONLY when the caller's role is `SUPER_ADMIN`. Non-SUPER_ADMIN callers passing `no_cache=1` SHALL be silently treated as a normal request (cache used; `no_cache` ignored). The system MUST NOT throw an error in either case.

The cache SHALL only be invalidated by TTL — write paths (registration / event create / event complete / user ban / etc.) MUST NOT explicitly invalidate dashboard cache keys. Staleness up to 5 minutes is documented and accepted by product.

#### Scenario: First request populates the cache
- **WHEN** admin calls `GET /api/admin/dashboard/trends?range=30d` for the first time
- **THEN** a Redis key `heypickler:dashboard:trends:30d` exists with TTL ≤ 300 seconds

#### Scenario: Second request within TTL served from cache
- **WHEN** the same admin calls the same endpoint within 5 minutes
- **THEN** MySQL receives zero queries for the trends portion (verified by query log); response is identical

#### Scenario: SUPER_ADMIN bypasses cache
- **GIVEN** `heypickler:dashboard:trends:30d` exists in Redis
- **WHEN** a SUPER_ADMIN calls `GET /api/admin/dashboard/trends?range=30d&no_cache=1`
- **THEN** MySQL receives the trends queries (cache bypassed); the cache key MAY be refreshed or left as-is

#### Scenario: Non-SUPER_ADMIN no_cache ignored
- **GIVEN** a cached key exists
- **WHEN** an OPERATOR calls `GET /api/admin/dashboard/trends?range=30d&no_cache=1`
- **THEN** the cached value is returned (MySQL sees no trends queries); no error

#### Scenario: Write paths do not invalidate cache
- **WHEN** an admin creates a new event (writes to `event` table)
- **THEN** `heypickler:dashboard:*` keys are not touched; staleness is tolerated for up to TTL

#### Scenario: TTL is fixed at 5 minutes
- **WHEN** the cache key is set
- **THEN** its TTL SHALL be 300s (5min); MUST NOT be configurable per-call

### Requirement: Authorization on all dashboard endpoints

All endpoints under `/api/admin/dashboard/**` (existing + new) SHALL require one of the roles `{SUPER_ADMIN, ADMIN, OPERATOR}` via the existing `@RequireRole` annotation / aspect-based role check. No endpoint SHALL be reachable by unauthenticated app / WeChat users.

The `no_cache=1` bypass SHALL additionally require `SUPER_ADMIN` (see "Redis caching" requirement).

#### Scenario: App user hits dashboard
- **WHEN** an authenticated WeChat app user (role from JWT = `app`) calls any `/api/admin/dashboard/**` endpoint
- **THEN** the system returns 403 FORBIDDEN