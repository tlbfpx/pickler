# Design: 后台操作审计日志

## 总体思路

**AOP 切面零侵入捕获 + 异步独立线程池写入 + 双重保险防审计失败**：

1. 切面拦截 controller 方法，业务代码完全感知不到
2. 异步线程池与业务线程池隔离，审计失败只能丢审计数据，不能拖垮业务请求
3. 脱敏在切面内完成（信任边界内部），写入前参数已安全

## 模块改动

### 1. 表结构 — `operation_log`

```sql
CREATE TABLE operation_log (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  operator_id BIGINT UNSIGNED NULL,           -- NULL = 未登录（如登录失败）
  operator_role VARCHAR(32) NOT NULL DEFAULT 'ANONYMOUS',
  method VARCHAR(8) NOT NULL,                  -- GET/POST/PUT/DELETE
  module VARCHAR(32) NOT NULL,                 -- USER/EVENT/BANNER/...
  action VARCHAR(64) NOT NULL,                 -- CREATE/UPDATE/DELETE/BAN/...
  target_type VARCHAR(32) NULL,                -- 单数形式：USER/EVENT/...
  target_id VARCHAR(64) NULL,                  -- 路径中的 {id} 段
  path VARCHAR(255) NOT NULL,                  -- 完整路径用于取证
  params TEXT NULL,                            -- 脱敏后的请求参数 JSON
  status TINYINT NOT NULL,                     -- 1=SUCCESS, 0=FAIL
  error_code INT NULL,                         -- BizException.code 或 500
  error_msg VARCHAR(512) NULL,
  ip VARCHAR(64) NULL,
  user_agent VARCHAR(512) NULL,
  latency_ms INT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_operator_time (operator_id, created_at),
  KEY idx_module_time (module, created_at),
  KEY idx_created_at (created_at),
  KEY idx_status_time (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

**决策**：

- **无 `deleted_at`** — 审计数据 append-only，不允许删除（合规要求）
- **无外键** — operator_id 与 admin_user 解耦，删管理员不影响历史审计
- **`target_id` 用 VARCHAR(64)** 而非 BIGINT — 部分 target（如 batch 操作）可能是组合 ID
- **`error_msg` 限 512 字符** — 避免异常堆栈撑爆字段；超长截断
- **`params` 用 TEXT** — 截断到 2000 字符（业务上足够还原请求），避免 longtext 拖慢查询
- **4 个索引对应 UI 查询模式** — 按操作人/模块/状态/时间范围过滤 + 默认时间倒序

### 2. 捕获 — `OperationLogAspect`

```java
@Around("execution(* com.heypickler.controller.admin..*(..))")
public Object around(ProceedingJoinPoint pjp) throws Throwable {
    HttpServletRequest request = currentRequest();
    if (request == null) return pjp.proceed();        // 非 HTTP 上下文，跳过
    if ("GET".equalsIgnoreCase(request.getMethod())) return pjp.proceed();  // GET 不记

    long start = System.currentTimeMillis();
    OperationLog entry = buildBaseLog(request, pjp);

    try {
        Object result = pjp.proceed();
        entry.setStatus(1);
        return result;
    } catch (BizException be) {
        entry.setStatus(0);
        entry.setErrorCode(be.getCode());
        entry.setErrorMsg(truncate(be.getMessage(), 512));
        throw be;
    } catch (Throwable t) {
        entry.setStatus(0);
        entry.setErrorCode(500);
        entry.setErrorMsg(truncate(t.getMessage(), 512));
        throw t;
    } finally {
        entry.setLatencyMs((int)(System.currentTimeMillis() - start));
        try {
            operationLogService.record(entry);        // @Async — 不阻塞请求
        } catch (Exception e) {
            log.error("audit write failed for path={}", request.getRequestURI(), e);
            // 关键：吞掉所有写库异常，绝不让审计失败拖垮 admin 请求
        }
    }
}
```

**`buildBaseLog` 提取**：

- `operatorId` ← `request.getAttribute("adminId")`（`AdminAuthFilter` 已注入）
- `operatorRole` ← `request.getAttribute("adminRole")`，缺失默认 `ANONYMOUS`（如登录失败）
- `ip` ← `X-Forwarded-For` 首段（多级代理场景），fallback 到 `request.getRemoteAddr()`
- `userAgent` ← `request.getHeader("User-Agent")`，截断到 512 字符
- `params` ← `pjp.getArgs()` → Jackson 序列化 → `SensitiveDataUtil.maskJson` → 截断到 2000 字符

**决策**：

- **`@Around` 而非 `@Before`/`@AfterReturning`/`@AfterThrowing`** — 一个 advice 同时覆盖成功/失败两条路径，状态机简单
- **`finally` 写库** — 保证成功/失败都落审计，但不能让写库异常覆盖业务异常（双重保险）
- **吞写库异常** — 审计是辅助系统，主业务必须正常返回。丢审计数据可接受，丢业务响应不可接受
- **GET 跳过** — 高频查询记了无价值（90% 是 dashboard 列表/筛选项），但会撑爆日志表

### 3. 异步执行器 — `auditLogExecutor`

```java
@Bean("auditLogExecutor")
public ThreadPoolTaskExecutor auditLogExecutor() {
    ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
    exec.setCorePoolSize(2);
    exec.setMaxPoolSize(4);
    exec.setQueueCapacity(500);
    exec.setThreadNamePrefix("audit-log-");
    exec.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardOldestPolicy());
    exec.initialize();
    return exec;
}
```

**决策**：

- **独立线程池** — 与 `rankingExecutor`（核心业务异步）隔离，审计突发不会饿死排名刷新
- **`DiscardOldestPolicy`** — 队列满时丢最老的一条保最新的。审计数据有"近期比远期重要"的特性（运营查日志默认看最近）
- **core=2, max=4** — 审计写入是低耗时操作（单条 INSERT），2 个线程足够处理常规并发
- **queue=500** — 缓冲业务突发（如批量导入），峰值后队列消化
- **`OperationLogServiceImpl.record()` 标 `@Async("auditLogExecutor")`** — 必须从外部 bean 调用（aspect 调 service），自调用会导致代理失效

### 4. URL 分类器 — `OperationLogClassifier`

正则 `^/api/admin/([^/]+)(?:/([^/]+))?(?:/([^/]+))?` 拆段，按下表分类：

| 第一段 | module | singular |
|--------|--------|----------|
| users | USER | USER |
| events | EVENT | EVENT |
| banners | BANNER | BANNER |
| admins / admin-users | ADMIN | ADMIN |
| rankings | RANKING | RANKING |
| ban-records | BAN_RECORD | BAN_RECORD |
| dashboard | DASHBOARD | DASHBOARD |
| auth | AUTH | — |
| operation-logs | OPERATION_LOG | OPERATION_LOG |
| 其他 | RAW | — |

**动作推断**（按 method + 末段）：

| method | 末段 | action |
|--------|------|--------|
| POST | (无 id) | CREATE |
| POST | `{id}/ban` | BAN |
| POST | `{id}/unban` | UNBAN |
| POST | `{id}/enter-points` | ENTER_POINTS |
| POST | `{id}/refresh` | REFRESH |
| POST | auth/login | LOGIN |
| POST | auth/logout | LOGOUT |
| PUT/PATCH | `{id}` | UPDATE |
| DELETE | `{id}` | DELETE |
| 无法匹配 | — | RAW |

**决策**：

- **正则拆段而非 spring `RequestMappingHandlerMapping` 反查** — 简单可控，且不需要 `RequestContextHolder` 异步传递
- **未识别不丢，标 RAW** — `path` 字段仍保留完整路径用于取证；RAW 行为可观测，运营反馈后扩充映射
- **`targetType` 是单数**（USER 而非 USERS） — 与 module 同值是巧合（USER→USER），不同值时区分（如 BAN_RECORD 模块的 target 通常是 USER）

### 5. 脱敏 — `SensitiveDataUtil`

`maskJson(String raw)` 解析 Jackson `JsonNode`，递归遍历：

- `password|passwd|token|secret|apikey|accesstoken|refreshtoken` → `"***"`（字段名 contains，大小写不敏感）
- `phone|mobile` → `138****1234`（长度 ≤ 7 返回 `"***"`，避免短号场景）
- `idcard` → `1101**********01`（前 4 + 中间掩 + 后 2）
- `bankcard` → `****4321`（后 4 位）
- 解析失败 fallback 到截断原文

**决策**：

- **Jackson 而非正则替换** — 正则在嵌套 JSON 上不可靠（字符串里恰好含 "password" 也会被换）；AST 遍历精确
- **字段名 contains 而非 equals** — 兼容 `oldPassword`、`accessToken`、`api_key`（snake）等命名
- **大小写不敏感** — 兼容 `Password` / `PASSWORD` / `password`
- **手机号长度 < 7 直接 mask 成 `***`** — 防止 4 位验证码场景反向泄露
- **解析失败不抛异常** — 老数据/非 JSON 入参直接截断保留，比 reject 强

### 6. 查询 — `GET /api/admin/operation-logs`

```java
@RequireRole({SUPER_ADMIN, ADMIN, OPERATOR})
@GetMapping
public Result<PageResult<OperationLogVO>> list(
    @RequestParam(defaultValue = "1") int page,
    @RequestParam(defaultValue = "20") int size,
    @RequestParam(required = false) Long operatorId,
    @RequestParam(required = false) String module,
    @RequestParam(required = false) String action,
    @RequestParam(required = false) Integer status,
    @RequestParam(required = false) @DateTimeFormat String startTime,
    @RequestParam(required = false) @DateTimeFormat String endTime
)
```

**`OperationLogVO`** 在 entity 基础上增加 `operatorName`（通过 `AdminUserMapper.selectBatchIds` 批量回填，避免 N+1）。

**决策**：

- **OPERATOR 角色也能看** — 不仅仅是管理员，运营人员需要看自己的操作历史
- **批量回填 operatorName** — 一次 `selectBatchIds` 取本页所有 operator_id 对应的 username，避免 N+1
- **operator_name 不存表** — 避免 admin 改名后历史日志不一致（join 临时回填即可）

### 7. 前端

- `AppSidebar.vue`：`/ban-records` 菜单文案「操作日志」→「用户日志」；下方新增 `<el-menu-item index="/admin-logs">操作日志</el-menu-item>`（使用 `List` icon）
- `router/index.ts`：新增 `admin-logs` 子路由 → `AdminLogListView.vue`
- `api/admin-logs.ts`：`getOperationLogs(params)` + `OperationLogItem` / `OperationLogQuery` 类型
- `views/admin-logs/AdminLogListView.vue`：
  - 过滤器：操作人 ID 输入 + 模块下拉 + 状态下拉 + 时间范围 datetimerange + 搜索/重置
  - 表格列：时间 / 操作人（双行 nickname + id·role） / 方法（彩色 tag） / 模块 / 动作 / 目标（type #id） / 状态（成功/失败 + 失败时显示 error_code:msg） / 耗时 / IP / 路径
  - 行点击 `<el-drawer>` 渲染 `el-descriptions` + 脱敏后的 params JSON `<pre>`
- `views/ban-records/BanRecordListView.vue`：H1 与提示文案同步改名「用户日志」

## 边界情况

### 登录接口 `/api/admin/auth/login`

`AdminAuthFilter.shouldNotFilter = true`，adminId attribute 缺失 → 记 `operator_id=null, operator_role=ANONYMOUS`。

**审计价值高** — 捕获暴力破解、错密码尝试。保留记录。

### 大 payload（批量导入等）

`params` 截断到 2000 字符 + 不加后缀（截断行为本身在字段说明中标注）。**不**加 `...[truncated]` 后缀避免破坏 JSON 解析。

### 审计 executor 饱和

`DiscardOldestPolicy` 丢老数据保新数据。这是显式决策：审计场景下"最近发生了什么"比"完整保留"更重要。

### `error_msg` 含敏感信息

切面内对 `error_msg` 截断到 512 字符。**不**做字段级脱敏 — 异常消息通常是业务码（"报名已满"）或参数错误（"imageUrl 格式不对"），泄露密码的可能性低；脱敏会破坏可读性。

### 异步切面内拿不到 `HttpServletRequest`

通过 `RequestContextHolder.getRequestAttributes()` 同步获取。切面是同步执行的（aspect 调 controller 同线程），request attributes 在线程上下文内可用；写库才是异步，那时 request 已经被复制到 `OperationLog` 实体里，不再依赖。

## 关键决策

| 决策点 | 选择 | 拒绝的备选 | 理由 |
|--------|------|-----------|------|
| 捕获方式 | AOP 切面 | 改每个 controller | 零侵入，新加 controller 自动覆盖 |
| 范围 | 仅 POST/PUT/DELETE | 包含 GET | GET 高频低值，记了刷量表 |
| 失败请求 | 也记 | 只记成功 | 安全审计核心需求 |
| 异步 | 独立 executor + `@Async` | 同步写 / 主 executor | 隔离故障域，主业务永不阻塞 |
| 拒绝策略 | DiscardOldestPolicy | AbortPolicy/CallerRuns | 审计近期优先于完整 |
| 脱敏 | Jackson AST + 字段名 contains | 正则替换 / 不脱敏 | 精确、可靠、覆盖嵌套 |
| `operator_id` 解耦 | 无外键 | FK 约束 | 删管理员不影响历史审计 |
| `operator_name` 回填 | 查询时 join | 存表 | 避免改名不一致 |
| `params` 长度 | 2000 字符截断 | 不限 / longtext | 业务够用，避免拖慢 |
| 异常分支 | `BizException` + 其他 throwable 分支 | 只 catch Exception | 区分业务码与系统错误 |
| `operator_role` 默认 | ANONYMOUS | 抛异常跳过 | 登录失败也要记 |

## 风险

| 风险 | 缓解 |
|------|------|
| 切面性能开销（每个写请求多一次反射 + Jackson 序列化） | Jackson 已被验证为低开销；序列化 ~微秒级；params 在异步线程中处理 |
| MySQL 写放大（高峰期 INSERT 频繁） | 异步线程池削峰；后续可通过批写优化（v2） |
| 异步丢日志导致审计不完整 | DiscardOldestPolicy 仅在队列满（500 条）时触发，正常情况下不丢；监控 `audit-log-*` 线程池的 queue size 可加告警（v2） |
| 切面在测试中难以验证 | 用 MockHttpServletRequest + `RequestContextHolder` 直接驱动 aspect，不依赖 `@SpringBootTest` |
| admin 用户被删除后历史日志的 operator_name 查不到 | VO 容忍 null name；前端显示"-" |
| 暴力破解登录产生大量 ANONYMOUS 日志撑表 | 已有 `RateLimitFilter` 限流；ANONYMOUS 日志本身就是取证用途，保留有意义 |

## 测试策略

| 测试 | 类型 | 覆盖 |
|------|------|------|
| `SensitiveDataUtilTest` | unit | 各敏感字段名、嵌套 Map/List、JSON 解析失败、大 payload |
| `OperationLogClassifierTest` | unit | 8 个 module 映射、CREATE/BAN/UNBAN/UPDATE/DELETE/ENTER_POINTS/REFRESH/LOGIN/LOGOUT、未知路径 fallback、query string 剥离 |
| `OperationLogAspectTest` | unit（Mockito） | 成功→status=1 / BizException→status=0+code / RuntimeException→status=0+500 / GET 跳过 / login 端点 ANONYMOUS / 审计写库失败不传播 / XFF 首段 / params 截断 |
| `AdminOperationLogControllerTest` | unit（Mockito） | 分页 + 4 种过滤、operator_name 批量回填、null operator 容错 |
| 集成测试 | 真实 MySQL（项目用 dev 库 3306） | 端到端：POST `/api/admin/banners` → 查 `operation_log` 表有 1 行 status=1 |
