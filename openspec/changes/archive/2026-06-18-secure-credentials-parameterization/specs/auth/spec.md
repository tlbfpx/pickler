## ADDED Requirements

### Requirement: Initial admin bootstrap from environment variables
The system SHALL bootstrap the initial SUPER_ADMIN account from environment variables when the `admin_user` table is empty. The username SHALL be read from `INITIAL_ADMIN_USERNAME` (default: `admin`), and the password SHALL be read from `INITIAL_ADMIN_PASSWORD` (no default). The password SHALL be bcrypt-hashed before persistence. If the table is empty and `INITIAL_ADMIN_PASSWORD` is missing or empty, the system SHALL refuse to start (fail-fast via `System.exit(1)`). If the table has existing rows, the system SHALL skip bootstrap silently. This replaces the previous `V2__init_data.sql` admin seed.

#### Scenario: Fresh deployment with env vars set
- **WHEN** the application starts against an empty `admin_user` table
- **AND** the `INITIAL_ADMIN_USERNAME` and `INITIAL_ADMIN_PASSWORD` env vars are both set
- **THEN** the system SHALL create a SUPER_ADMIN row with the bcrypt-hashed password
- **AND** log `Initial admin '<username>' created from env vars`

#### Scenario: Fresh deployment missing INITIAL_ADMIN_PASSWORD
- **WHEN** the application starts against an empty `admin_user` table
- **AND** `INITIAL_ADMIN_PASSWORD` is missing or empty
- **THEN** the system SHALL log a FATAL message with deployment guidance
- **AND** exit with code 1 (refusing to start)

#### Scenario: Existing admin_user table is untouched
- **WHEN** the application starts against a non-empty `admin_user` table
- **THEN** the system SHALL skip bootstrap and log at DEBUG level
- **AND** SHALL NOT modify any existing admin rows

#### Scenario: Bootstrap warns on short password
- **WHEN** the application bootstraps a new admin from `INITIAL_ADMIN_PASSWORD`
- **AND** the password length is less than 12 characters
- **THEN** the system SHALL log a WARN message recommending at least 12 characters
- **AND** SHALL still create the admin (warn, not block)

#### Scenario: Env-var bootstrap works regardless of profile
- **WHEN** the application starts with `spring.profiles.active=dev` OR `=prod`
- **AND** the table is empty
- **THEN** the bootstrap behavior SHALL be identical (both profiles use the same `AdminBootstrapper`)

## MODIFIED Requirements

### Requirement: Admin login
The system SHALL provide an endpoint (`POST /api/admin/auth/login`) that accepts `username` and `password`. The system SHALL verify credentials against `admin_user` table (bcrypt hash), store session in Redis, and return a JWT token. The initial admin SHALL be created at application startup via `AdminBootstrapper` (see "Initial admin bootstrap from environment variables"), not seeded by Flyway migration.

#### Scenario: Successful admin login
- **WHEN** a POST request is sent with valid `username` and `password`
- **AND** the admin's `status` is `ACTIVE`
- **THEN** the system SHALL return `{ "code": 0, "data": { "token": "<jwt>", "role": "<role>" } }`

#### Scenario: Invalid credentials
- **WHEN** the `username` or `password` is incorrect
- **THEN** the system SHALL return `{ "code": 1001, "message": "用户名或密码错误", "data": null }`
- **AND** SHALL log the failed attempt at WARN level (including source IP)

#### Scenario: Disabled admin account
- **WHEN** the `username` and `password` are correct
- **AND** the admin's `status` is `DISABLED`
- **THEN** the system SHALL return `{ "code": 1002, "message": "账号已被禁用", "data": null }`
