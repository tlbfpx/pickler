# Tasks: 清理孤儿排名 / 报名引用

## 1. V7 migration
- [ ] 1.1 新建 `hey-pickler-server/src/main/resources/db/migration/V7__cleanup_orphan_rankings_and_registrations.sql`
- [ ] 1.2 SQL 内容按 design.md：DELETE ranking 孤儿 + UPDATE registration 孤儿（软删 + CANCELLED）
- [ ] 1.3 启动应用验证 Flyway 应用成功（看日志 `Migrating schema ... to version "7"`）

## 2. RankingServiceImpl 过滤
- [ ] 2.1 修改 `getRankings`：在 `.map(ranking -> ...)` 之前加 `.filter(r -> userMap.containsKey(r.getUserId()))`
- [ ] 2.2 简化 `.map` 内部：去掉 `if (user != null)`，直接 set
- [ ] 2.3 修改 `getTop5`：同样 filter

## 3. AdminDashboardController 过滤
- [ ] 3.1 修改 `getStats`：在 recent registrations `.map(reg -> ...)` 之前加 `.filter(reg -> userMap.containsKey(reg.getUserId()))`
- [ ] 3.2 移除 `"未知"` fallback，直接 `user.getNickname()`

## 4. 单测（TDD）
- [ ] 4.1 `RankingServiceImplTest.getRankings_filtersOrphanRows`：mock userMapper 返回缺一个 user，断言 list size 减少
- [ ] 4.2 `RankingServiceImplTest.getTop5_filtersOrphanRows`
- [ ] 4.3 `AdminDashboardControllerTest.getStats_omitsOrphanRegistrations`
- [ ] 4.4 跑 `mvn test -Dtest='*RankingService*,*DashboardController*'` 全绿

## 5. 验证
- [ ] 5.1 `mvn test`（全量后端）
- [ ] 5.2 启动应用 + 前端，访问 `/rankings`，前 30 名全部显示用户信息
- [ ] 5.3 访问 `/`，最新报名列表无 "未知" 行

## 6. 归档
- [ ] 6.1 移动 change 目录到 `archive/`
- [ ] 6.2 合并 spec delta 到 `ranking/spec.md` 和 `infrastructure/spec.md`
