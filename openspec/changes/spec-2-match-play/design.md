## Context

Spec 1（已合并）建立了：
- `event.format` (SINGLES / DOUBLES / MIXED)
- `event.grouping_locked` (true 后冻结名单 + 分组)
- `match_group` / `group_assignment` 表，team 实体
- 5 个分组 admin API + 2 个 app team endpoint

Spec 2 在分组锁定的基础上，向下推进赛事生命周期：生成对阵签表 → 自助记分 → 组内排名 → 赛事完成。

**关键约束**：
- 用户是业余羽毛球爱好者，期望"我自己能录分"，不需要管理员/裁判。
- 循环赛（每组内每两人/队恰好一场）足够覆盖大多数场景，无需引入淘汰赛签表/季军赛。
- 三局两胜（21 分制）是事实标准。
- **MVP 不引入淘汰赛阶段**（spec §9 提及但本 spec 不包含）。

## Goals / Non-Goals

**Goals:**
- 在已锁定分组的赛事上生成循环赛对阵
- 参赛双方任何一方都能提交 3 局比分
- 实时计算组内排名（胜场 → 胜局 → 直接交锋）
- 支持 admin 重置单场（重赛）+ 标记赛事完成
- 与现有 Event 状态机无缝集成：`IN_PROGRESS` ↔ 完成 ↔ `COMPLETED`

**Non-Goals:**
- 淘汰赛签表 + 季军赛（spec 2 末的"分组后淘汰"未来 PR）
- 跨组别赛（如同时有 A/B 组产生的交叉对决）
- 比分的可视化回放（仅看最新一局）
- 实时通知/推送（参与者查时再拉取）
- 与 Spec 3（按名次发分）的耦合；本 spec 仅产出排名，Spec 3 单独消费

## Decisions

### D1. 比分存储：单 `match` 表 + JSON 列 vs 独立的 `match_score` 表

**决策：单 `match` 表 + 内嵌 JSON 列存每局比分。**

为什么：
- 单场比赛最多 3 局（bo3），数据极简
- 查询"我的比赛列表"已经要 join user/team；再 join 一张表不必要
- MVP 阶段，避免引入新表 + migration + 一个新 mapper

替代方案（拒绝）：独立的 `match_game` 表。优势是 schema 更规范（可对每局单独加字段如 duration_seconds），但成本是新表 + 新 migration + 新 mapper。

数据结构：
```sql
CREATE TABLE match (
  id BIGINT PK,
  event_id BIGINT,        -- redundant with group_id→match_group.event_id for fast lookup
  group_id BIGINT,         -- FK to match_group
  slot_a_user_id BIGINT NULL,  -- SINGLES participant
  slot_a_team_id BIGINT NULL,  -- DOUBLES/MIXED participant (mutex with slot_a_user_id)
  slot_b_user_id BIGINT NULL,
  slot_b_team_id BIGINT NULL,
  status VARCHAR(16),      -- SCHEDULED | IN_PROGRESS | COMPLETED
  games JSON,              -- [{"game":1, "a":21, "b":15}, ...]
  games_won_a TINYINT,     -- 0..3, redundant derived for ranking
  games_won_b TINYINT,
  submitted_by_user_id BIGINT NULL,  -- who submitted
  submitted_at DATETIME,
  started_at DATETIME,
  completed_at DATETIME,
  created_at DATETIME,
  UNIQUE KEY uk_group_slot (group_id, slot_a_user_id, slot_b_user_id),  -- prevent dup
  KEY idx_event (event_id),
  KEY idx_group (group_id)
);
```

**CHECK 约束**：`((slot_a_user_id IS NOT NULL AND slot_a_team_id IS NULL) OR (slot_a_user_id IS NULL AND slot_a_team_id IS NOT NULL))` 同 slot_b。要求 MySQL ≥ 8.0.16（项目已要求）。

### D2. 循环赛生成：服务端纯函数

**决策：纯函数 `RoundRobinGenerator.generate(List<Participant>, int groupId, Long eventId) -> List<Match>`，在 `MatchService.generate(eventId)` 事务内被调用并批量插入。**

- N 名参赛者 → N*(N-1)/2 场
- N=1 → 0 场（无对阵）；N=2 → 1 场；N=3 → 3 场；N=4 → 6 场
- 生成算法：固定位置 `[0,1,2,...,N-1]`，第一次 `[0,1],[2,3],[4,5],...`；轮转第二个位置

为什么服务端生成（不让 wxapp 生成再 POST）：
- 防止客户端构造非法对阵
- 服务端在事务内清旧 match + 批量插入，原子
- admin 一键触发

### D3. 记分：3 局两胜 / 21 分制（deuce 到 30）

**决策：服务端校验每局比分规则（game validation），拒绝非法提交。**

- 每局：胜方 ≥ 21 分；胜方 - 负方 ≥ 2；双方均 ≤ 30 分
- BO3：先赢 2 局者获胜；最多 3 局
- 输入：JSON 数组，如 `[{game:1, a:21, b:15}, {game:2, a:21, b:18}]`（2-0 胜）或 3 局 `[{game:1, a:21, b:23}, {game:2, a:18, b:21}, {game:3, a:21, b:15}]`（2-1）

