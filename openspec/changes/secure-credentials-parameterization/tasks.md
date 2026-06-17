# Tasks: 凭据参数化与启动时校验

> 配套：[proposal.md](./proposal.md) · [design.md](./design.md) · [specs/auth/spec.md](./specs/auth/spec.md) · [specs/infrastructure/spec.md](./specs/infrastructure/spec.md)
>
> 目标：9 个 atomic commits，2 天工作量。每个 task = 一个 commit。每步落地后必须跑相关测试，红 → 绿 → commit。

## 1. 移除 V2 admin seed（Day 1）

- [ ] 1.1 修改 `hey-pickler-server/src/main/resources/db/migration/V2__init_data.sql`：清空 `INSERT INTO admin_user ...` 行，替换为注释说明初始 admin 改由 `AdminBootstrapper` 通过环境变量创建
- [ ] 1.2 在 `docs/CREDENTIALS.md`（任务 7.2 创建）和 V2 注释里 cross-reference
- [ ] 1.3 本地 dev 库手动跑 `mvn flyway:repair` 一次（验证 checksum 修复路径），确认无残留 admin 行
- [ ] 1.4 Commit: `feat(db): 移除 V2 admin 种子，改由 AdminBootstrapper 注入`

## 2. AesUtil 严格 key 长度校验（Day 1，TDD）

- [ ] 2.1 新建 `AesUtilTest.java`（若已有则扩展），覆盖：16/24/32 字节 OK、17 字节失败、3 字节失败、encrypt→decrypt 往返
- [ ] 2.2 跑测试确认红
- [ ] 2.3 修改 `common/util/AesUtil.java`：
  - 加 `@PostConstruct void validate()` 方法
  - `getKeySpec()` 移除零填充逻辑，直接 `new SecretKeySpec(keyBytes, "AES")`
  - 校验失败抛 `IllegalStateException`，消息包含 `openssl rand -base64 32` 提示
- [ ] 2.4 跑测试确认绿
- [ ] 2.5 Commit: `feat(aes): 严格校验 AES_KEY 长度为 16/24/32 字节，移除零填充兜底`

## 3. AdminBootstrapper（Day 1，TDD）

- [ ] 3.1 新建 `config/AdminBootstrapperTest.java`，覆盖 4 个 case：
  - 表非空 → skip
  - 表空 + env 全 → INSERT（mock PasswordEncoder 验证 bcrypt 调用）
  - 表空 + env 缺密码 → System.exit(1)（用 `SecurityManager` mock checkExit）
  - 密码 < 12 字符 → warn 但仍 INSERT
