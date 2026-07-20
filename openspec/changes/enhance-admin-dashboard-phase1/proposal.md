> **Status: ACTIVE** — Phase 1 交付已签字验收，2026-07-20 起推进本 change。Open questions 已答（见下文），spec delta 起草中。

# Proposal: 运营仪表盘 Phase 1 — 扩展现有 Dashboard

## Why

当前 `AdminDashboardController.getStats()` 已经返回一堆 KPI + 30 天趋势数据，前端 `DashboardView.vue` 也已经用 ECharts 6 渲染了 5 张图。但存在三类问题：

1. **运营视角缺失**：没有活动排行（哪场活动最热）、没有出席率（用户报了名到底来没来）、没有同比环比（业务在涨还是在跌）。
2. **性能隐患**：30 天趋势用 `for (day in 30d) { selectCount }` 跑 30 次 SQL；收入用内存 join（`Registration.selectList` 全表加载 + 应用层算 `Event.fee`），数据量上来会爆。无任何 Redis 缓存，每次刷新都打 DB。
3. **没有时间范围选择器**：固定「近 30 天」，运营想看「近 90 天」或「上个月对比这个月」做不到。

这是规划的「完整运营仪表盘」4 期项目中的**第 1 期**，目标是把「数据已有但没用好」的部分榨干。漏斗、留存、异常告警等需要新埋点 / 新表的指标留到 Phase 2-4。

## What Changes

### 后端

- **新建 `DashboardService`**：把 `AdminDashboardController` 内联的聚合逻辑下沉到 service 层，可单测、可复用
- **重写趋势查询**：用 `SELECT DATE(created_at), COUNT(*) FROM user GROUP BY DATE(created_at)` 等 GROUP BY 查询替代 N+1 loop；同样模式套用到 registrations 和 revenue
- **重写收入计算**：SQL JOIN `registration × event` + `SUM(event.fee)`，去掉内存 join
- **加 Redis 缓存**：所有聚合查询结果缓存 5 分钟（key 含日期范围参数），key 前缀 `heypickler:dashboard:`
- **新增 endpoints**（全在 `/api/admin/dashboard/*` 下，权限同现有 `@RequireRole({SUPER_ADMIN, ADMIN, OPERATOR})`）：
  - `GET /trends?range=30d|90d|custom&from=&to=` — 通用趋势（用户/报名/收入/活动 4 条时序）
  - `GET /top-events?metric=registrations|revenue|fill_rate&limit=10&from=&to=` — Top 10 活动排行
  - `GET /attendance?from=&to=` — 出席漏斗（已报名总数 / 已签到总数 / no-show 率）
  - `GET /compare?metric=*&current=range&previous=range` — 同比环比（百分比 + 绝对差）
- **保留** `GET /api/admin/dashboard`（原 endpoint）：返回 KPI 概览 + 最近活动列表，但内部改为调用 `DashboardService`，KPI 加上同比环比字段

### 前端

- **`DashboardView.vue` 顶部加日期范围选择器**：默认「近 30 天」，快捷选项「近 7 天 / 近 30 天 / 近 90 天 / 本月 / 上月 / 自定义」
- **新增 3 张 ECharts 图**：
  - 收入趋势折线图（含同比虚线对比）
  - Top 10 活动横向柱状图（切换：报名数 / 收入 / 满座率）
  - 出席漏斗（已报名 → 已签到 → no-show 率）
- **KPI 卡升级**：每张卡显示同比/环比百分比（绿色涨 / 红色跌），鼠标 hover 显示绝对差
- **保留现有 5 张图**：用户增长、报名趋势、3 个分布饼图（这些已经是 ECharts，不动）

## Impact

- **Affected capabilities**: **新增 `dashboard` 能力**（之前后端有 controller 但 OpenSpec 没有对应 spec，这是补漏）；`infrastructure` 加 Redis 命名空间约定
- **Affected code**:
  - 后端：
    - 新建：`service/DashboardService.java` + `service/impl/DashboardServiceImpl.java`、`common/dto/DashboardTrendQuery.java`、`common/dto/DashboardCompareQuery.java`、`vo/DashboardTrendVO.java`、`vo/TopEventVO.java`、`vo/AttendanceFunnelVO.java`、`vo/CompareResultVO.java`
    - 重构：`controller/admin/AdminDashboardController.java`（瘦身，调用 service；新增 4 个 endpoint）
    - 改：`mapper/UserMapper.java` / `RegistrationMapper.java` / `EventMapper.java`（加 GROUP BY 查询方法，用 `@Select` 注解或 XML）
  - 前端：
    - 改：`views/dashboard/DashboardView.vue`（加日期选择器 + 3 张图 + KPI 同比）
    - 改：`api/dashboard.ts`（加 4 个新 API 方法）
