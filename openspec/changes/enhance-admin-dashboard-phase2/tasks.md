# Tasks: enhance-admin-dashboard-phase2

## 1. 后端 — V21 + entity/mapper + IpResolver

- [ ] 1.1 `V21__login_log_and_access_log.sql`：login_log + access_log 2 张表 + 6 个索引
- [ ] 1.2 `entity/LoginLog.java` + `entity/AccessLog.java`（MyBatis-Plus `@TableName` + 驼峰映射）
- [ ] 1.3 `mapper/LoginLogMapper.java` + `mapper/AccessLogMapper.java`（继承 BaseMapper）
- [ ] 1.4 `common/util/IpResolver.java`：抽出 `resolveIp(HttpServletRequest)` 静态方法（X-Forwarded-For 第一跳优先）
- [ ] 1.5 `common/aspect/OperationLogAspect.java` 改调 `IpResolver.resolveIp`（保持单测不破）

## 2. 后端 — Service + Executor + Filter + Track endpoint

- [ ] 2.1 `config/AsyncConfig.java` 加 `loginLogExecutor`（core=2/max=4/queue=500/CallerRunsPolicy，thread name prefix `login-log-`）
- [ ] 2.2 `service/LoginLogService.java` + `service/impl/LoginLogServiceImpl.java`：`recordLogin(LoginLog)` `@Async("loginLogExecutor")` 异步写
- [ ] 2.3 `service/AccessLogService.java` + `service/impl/AccessLogServiceImpl.java`：`recordAccess(AccessLog)` 复用 `loginLogExecutor`（access 量级 ≤ login）
- [ ] 2.4 `filter/AccessLogFilter.java`：`@Order(Ordered.LOWEST_PRECEDENCE - 10)` + finally 块异步写 + catch all 防抛
- [ ] 2.5 `controller/app/AppTrackController.java` `POST /api/app/track/event` + `dto/TrackEventRequest.java`
- [ ] 2.6 `controller/app/AppAuthController.java` 登录成功/失败处调 `loginLogService.recordLogin`
- [ ] 2.7 `controller/admin/AdminAuthController.java` 同上（channel=ADMIN）

## 3. 后端 — 单测与回归

- [ ] 3.1 `AccessLogServiceImplTest`：happy + null userId 匿名 + executor 异常降级
- [ ] 3.2 `LoginLogServiceImplTest`：4 种 FAIL_* 枚举映射 + channel 区分
- [ ] 3.3 `AccessLogFilterTest`：鉴权前/后 userId 都拿到 + 500 异常也记
- [ ] 3.4 `AppTrackControllerTest`：MockMvc POST + payload 校验 + 失败不阻塞
- [ ] 3.5 `mvn clean verify -DskipITs -Dtest='!*IntegrationTest'` BUILD SUCCESS（jacoco ≥ 80%）

## 4. 小程序 — tracker + app.js + request.js

- [ ] 4.1 `utils/tracker.js`：`trackEvent(name, props)` + `trackError(msg, stack)` + `ensureDid()` 生成 UUID 存 `wx.getStorageSync('did')`
- [ ] 4.2 `utils/request.js`：拦截器记录 `startTime`；成功/失败处调 `trackEvent('http_request', { method, path, code, latencyMs })`
- [ ] 4.3 `app.js`：`onLaunch` / `onError` / `onUnhandledRejection` / `onHide` 钩入 tracker

## 5. 文档同步

- [ ] 5.1 `docs/RUNBOOK.md` §5.4 新增"登录与访问日志"小节：保留期 180/90 天 + 暴力破解监控 SQL + 归档模板
- [ ] 5.2 `docs/RELEASE-CHECKLIST.md` §三 加 V21 表部署后验证项

## 6. PR 与 archive

- [ ] 6.1 后端 commit A：V21 + entity/mapper + IpResolver
- [ ] 6.2 后端 commit B：Service + Executor + Filter + Track endpoint + 登录点改造
- [ ] 6.3 后端 commit C：单测 + jacoco 门禁
- [ ] 6.4 小程序 commit D：tracker + app.js + request.js
- [ ] 6.5 文档 commit E：RUNBOOK + RELEASE-CHECKLIST
- [ ] 6.6 开 PR（base master），CI 全绿后 squash merge
- [ ] 6.7 `openspec archive enhance-admin-dashboard-phase2`（proposal + tasks + specs → archive/，spec delta 合入 `openspec/specs/track/spec.md`）
- [ ] 6.8 打 tag `v3.4.0`（Phase 2 完整体）

## 7. 端到端 smoke（合并后人工验证）

- [ ] 7.1 `mysql -e "SHOW TABLES LIKE '%log%'"` 看到 `login_log` / `access_log`
- [ ] 7.2 错密码登录 5 次 → `SELECT * FROM login_log WHERE login_result='FAIL_PWD'` 看到 5 条
- [ ] 7.3 访问 `/api/app/events` → `SELECT * FROM access_log WHERE path='/api/app/events'` 看到 1 条 200
- [ ] 7.4 启动 wxapp devtools → `access_log WHERE path='/api/app/track/event'` 看到 onLaunch 上报