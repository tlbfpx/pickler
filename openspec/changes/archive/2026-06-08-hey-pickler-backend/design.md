## Context

Hey Pickler 是全国性匹克球赛事活动管理平台，包含微信小程序（用户端）和 Web 后台管理系统（管理端）。当前已有完整的设计文档和实现计划，需要从零构建 Spring Boot 后端服务。

**技术约束**：单体分层架构，Java 17 + Spring Boot 3.x + MyBatis-Plus + MySQL 8 + Redis。用户规模十万级，峰值 1000 QPS。

**现有文档**：
- 系统设计文档：`docs/superpowers/specs/2026-06-08-pickleball-system-design.md`
- 后端实现计划：`docs/superpowers/plans/2026-06-08-backend-implementation.md`

## Goals / Non-Goals

**Goals:**

- 构建完整可运行的后端 API，覆盖小程序端 13 个 + 管理端 14 个端点
- 实现微信登录 + 管理员登录的 JWT 双轨鉴权
- 实现 Star/Party 双轨积分体系，异步排名重算
- 段位自动计算（Legend Star / Super Star / Shining Star）
- 排名数据 Redis 缓存，积分变更后主动失效
- 管理员三级角色权限（SUPER_ADMIN / ADMIN / OPERATOR）
- 统一错误码、全局异常处理、接口限流

**Non-Goals:**

- 商城模块（二期）
- 实时比分推送（二期）
- 消息推送（二期）
- 数据分析统计（二期）
- 前端开发（小程序 + 管理后台另行开发）
- 分布式架构改造（单实例足够）

## Decisions

### D1: 单体分层架构

**选择**: Controller → Service → Repository 三层，单 Spring Boot 应用

**替代方案**: 微服务（Spring Cloud）

**理由**: 用户规模十万级、峰值 1000 QPS，单体完全满足。避免微服务带来的运维复杂度和分布式事务问题。后期如有瓶颈可按模块拆分。

### D2: 赛事/活动共用 event 表

**选择**: 一张 event 表，`type` 字段区分 STAR/PARTY

**替代方案**: 独立的 event 表和 activity 表

**理由**: 两者字段高度重叠（标题、封面、时间、地点、报名、费用），合并减少重复代码和 JOIN。通过 `type` 字段在 Service 层做差异化处理（如 Party 报名费常为 0）。

### D3: 排名异步重算（Spring Event + @Async）

**选择**: 积分写入后发布 Spring ApplicationEvent，由 @Async 监听器消费，重算排名快照并清除 Redis 缓存

**替代方案**: 消息队列（RabbitMQ/Kafka）

**理由**: 单体架构下 Spring Event 足够可靠，无需引入外部 MQ。同一事务内发布事件，监听器异步执行。若后续拆分微服务可替换为 MQ。

### D4: JWT 双轨鉴权（独立 Filter）

**选择**: AppAuthFilter 和 AdminAuthFilter 两个独立 Filter，分别拦截 `/api/app/*` 和 `/api/admin/*`

**替代方案**: 单一 Filter + 角色判断

**理由**: 两套认证逻辑完全不同（微信 openid vs 用户名密码），独立 Filter 职责清晰，互不干扰。Token 格式相同（JWT），但签发来源和校验逻辑分离。

### D5: 排名缓存策略（Cache-Aside + 主动 DELETE）

**选择**: 查询时先读 Redis，miss 则查 MySQL 并回填；积分变更后主动 DELETE 缓存 key，TTL 5 分钟兜底

**替代方案**: 纯 TTL 过期 / Write-Through

**理由**: 排名数据变更不频繁（仅管理员录入积分后），主动 DELETE 保证数据一致性的同时避免每次查询都穿透。TTL 兜底防止极端情况缓存脏数据。

### D6: 手机号 AES 加密存储

**选择**: AES 对称加密存储手机号，密钥配置在 application.yml

**替代方案**: 不加密 / SHA 哈希

**理由**: 手机号属于个人隐私数据，AES 加密后可解密（管理端需查看），SHA 不可逆不适合。密钥不进代码仓库，通过环境变量注入。

### D7: 软删除（Event 仅管理端）

**选择**: event 表使用 `deleted_at` 字段软删除，仅管理端可操作

**理由**: 赛事/活动数据有业务价值（历史记录、积分关联），硬删除会导致数据不一致。小程序端查询自动过滤 `deleted_at IS NULL`。

## Risks / Trade-offs

**[Ranking 一致性]** 异步重算在应用重启时会丢失未处理的事件 → 积分写入和排名重算在同一事务内完成核心部分（积分累加 + 段位更新），仅排名快照刷新为异步。应用重启后快照可在下次查询时触发重算。

**[Token 安全]** JWT 无法主动失效 → 小程序端 Token 7 天有效，依赖过期自然失效。管理端 Token 额外存 Redis，支持强制下线。

**[并发报名]** 高并发下报名人数可能超限 → 使用数据库 `UPDATE event SET current_participants = current_participants + 1 WHERE id = ? AND current_participants < max_participants` 原子操作，返回影响行数为 0 则拒绝。

**[微信 API 依赖]** code2Session 接口故障会阻断登录 → 设置合理超时（3s），失败返回明确错误码，前端引导重试。

**[缓存穿透]** 排名查询大量 miss → 布隆过滤器不必要（数据量小），空结果缓存短 TTL（1 分钟）即可。
