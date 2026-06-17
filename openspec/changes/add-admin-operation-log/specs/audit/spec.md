# audit Specification

## Purpose

捕获并查询后台所有写操作的审计记录。通过 AOP 切面零侵入拦截 `com.heypickler.controller.admin..*` 的 POST/PUT/DELETE 方法，记录「谁 + 在什么时间 + 做了什么操作」，包括成功与失败两类，作为事后追责与安全审计的依据。

## ADDED Requirements

### Requirement: 操作日志表结构
系统 SHALL 通过 Flyway V8 migration 创建 `operation_log` 表，字段包括 `operator_id`（NULL 允许）、`operator_role`、`method`、`module`、`action`、`target_type`、`target_id`、`path`、`params`（脱敏 JSON）、`status`（TINYINT，1=SUCCESS, 0=FAIL）、`error_code`、`error_msg`、`ip`、`user_agent`、`latency_ms`、`created_at`。表 SHALL 为 append-only（无 `deleted_at` 字段），并建立 `idx_operator_time`、`idx_module_time`、`idx_created_at`、`idx_status_time` 四个索引。

#### Scenario: 全新数据库初始化
- **WHEN** 应用启动且数据库未应用 V8
- **THEN** Flyway SHALL 自动执行 V8 创建 `operation_log` 表与 4 个索引

#### Scenario: 表无软删除字段
- **WHEN** DBA 检查 `operation_log` 表结构
- **THEN** SHALL 不存在 `deleted_at` 字段（审计数据 append-only）

### Requirement: 捕获所有 admin 写操作
系统 SHALL 通过 `OperationLogAspect.@Around` 切面拦截 `com.heypickler.controller.admin..*` 包下所有方法，对 HTTP method 为 POST/PUT/DELETE 的请求记录审计日志，GET 请求 SHALL 被跳过。

#### Scenario: 成功的写操作
- **WHEN** 管理员 POST `/api/admin/banners` 创建 Banner 成功
- **THEN** 系统 SHALL 异步插入一条 `operation_log` 记录，`status=1`，`module=BANNER`，`action=CREATE`

#### Scenario: 业务异常的写操作
- **WHEN** 管理员 POST `/api/admin/banners` 触发 `BizException(code=1001, message="imageUrl 格式不对")`
- **THEN** 系统 SHALL 插入一条 `operation_log` 记录，`status=0`，`error_code=1001`，`error_msg="imageUrl 格式不对"`
- **AND** 原异常 SHALL 正常向上抛出（不吞业务异常）

#### Scenario: 系统异常的写操作
- **WHEN** 管理员 PUT `/api/admin/users/123` 触发 `NullPointerException`
- **THEN** 系统 SHALL 插入一条 `operation_log` 记录，`status=0`，`error_code=500`
- **AND** 原异常 SHALL 正常向上抛出

#### Scenario: GET 请求不记录
- **WHEN** 管理员 GET `/api/admin/users?page=1`
- **THEN** 系统 SHALL NOT 写入 `operation_log`（避免高频查询刷量表）

### Requirement: 审计写入失败不影响主业务
系统 SHALL 通过独立的 `auditLogExecutor` 异步线程池写入审计日志。任何写库异常（DB 不可达、连接超时等）SHALL 被切面捕获并吞掉，**绝不**传播给主业务请求。

#### Scenario: 数据库短暂不可用
- **WHEN** 主业务请求成功，但 `OperationLogService.record` 因 MySQL 不可达抛 SQLException
- **THEN** 切面 SHALL `log.error` 记录失败，但业务响应 SHALL 正常返回

#### Scenario: 异步队列饱和
- **WHEN** `auditLogExecutor` 队列（capacity=500）已满
- **THEN** `DiscardOldestPolicy` SHALL 丢弃最旧的一条审计任务以腾出空间给新任务

### Requirement: 操作人信息提取
系统 SHALL 从 `HttpServletRequest` attribute 中提取操作人：`adminId` → `operator_id`，`adminRole` → `operator_role`。当 attribute 缺失（如未登录访问 `/api/admin/auth/login`）SHALL 记 `operator_role=ANONYMOUS`、`operator_id=NULL`。

