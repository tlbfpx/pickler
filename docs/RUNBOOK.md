# Hey Pickler 运营 Runbook

> **目标读者**：on-call SRE / 运维工程师。**触发场景**：线上告警 / 部署后验证 / 紧急回滚。
>
> 配合 `docs/CREDENTIALS.md`（密钥管理）+ `docs/DEPLOYMENT-REQUIREMENTS.md`（部署清单）使用。

## 0. 5 分钟定位清单

| 现象 | 第一动作 | 链接 |
|---|---|---|
| 服务 5xx | 看 §1 健康检查 | [健康检查](#1-健康检查) |
| 报名/签到慢 | 看 §4 数据库指标 | [数据库](#4-mysql-指标) |
| 登录失败率飙升 | 看 §2 JWT 错误 | [鉴权](#2-鉴权指标) |
| 内存爆 | 看 §3 JVM | [JVM](#3-jvm-指标) |
| 紧急回滚 | §6 3 步 | [紧急回滚](#6-紧急回滚) |

## 1. 健康检查

### 1.1 Spring Boot Actuator（v17 起）
- `GET /actuator/health` → 200 OK 表示应用启动完成
- 子端点：`/actuator/health/liveness`、`/actuator/health/readiness`
- 仅暴露 health 端点（其他 env、beans、mappings 关闭）

### 1.2 Dockerfile HEALTHCHECK（v16B）
- 容器内每 30s ping `/actuator/health`，连续 3 次失败重启

### 1.3 手动检查
```bash
curl -fsS http://localhost:8080/actuator/health
# → {"status":"UP"}
```

## 2. 鉴权指标

### 2.1 JWT 错误
- 401 Unauthorized：token 缺失/过期/篡改
- 403 Forbidden：token 有效但角色不足
- 看 `admin_user` 表的 last_login_at 判断 brute force

### 2.2 限流
- `RateLimitFilter` 用 Redis Lua 原子计数
- 默认：登录 10/min、admin 120/min、app 60/min
- 429 频发 = 有人在刷

## 3. JVM 指标

### 3.1 关键参数
- `-XX:MaxRAMPercentage=75`（Dockerfile）
- Spring Boot 3.2 启动后约 200-400MB heap
- GC 用 G1 默认

### 3.2 关键指标
- Heap usage > 80% → 触发 dump
- Full GC 频次 > 1/hour → 内存泄漏
- Thread 数 > 500 → 线程池耗尽

## 4. MySQL 指标

### 4.1 慢查询
```sql
SELECT * FROM information_schema.PROCESSLIST 
WHERE COMMAND != 'Sleep' AND TIME > 5;
```

### 4.2 关键表
- `registration`（最大表，写多读多）
- `point_record`（PLACEMENT 一次插入 N 行）
- `match_record`（生成时 N×(N-1)/2 写入）

### 4.3 备份
- `deploy/hey_pickler_backup.sql`（dev 用）
- 生产环境需配 mysqldump cron 每日全量 + binlog 增量
- 备份位置：跨 region 对象存储，保留 ≥ 30 天

## 5. Redis 指标

### 5.1 Key 类型
- `heypickler:ratelimit:*`（限流计数器，TTL 60s）
- `heypickler:ranking:STAR:tier`（榜单缓存，TTL 5min）
- `heypickler:rankingTop5:STAR`（前 5 缓存）
- `heypickler:wx:session:{openid}`（微信 session，TTL 30min）
- `heypickler:dashboard:*`（管理端首页聚合缓存，TTL 5min；详见 §5.3）

### 5.2 内存爆
- `INFO memory` 看 used_memory_peak
- 生产基线 `maxmemory` 512MB~1GB、eviction 策略 `allkeys-lru`（见 `DEPLOYMENT-REQUIREMENTS.md` §3.3）
- `used_memory` 逼近 `maxmemory` 时先排查大 key：`redis-cli --bigkeys`

### 5.3 Dashboard 缓存（v3.3.0 起，Loop-v19 Dashboard Phase 1）

**命名空间**：`heypickler:dashboard:*`（5 类子 key）
- `heypickler:dashboard:snapshot` — 首页 KPI 快照
- `heypickler:dashboard:trends:{range}` — 时序趋势（按 range/from/to 区分）
- `heypickler:dashboard:top:{metric}:{range}:{limit}` — Top 活动排行
- `heypickler:dashboard:attendance:{range}` — 出席漏斗
- `heypickler:dashboard:compare:{metric}:{current}:{previous}` — 同比环比

**TTL**：5 分钟（300s），**不**做主动 invalidate；写操作（创建活动、报名等）不刷缓存，等 TTL 自然过期。运营数据秒级实时无业务诉求，5 分钟延迟可接受。

**SUPER_ADMIN bypass**：URL 加 `?no_cache=1` 跳过 Redis 直查 DB；OPERATOR / ADMIN 传 `no_cache=1` 静默忽略（spec R6 防止误用导致 DB 抖动）。前端 dashboard 顶部日期选择器旁对 SUPER_ADMIN 显示状态 chip 提示。

**紧急清缓存**（数据疑似 stale 或运营反馈异常）：
```bash
redis-cli --scan --pattern 'heypickler:dashboard:*' | xargs redis-cli del
```
或仅清某一类：
```bash
redis-cli --scan --pattern 'heypickler:dashboard:top:*' | xargs redis-cli del
```

**故障定位**：
- 5xx 集中在 `/api/admin/dashboard/*` → Redis 不可用 → `DashboardCache` 内部 catch 后降级直查 DB，业务应仍 200；若仍 5xx 看应用日志 `RedisConnectionFailureException`
- 运营反馈「数据不准」→ 90% 是缓存滞后，先 `DEL heypickler:dashboard:snapshot` 让下一次刷新重读

## 6. 紧急回滚

### 6.1 服务回滚（保留数据）
```bash
# 找到上一个 tag
git tag -l | sort -V | tail -5
# 回滚到上一个稳定 tag
git checkout v1.X.Y
mvn clean package -DskipTests
java -jar target/hey-pickler-server-1.X.Y.jar
```

### 6.2 数据库回滚
```bash
# 查 migration 历史
mvn flyway:info
```

> ⚠️ **Flyway 社区版不支持 `undo`**（Teams/Enterprise 付费功能）。本项目用 Boot 管理的社区版，`mvn flyway:undo` 会直接报 `Command 'undo' not found`。回滚策略：
> 1. **优先前向修复**：新增 `V(N+1)__fix_...` 迁移修正问题（Flyway 设计哲学，安全、可重入）。
> 2. **数据级回滚**：用部署前备份（§4.3 mysqldump 全量 + binlog）恢复到时间点；仅用于无法前向修复的破坏性 schema 变更。
> 3. **极端情况**（需停服）：维护窗口内手动 `flyway repair` + 点对点 SQL，必须 DBA review。

### 6.3 紧急停机
```bash
# 优雅停机
docker stop hey-pickler-server
# 强杀（不推荐）
docker kill hey-pickler-server
```

## 7. 部署后验证（Smoke Test）

### 7.1 自动化脚本
`scripts/smoke-test.sh` —— 部署后跑一遍：
- `/actuator/health` → 200
- `/api/admin/dashboard` 鉴权（401 无 token / 200 admin）
- `/api/app/events` 公开 GET 200
- `/api/admin/events/{id}/summary` 鉴权
- DB 连接（admin_user 存在）
- Redis 写入（rate-limit key）

### 7.2 人工 smoke
1. 后台 admin 登录
2. 拉一次赛事列表（分页正常）
3. 详情页 summary 卡片渲染
4. 选一批报名→批量签到
5. wxapp 拉一次首页

## 8. 升级（Upgrading）

### 8.1 后端
1. 拉新 tag
2. `mvn clean package`
3. 停旧进程，启动新进程
4. 跑 §7 smoke test

### 8.2 数据库
- Flyway 自动迁移 v1 → vN
- **新加的 migration 一定可重入**：不要在已部署环境改写已生效的 migration
- 回滚 migration 必须有对应的 `undo` SQL

### 8.3 admin UI / wxapp
- 与后端 API 兼容即可（向后兼容）
- 单独部署，无需停后端

## 9. 关键指标告警（建议）

| 指标 | 阈值 | 通知方式 |
|---|---|---|
| `/actuator/health` 失败 | 1min | PagerDuty / 钉钉 |
| `5xx 错误率` | > 1% / 5min | 钉钉告警群 |
| `API p99 延迟` | > 2s / 5min | 告警群 |
| `JVM heap usage` | > 85% | 告警群 |
| `MySQL slow query` | > 10/min | 告警群 |
| `Redis memory` | > 80% | 告警群 |
| `rate-limit 429` | > 100/min | 告警群（可能 brute force） |
| `DB 连接池等待` | > 0 | 告警群 |

## 10. 联系人

- Backend tech lead:（team 内部约定）
- DBA：（team 内部约定）
- Security：（team 内部约定）
- Vendor support：（CLAUDE.md "vendor" 段）

---

**附录**：
- CREDENTIALS.md — 密钥管理
- DEPLOYMENT-REQUIREMENTS.md — 部署清单（硬件 + 拓扑）
- scripts/smoke-test.sh — 部署后自动化验证
