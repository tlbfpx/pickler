## ADDED Requirements

### Requirement: Get user profile
The system SHALL provide an endpoint (`GET /api/app/user/profile`) that returns the authenticated user's profile including nickname, avatar, city, phone (masked), star/party points, star/party tier.

#### Scenario: Successful profile retrieval
- **WHEN** an authenticated user sends a GET request to `/api/app/user/profile`
- **THEN** the system SHALL return `{ "code": 0, "data": { "nickname", "avatarUrl", "city", "phone": "138****1234", "starPoints", "partyPoints", "starTier", "partyTier" } }`

### Requirement: Update user profile
The system SHALL provide an endpoint (`PUT /api/app/user/profile`) that allows the authenticated user to update nickname, city, and avatar URL.

#### Scenario: Successful profile update
- **WHEN** an authenticated user sends a PUT request with `{ "nickname": "新昵称", "city": "上海" }`
- **THEN** the system SHALL update the user record and return `{ "code": 0 }`

#### Scenario: Nickname too long
- **WHEN** the `nickname` exceeds 64 characters
- **THEN** the system SHALL return `{ "code": 1001, "message": "昵称长度不能超过64个字符" }`

### Requirement: Get my events
The system SHALL provide an endpoint (`GET /api/app/user/events`) that returns a paginated list of events the authenticated user has registered for or participated in.

#### Scenario: List my events
- **WHEN** an authenticated user sends a GET request to `/api/app/user/events` with optional `type` (STAR/PARTY) filter
- **THEN** the system SHALL return a paginated list of events with registration status and event details

### Requirement: Get point history
The system SHALL provide an endpoint (`GET /api/app/user/points`) that returns a paginated list of the authenticated user's point records, ordered by creation time descending.

#### Scenario: List point history
- **WHEN** an authenticated user sends a GET request to `/api/app/user/points` with optional `type` (STAR/PARTY) filter
- **THEN** the system SHALL return a paginated list of point records including event title, points, reason, and creation time

### Requirement: Admin list users
The system SHALL provide an endpoint (`GET /api/admin/users`) that returns a paginated, searchable, filterable list of users. Support search by nickname/phone, filter by tier and status.

#### Scenario: Search users by nickname
- **WHEN** an admin sends a GET request to `/api/admin/users?keyword=张`
- **THEN** the system SHALL return paginated users whose nickname contains "张"

#### Scenario: Filter banned users
- **WHEN** an admin sends a GET request to `/api/admin/users?status=BANNED`
- **THEN** the system SHALL return paginated users whose status is BANNED

### Requirement: Admin get/edit user
The system SHALL provide endpoints (`GET/PUT /api/admin/users/{id}`) for admins to view and edit user details.

#### Scenario: View user detail
- **WHEN** an admin sends a GET request to `/api/admin/users/{id}`
- **THEN** the system SHALL return full user profile including points, tier, registration history, and ban records

#### Scenario: Edit user info
- **WHEN** an admin sends a PUT request to `/api/admin/users/{id}` with updatable fields
- **THEN** the system SHALL update the user record and return `{ "code": 0 }`

### Requirement: Ban/unban user
The system SHALL provide endpoints (`POST /api/admin/users/{id}/ban` and `POST /api/admin/users/{id}/unban`) for admins to ban or unban a user. Ban SHALL set user status to BANNED, record the reason and operator. Unban SHALL restore status to NORMAL.

#### Scenario: Ban user permanently
- **WHEN** an admin sends a POST to `/api/admin/users/{id}/ban` with `{ "reason": "违规行为" }`
- **THEN** the system SHALL set `user.status` to `BANNED`, create a `ban_record` with `action=BAN` and `ban_until=NULL`
- **AND** return `{ "code": 0 }`

#### Scenario: Temporary ban
- **WHEN** an admin sends a POST to `/api/admin/users/{id}/ban` with `{ "reason": "...", "banUntil": "2026-07-01T00:00:00" }`
- **THEN** the system SHALL set `ban_until` to the specified datetime

#### Scenario: Unban user
- **WHEN** an admin sends a POST to `/api/admin/users/{id}/unban`
- **THEN** the system SHALL set `user.status` to `NORMAL`, create a `ban_record` with `action=UNBAN`
- **AND** return `{ "code": 0 }`

#### Scenario: Banned user attempts login
- **WHEN** a banned user successfully authenticates via WeChat login
- **THEN** the system SHALL return `{ "code": 1002, "message": "账号已被封禁" }`

### Requirement: Banned user API access
When a banned user makes any request to `/api/app/*` endpoints (except auth), the system SHALL reject it.

#### Scenario: Banned user accesses protected endpoint
- **WHEN** a banned user with a valid token sends a request to any `/api/app/*` endpoint (except `/api/app/auth/*`)
- **THEN** the system SHALL return `{ "code": 1002, "message": "账号已被封禁" }`
