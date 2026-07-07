# Spec: event search multi-keyword

## EventService.adminListEvents(..., String sort, int page, int size)

Enhanced event list with multi-keyword + multi-field search.

### Behavior
- `keyword`: trimmed, split by `\\s+`. Each token matches if (title LIKE OR description LIKE). All tokens must match (AND across tokens).
- `sort`: default `event_time_desc`. Allowed: `event_time_asc`/`event_time_desc`/`created_at_asc`/`created_at_desc`/`current_participants_asc`/`current_participants_desc`. Invalid → default (event_time_desc).
- `startTime`/`endTime`: `ge`/`le` against `event_time`. Empty string or null → ignored.
- `type`/`status`: EQUALS. Empty string or null → ignored.
- `location`: LIKE.

### Returns
`PageResult<EventVO>` — paginated list ordered by `sort` (default event_time desc).

### Throws
- `BizException(NOT_FOUND)` only if page/size invalid (delegated to Page constructor)
- No additional throws for search parameters.
