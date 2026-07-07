# Proposal: admin 后端接入 Loop-v13/v14 新功能（Loop-v15）

## Why

Loop-v13 / v14 在后端新增了：
- `GET /api/admin/events/{id}/summary`（PR #33）
- `POST /api/admin/events/{id}/registrations/bulk-check-in`（PR #34）

但 admin 前端（`hey-pickler-admin/` Vue 3 仓）**没接入**。结果：运维每天仍要看 5 个独立 endpoint + 手动单条签到。

## What Changes

### 前端
- **新 API 客户端**（`src/api/events.ts`）：
  - `getEventSummary(eventId)` — 调用 v13 endpoint
  - `bulkCheckIn(eventId, registrationIds)` — 调用 v14 endpoint
- **`RegistrationDrawer.vue` 改造**：
  - 顶部加"全选已选" / "批量签到"按钮
  - 调 `bulkCheckIn` 后回显 updated/skipped 分类
  - ElMessage.success/error 提示
- **`EventDetailView.vue` 改造**（可选，small scope）：
  - 顶部加"运营概览"卡片：fillRate、checkInRate、状态转换
  - 调 `getEventSummary`

## Non-Goals
- 不改 OpenSpec（v13/v14 的）
- 不加新路由（继续在 EventDetailView + RegistrationDrawer 内）
- 不改 user permission
- 不动其他 controller

## Affected Specs
- `src/api/events.ts`（新增 2 client function）
- `src/views/events/RegistrationDrawer.vue`（加按钮 + 弹窗）
- `src/views/events/EventDetailView.vue`（可选 summary 卡片）

## Risks
- **类型映射**：TS 接口必须与 Java VO 字段一一对应。错字段 = UI 渲染错误。
- **大列表性能**：50+ registration 选中时 `bulkCheckIn` 是单 SQL，没问题
- **错误处理**：ElMessage 显示后端错误消息
