# Proposal: 后台操作审计日志

## Why

后台侧边栏现有「操作日志」菜单其实路由到 `/ban-records`，显示的是用户封禁/解禁记录（`ban_record` 表），名不副实。

更严重的是：后端 8 个 admin controller 共 31 个接口，**完全没有任何审计日志** — 谁、什么时间、做了什么写操作，完全不可追溯。一旦出现误操作（删错赛事、错封用户、改错 Banner），无从追查责任人或影响面。

用户需求原文："日志要记录，谁，在什么时间，做了什么操作。"

## What Changes

- **新建 `operation_log` 表**（V8 migration）：append-only，记录操作人/时间/方法/模块/动作/目标/参数（脱敏）/状态/IP/UA/耗时
- **新增 `OperationLogAspect`**（AOP 切面）：`@Around("execution(* com.heypickler.controller.admin..*(..))")` 拦截所有 admin 写操作，零侵入 controller 代码
- **新增 `SensitiveDataUtil`**：JSON 字段级脱敏（password/token/phone/idcard/bankcard）
- **新增 `OperationLogClassifier`**：URL → module/action/target 自动分类（USER/EVENT/BANNER/ADMIN/RANKING/BAN_RECORD/AUTH/DASHBOARD/OPERATION_LOG/RAW）
- **新增 `auditLogExecutor`**：独立异步线程池（core=2, max=4, queue=500, DiscardOldestPolicy），保证审计失败绝不拖垮 admin 主流程
- **新增 `GET /api/admin/operation-logs`**：分页查询，支持按操作人/模块/状态/时间范围过滤
- **前端**：原菜单「操作日志」改名「用户日志」（路由 /ban-records 不变），同级新增「操作日志」→ 新路由 `/admin-logs`，提供过滤 + 详情 drawer

## Impact

- **Affected capabilities**: 新增 `audit` 能力；`infrastructure` 增加 V8 migration 条目
- **Affected code**:
  - 后端：`db/migration/V8__add_operation_log.sql`, `entity/OperationLog.java`, `mapper/OperationLogMapper.java`, `common/util/SensitiveDataUtil.java`, `common/util/OperationLogClassifier.java`, `common/aspect/OperationLogAspect.java`, `service/OperationLogService.java` + impl, `common/dto/OperationLogQuery.java`, `config/AsyncConfig.java`, `vo/OperationLogVO.java`, `controller/admin/AdminOperationLogController.java`
  - 前端：`components/layout/AppSidebar.vue`, `router/index.ts`, `api/admin-logs.ts`, `views/admin-logs/AdminLogListView.vue`, `views/ban-records/BanRecordListView.vue`（文案同步改名）
- **Affected API**: 新增 `GET /api/admin/operation-logs`；所有 admin POST/PUT/DELETE 行为不变但新增异步副作用（写日志）
- **Operational**: MySQL 增加 `operation_log` 表；线程池新增 audit-log-* 线程；磁盘占用按写操作量增长（每条 ~1KB，可估算）

## Decisions confirmed

- **只记写操作**（POST/PUT/DELETE），GET 查询不记（高频且无副作用，记了无价值还刷量表）
- **失败请求也记**（参数错/权限拒/异常），`status` 字段区分 SUCCESS/FAIL — 安全审计的核心需求
- **菜单同级两项**：原"操作日志"改名"用户日志"，新增"操作日志"

## Non-goals

- 不做用户端（小程序）操作日志 — 只审计后台
- 不做日志归档/冷存储策略 — 当前量级未到，后续 v2 通过 `created_at` 索引按月归档
- 不做日志导出 CSV/Excel — 当前需求只有"看到"，导出留 v2
- 不做实时日志推送（WebSocket） — 当前是查询式访问，无实时性需求
- 不重写 `AdminAuthFilter` — aspect 通过现有 `request.getAttribute("adminId"/"adminRole")` 拿操作人，零改动 filter
