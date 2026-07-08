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

### 5.2 内存爆
- `INFO memory` 看 used_memory_peak
- 默认 256MB 不够时调整 maxmemory-policy allkeys-lru

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
# 回滚指定 migration（注意破坏性）
mvn flyway:undo -Dflyway.undo=N
```

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
