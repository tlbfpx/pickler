# Proposal: 清理排名 / 报名表的孤儿引用

## Why

QA 在 2026-06-16 发现：
1. `/rankings` 页（明星 / 派对 tab）前 30 名中有 ~20 行用户列空白 — ranking 表里 `user_id` 指向的用户已不存在
2. Dashboard 「最新报名」第一行显示「未知」用户 — `AdminDashboardController:161` 显式 fallback 到 "未知" 字符串，因为 `registration.user_id` 找不到对应 user

根因：
- 历史测试期间 ranking / registration 行被插入，对应的 user 后来被删除（物理删除，绕过软删除机制）
- `RankingServiceImpl.batchLoadUsers` 用 `selectBatchIds`，自动加 `deleted_at IS NULL` 过滤，找不到的 user 在 VO 里不设置字段 → 前端空白
- 没有定期清理机制

## What Changes

- **数据清理**：新增 V7 migration 删除 ranking 表中孤儿行 + 软删 registration 表中孤儿行
- **查询防御**：`RankingServiceImpl.getRankings` 在 `selectList` 之后过滤掉 user 找不到的行（防止未来再次出现）
- **Dashboard 防御**：`AdminDashboardController.getStats` 在 recent registrations 里过滤孤儿（不再显示「未知」）

## Impact

- **Affected capabilities**: `ranking`, `event`（registration 属于 event 模块）, `infrastructure`（migration）
- **Affected code**:
  - `hey-pickler-server/.../db/migration/V7__cleanup_orphan_rankings_and_registrations.sql`（新增）
  - `hey-pickler-server/.../service/impl/RankingServiceImpl.java`
  - `hey-pickler-server/src/main/java/com/heypickler/controller/admin/AdminDashboardController.java`
- **Affected API**: 无行为变化（只是不再返回孤儿行）
- **Operational**: 部署后 ranking 表行数会减少（删除孤儿），dashboard 「未知」行消失

## Non-goals

- 不修改 user 删除逻辑（如改成强制软删）— 那是另一个 scope
- 不引入外键约束 — MyBatis-Plus 习惯不用 FK，避免迁移复杂度
- 不重写 PointRecord 清理（同源问题但当前未暴露给 UI，留 v2）