- **Affected API**: 
  - 不破坏：`GET /api/admin/dashboard` 行为不变（但响应多 4 个字段：每个 KPI 的同比环比）
  - 新增：`GET /api/admin/dashboard/trends`, `/top-events`, `/attendance`, `/compare`
- **Operational**: 
  - Redis 新增 `heypickler:dashboard:*` 命名空间（TTL 5min）
  - 数据库无 schema 变化（无新表，无新 migration）
  - 网络流量：单次 Dashboard 加载从 1 个请求涨到 5 个（KPI + 4 个新 endpoint），但每个都命中缓存时 < 10ms

## Decisions confirmed

- **范围**：A（日期选择器）+ B（3 张新图）+ C（性能重构 + Redis 缓存）+ D（KPI 同比环比），全做
- **缓存 TTL 5 分钟**：运营数据不需要秒级实时，5 分钟足够；用户主动刷新可点「刷新」按钮旁路缓存（传 `?no_cache=1`，仅 SUPER_ADMIN 可用）
- **Top 活动排行 3 个 metric**：报名数（默认）/ 收入 / 满座率（`currentParticipants / maxParticipants`），用 tab 切换不增加卡片数
- **出席漏斗的窗口**：默认按日期范围（统计范围内所有活动），不强制选具体活动（简化交互）
- **同比环比只做周/月**：年同比数据量不够（项目刚启动），不做
- **权限维持现状**：`{SUPER_ADMIN, ADMIN, OPERATOR}` 都能看全量数据，不做脱敏分级
- **不引入新表**：Phase 1 严格只用现有数据，所有新指标都从 `User` / `Event` / `Registration` 派生

## Non-goals

- **不做漏斗（浏览 → 报名 → 出席）** — 小程序零埋点，需要 Phase 2 加 wx.reportEvent + 后端 access log
- **不做留存分析（D1/D7/D30 同期群）** — 需要 V9 `login_log` 表，留 Phase 2
- **不做异常告警** — 需要先有 Phase 1-3 的基线，留 Phase 4
- **不做数据导出 CSV/Excel** — 当前需求是「看」，导出留 Phase 5
- **不重构现有 5 张图的视觉风格** — 已有 ECharts 主题，新图沿用
- **不增加新的 admin 菜单** — 仍在 `/dashboard` 路由下，只是内容更丰富
- **不做实时刷新（WebSocket / SSE）** — 5 分钟缓存 TTL 已经够用
- **不动小程序端** — 仅 admin 后台

## Open questions

（这些是 design 阶段需要回答的，proposal 阶段先标记出来）

1. **GROUP BY 查询写哪里**：MyBatis-Plus 注解 `@Select` 还是 XML mapper？项目当前都是注解，但 GROUP BY + JOIN 查询在注解里写可读性差，建议允许本 change 引入 XML mapper 文件
2. **缓存失效策略**：写操作（创建活动、用户报名等）要不要主动 invalidate 缓存？还是只靠 TTL？— 倾向只靠 TTL（简单，5 分钟可接受）
3. **满座率 metric 的边界**：`maxParticipants = 0`（无限制活动）怎么处理？— 倾向排除出排行（不参与 Top 10 计算）
4. **同比环比的时间对齐**：本月（28 天）vs 上月（31 天）如何对齐？— 倾向按「自然月」直接比，文档说明可能有天数差异

## Phased plan (上下文，不在本 change 范围)

| Phase | 内容 | 工作量 | 依赖 |
|-------|------|--------|------|
| **Phase 1（本 change）** | 扩展现有 Dashboard + 性能重构 + Redis 缓存 | 2-3 天 | 无 |
| Phase 2 | 后端埋点基建：V9 `login_log` + 小程序 wx.reportEvent + app access log filter | 2-3 天 | Phase 1 上线 1-2 周 |
| Phase 3 | 真·留存 + 漏斗：D1/D7/D30、活动级漏斗、用户分群 | 2-3 天 | Phase 2 数据沉淀 ≥ 2 周 |
| Phase 4 | 异常告警：报名骤降、退订激增、错误率，企微/邮件通知 | 2-3 天 | Phase 1-3 基线 |
