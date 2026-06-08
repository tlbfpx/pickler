## ADDED Requirements

### Requirement: List admin users
The system SHALL provide an endpoint (`GET /api/admin/admin-users`) for SUPER_ADMIN to list all admin users with pagination.

#### Scenario: List admin users
- **WHEN** a SUPER_ADMIN sends a GET request to `/api/admin/admin-users`
- **THEN** the system SHALL return a paginated list of admin users with id, username, role, status, createdAt

#### Scenario: Non-SUPER_ADMIN access denied
- **WHEN** an ADMIN or OPERATOR sends a GET request to `/api/admin/admin-users`
- **THEN** the system SHALL return `{ "code": 403, "message": "无权限" }`

### Requirement: Create admin user
The system SHALL provide an endpoint (`POST /api/admin/admin-users`) for SUPER_ADMIN to create a new admin user. The password SHALL be stored as bcrypt hash.

#### Scenario: Create admin user
- **WHEN** a SUPER_ADMIN sends a POST with `{ "username": "operator1", "password": "secure123", "role": "OPERATOR" }`
- **THEN** the system SHALL create the admin user with bcrypt-hashed password and return `{ "code": 0, "data": { "id": <id> } }`

#### Scenario: Duplicate username
- **WHEN** the username already exists
- **THEN** the system SHALL return `{ "code": 1001, "message": "用户名已存在" }`

#### Scenario: Invalid role
- **WHEN** the role is not one of SUPER_ADMIN/ADMIN/OPERATOR
- **THEN** the system SHALL return `{ "code": 1001, "message": "无效的角色" }`

### Requirement: Get/edit admin user
The system SHALL provide endpoints (`GET/PUT /api/admin/admin-users/{id}`) for SUPER_ADMIN to view and edit admin user details (username, role, status).

#### Scenario: View admin user detail
- **WHEN** a SUPER_ADMIN sends a GET request to `/api/admin/admin-users/{id}`
- **THEN** the system SHALL return admin user details (without password hash)

#### Scenario: Edit admin user
- **WHEN** a SUPER_ADMIN sends a PUT with `{ "role": "ADMIN" }`
- **THEN** the system SHALL update the role and return `{ "code": 0 }`

#### Scenario: SUPER_ADMIN cannot demote themselves
- **WHEN** a SUPER_ADMIN attempts to change their own role
- **THEN** the system SHALL return `{ "code": 1001, "message": "不能修改自己的角色" }`

### Requirement: Reset admin password
The system SHALL provide an endpoint (`POST /api/admin/admin-users/{id}/reset-password`) for SUPER_ADMIN to reset another admin's password.

#### Scenario: Reset password
- **WHEN** a SUPER_ADMIN sends a POST with `{ "newPassword": "newSecure456" }`
- **THEN** the system SHALL update the password hash and invalidate the target admin's Redis session (force logout)
- **AND** return `{ "code": 0 }`

#### Scenario: Cannot reset own password via this endpoint
- **WHEN** a SUPER_ADMIN attempts to reset their own password
- **THEN** the system SHALL return `{ "code": 1001, "message": "不能通过此接口重置自己的密码" }`
