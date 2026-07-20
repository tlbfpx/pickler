# track Specification (delta)

## Purpose

Hey Pickler 平台的**用户行为埋点与访问日志**能力契约。Phase 2 提供 ① 登录行为记录（`login_log`，覆盖成功/失败枚举）、② 全量 API 访问日志（`access_log`）、③ 小程序客户端事件上报通道（`POST /api/app/track/event`），为后续 Phase 3 留存与漏斗分析、Phase 4 异常告警提供数据地基。

## Requirements

### Requirement: LoginLog 表 + 登录行为记录（R1）

The system SHALL persist every login attempt (success or failure) for both WeChat app users and admin users into a `login_log` table. Each row MUST contain:

- `id` — BIGINT auto-increment primary key
- `user_id` — nullable; the authenticated user's id when `channel = APP` and login succeeds; null for `ADMIN` channel or failed app logins without a bound user
- `admin_id` — nullable; the authenticated admin's id when `channel = ADMIN` and login succeeds; null otherwise
- `channel` — VARCHAR(16), MUST be one of `APP` / `ADMIN`
- `login_result` — VARCHAR(32), MUST be one of `SUCCESS` / `FAIL_PWD` / `FAIL_BANNED` / `FAIL_RATE_LIMIT` / `FAIL_INVALID_CODE` / `FAIL_OTHER`
- `error_code` — VARCHAR(64) nullable; the business error code when `login_result != SUCCESS`, null otherwise
- `ip` — VARCHAR(64) nullable; resolved from `X-Forwarded-For` first hop (fallback `request.getRemoteAddr()`), via shared `IpResolver` util
- `device_id` — VARCHAR(64) nullable; the persistent `did` from WeChat app `wx.getStorageSync`, null for admin channel
- `user_agent` — VARCHAR(256) nullable; the request's `User-Agent` header, truncated to 256 chars
- `created_at` — DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3)

Indexes MUST include `(user_id, created_at)`, `(admin_id, created_at)`, `(login_result, created_at)`.

The system SHALL write rows **asynchronously** via a dedicated `loginLogExecutor` thread pool (core=2, max=4, queue=500, `CallerRunsPolicy` for soft back-pressure). The executor MUST be independent from `auditLogExecutor` so login spikes cannot starve audit writes.

The `login_log` table MUST be append-only — no `deleted_at` column, no UPDATE statements. Audit data integrity requires rows remain immutable.

#### Scenario: Successful app login
- **WHEN** a WeChat app user authenticates successfully via `POST /api/app/auth/login`
- **THEN** a `login_log` row is written with `user_id=<authenticated id>`, `channel=APP`, `login_result=SUCCESS`, `error_code=null`

#### Scenario: Failed password on admin login
- **WHEN** an admin submits wrong password to `POST /api/admin/auth/login`
- **THEN** a `login_log` row is written with `admin_id=null`, `channel=ADMIN`, `login_result=FAIL_PWD`, `error_code=<the BizException errorCode>`

#### Scenario: Banned user attempt
- **WHEN** a user with `status = BANNED` tries to log in
- **THEN** a `login_log` row is written with `login_result=FAIL_BANNED`, `error_code=<USER_BANNED>`

#### Scenario: Rate-limited login attempt
- **WHEN** `RateLimitFilter` blocks a login request before it reaches the auth controller
- **THEN** a `login_log` row is written with `login_result=FAIL_RATE_LIMIT`, `error_code=429`

#### Scenario: Async write does not block login response
- **WHEN** the `loginLogExecutor` queue is saturated
- **THEN** the login HTTP response is sent within 200ms (the executor's `CallerRunsPolicy` ensures the row is written even under saturation; the request is not dropped)

#### Scenario: Independent from audit executor
- **WHEN** `auditLogExecutor` queue is saturated (e.g., 500 pending audit writes)
- **THEN** `loginLogExecutor` continues to accept login writes independently

### Requirement: AccessLog 表 + 全量 /api 访问记录（R2）

The system SHALL record every request to `/api/**` (both app and admin endpoints) into an `access_log` table via an `AccessLogFilter` registered with `@Order(Ordered.LOWEST_PRECEDENCE - 10)`. Each row MUST contain:

- `id` — BIGINT auto-increment primary key
- `path` — VARCHAR(256); the request URI (without query string)
- `method` — VARCHAR(8); the HTTP method
- `status_code` — INT; the HTTP response status code (200, 401, 403, 500, etc.)
- `latency_ms` — INT; the wall-clock latency from request entry to response commit
- `user_id` — nullable; the bound WeChat user id (from `AppAuthFilter` request attribute), null if anonymous or admin path
- `admin_id` — nullable; the bound admin id (from `AdminAuthFilter` request attribute), null if anonymous or app path
- `ip` — VARCHAR(64) nullable; from `IpResolver.resolveIp`
- `user_agent` — VARCHAR(256) nullable
- `created_at` — DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3)

