# infrastructure Specification Delta: 数据清理 migration

## Added Requirements

### Requirement: V7 migration cleans orphan rankings
The system SHALL ship a Flyway migration `V7__cleanup_orphan_rankings_and_registrations.sql` that hard-deletes ranking rows whose `user_id` does not resolve to an active user, and soft-deletes + marks CANCELLED on registration rows whose `user_id` does not resolve.

#### Scenario: Migration runs on application startup
- **WHEN** the application starts and Flyway detects V7 is not yet applied
- **THEN** the migration SHALL execute the cleanup SQL

#### Scenario: Migration idempotency
- **WHEN** V7 has already run and orphan rows are reintroduced later
- **THEN** V7 SHALL NOT re-run (Flyway default) — query-layer defense in `RankingServiceImpl` covers the gap

## Added Requirements

### Requirement: Dashboard recent registrations excludes orphan users
The `AdminDashboardController.getStats` endpoint SHALL filter out registration rows whose user cannot be resolved, instead of returning a "未知" placeholder nickname.

#### Scenario: Recent registrations all valid
- **WHEN** the last 10 registrations all reference active users
- **THEN** the `recentRegistrations` array SHALL include all 10 with their real nicknames

#### Scenario: Recent registrations include orphan
- **WHEN** one of the last 10 registrations references a soft-deleted user
- **THEN** the `recentRegistrations` array SHALL exclude that row (returned list may be shorter than 10)
