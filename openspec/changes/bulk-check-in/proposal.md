# Proposal: 批量签到 API（Loop-v14）

## Why

当前 admin 批量签到流程是 N+1：

```
For each registered user (50+ on big events):
  PATCH /api/admin/events/{eventId}/registrations/{id}/status
    body: { "status": "CHECKED_IN" }
```

**3 个问题**：
1. **N+1 性能**：50 个用户 = 50 次 PATCH = 50 × ~50ms = 2.5s 延迟
2. **N+1 SQL**：50 次 single-row UPDATE
3. **失败时不一致**：第 30 个失败时前 29 个已签到（无原子性）

## What Changes

### 后端
- **新 endpoint**: `POST /api/admin/events/{eventId}/registrations/bulk-check-in`
  - 权限：`@RequireRole({SUPER_ADMIN, ADMIN})`（不允许 OPERATOR 批改）
  - Body: `{"registrationIds": [1, 2, 3, ...]}` (max 200 per request)
  - 返回: `BulkCheckInResult` 含 updated/skipped/notFound/withdrawn 分类
- **新 service 方法**: `EventService.bulkCheckIn(Long eventId, List<Long> ids)`
  - 单事务内：1 个批量 UPDATE，then 拆分类别
  - 原子性：失败回滚
- **新 DTO**: `BulkCheckInRequest`, `BulkCheckInResult`

### Non-Goals
- 不改单条 PATCH 行为
- 不支持全量自动签到（admin 显式选择）
- 不加权限/角色矩阵调整
- 不发站内通知（admin 端已可手动 push）

## Affected Specs
- `event-service` (新 method)
- `admin-event-controller` (新 endpoint)

## Risks
- **大列表超时**：max 200 cap（同时够 50 用户的单批）
- **失败原子性**：用 `@Transactional(rollbackFor=Exception.class)` + UPDATE 一次
- **DB 锁竞争**：单 SQL `UPDATE ... WHERE id IN (...)` 锁多行
