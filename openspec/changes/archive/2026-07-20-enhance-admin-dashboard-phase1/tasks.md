# Tasks: enhance-admin-dashboard-phase1

## 1. 后端 — DashboardService 抽取与重构

- [ ] 1.1 新建 `service/DashboardService.java` 接口（定义 5 个查询方法：snapshot / trends / topEvents / attendance / compare）
- [ ] 1.2 新建 `service/impl/DashboardServiceImpl.java`，把 `AdminDashboardController.getStats` 内联逻辑（KPI + tier 分布 + recent events + 30 天趋势 + 收入汇总）下沉到 impl，controller 瘦身为调 service
- [ ] 1.3 用 `@Select` 注解在 mapper 加 4 条 GROUP BY 时序查询（user / registration / revenue / event count 各 1 条 SQL），替代现有 30-day for-loop
- [ ] 1.4 用 `RegistrationMapper SELECT count(…) WHERE event_id IN (SELECT id FROM event WHERE …)` 之类 SQL 算 revenue，**禁止**内存 join `Registration.selectList` + 应用层算 fee
- [ ] 1.5 在 `DashboardServiceImpl` 加 query / response 转换方法 + 写单测覆盖快照 + 趋势边界（空数据 / 1 天 / 90 天）

## 2. 后端 — 新增 4 个 endpoint

- [ ] 2.1 `GET /api/admin/dashboard/trends?range=7d|30d|90d|thisMonth|lastMonth|custom&from=&to=` → `DashboardTrendVO`（buckets 数组，无空缺，按 day 填零）
- [ ] 2.2 `GET /api/admin/dashboard/top-events?metric=registrations|revenue|fillRate&range=…&limit=1..50` → `TopEventVO[]`；`fillRate` metric 必须排除 `maxParticipants <= 0` 的事件
- [ ] 2.3 `GET /api/admin/dashboard/attendance?range=…` → `AttendanceFunnelVO`；`registered=0` 时 `noShowRate=null`
- [ ] 2.4 `GET /api/admin/dashboard/compare?metric=users|registrations|revenue|events&currentRange=…&previousRange=…` → `CompareResultVO`；`previous=0` 时 `deltaPct=null`
- [ ] 2.5 现有 `GET /api/admin/dashboard` 加每个 KPI 的 `deltaPct` + `deltaAbs` 字段，向后兼容（旧字段保留）

## 3. 后端 — Redis 缓存

- [ ] 3.1 新建 `DashboardCache` 工具类（key 前缀 `heypickler:dashboard:`，TTL `Duration.ofMinutes(5)`，**不**做主动 invalidate）
- [ ] 3.2 把 5 个查询方法都包成 cache-aside（先查 cache，无则查 DB + 写 cache）
- [ ] 3.3 加 `no_cache=1` 参数解析 + SUPER_ADMIN bypass（普通角色传 `no_cache=1` 时静默忽略，不报错）
- [ ] 3.4 单测验证：第一次 DB 命中 + 设 cache；5 分钟内第二次 0 DB hits；SUPER_ADMIN `no_cache=1` 必查 DB；OPERATOR `no_cache=1` 仍走 cache

## 4. 后端 — 单测与回归

- [ ] 4.1 `DashboardServiceImplTest` 覆盖 5 个查询的 happy path + 边界（空数据、单天、跨年、自定义范围、满座率除零、compare 除零）
- [ ] 4.2 `AdminDashboardControllerTest` 加 endpoint 集成（MockMvc）+ RBAC（403 非 admin）+ `no_cache` 行为
- [ ] 4.3 全量 `mvn test -Dtest='!*IntegrationTest'` BUILD SUCCESS（不能回归 review #4 修复）

## 5. 前端 — DashboardView.vue

- [ ] 5.1 顶部加日期范围选择器（`el-date-picker` 或等同组件）：默认近 30 天，快捷「近 7 / 30 / 90 / 本月 / 上月 / 自定义」；选完触发 4 个新 endpoint 重新拉数据
- [ ] 5.2 新增 3 张 ECharts（沿用 `views/dashboard/` 现有主题）：
  - 收入趋势折线图（含同比虚线）
  - Top 10 横向柱状图（metric 切换 tab：报名数 / 收入 / 满座率）
  - 出席漏斗（已报名 → 已签到 → no-show%）
- [ ] 5.3 KPI 卡升级：显示同比/环比百分比 + 绝对差，涨绿跌红，hover 显示绝对差
- [ ] 5.4 保留现有 5 张图（用户增长、报名趋势、3 个分布饼图）

## 6. 前端 — api 集成 + 工程

- [ ] 6.1 `api/dashboard.ts` 加 4 个新方法：`getTrends(params)` / `getTopEvents(params)` / `getAttendance(params)` / `getCompare(params)`
- [ ] 6.2 `npm run lint:check` 全绿
- [ ] 6.3 `npm run build` 产物 dist/ 大小变化 < 200KB（ECharts 已大；新图复用主题）
- [ ] 6.4 `npx playwright` E2E（可选，看时间）：访问 `/dashboard`，截屏验证 3 新图渲染、日期选择器交互

## 7. PR 与 archive

- [ ] 7.1 后端 commit `refactor(dashboard): 抽取 service + GROUP BY 重写 + Redis 缓存 + 4 endpoint + KPI 同比`
- [ ] 7.2 前端 commit `feat(dashboard): 日期选择器 + 3 张 ECharts 图 + KPI 同比环比`
- [ ] 7.3 开 PR（base master），title 引用 spec；CI 全绿后 squash merge
- [ ] 7.4 `openspec archive enhance-admin-dashboard-phase1`（proposal + tasks + specs → archive/，spec delta 合入 `openspec/specs/dashboard/spec.md`）
- [ ] 7.5 打 tag `v3.3.0`（首个 dashboard phase 完整体）

## 8. 文档同步（可选）

- [ ] 8.1 `docs/RUNBOOK.md` 加 dashboard 缓存命名空间 + SUPER_ADMIN bypass 说明
- [ ] 8.2 `docs/RELEASE-CHECKLIST.md` §4 部署步骤加 dashboard 缓存键（仅文档，无代码影响）