#### Scenario: 已认证管理员操作
- **WHEN** `AdminAuthFilter` 已在 request 中放入 `adminId=1, adminRole=SUPER_ADMIN`
- **THEN** 切面 SHALL 把 `operator_id=1, operator_role=SUPER_ADMIN` 写入审计记录

#### Scenario: 登录接口（未认证）
- **WHEN** 任意用户 POST `/api/admin/auth/login`（filter 跳过该路径）
- **THEN** 切面 SHALL 记 `operator_role=ANONYMOUS, operator_id=NULL`，捕获登录尝试用于安全审计

### Requirement: IP 与 UA 提取
系统 SHALL 按以下优先级提取客户端 IP：`X-Forwarded-For` 首段（多级代理）→ `request.getRemoteAddr()`。`User-Agent` 头 SHALL 原样记录并截断到 512 字符。

#### Scenario: 经过反向代理
- **WHEN** 请求头 `X-Forwarded-For: 1.2.3.4, 10.0.0.1`
- **THEN** 切面 SHALL 记 `ip=1.2.3.4`（取首段）

#### Scenario: 直连请求
- **WHEN** 请求无 `X-Forwarded-For` 头
- **THEN** 切面 SHALL 记 `ip=request.getRemoteAddr()`

### Requirement: 请求参数脱敏
系统 SHALL 在写入审计日志前对请求参数 JSON 进行字段级脱敏，由 `SensitiveDataUtil.maskJson` 实现。脱敏字段名（大小写不敏感 contains）：`password|passwd|token|secret|apikey|accesstoken|refreshtoken` → `***`；`phone|mobile` → 前 3 + `****` + 后 4；`idcard` → 前 4 + `*` + 后 2；`bankcard` → `****` + 后 4。参数 SHALL 截断到 2000 字符。

#### Scenario: 普通字段不脱敏
- **WHEN** 请求参数 `{"name": "活动 A"}`
- **THEN** 审计 `params` 字段 SHALL 保留原文 `{"name":"活动 A"}`

#### Scenario: password 被脱敏
- **WHEN** 请求参数 `{"username": "admin", "password": "admin123"}`
- **THEN** 审计 `params` 字段 SHALL 是 `{"username":"admin","password":"***"}`

#### Scenario: 嵌套 JSON 脱敏
- **WHEN** 请求参数 `{"user": {"phone": "13800138000"}}`
- **THEN** 审计 `params` 字段 SHALL 是 `{"user":{"phone":"138****8000"}}`

#### Scenario: JSON 解析失败 fallback
- **WHEN** 参数序列化后不是合法 JSON（极少见）
- **THEN** `SensitiveDataUtil` SHALL 返回截断后的原文，不抛异常

### Requirement: URL → module/action 分类
系统 SHALL 通过 `OperationLogClassifier.classify(method, path)` 把请求路径映射为 `(module, action, target_type, target_id)`。已知 module 映射：users→USER, events→EVENT, banners→BANNER, admins/admin-users→ADMIN, rankings→RANKING, ban-records→BAN_RECORD, dashboard→DASHBOARD, auth→AUTH, operation-logs→OPERATION_LOG。未知路径 SHALL 标 `module=RAW, action=RAW`，但 `path` 字段仍保留完整路径用于取证。

#### Scenario: POST /api/admin/users
- **WHEN** classifier 收到 `("POST", "/api/admin/users")`
- **THEN** SHALL 返回 `(module=USER, action=CREATE, target_type=USER, target_id=null)`

#### Scenario: POST /api/admin/users/{id}/ban
- **WHEN** classifier 收到 `("POST", "/api/admin/users/123/ban")`
- **THEN** SHALL 返回 `(module=USER, action=BAN, target_type=USER, target_id="123")`

#### Scenario: PUT /api/admin/banners/{id}
- **WHEN** classifier 收到 `("PUT", "/api/admin/banners/45")`
- **THEN** SHALL 返回 `(module=BANNER, action=UPDATE, target_type=BANNER, target_id="45")`

#### Scenario: DELETE /api/admin/events/{id}
- **WHEN** classifier 收到 `("DELETE", "/api/admin/events/9")`
- **THEN** SHALL 返回 `(module=EVENT, action=DELETE, target_type=EVENT, target_id="9")`