- [ ] 3.2 跑测试确认红
- [ ] 3.3 实现 `config/AdminBootstrapper.java`：
  - `implements ApplicationRunner`
  - 注入 `AdminUserMapper` + `PasswordEncoder`
  - 逻辑见 [design.md §2.1](./design.md#21-adminbootstrapper)
  - System.exit(1) 走 `ExitAction` 函数式接口（便于测试）或直接 `System.exit` + SecurityManager mock
- [ ] 3.4 跑测试确认绿
- [ ] 3.5 Commit: `feat(auth): AdminBootstrapper 启动时通过 INITIAL_ADMIN_* 环境变量创建初始管理员`

## 4. ProfileGuard（Day 1，TDD）

- [ ] 4.1 新建 `config/ProfileGuardTest.java`，覆盖 4 个 case：
  - prod profile + unique secrets → pass（验证日志输出 `Profile guard passed`）
  - prod profile + dev JWT_SECRET → exit(2)
  - PROD_GUARD=true + dev profile → exit(2)
  - dev profile 无 PROD_GUARD → pass
- [ ] 4.2 跑测试确认红
- [ ] 4.3 实现 `config/ProfileGuard.java`：
  - `implements ApplicationListener<ApplicationReadyEvent>`
  - 注入 `Environment`
  - KNOWN_DEV_JWT_SECRETS / KNOWN_DEV_AES_KEYS 常量集合
  - 逻辑见 [design.md §2.2](./design.md#22-profileguard)
- [ ] 4.4 跑测试确认绿
- [ ] 4.5 Commit: `feat(security): ProfileGuard 拦截生产环境使用 dev fallback 密钥的配置错误`

## 5. application-prod.yml 补 rate-limit 段（Day 1）

- [ ] 5.1 修改 `hey-pickler-server/src/main/resources/application-prod.yml`：添加 `hey-pickler.rate-limit` 段，参考 dev 但用更保守默认值（login=60/min, admin=120/min, admin-anon=30/min, default=60/min）
- [ ] 5.2 确认 `RateLimitFilter` 读取的 properties 路径正确（`hey-pickler.rate-limit.*`）
- [ ] 5.3 Commit: `chore(config): prod profile 补齐 rate-limit 段，避免走代码默认值`

## 6. 后端测试去 `admin123` 字面量（Day 2）

- [ ] 6.1 `OperationLogAspectTest.java`（行 ~152, 168）：把 `loginBody.put("password", "admin123")` 改为 `test-password` 任意值，断言改为 `assertThat(masked).doesNotContain("test-password")`（语义不变——本测试只验证脱敏，不依赖具体值）
- [ ] 6.2 `AuthServiceTest.java`（行 ~78, 82）：mock 时用任意 password，确认测试逻辑不依赖具体值
- [ ] 6.3 `IntegrationTestConfig.java`（行 ~55）：用 `@DynamicPropertySource` 注入测试专用 `INITIAL_ADMIN_PASSWORD=test-admin-password-123`
- [ ] 6.4 `AdminAuthIntegrationTest.java`（行 ~14）：bootstrap 后用 `test-admin-password-123` 登录，断言成功
- [ ] 6.5 `mvn test -Dtest='!*IntegrationTest'` 全绿
- [ ] 6.6 `mvn test -Dtest='*IntegrationTest'` 全绿
- [ ] 6.7 Commit: `test: 后端测试移除 admin123 字面量，改用注入的 INITIAL_ADMIN_PASSWORD`

## 7. .env.example + docs/CREDENTIALS.md（Day 2）

- [ ] 7.1 新建仓库根 `.env.example`，覆盖 15 个环境变量（DB_URL, DB_USERNAME, DB_PASSWORD, REDIS_HOST, REDIS_PORT, REDIS_PASSWORD, JWT_SECRET, AES_KEY, INITIAL_ADMIN_USERNAME, INITIAL_ADMIN_PASSWORD, WX_APPID, WX_SECRET, CORS_ADMIN_ORIGINS, SPRING_PROFILES_ACTIVE, PROD_GUARD），每个带注释说明用途和生成命令
- [ ] 7.2 新建 `docs/CREDENTIALS.md`，覆盖 4 个章节：
  - First Deployment（每个 secret 的 `openssl rand` 命令 + 设置顺序）
  - Upgrading from pre-hardening version（`mvn flyway:repair` 步骤 + AdminBootstrapper 保留现有 admin 的说明）
  - Key Rotation（JWT 轮换：所有 session 失效；AES_KEY 轮换：需重新加密所有 PII）
  - Emergency Response（怀疑泄漏：立即轮换 + 通知所有 admin）
- [ ] 7.3 Commit: `docs: 新增 .env.example 和 docs/CREDENTIALS.md，覆盖凭据生成、轮换、紧急响应`

## 8. application-prod.yml 完善 + 集成测试（Day 2）

- [ ] 8.1 修改 `application-prod.yml`：确认所有敏感字段都使用 `${ENV_VAR}` 占位符（无 fallback），包括 `JWT_SECRET`、`AES_KEY`、`DB_PASSWORD`、`REDIS_PASSWORD`、`WX_SECRET`、`INITIAL_ADMIN_PASSWORD`
- [ ] 8.2 新建 `CredentialHardeningIntegrationTest.java`：
  - Case 1: Fresh DB（H2 或 Testcontainers MySQL） + 完整 env → bootstrap 成功
  - Case 2: Fresh DB + 缺 `INITIAL_ADMIN_PASSWORD` → System.exit(1)
  - Case 3: prod profile + dev JWT secret → System.exit(2)
- [ ] 8.3 `mvn test -Dtest='*CredentialHardeningIntegrationTest'` 全绿
- [ ] 8.4 Commit: `test: 新增凭据硬化集成测试 + prod profile 完善 ${ENV_VAR} 占位符`

## 9. 前端 e2e 测试 + 最终验证（Day 2）

- [ ] 9.1 `hey-pickler-admin/e2e/fixtures/admin.fixture.ts`：改为 `process.env.E2E_ADMIN_PASSWORD || 'e2e-default-password-123'`
- [ ] 9.2 `hey-pickler-admin/e2e/auth.spec.ts` 等 6 处 e2e 同步改造（grep `admin123` 确认全部覆盖）
- [ ] 9.3 `hey-pickler-admin/e2e/README.md` 或根 `README.md` 补充说明：CI 必须设置 `E2E_ADMIN_PASSWORD` env var
- [ ] 9.4 `hey-pickler-wxapp/e2e/fixtures/flow.fixture.ts:11`：确认已是 env pattern，补充 CI 文档说明
- [ ] 9.5 手动验证（按 [proposal §Verification](./proposal.md#verification验收标准) 8 条 checklist 跑）：
  - Fresh DB + 完整 15 个 env vars → 应用启动、bootstrap admin 创建、登录正常
  - Fresh DB + 缺 `INITIAL_ADMIN_PASSWORD` → 启动 fail-fast，错误日志清晰
  - Fresh DB + `AES_KEY=short` → 启动 fail-fast
  - `spring.profiles.active=prod` + dev JWT_SECRET → fail-fast
  - `spring.profiles.active=dev` + 任何配置 → 启动正常（保留 dev fallback）
- [ ] 9.6 Commit: `test(e2e): 前端 e2e 通过 env 注入 admin 密码，CI 文档同步更新`

## 10. OpenSpec 归档

- [ ] 10.1 `openspec validate secure-credentials-parameterization --strict` 通过
- [ ] 10.2 移动 `openspec/changes/secure-credentials-parameterization/` 到 `archive/2026-06-XX-secure-credentials-parameterization/`
- [ ] 10.3 合并 spec delta：
  - 新建/更新 `openspec/specs/auth/spec.md`（加入 "Initial admin bootstrap from environment variables" requirement）
  - 更新 `openspec/specs/infrastructure/spec.md`（加入 AES key 校验、ProfileGuard、env template、CREDENTIALS.md requirements）
- [ ] 10.4 更新 `CLAUDE.md` 的 Environment Variables 段，指向 `.env.example` 和 `docs/CREDENTIALS.md`
- [ ] 10.5 Commit: `chore(openspec): 归档 secure-credentials-parameterization 变更，合并 spec delta`

## 改动文件总览

**后端修改**（5 个文件）：
- `db/migration/V2__init_data.sql` — 移除 admin INSERT
- `common/util/AesUtil.java` — 严格校验 + @PostConstruct
- `application-prod.yml` — 补 rate-limit 段 + 确认所有敏感字段用 `${ENV_VAR}`

**后端新增**（2 个文件）：
- `config/AdminBootstrapper.java` — ApplicationRunner
- `config/ProfileGuard.java` — ApplicationListener<ApplicationReadyEvent>

**后端测试**（4 改 + 3 新）：
- 改：`OperationLogAspectTest.java`, `AuthServiceTest.java`, `IntegrationTestConfig.java`, `AdminAuthIntegrationTest.java`
- 新：`AesUtilTest.java`（扩展）, `AdminBootstrapperTest.java`, `ProfileGuardTest.java`, `CredentialHardeningIntegrationTest.java`

**配置交付物**（2 新）：
- `.env.example`（仓库根）
- `docs/CREDENTIALS.md`

**前端 e2e**（7 改）：
- `admin.fixture.ts`, `auth.spec.ts` 等 6 处 + `flow.fixture.ts` 文档同步

**OpenSpec**（4 文件 + 归档）：
- proposal.md, design.md, specs/auth/spec.md, specs/infrastructure/spec.md, tasks.md（本文件）
