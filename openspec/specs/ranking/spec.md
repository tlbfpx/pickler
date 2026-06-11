# ranking Specification

## Purpose
TBD - created by archiving change hey-pickler-backend. Update Purpose after archive.
## Requirements
### Requirement: Admin enter points
The system SHALL provide an endpoint (`POST /api/admin/events/{id}/points`) for admins to enter point records for participants after an event. Each entry SHALL contain user_id, points, and reason.

#### Scenario: Single point entry
- **WHEN** an admin sends a POST with `{ "records": [{ "userId": 1, "points": 100, "reason": "冠军" }] }`
- **THEN** the system SHALL create a `point_record`, update the user's cumulative points (star_points or party_points based on event type), recalculate tier, and trigger async ranking refresh
- **AND** return `{ "code": 0 }`

#### Scenario: Batch point entry
- **WHEN** an admin sends multiple records in one request
- **THEN** the system SHALL process all records in a single transaction

#### Scenario: Points for non-existent event
- **WHEN** the event ID does not exist
- **THEN** the system SHALL return `{ "code": 404, "message": "赛事不存在" }`

#### Scenario: Negative points
- **WHEN** points value is negative
- **THEN** the system SHALL allow it (for corrections) but SHALL NOT let cumulative points go below 0

### Requirement: Automatic tier calculation
The system SHALL automatically calculate and update user tier based on cumulative points whenever points change. Thresholds: Legend Star >= 1000, Super Star >= 500, Shining Star >= 0 (for STAR type). Same thresholds scaled for PARTY type: Legend >= 500, Super >= 200, Shining >= 0.

#### Scenario: Tier upgrade
- **WHEN** a user's star_points increases from 480 to 520
- **THEN** the system SHALL update `star_tier` from `SHINING` to `SUPER`

#### Scenario: No tier change
- **WHEN** a user's points change but remain in the same threshold bracket
- **THEN** the system SHALL keep the current tier unchanged

### Requirement: Async ranking refresh
The system SHALL asynchronously refresh the ranking snapshot after point entry. The process SHALL: group users by type and tier, sort by points descending, assign ranks, calculate rank change from previous snapshot, write to `ranking` table, and clear Redis cache.

#### Scenario: Ranking refresh after point entry
- **WHEN** point records are successfully entered
- **THEN** the system SHALL publish a ranking refresh event
- **AND** the async listener SHALL recalculate rankings within the affected type and tier

#### Scenario: Rank change calculation
- **WHEN** a user was previously rank 5 and is now rank 3
- **THEN** the `change` field SHALL be set to +2

#### Scenario: New user in rankings
- **WHEN** a user appears in rankings for the first time
- **THEN** the `change` field SHALL be set to 0

### Requirement: Get rankings for app
The system SHALL provide an endpoint (`GET /api/app/rankings`) that returns a paginated ranking list filtered by `type` (STAR/PARTY) and `tier` (LEGEND/SUPER/SHINING). The system SHALL check Redis cache first, falling back to MySQL on miss.

#### Scenario: Get STAR LEGEND rankings
- **WHEN** a GET request is sent to `/api/app/rankings?type=STAR&tier=LEGEND&page=1&size=20`
- **THEN** the system SHALL return paginated rankings with avatar, nickname, city, points, rank, and change

#### Scenario: Cache hit
- **WHEN** the ranking data exists in Redis cache
- **THEN** the system SHALL return data from Redis without querying MySQL

#### Scenario: Cache miss
- **WHEN** the ranking data is not in Redis
- **THEN** the system SHALL query MySQL, write the result to Redis with 5-minute TTL, and return the data

### Requirement: Homepage top 5
The system SHALL provide the top 5 ranked users (regardless of tier) for the homepage display, served from Redis cache.

#### Scenario: Get top 5
- **WHEN** the homepage requests top rankings
- **THEN** the system SHALL return the top 5 users by absolute points for the given type, from Redis cache

### Requirement: Redis cache invalidation
The system SHALL proactively DELETE ranking-related Redis keys after ranking refresh completes. Keys SHALL use TTL of 5 minutes as a safety fallback.

#### Scenario: Cache cleared after ranking refresh
- **WHEN** async ranking refresh completes
- **THEN** the system SHALL DELETE all affected ranking cache keys in Redis

#### Scenario: Stale cache auto-expires
- **WHEN** cache has not been invalidated for 5 minutes
- **THEN** the TTL SHALL cause automatic expiration

