# infrastructure Specification

## Purpose
TBD - created by archiving change hey-pickler-backend. Update Purpose after archive.
## Requirements
### Requirement: Unified response format
All API endpoints SHALL return responses in the format `{ "code": <int>, "message": "<string>", "data": <any> }`. Success SHALL use `code: 0`. Paginated responses SHALL nest data in `{ "total", "page", "size", "list" }`.

#### Scenario: Successful single-item response
- **WHEN** any API call succeeds with a single result
- **THEN** the response SHALL be `{ "code": 0, "message": "success", "data": { ... } }`

#### Scenario: Successful paginated response
- **WHEN** any API call returns a list with pagination
- **THEN** the response SHALL be `{ "code": 0, "message": "success", "data": { "total": 100, "page": 1, "size": 20, "list": [...] } }`

### Requirement: Global exception handling
The system SHALL use a `GlobalExceptionHandler` to catch all unhandled exceptions and return structured error responses. Business exceptions (`BizException`) SHALL map to specific error codes.

#### Scenario: Business exception
- **WHEN** a `BizException` is thrown with code 1003 and message "报名已满"
- **THEN** the response SHALL be `{ "code": 1003, "message": "报名已满", "data": null }`

#### Scenario: Unexpected exception
- **WHEN** an unhandled runtime exception occurs
- **THEN** the response SHALL be `{ "code": 500, "message": "服务器内部错误", "data": null }`
- **AND** the exception SHALL be logged with full stack trace

### Requirement: Error code system
The system SHALL define a standard set of error codes: 0 (success), 401 (unauthenticated), 403 (forbidden), 404 (not found), 429 (rate limited), 1001 (validation error), 1002 (user banned), 1003 (registration full), 1004 (duplicate registration), 1005 (registration deadline passed).

#### Scenario: Error code returned
- **WHEN** any validation or business rule violation occurs
- **THEN** the system SHALL return the corresponding error code and a descriptive message in Chinese

### Requirement: Rate limiting
The system SHALL implement request rate limiting via a `RateLimitFilter` using Redis token bucket algorithm. Default limit: 60 requests per minute per IP for app endpoints, 120 per minute per admin for admin endpoints.

#### Scenario: Under rate limit
- **WHEN** a client sends requests within the rate limit
- **THEN** the system SHALL process all requests normally

#### Scenario: Rate limit exceeded
- **WHEN** a client exceeds the rate limit
- **THEN** the system SHALL return `{ "code": 429, "message": "请求过于频繁" }`

### Requirement: CORS configuration
The system SHALL configure CORS to allow admin management frontend domain only. App endpoints are accessed from WeChat mini-program and do not need CORS.

#### Scenario: Allowed admin domain
- **WHEN** a request from the configured admin domain includes CORS headers
- **THEN** the system SHALL allow the request

#### Scenario: Unknown origin
- **WHEN** a request from an unconfigured origin includes CORS headers
- **THEN** the system SHALL reject the CORS request

### Requirement: Swagger API documentation
The system SHALL expose Swagger/Knife4j API documentation at `/doc.html` in development profile. All endpoints SHALL be annotated with operation descriptions and parameter docs.

#### Scenario: Access Swagger UI
- **WHEN** the application runs with `dev` profile
- **THEN** the Swagger UI SHALL be accessible at `/doc.html` with all endpoints documented

### Requirement: AES encryption utility
The system SHALL provide an `AesUtil` for symmetric encryption/decryption of sensitive fields (e.g., phone numbers). The key SHALL be loaded from configuration, NOT hardcoded.

#### Scenario: Encrypt and decrypt phone
- **WHEN** a phone number "13800138000" is encrypted and then decrypted
- **THEN** the result SHALL be "13800138000"

### Requirement: JWT utility
The system SHALL provide a `JwtUtil` that can generate, validate, and parse JWT tokens. Tokens SHALL contain user ID and (for admin) role as claims. App tokens expire in 7 days, admin tokens in 24 hours.

#### Scenario: Generate and validate app token
- **WHEN** a token is generated for user ID 123
- **THEN** parsing the token SHALL return claims with `userId: 123`

#### Scenario: Expired token validation
- **WHEN** a token past its expiration is validated
- **THEN** the utility SHALL throw an exception indicating token expiration

### Requirement: Redis key conventions
The system SHALL use a consistent Redis key naming convention: `heypickler:<module>:<identifier>`. Examples: `heypickler:ranking:star:legend` for ranking cache, `heypickler:session:admin:<tokenId>` for admin sessions.

#### Scenario: Ranking cache key
- **WHEN** caching STAR LEGEND rankings
- **THEN** the key SHALL be `heypickler:ranking:star:legend`

### Requirement: Database migration
The system SHALL use Flyway for database migration. Initial schema SHALL be in `V1__init_schema.sql` and seed data in `V2__init_data.sql`.

#### Scenario: Fresh database setup
- **WHEN** the application starts against an empty database
- **THEN** Flyway SHALL execute V1 and V2 migrations, creating all tables and seed data (including a default SUPER_ADMIN account)

### Requirement: V6 migration marks invalid banner URLs inactive
The system SHALL ship `V6__cleanup_invalid_banner_urls.sql` that UPDATEs any banner whose `image_url` does not match `^https://[^/]+/.*\.(jpg|jpeg|png|webp|gif)(\?.*)?$` to `status = 'INACTIVE'`. This is a one-shot data cleanup; ongoing validation is enforced at the DTO + service layer.

#### Scenario: Migration runs on application startup
- **WHEN** the application starts and Flyway detects V6 is not yet applied
- **THEN** the migration SHALL execute the cleanup SQL

### Requirement: V7 migration cleans orphan rankings and registrations
The system SHALL ship `V7__cleanup_orphan_rankings_and_registrations.sql` that hard-deletes ranking rows whose `user_id` does not exist (LEFT JOIN `user` ... WHERE `u.id IS NULL`), and updates orphan registration rows to `status = 'CANCELLED'` (preserving audit; `AdminDashboardController.notIn(WITHDRAWN, CANCELLED)` then filters them out).

#### Scenario: Migration runs on application startup
- **WHEN** the application starts and Flyway detects V7 is not yet applied
- **THEN** the migration SHALL execute the cleanup SQL

#### Scenario: Migration idempotency
- **WHEN** V7 has already run and orphan rows are reintroduced later (e.g., admin hard-deletes a user directly)
- **THEN** V7 SHALL NOT re-run (Flyway default) — query-layer defense in `RankingServiceImpl` covers the gap

### Requirement: Dashboard recent registrations excludes orphan users
The `AdminDashboardController.getStats` endpoint SHALL filter out registration rows whose user cannot be resolved, instead of returning a placeholder "未知" nickname. With the V7 cleanup applied, the recent-registrations stream is expected to never encounter an orphan; if one is observed it indicates a bug and SHALL surface as a missing row (not a silent placeholder).

#### Scenario: Recent registrations all valid
- **WHEN** the last 10 registrations all reference existing users
- **THEN** the `recentRegistrations` array SHALL include all 10 with their real nicknames

#### Scenario: Recent registrations include orphan
- **WHEN** one of the last 10 registrations references a missing user
- **THEN** the `recentRegistrations` array SHALL exclude that row (returned list may be shorter than 10)

### Requirement: Async configuration
The system SHALL configure an async thread pool for ranking refresh and other background tasks. Core pool size: 2, max pool size: 4, queue capacity: 100.

#### Scenario: Async task execution
- **WHEN** a ranking refresh event is published
- **THEN** it SHALL be processed by the configured async thread pool, not the request thread

