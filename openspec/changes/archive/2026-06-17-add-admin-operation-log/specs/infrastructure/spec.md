# infrastructure Specification Delta: V8 migration + auditLogExecutor

## MODIFIED Requirements

### Requirement: Async configuration
The system SHALL configure an async thread pool for ranking refresh and other background tasks. Core pool size: 2, max pool size: 4, queue capacity: 100. A separate `auditLogExecutor` (core=2, max=4, queue=500, `DiscardOldestPolicy`) SHALL be configured for audit log writes (see `audit` capability for usage).

#### Scenario: Async task execution
- **WHEN** a ranking refresh event is published
- **THEN** it SHALL be processed by the configured async thread pool, not the request thread

#### Scenario: Audit log async execution
- **WHEN** `OperationLogService.record` is invoked from the aspect
- **THEN** it SHALL be processed by `auditLogExecutor` (thread name prefix `audit-log-`), not the request thread or the ranking executor

## ADDED Requirements

### Requirement: V8 migration creates operation_log table
The system SHALL ship `V8__add_operation_log.sql` that creates the `operation_log` table for audit logging (see `audit` capability for field requirements). The table SHALL be append-only (no `deleted_at`) with four indexes: `idx_operator_time`, `idx_module_time`, `idx_created_at`, `idx_status_time`.

#### Scenario: Migration runs on application startup
- **WHEN** the application starts and Flyway detects V8 is not yet applied
- **THEN** the migration SHALL execute the CREATE TABLE + CREATE INDEX SQL
