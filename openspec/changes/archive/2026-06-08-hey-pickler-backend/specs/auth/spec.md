## ADDED Requirements

### Requirement: WeChat mini-program login
The system SHALL provide a login endpoint (`POST /api/app/auth/login`) that accepts a WeChat `code` from `wx.login()`. The system SHALL call WeChat `code2Session` API to exchange the code for `openid` and `session_key`. If the user exists, the system SHALL return a JWT token. If the user does not exist, the system SHALL return a response indicating phone binding is required.

#### Scenario: Existing user login
- **WHEN** a POST request is sent to `/api/app/auth/login` with a valid WeChat `code`
- **AND** the corresponding `openid` matches an existing user
- **THEN** the system SHALL return HTTP 200 with `{ "code": 0, "data": { "token": "<jwt>", "needBindPhone": false } }`

#### Scenario: New user login
- **WHEN** a POST request is sent to `/api/app/auth/login` with a valid WeChat `code`
- **AND** the corresponding `openid` does not match any existing user
- **THEN** the system SHALL create a user record with `openid`, set `status` to `NORMAL`
- **AND** return HTTP 200 with `{ "code": 0, "data": { "token": "<jwt>", "needBindPhone": true } }`

#### Scenario: Invalid WeChat code
- **WHEN** a POST request is sent to `/api/app/auth/login` with an invalid or expired `code`
- **THEN** the system SHALL return HTTP 200 with `{ "code": 1001, "message": "微信登录失败" }`

### Requirement: Phone number binding
The system SHALL provide an endpoint (`POST /api/app/auth/phone`) that accepts an encrypted phone number data from WeChat. The system SHALL decrypt it using the stored `session_key`, and update the user's `phone` field (AES encrypted in database).

#### Scenario: Successful phone binding
- **WHEN** an authenticated user sends a POST request to `/api/app/auth/phone` with valid encrypted data
- **THEN** the system SHALL decrypt the phone number, store it AES-encrypted, and return `{ "code": 0 }`

#### Scenario: Session key expired
- **WHEN** the `session_key` has expired or the encrypted data is invalid
- **THEN** the system SHALL return `{ "code": 1001, "message": "会话已过期，请重新登录" }`

### Requirement: Token refresh
The system SHALL provide an endpoint (`POST /api/app/auth/refresh`) that accepts a valid (possibly near-expiry) JWT token and returns a new token with refreshed expiry.

#### Scenario: Successful token refresh
- **WHEN** an authenticated user sends a POST request to `/api/app/auth/refresh`
- **THEN** the system SHALL return a new JWT token with expiry reset to 7 days

#### Scenario: Expired token
- **WHEN** the provided token has already expired
- **THEN** the system SHALL return `{ "code": 401, "message": "登录已过期" }`

### Requirement: Admin login
The system SHALL provide an endpoint (`POST /api/admin/auth/login`) that accepts `username` and `password`. The system SHALL verify credentials against `admin_user` table (bcrypt hash), store session in Redis, and return a JWT token.

#### Scenario: Successful admin login
- **WHEN** a POST request is sent with valid `username` and `password`
- **AND** the admin's `status` is `ACTIVE`
- **THEN** the system SHALL return `{ "code": 0, "data": { "token": "<jwt>", "role": "<role>" } }`

#### Scenario: Invalid credentials
- **WHEN** the `username` or `password` is incorrect
- **THEN** the system SHALL return `{ "code": 1001, "message": "用户名或密码错误" }`

#### Scenario: Disabled admin account
- **WHEN** the admin's `status` is `DISABLED`
- **THEN** the system SHALL return `{ "code": 1001, "message": "账号已被禁用" }`

### Requirement: JWT authentication filters
The system SHALL use two independent servlet filters: `AppAuthFilter` for `/api/app/*` and `AdminAuthFilter` for `/api/admin/*`. Each filter SHALL validate the JWT token from the `Authorization: Bearer <token>` header.

#### Scenario: Valid app token
- **WHEN** a request to `/api/app/*` includes a valid JWT token
- **THEN** the filter SHALL set the user ID in the request context and proceed

#### Scenario: Missing or invalid app token
- **WHEN** a request to `/api/app/*` has no `Authorization` header or an invalid token
- **THEN** the filter SHALL return `{ "code": 401, "message": "未登录" }`

#### Scenario: Valid admin token with Redis session
- **WHEN** a request to `/api/admin/*` includes a valid JWT token
- **AND** the token exists in Redis session store
- **THEN** the filter SHALL set the admin user ID and role in the request context and proceed

#### Scenario: Admin token not in Redis (force logged out)
- **WHEN** a request to `/api/admin/*` includes a valid JWT token
- **AND** the token is NOT in Redis session store
- **THEN** the filter SHALL return `{ "code": 401, "message": "会话已失效" }`

### Requirement: Role-based access control
The system SHALL enforce role permissions on admin endpoints: `SUPER_ADMIN` has full access, `ADMIN` cannot manage admin users, `OPERATOR` can only manage events and content.

#### Scenario: SUPER_ADMIN accesses admin user management
- **WHEN** a `SUPER_ADMIN` sends a request to `/api/admin/admin-users`
- **THEN** the system SHALL allow the request

#### Scenario: OPERATOR attempts user ban
- **WHEN** an `OPERATOR` sends a request to `/api/admin/users/{id}/ban`
- **THEN** the system SHALL return `{ "code": 403, "message": "无权限" }`
