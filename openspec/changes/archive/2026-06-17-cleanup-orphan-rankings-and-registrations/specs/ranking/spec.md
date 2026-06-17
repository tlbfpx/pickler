# ranking Specification Delta: 过滤孤儿排名

## Modified Requirements

### Requirement: Ranking list returns only resolvable users
The system SHALL exclude any ranking row whose `user_id` cannot be resolved to an active (non-soft-deleted) user from the response of `GET /api/app/rankings` and `GET /api/app/rankings/top5`.

#### Scenario: All rankings have valid users
- **WHEN** the system has 30 ranking rows and all user_ids resolve to active users
- **THEN** the response SHALL include all 30 rows

#### Scenario: Orphan ranking filtered out
- **WHEN** the system has a ranking row with `user_id = 999` and user 999 is soft-deleted or does not exist
- **THEN** the response SHALL exclude that row from the list

#### Scenario: Cache invalidated on cleanup
- **WHEN** rankings are queried after cleanup
- **THEN** the cached list SHALL also be free of orphan rows (cache is populated from filtered list)

## Added Requirements

### Requirement: Top 5 ranking excludes orphan rows
The system SHALL apply the same orphan-filter rule to `getTop5`.

#### Scenario: Top 5 with orphans
- **WHEN** 6+ ranking rows exist, 1 of which is orphan
- **THEN** `getTop5` SHALL return 5 rows, all with valid user info
