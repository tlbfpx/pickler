# Proposal: 赛事汇总 endpoint（Loop-v13）

## Why

当前 admin 详情页面要展示一个赛事的"运行概览"，前端要依次调 5 个 endpoint：

- `GET /api/admin/events/{id}` — 基础信息
- `GET /api/admin/events/{id}/participants` — 注册列表
- `GET /api/admin/events/{id}/registrations` — 报名状态（admin 视角）
- `GET /api/admin/events/{id}/placement-points` — 积分表
- `GET /api/admin/events/{id}/placements` — 已发名次

**3 个问题**：
1. **N+1 网络开销**：5 次往返 = ~200-500ms 延迟（取决于网络），高频操作卡顿
2. **前端要二次聚合**：签到率、容量、费用都靠前端算
3. **后端查询冗余**：每个 endpoint 单独查 registration 表，5 次

## What Changes

### 后端
- **新 endpoint**: `GET /api/admin/events/{id}/summary`
  - 权限：同 AdminEventController（`SUPER_ADMIN` / `ADMIN` / `OPERATOR` 全部可读）
  - 返回单次聚合的 DTO，避免前端二次计算
- **新 service 方法**: `EventService.getEventSummary(Long eventId)`
  - 单事务内聚合：registration counts（按 status 分组）、team counts（按 status 分组）、match counts（按 status 分组）、fees
  - 复用现有 mapper，不开新表
- **新 DTO**: `EventSummaryVO`
  - 字段：event 基本字段 + 各 status 计数 + 派生指标（签到率、容量使用率）
  - 在 `vo/` 包

### 前端
（这次不涉及，admin 后端 vue 代码在另一仓 `hey-pickler-admin/`）

## Non-Goals

- 不重写现有 endpoint 行为（只是新增）
- 不开新缓存（这次只做单查询聚合）
- 不改权限（沿用 AdminEventController 的 `@RequireRole` 矩阵）
- 不进 admin 后端 UI（用户后续接入）

## Affected Specs

- `event-service` (新增 method)
- `admin-event-controller` (新增 endpoint)

## Risks

- **聚合查询性能**：N+1 风险。本次用 `selectCount(groupBy)` 替代循环 selectList。一次查询返回所有分组。
- **事务一致性**：聚合查询不写，不需事务
