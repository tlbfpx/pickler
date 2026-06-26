## Why

Spec 1 (比赛形式 + 队伍 + 分组, 已合并) 完成了报名阶段：赛事有 format、参赛者被分组并被锁定。现在赛事可以"开打"了，但目前没有任何机制记录比赛、推进名次、产出最终结果 — 锁定之后整个生命周期悬空。

本变更（Spec 2）完成比赛阶段：在已锁定的分组基础上生成对阵签表，让参赛者自助记录比分，按循环赛积分规则产出组内名次，最终把赛事推进到 `COMPLETED`。最终名次是 Spec 3（按名次发分）的输入。

## What Changes

- **新增 `match` 表**：记录每场比赛（组内两两对阵），含状态（SCHEDULED / IN_PROGRESS / COMPLETED）、两方参赛者（user_id 或 team_id，互斥）、位置（slotA/slotB）、最终比分（games_won_a/b）、提交者。
- **新增 `match_score` 表（或将比分直接存在 match 表）**：每局比分（game_number 1..3, points_a, points_b）。MVP 决策：把每局比分作为 JSON 列存在 match 表上（避免新建表 + 简化查询），score_a / score_b 存总胜局数冗余字段。
- **新增 `event_round` 概念**（轻量）：用 `event.status` 表示生命周期（DRAFT → OPEN → FULL → IN_PROGRESS → COMPLETED），不引入新表。锁定后 admin / 系统触发"开始比赛"，生成所有 match 行（每组内 n 选 2 场循环赛）。
- **循环赛签表生成器**：纯函数，输入一个 `match_group` 的 participants（user/team），输出 N*(N-1)/2 个 match。
- **自助记分**：每场比赛双方任意一方提交 3 局比分，服务端校验格式（每局 21+，先到 21 且领先 2，最多 30，3 局两胜）。任一方提交后 match 状态置 COMPLETED。
- **重赛**：admin（或双方？MVP 限定 admin）可重置 match 回到 SCHEDUWN，删除比分，允许重新提交。
- **组内排名**：match 完成后（group 内所有 match 完成），实时计算组内排名（胜场数 → 胜局数 → 直接交锋胜负）。排名是只读计算结果，不持久化。
- **赛事完成**：所有 match 完成后，admin（或系统自动？）触发 `event.status = COMPLETED`。
- **新增 app API**：
  - `GET /api/app/events/{id}/matches` — 当前用户在赛事中的待比赛列表（含对手 + 比分）
  - `POST /api/app/matches/{id}/score` — 自助记分（双方可提交）
  - `GET /api/app/events/{id}/standings` — 组内排名
- **新增 admin API**：
  - `POST /api/admin/events/{id}/matches/generate` — 开始比赛，生成签表
  - `GET  /api/admin/events/{id}/matches` — 全部比赛列表（管理视角）
  - `POST /api/admin/matches/{id}/reset` — 重置单场（重赛）
  - `POST /api/admin/events/{id}/complete` — 标记完成

无破坏性变更；仅追加。

## Capabilities

### New Capabilities

- `match-play`: 比赛生成、自助记分、组内排名、重赛、赛事完成（覆盖新增的 match 实体、循环赛生成器、记分规则、排名计算）

### Modified Capabilities

- `event`: 增加需求 — `event.status=IN_PROGRESS` 时赛事进入比赛阶段，`event.status=COMPLETED` 表示所有比赛已结束并产出最终排名。这是 Spec 1 已定义的状态枚举的延续，仅补充状态转换的语义需求（不是改 API）。
- `ranking`: 增加需求 — Spec 3 的"按名次发分"消费 Spec 2 产出的最终排名。MVP 不改 ranking 表结构（仍由 point_record 驱动），但 spec 层声明输入契约。

## Impact

- **后端代码**：
  - 新增 entity `Match` (含 score_a/b 与 games JSON 列)
  - 新增 enum `MatchStatus` (SCHEDULED / IN_PROGRESS / COMPLETED)
  - 新增 service `MatchService`（生成、记分、重置、排名计算）+ `RoundRobinGenerator`（纯函数）
  - 新增 mapper `MatchMapper`
  - 新增 controller `AppMatchController` + `AdminMatchController`
  - 修改 `EventService`：在状态转换 IN_PROGRESS / COMPLETED 时拒绝错误的报名/取消操作（已部分支持）；新增 start/complete API
  - 新增 migration V13（match 表 + index）
- **app API**：~3 个新端点（matches 列表、记分、standings）
- **admin API**：~4 个新端点（generate、列表、reset、complete）
- **wxapp**：赛事详情页加"我的比赛"标签；新增比赛记分页面
- **admin**：赛事详情加"比赛管理"标签（生成 / 列表 / 重置 / 完成）
- **集成测试**：至少 3 个（生成、自助记分排名、admin 重置+完成）
- **不影响**：PointRecord / PointService / Season（Spec 3 范围）