校验失败 → `PARAM_ERROR` + 详细信息。

为什么服务端严格校验（不信任客户端计算胜局数）：
- 客户端可以传 `games_won_a=2` 但实际只有 1 场胜利；服务端从 games JSON 重算

### D4. 谁可以记分：双方任意一方 + admin

**决策：双方所属用户/队长 + admin（@RequireRole ADMIN+）都可记分；非双方成员 401。**

- 单打：slot_a_user_id 或 slot_b_user_id 等于当前 user → 允许
- 双打：slot_a_team_id 或 slot_b_team_id 的成员（member1 或 member2）→ 允许
- admin → 总是允许（用于修正错误）

提交时记录 `submitted_by_user_id`（audit）。

### D5. 重赛：admin 限定

**决策：只有 admin 可以重置 match 回 SCHEDULED，清空 games、games_won_a/b、submitted_by。**

为什么限制：admin 才能重置（不是双方）：
- 避免双方因分歧反复回滚
- admin 在后台可以审计 + 干预
- MVP 简化权限；后续可开放双方共识重置

### D6. 组内排名：实时计算，不持久化

**决策：每次查询 standings 时实时从 match 计算，不写新表。**

排名规则（按优先级）：
1. **胜场数** wins desc
2. **胜局差** (games_won_a - games_won_b 之和) desc
3. **直接交锋** 头对头胜负者排名靠前
4. **若仍平**：相同名次

为什么实时计算：
- 排名是 match 的派生数据；持久化会导致"match 重赛后排名缓存与现实不一致"
- 数据量小：单 group 一般 ≤ 6 场比赛，组内 4-6 名参赛者，O(M) 计算

数据结构（rankings 端点返回）：
```json
[{"userId/teamId": ..., "wins": 2, "losses": 1, "gamesFor": 5, "gamesAgainst": 3, "rank": 1}, ...]
```

**doubles 注意**：team 排名时 wins/losses 累加 team 整场（不是成员个人）。

### D7. 赛事完成：所有 match COMPLETED → 触发 COMPLETED 转换

**决策：admin 显式调用 `POST /api/admin/events/{id}/complete`；MVP 不自动完成。**

- admin 在后台能看到"还有 N 场比赛未完成"
- 触发后：写 `event.status = COMPLETED`，记录 `event.completed_at = now()`
- 不持久化最终排名（实时计算）
- 与 Spec 3 解耦：Spec 3 自行监听 COMPLETED（用 EventStatusScheduler 或在 complete service 里 publish event）

为什么不自动：
- admin 可能想核对异常后再标记完成
- 自动转换可能与 admin 重置产生竞态（"刚重置，比赛自动完成了？"）

### D8. app API 的"我的比赛"是看 slot 匹配，不是 user 全表

**决策：`GET /api/app/events/{id}/matches` 返回当前用户作为任一方参赛的 match 列表（无论 status）。**

- 单打：user_id = slot_a_user_id OR slot_b_user_id
- 双打：team 成员（member1_user_id 或 member2_user_id）= current user

让 wxapp 直接展示"我今天要打的 3 场"。

## Risks / Trade-offs

- **R1**: 循环赛算法 N=1 时生成 0 场，但 admin 已生成过比赛后又有人退赛？MVP 不处理退赛（Spec 1 已禁止 locked 后退赛），所以 N 是固定的。→ 缓解：doc 注明"lock 后不能新增/减少参赛者"。

- **R2**: 重赛后排名会变；wxapp 缓存可能短暂显示旧排名。→ 缓解：standings 端点不缓存，每次实时算。

- **R3**: 大量赛事（≥100 场 match）+ 高并发写 → 单 match 多次提交竞态。→ 缓解：服务端用乐观锁（`UPDATE match SET games=? WHERE id=? AND status=?`），且仅当 status='SCHEDULED'/'IN_PROGRESS' 时允许提交，第一次提交后置 IN_PROGRESS。

- **R4**: 三局两胜校验复杂，错误信息不友好。→ 缓解：明确报错"第 N 局 A 队得分须 ≥ 21 且 ≥ B + 2"。

- **R5**: V13 migration 涉及 match 表 + index + CHECK，与 Spec 1 的 V12 模式一致；不破坏向后兼容（独立表）。

## Migration Plan

1. **V13 migration**（`V13__match_play.sql`）：
   - `match` 表创建（见 D1 schema）
   - CHECK 约束
   - index：event_id / group_id / (group_id, slot_a_user_id, slot_b_user_id)
2. **代码部署顺序**：
   - V13 migration（仅 schema，可独立 deploy）
   - entity + mapper + service（不影响运行时）
   - controller + service 业务方法（启用）
3. **回滚**：V13 是新表，删除 match 表即可；旧的分组/赛事继续工作（只是不能开始比赛）。

## Open Questions

- **OQ1**: doubles 比赛的对阵方展示 — 是否显示两位成员昵称（"A1 / A2 vs B1 / B2"）？MVP 答：显示。
- **OQ2**: standings 是否需要也返回跨组名次？MVP 答：不需要（仅组内），跨组排序交给 Spec 3。
- **OQ3**: 比赛完成时是否给参与者发通知（站内信/微信模板消息）？MVP 答：不发，仅查询可见。