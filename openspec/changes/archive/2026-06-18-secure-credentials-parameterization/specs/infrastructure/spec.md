## ADDED Requirements

### Requirement: AES key strict length validation
The `AesUtil` SHALL validate at bean initialization (`@PostConstruct`) that the configured `AES_KEY` is exactly 16, 24, or 32 bytes (corresponding to AES-128, AES-192, AES-256). The previous behavior of silently zero-padding shorter keys SHALL be removed — it produced a false sense of security by accepting arbitrarily weak keys. If the key length is invalid, the system SHALL throw `IllegalStateException` with a message explaining valid lengths and how to generate a proper key.

#### Scenario: AES_KEY is exactly 32 bytes
- **WHEN** `AES_KEY` is set to a 32-byte value (e.g., `openssl rand -base64 32` trimmed to 32 chars)
- **THEN** the application SHALL start normally
- **AND** encryption/decryption round-trip SHALL work

#### Scenario: AES_KEY is exactly 16 bytes
- **WHEN** `AES_KEY` is set to a 16-byte value
- **THEN** the application SHALL start normally (AES-128 mode)

#### Scenario: AES_KEY is shorter than 16 bytes
- **WHEN** `AES_KEY` is set to a value of less than 16 bytes (e.g., `abc`)
- **THEN** the application SHALL fail to start with `IllegalStateException`
- **AND** the error message SHALL list valid lengths and recommend `openssl rand -base64 32`

#### Scenario: AES_KEY is between valid lengths
- **WHEN** `AES_KEY` is set to a value of 17 bytes
- **THEN** the application SHALL fail to start (only 16, 24, 32 are valid)

### Requirement: Startup profile guard
The system SHALL run a `ProfileGuard` check on `ApplicationReadyEvent` that refuses to start the application if it detects a known-insecure configuration in a production context. "Production context" is defined as either (a) `spring.profiles.active` contains `prod`, or (b) the `PROD_GUARD` env var is `true` (allowing the check to fire even when operators forget to switch the profile). The guard SHALL compare `JWT_SECRET` and `AES_KEY` against a known set of dev-only fallback values and fail-fast (exit code 2) if any match in a production context. The guard SHALL also fail-fast if `PROD_GUARD=true` AND the active profile is `dev` (likely a misconfigured prod environment).

#### Scenario: Prod profile with unique secrets
- **WHEN** the application starts with `spring.profiles.active=prod`
- **AND** `JWT_SECRET` and `AES_KEY` do not match known dev fallback values
- **THEN** the application SHALL start normally
- **AND** log `Profile guard passed (prod profile, secrets verified unique)`

#### Scenario: Prod profile with dev JWT_SECRET fallback
- **WHEN** the application starts with `spring.profiles.active=prod`
- **AND** `JWT_SECRET` is `HeyPickler2026DevSecretK3y!MustChangeInProd!!`
- **THEN** the application SHALL refuse to start (exit code 2)
- **AND** log a FATAL message with a remediation hint

#### Scenario: PROD_GUARD=true with dev profile active
- **WHEN** the application starts with `PROD_GUARD=true`
- **AND** `spring.profiles.active=dev`
- **THEN** the application SHALL refuse to start (exit code 2)
- **AND** the error message SHALL explain this looks like a misconfigured production environment

#### Scenario: Dev profile without PROD_GUARD
- **WHEN** the application starts with `spring.profiles.active=dev`
- **AND** `PROD_GUARD` is not set
- **THEN** the application SHALL start normally (dev fallback values allowed)
- **AND** log `Profile guard passed (non-prod profile, skipped strict checks)`