Indexes MUST include `(user_id, created_at)`, `(path(64), created_at)`, `(status_code, created_at)`.

The filter MUST record **both successful and failed requests** — anonymous access attempts, 401/403 from auth filters, and 500 from global exception handler MUST all generate rows with `user_id=admin_id=null` and the appropriate `status_code`. The filter MUST NOT throw if the underlying `accessLogService.recordAccess(...)` fails — it MUST catch all exceptions, log at WARN level, and let the original request response pass through unchanged.

The filter MUST run regardless of `AppAuthFilter.shouldNotFilter(...)` short-circuits — anonymous browsing of `/api/app/{events,banners,rankings}` MUST also be recorded.

#### Scenario: Successful app event browse (anonymous)
- **WHEN** an unauthenticated WeChat user visits `GET /api/app/events`
- **THEN** an `access_log` row is written with `path=/api/app/events`, `method=GET`, `status_code=200`, `user_id=null`

#### Scenario: Failed auth — 401
- **WHEN** a request without valid JWT hits `GET /api/app/user/profile`
- **THEN** an `access_log` row is written with `status_code=401`, `user_id=null`

#### Scenario: Failed auth — 403
- **WHEN** an OPERATOR tries to call `DELETE /api/admin/admins/{id}` (requires SUPER_ADMIN)
- **THEN** an `access_log` row is written with `status_code=403`, `admin_id=<operator id>`, `ip=<resolved IP>`

#### Scenario: 500 exception still recorded
- **WHEN** a controller throws an unhandled exception and `GlobalExceptionHandler` returns 500
- **THEN** an `access_log` row is written with `status_code=500`, `latency_ms=<actual>`

#### Scenario: Filter failure does not break request
- **WHEN** the `accessLogService.recordAccess(...)` call throws (e.g., DB unavailable)
- **THEN** the filter catches the exception, logs at WARN, and lets the original request response proceed unchanged

#### Scenario: Latency accuracy
- **WHEN** the filter records `latency_ms` for a request that took 247ms
- **THEN** the recorded value MUST be within ±10ms of the actual wall-clock duration

### Requirement: 客户端事件上报通道 — `POST /api/app/track/event`（R3）

The system SHALL expose `POST /api/app/track/event` under `/api/app/**`, accepting a JSON body `{ name: string, props?: object, ts?: number, did?: string }`. The endpoint MUST:

- Require no separate authentication — `AppAuthFilter` automatically binds `userId` if the request carries a valid JWT; anonymous requests MUST be accepted (with `user_id=null` in the resulting access_log row)
- Persist the event by writing one `access_log` row with `path=/api/app/track/event`, `method=POST`, `status_code=200`, and `error_msg=<name>` (reusing the existing `error_msg` column to keep the schema minimal)
- Return `200 OK` with empty body within 50ms even under load — the write MUST be asynchronous (fire-and-forget)
- Reject payloads where `name` is missing, empty, or longer than 64 characters with `400 BAD_REQUEST`
- Cap `props` JSON size at 2 KB (truncate or 400 — implementation-defined; consistent within the endpoint)

#### Scenario: Anonymous launch event
- **WHEN** a WeChat app calls `POST /api/app/track/event` with `{ name: "app_launch" }` and no auth header
- **THEN** `access_log` has a row with `path=/api/app/track/event`, `status_code=200`, `error_msg=app_launch`, `user_id=null`

#### Scenario: Authenticated page view
- **WHEN** a logged-in WeChat user visits an event detail page and the app calls `POST /api/app/track/event` with `{ name: "event_view", props: { eventId: 123 } }`
- **THEN** `access_log` has a row with `user_id=<user id>`, `error_msg=event_view`

#### Scenario: Empty name rejected
- **WHEN** a client calls with `{ name: "" }`
- **THEN** the endpoint returns `400 BAD_REQUEST` and writes no log row

#### Scenario: Oversized props truncated
- **WHEN** `props` JSON exceeds 2 KB
- **THEN** the endpoint either returns `400` or truncates `props` and writes the row (consistent behavior within the endpoint)

#### Scenario: Slow DB does not block response
- **WHEN** the `loginLogExecutor` is busy
- **THEN** the `/api/app/track/event` response is sent within 50ms — the access_log write is fire-and-forget via `@Async`