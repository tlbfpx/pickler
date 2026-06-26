## 1. 数据层（V13 migration + 实体 + 枚举）

- [ ] 1.1 创建 V13 migration（`V13__match_play.sql`）：`match` 表 + CHECK（slot_a/b user_id/team_id 互斥）+ index（event_id, group_id, uk (group_id, slot_a_user_id, slot_b_user_id)）
- [ ] 1.2 创建 `MatchStatus` 枚举（`SCHEDULED`/`IN_PROGRESS`/`COMPLETED`）
- [ ] 1.3 创建 `Match` 实体 + `MatchMapper`
- [ ] 1.4 启动应用验证 Flyway V13 执行成功

## 2. 核心服务（生成 + 记分 + 排名）

- [ ] 2.1 写失败测试 `RoundRobinGeneratorTest`（4 人 → 6 场；3 队 → 3 场；1 人 → 0 场；N=2 → 1 场）
- [ ] 2.2 实现 `RoundRobinGenerator`（纯函数）
- [ ] 2.3 写失败测试 `GameValidatorTest`（合法 21-N、21-20 拒绝、30 封顶拒绝、3 局 2-1、错误局数拒绝）
- [ ] 2.4 实现 `GameValidator`（21+ 领先 2，≤30）
- [ ] 2.5 写失败测试 `MatchServiceImplTest`（generate 流程、score 提交流程含校验、reset、standings 排名 + 平局规则）
- [ ] 2.6 实现 `MatchService`（generate / submitScore / reset / standings / complete）+ `MatchServiceImpl`

## 3. Controller 层

- [ ] 3.1 `AppMatchController`：`GET /api/app/events/{id}/matches`、`POST /api/app/matches/{id}/score`、`GET /api/app/events/{id}/standings`
- [ ] 3.2 `AdminMatchController`：`POST /api/admin/events/{id}/matches/generate`、`GET /api/admin/events/{id}/matches`、`POST /api/admin/matches/{id}/reset`、`POST /api/admin/events/{id}/complete`

## 4. EventService 调整

- [ ] 4.1 register/cancel 拒绝 `IN_PROGRESS` / `COMPLETED`（已部分支持 groupingLocked；扩展 status 校验）
- [ ] 4.2 单测覆盖：IN_PROGRESS 时 register 抛错

## 5. 集成测试

- [ ] 5.1 `MatchPlayIntegrationTest`：admin 生成 → app 双方自助记分 → standings 验证名次 → admin 完成
- [ ] 5.2 admin reset 重赛后再次记分验证 games 字段清空
- [ ] 5.3 非参赛方记分被拒

## 6. 前端 (admin + wxapp) — 与 PR-2 同 PR

- [ ] 6.1 admin: `MatchPanel.vue` 嵌入赛事详情：生成按钮、比赛列表（组 × 双方）、reset/complete 按钮
- [ ] 6.2 wxapp: `event-detail` 加"我的比赛"标签 + 比赛记分页（3 局比分输入）
- [ ] 6.3 admin/wxapp: standings 表格

## 7. 文档 + 验证

- [ ] 7.1 CLAUDE.md: 加 match 实体、MatchService、status IN_PROGRESS/COMPLETED、migration head V13
- [ ] 7.2 `mvn test -Dtest='!*IntegrationTest'` 全量绿
- [ ] 7.3 `mvn test -Dtest='*IntegrationTest'` 全量绿
- [ ] 7.4 admin lint + build
- [ ] 7.5 wxapp 人工编译验证
- [ ] 7.6 提交 + push + 创建 PR（spec-2-match-play）