### Requirement: Environment variable template
The repository SHALL ship `.env.example` at the project root listing all environment variables required for production deployment. Each variable SHALL include a comment explaining its purpose and a generation command where applicable (e.g., `openssl rand -base64 48` for `JWT_SECRET`). `.env` SHALL be gitignored (already in `.gitignore`). The template SHALL cover: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`, `JWT_SECRET`, `AES_KEY`, `INITIAL_ADMIN_USERNAME`, `INITIAL_ADMIN_PASSWORD`, `WX_APPID`, `WX_SECRET`, `CORS_ADMIN_ORIGINS`, `SPRING_PROFILES_ACTIVE`, `PROD_GUARD`.

#### Scenario: New deployment uses .env.example
- **WHEN** an operator copies `.env.example` to `.env` and fills in real values
- **THEN** the application SHALL start successfully with all 15 environment variables resolved

#### Scenario: Missing required variable
- **WHEN** an operator starts the application without setting `JWT_SECRET`
- **AND** profile is `prod`
- **THEN** Spring placeholder resolution SHALL fail at startup
- **AND** the error SHALL clearly indicate which variable is missing

### Requirement: Credentials management documentation
The repository SHALL ship `docs/CREDENTIALS.md` covering: (a) first-deployment credential generation with exact commands, (b) upgrade path for existing dev environments (including `mvn flyway:repair`), (c) key rotation procedures for `JWT_SECRET` and `AES_KEY` (including impact: rotating JWT invalidates all sessions; rotating AES_KEY requires re-encrypting all PII fields), (d) emergency response procedure when a secret is suspected leaked.

#### Scenario: Operator follows first-deployment guide
- **WHEN** an operator reads `docs/CREDENTIALS.md` "First Deployment" section
- **THEN** they SHALL find exact `openssl rand` commands for each secret
- **AND** the order in which to set them (DB → Redis → Crypto → Initial admin → WeChat → CORS)

#### Scenario: Operator needs to rotate JWT_SECRET
- **WHEN** an operator reads the "JWT rotation" section
- **THEN** they SHALL find step-by-step instructions
- **AND** the impact statement: all existing admin and user sessions become invalid; everyone must re-login

#### Scenario: Operator upgrades from pre-hardening version
- **WHEN** an existing dev reads the "Upgrading" section
- **THEN** they SHALL find the `mvn flyway:repair` step (because V2 checksum changed)
- **AND** reassurance that existing admin users are preserved (AdminBootstrapper skips non-empty table)

## MODIFIED Requirements

### Requirement: Database migration
The system SHALL use Flyway for database migration. Initial schema SHALL be in `V1__init_schema.sql`. The `V2__init_data.sql` migration SHALL contain only non-sensitive reference data (enums, lookup tables) — it SHALL NOT seed any admin user, since admin credentials must be provided per-deployment via environment variables and the `AdminBootstrapper`. Subsequent migrations SHALL be incremental versions (V3__, V4__, etc.). The system SHALL support `baseline-on-migrate: true` for dev convenience.

#### Scenario: Fresh database setup
- **WHEN** the application starts against an empty database
- **THEN** Flyway SHALL execute all migration scripts (currently V1 through V8)
- **AND** NO admin user SHALL be created by migrations
- **AND** `AdminBootstrapper` SHALL create the initial admin from `INITIAL_ADMIN_PASSWORD` env var (see auth spec)
- **AND** reference data (event types, status enums) SHALL be populated by V2 if any non-sensitive seed data exists

#### Scenario: Flyway checksum mismatch on existing dev environment
- **WHEN** an existing dev environment pulls the new V2 (without admin seed)
- **THEN** Flyway SHALL fail with a checksum mismatch error
- **AND** the operator SHALL run `mvn flyway:repair` to update the checksum
- **AND** restart the application (no data loss; existing admin rows preserved)

### Requirement: Async configuration
The system SHALL configure an async thread pool for ranking refresh and other background tasks. Core pool size: 2, max pool size: 4, queue capacity: 100. A separate `auditLogExecutor` (core=2, max=4, queue=500, `DiscardOldestPolicy`) SHALL be configured for audit log writes (see `audit` capability for usage).

#### Scenario: Async task execution
- **WHEN** a ranking refresh event is published
- **THEN** it SHALL be processed by the configured async thread pool, not the request thread

#### Scenario: Audit log async execution
- **WHEN** `OperationLogService.record` is invoked from the aspect
- **THEN** it SHALL be processed by `auditLogExecutor` (thread name prefix `audit-log-`), not the request thread or the ranking executor