#### Scenario: POST /api/admin/auth/login
- **WHEN** classifier 收到 `("POST", "/api/admin/auth/login")`
- **THEN** SHALL 返回 `(module=AUTH, action=LOGIN, target_type=null, target_id=null)`

#### Scenario: 未知路径
- **WHEN** classifier 收到 `("POST", "/api/admin/something-new")`
- **THEN** SHALL 返回 `(module=RAW, action=RAW, target_type=null, target_id=null)`

#### Scenario: 路径含 query string
- **WHEN** classifier 收到 `("DELETE", "/api/admin/users/123?force=true")`
- **THEN** SHALL 剥离 query string，按 `/api/admin/users/123` 分类

### Requirement: 审计日志查询接口
系统 SHALL 提供 `GET /api/admin/operation-logs` 端点供 SUPER_ADMIN / ADMIN / OPERATOR 查询审计日志，支持 `page`、`size`、`operatorId`、`module`、`action`、`status`、`startTime`、`endTime` 8 个查询参数。响应 SHALL 在 `OperationLogVO` 中附加 `operatorName` 字段（通过 `AdminUserMapper.selectBatchIds` 批量回填，避免 N+1）。

#### Scenario: 分页查询
- **WHEN** SUPER_ADMIN 发送 `GET /api/admin/operation-logs?page=1&size=20`
- **THEN** 系统 SHALL 返回 `{ code: 0, data: { total, page: 1, size: 20, list: [...] } }`，按 `created_at DESC` 排序

#### Scenario: 按模块过滤
- **WHEN** 发送 `GET /api/admin/operation-logs?module=USER`
- **THEN** 系统 SHALL 只返回 `module=USER` 的记录

#### Scenario: 按状态过滤
- **WHEN** 发送 `GET /api/admin/operation-logs?status=0`
- **THEN** 系统 SHALL 只返回 `status=0`（失败）的记录

#### Scenario: 按时间范围过滤
- **WHEN** 发送 `GET /api/admin/operation-logs?startTime=2026-06-01T00:00:00&endTime=2026-06-17T23:59:59`
- **THEN** 系统 SHALL 只返回该时间段内的记录

#### Scenario: operator_name 批量回填
- **WHEN** 返回 20 条记录涉及 3 个不同的 operator_id
- **THEN** 系统 SHALL 通过单次 `selectBatchIds([id1, id2, id3])` 回填 username

#### Scenario: operator 已被删除
- **WHEN** 某条记录的 `operator_id` 在 `admin_user` 表中已不存在
- **THEN** `operatorName` SHALL 为 `null`（前端显示 "-"）

#### Scenario: 非 admin 角色访问
- **WHEN** 未认证用户或 app 用户访问该端点
- **THEN** 系统 SHALL 返回 401/403（由 `AdminAuthFilter` + `@RequireRole` 拦截）

### Requirement: 前端审计日志页面
系统 SHALL 在 admin 前端提供 `/admin-logs` 路由，渲染 `AdminLogListView.vue`，包含过滤器（操作人 ID / 模块下拉 / 状态下拉 / 时间范围 / 搜索 + 重置）、表格（时间 / 操作人 / 方法 / 模块 / 动作 / 目标 / 状态 / 耗时 / IP / 路径）、行点击 `<el-drawer>` 渲染详情。原 `/ban-records` 菜单的文案 SHALL 从「操作日志」改为「用户日志」。

#### Scenario: 新增侧边栏菜单
- **WHEN** 任意角色管理员登录后台
- **THEN** 侧边栏 SHALL 显示「操作日志」菜单项（指向 `/admin-logs`）

#### Scenario: 原 ban-records 菜单文案
- **WHEN** 任意角色管理员登录后台
- **THEN** 侧边栏 `/ban-records` 菜单文案 SHALL 为「用户日志」（不再叫「操作日志」）

#### Scenario: 行点击查看详情
- **WHEN** 管理员点击表格某行
- **THEN** 右侧 SHALL 弹出 drawer 显示 `el-descriptions`（含时间/操作人/角色/方法/模块/动作/目标/状态/IP/耗时/路径/User-Agent/错误）+ 脱敏后的 params JSON `<pre>` 块
