# Proposal: 运营仪表盘 Phase 2 — 埋点基建

## Why

v3.3.0 Phase 1 已落地（snapshot KPI + 4 endpoint + Redis 缓存 + 前端日期选择器），但 Phase 1 proposal §"Phased plan" 明确标注 **Phase 2 是 Phase 3 留存 + 漏斗的前置**：必须先有 `login_log` + access log + 客户端事件上报通道，才能算 D1/D7/D30 同期群与"浏览 → 报名 → 出席"漏斗。

现状三块缺失：
1. **后端无登录行为日志**：`operation_log`（V8）只覆盖**管理端审计**（`@Around` 切 admin controller）。用户/管理员登录成功/失败都无法回溯，运营想做"近 5 分钟失败登录激增 → 暴力破解告警"无数据源。
2. **后端无 access log filter**：5 个现有 filter（AppAuth / AdminAuth / RateLimit / Xss / SecurityHeaders）都不记录请求路径+延迟+状态码。Phase 4 想做"错误率突增告警"也要先有这张表。
3. **小程序零埋点**：`utils/request.js` 仅 token 注入 + 401 重定向，无任何自定义事件上报。Phase 3 的"浏览 → 报名 → 出席"漏斗最前一环（浏览）没数据。

## What Changes

### 后端

- **V21 migration 新建 2 张表**：
  - `login_log` — 登录行为；`user_id` / `admin_id` 二选一；`channel (APP|ADMIN)`；`login_result (SUCCESS|FAIL_PWD|FAIL_BANNED|FAIL_RATE_LIMIT|FAIL_INVALID_CODE)`；`error_code`、`ip`、`device_id`、`user_agent`、`created_at`；append-only 无 `deleted_at`（与 `operation_log` 同模式）
  - `access_log` — 全量 `/api/**` 请求；`path`、`method`、`status_code`、`latency_ms`、`user_id|admin_id|null`、`ip`、`user_agent`、`created_at`
- **新建 `loginLogExecutor`**（与 `auditLogExecutor` 同形：core=2/max=4/queue=500/`CallerRunsPolicy`，thread name prefix `login-log-`）。**不**复用 audit executor——登录高峰可能拖死审计
- **`AccessLogFilter`**（`@Order(Ordered.LOWEST_PRECEDENCE - 10)`）：包所有 `/api/**`，finally 块异步写 access_log；鉴权失败也记（`user_id=null` + `status_code=401/403`）
- **新建 `IpResolver` util**：从 `OperationLogAspect.resolveIp` 抽出（`X-Forwarded-For` 第一跳优先，fallback `request.getRemoteAddr()`），admin 与 app filter 复用
- **`POST /api/app/track/event`**：小程序自定义事件上报入口。请求体 `{ name, props?, ts?, did }`；写 access_log（`path=/api/app/track/event` + `error_msg=name` 便于 SQL 检索）
- **登录点改造**：`AppAuthController.login` / `AdminAuthController.login` 成功/失败按 `errorCode` 映射后写 `login_log`

### 前端

- **`utils/tracker.js`** — 暴露 `trackEvent(name, props)` + `trackError(msg, stack)`，内部 `wx.request POST /api/app/track/event`，best-effort（失败 `console.warn`，不抛）
- **`utils/request.js`** — 在请求拦截器记录 `startTime`，响应/失败处 `trackEvent('http_request', { method, path, code, latencyMs })`
- **`app.js`** — `onLaunch` / `onError` / `onUnhandledRejection` / `onHide` 钩入 tracker；首次启动生成 `did` 存 `wx.getStorageSync`

### 文档

- `docs/RUNBOOK.md` §5.4 — login/access log 保留期（180 / 90 天）+ 监控 SQL（暴力破解告警）+ 归档模板
- `docs/RELEASE-CHECKLIST.md` §三 — V21 migration 部署后验证

## Impact

- **Affected capabilities**: 新增 `tracking` 能力（OpenSpec 之前无对应 spec）
- **Affected code**:
  - 后端新增：2 entity + 2 mapper + 2 service + 1 filter + 1 util + 1 controller + 1 dto
  - 后端修改：`AsyncConfig` / `OperationLogAspect` / `AppAuthController` / `AdminAuthController`
  - 小程序新增：1 utils/tracker.js
  - 小程序修改：utils/request.js / app.js
- **Affected API**: **不破坏**——只新增 `POST /api/app/track/event`，现有任何 endpoint 行为不变
- **Operational**:
  - 新增 MySQL 表 `login_log` / `access_log`（V21 自动迁移）
  - access_log 写入量：每请求一行；10 万 PV/天 ≈ 1000 万行/月 → Phase 3 前需评估分表或归档
  - Redis 无变化（这两张表本身不入 Redis）

## Decisions confirmed

- **数据保留期**：login_log 180 天、access_log 90 天（运营/审计合理时间窗）
- **IpResolver 抽出**：避免 admin 和 app filter 各写一份
- **track endpoint 不做 RBAC**：走 `AppAuthFilter` 自动绑 userId，未登录用户也能上报（`user_id=null`）；不引入匿名攻击向量（仅写表，DB 容量是软限制）
- **失败登录计入 login_log**：暴力破解告警的数据源；与 `RateLimitFilter` 协同（被限流的登录尝试 FAIL_RATE_LIMIT）

## Non-goals

- **Phase 3 真·留存（D1/D7/D30 同期群）SQL 与 admin 端 UI** — 等数据沉淀 ≥ 2 周
- **Phase 4 异常告警**（报名骤降 / 错误率告警）— 需 Phase 1-3 基线
- **`access_log` 表的 admin 端查询界面** — 运维先直查 DB，UI 留 Phase 5
- **`wx.reportEvent` 微信官方分析上报** — 先用自建后端接收，Phase 5 决定是否双写
- **access_log 按月分表** — 量起来再 sharding
- **登录失败发短信/邮箱告警** — 留给 Phase 4

## Phased plan（已在 Phase 1 proposal 里写过，复述）

| Phase | 状态 |
|---|---|
| Phase 1（v3.3.0） | ✅ 已合 master + tag |
| **Phase 2（本 change，v3.4.0）** | **当前** |
| Phase 3 留存 + 漏斗 | 依赖 Phase 2 上线 + 数据沉淀 ≥ 2 周 |
| Phase 4 异常告警 | 依赖 Phase 1-3 基线 |
| Phase 5 数据导出 + 微信分析对接 | 自由时序 |