## 1. 数据层（V14 migration + 实体 + 配置）

- [ ] 1.1 V14 migration：`event_placement_points` (event_id PK, points JSON NOT NULL, updated_at)
- [ ] 1.2 `EventPlacementPoints` 实体 + mapper
- [ ] 1.3 `application.yml` 添加 `hey-pickler.placement.defaultPoints` 默认表
- [ ] 1.4 启动应用验证 V14 应用成功

## 2. PlacementService 核心

- [ ] 2.1 写失败测试 `PlacementServiceImplTest`（覆盖：表查找、默认回退、3 人单打发分、双打 50/50 拆分 + 奇数处理、越界名次 0 分、重复发分拒绝）
- [ ] 2.2 实现 `PlacementService.issue(eventId)`：查表 → 调 `MatchService.standings` → 跨组排序 → 写 `point_record` (source=PLACEMENT) → 发 PointChangeEvent

## 3. MatchService 集成

- [ ] 3.1 `MatchServiceImpl.complete(eventId)` 内调用 `PlacementService.issue`（同事务）
- [ ] 3.2 已 COMPLETED 时 `complete` 幂等（no-op）

## 4. 控制器 + DTO

- [ ] 4.1 `PlacementPointsRequest` + `PlacementPointsVO`
- [ ] 4.2 `AdminEventController` 添加 `PUT /api/admin/events/{id}/placement-points` 和 `GET ...`（@RequireRole ADMIN+）

## 5. 集成测试

- [ ] 5.1 `PlacementIntegrationTest`：admin 设置积分表 → 完成赛事 → 验证 point_record 包含 3 行 PLACEMENT（点数与名次匹配）；双打赛事 100 分 → 两成员各得 50

## 6. 文档 + 验证

- [ ] 6.1 CLAUDE.md：添加 placement-points、PlacementService、迁移 head V14
- [ ] 6.2 `mvn test -Dtest='!*IntegrationTest'` 全绿
- [ ] 6.3 `mvn test -Dtest='*IntegrationTest'` 全绿
- [ ] 6.4 提交 + push