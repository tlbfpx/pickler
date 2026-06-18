# Proposal: 凭据参数化与启动时校验

> **Status: Phase 1 交付 · Day 1-2** — 客户源码交付前最高优先级硬化。是 5 个交付 change 中的第 1 个。

## Why

当前代码库有 4 处凭据硬阻塞，**任何一处都让客户拿到代码就能登入生产后台或解密用户数据**：

1. **`V2__init_data.sql`** 直接 INSERT `admin` 用户，密码 `admin123`（bcrypt 哈希公开）。客户首次部署 Fresh DB 时会自动创建这个账号，任何拿到源码的人都能登入 SUPER_ADMIN。
2. **`application-dev.yml`** 所有敏感字段都有「dev fallback」：`JWT_SECRET=HeyPickler2026DevSecretK3y!MustChangeInProd!!`、`AES_KEY=PicklerDevAesKey16`、`DB_PASSWORD=root`、`WX_SECRET=your-secret`。`application.yml` 默认 `spring.profiles.active: dev`——如果运维忘记切 prod profile，应用会用这些公开值跑起来。
3. **`AesUtil.getKeySpec()`** 把短 key 静默零填充到 32 字节：`System.arraycopy(keyBytes, 0, aesKey, 0, Math.min(keyBytes.length, 32))`。意味着 `AES_KEY=abc` 也能跑，但实际只用 3 字节熵 + 29 字节零，加密强度等于无。这是真正的安全 bug，不是「未来再说」。
4. **`prod` profile 缺 `hey-pickler.rate-limit` 配置**——`application-prod.yml` 没有这一段，prod 跑起来限流走默认值（依赖代码 fallback），不可控。

附加：4 个后端单测 + 7 个 e2e 用例直接 hardcode `admin123`，凭据参数化后必须同步改造。

## What Changes

### 后端

#### 1. 移除 `V2__init_data.sql` 的 admin seed

V2 改为只 seed 非敏感数据（如果有的话；当前 V2 只有这一行 INSERT）。**直接修改 V2，不新建 V9**——理由：项目还没有真实生产部署，Flyway checksum 冲突只影响现有 dev 环境（`mvn flyway:repair` 一次性修复），换来 forward-only 干净路径值得。

> ⚠️ Open question：现有 dev 环境已经依赖 `admin/admin123` 登入。V2 移除后，dev profile 需要 ApplicationRunner（见下）也跑一遍 bootstrap，否则 dev 也没 admin 了。

#### 2. 新建 `AdminBootstrapper` ApplicationRunner

启动时执行：
- `SELECT COUNT(*) FROM admin_user`
- 如果 = 0：
  - 读 `INITIAL_ADMIN_USERNAME`（默认 `admin`）+ `INITIAL_ADMIN_PASSWORD`（无默认）
  - 两者都设置 → bcrypt 哈希后 INSERT，日志记录「Initial admin created from env vars」
  - 密码缺失 → **fail-fast**：`System.exit(1)` + 错误日志「No admin user exists and INITIAL_ADMIN_PASSWORD env var not set」
- 如果 > 0：日志 DEBUG「admin_user table not empty, skipping bootstrap」，跳过

不区分 prod/dev profile——dev 也用同一套（dev 环境的 `.env` 提供 `INITIAL_ADMIN_PASSWORD=devpassword123`，写到 `.env.example`）。

#### 3. 加严 `AesUtil` key 校验

把 `getKeySpec()` 改成严格校验：
```java
private SecretKeySpec getKeySpec() {
    byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
    if (keyBytes.length != 16 && keyBytes.length != 24 && keyBytes.length != 32) {
        throw new IllegalStateException(
            "AES_KEY must be exactly 16, 24, or 32 bytes (got " + keyBytes.length + ")");
    }
    return new SecretKeySpec(keyBytes, "AES");
}
```
+ `@PostConstruct` 触发校验，启动时失败，不留到运行时。

#### 4. 启动时 prod profile 自检

新建 `ProfileGuard` ApplicationListener：
- `spring.profiles.active=dev` 且 `WX_DEV_MODE!=true` → warn 日志（dev profile 但没显式开 wx dev mode）
- `spring.profiles.active=dev` 且环境变量 `PROD_GUARD=true`（CI/部署环境设） → **fail-fast**（dev profile 在生产环境跑）
- `JWT_SECRET` / `AES_KEY` / `DB_PASSWORD` 等于已知 dev fallback 值 → warn 日志（dev profile 下）/ **fail-fast**（prod profile 下）

#### 5. 补齐 `application-prod.yml` 的 `rate-limit` 配置

把 `hey-pickler.rate-limit` 段从 dev 拷到 prod，使用合理默认值（参考 README 建议）：
```yaml
hey-pickler:
  rate-limit:
    login: 60        # 每分钟每 IP
    admin: 120       # 每分钟每 admin
    admin-anon: 30
    default: 60
```

### 配置交付物

#### 6. `.env.example`

仓库根目录新增 `.env.example`，列出所有 prod 部署需要的环境变量（11 个），每个带注释说明用途、生成方式、最小长度要求。`.gitignore` 已经包含 `.env`，不会被误提交。

#### 7. `docs/CREDENTIALS.md`

新增凭据管理文档：
- 首次部署：怎么生成强 JWT_SECRET / AES_KEY（`openssl rand -base64 48`）
- 如何设置 `INITIAL_ADMIN_PASSWORD`（生成一次性密码、首次登录后改）
- 密钥轮换流程（JWT / AES 各自的影响面 + 步骤）
- 紧急响应：怀疑 JWT 泄漏怎么办（轮换 → 失效所有 token → 通知所有 admin 重登）

### 测试改造

#### 8. 后端测试：去 `admin123` 字面量

- `OperationLogAspectTest.java:152,168`：把 `admin123` 改成 `loginBody.put("password", "test-password")` 等任意值，断言改成 `contains("test-password")` 反向（不出现）— 这个测试本来就只验证脱敏，不依赖具体值
- `AuthServiceTest.java:78,82`：mock 时用任意 password 值，不影响测试逻辑
- `IntegrationTestConfig.java` + `AdminAuthIntegrationTest.java`：通过 `@TestPropertySource` 或 `@DynamicPropertySource` 注入测试专用 `INITIAL_ADMIN_PASSWORD`，测试 setup 时用同一密码；V2 移除 admin 后这些测试需要先调 bootstrap 或直接 SQL 插入测试 admin

#### 9. 前端 e2e：通过 env 注入

- `hey-pickler-admin/e2e/fixtures/admin.fixture.ts`、`hey-pickler-admin/e2e/auth.spec.ts` 等 7 处：用 `process.env.E2E_ADMIN_PASSWORD || 'e2e-test-password'`
- `hey-pickler-wxapp/e2e/fixtures/flow.fixture.ts:11` 已经是 env pattern，只需文档说明 CI 必须设置

### Non-goals

- **不实现密码强度校验**——`INITIAL_ADMIN_PASSWORD` 接受任意值，靠运维生成强密码；强度校验留 v2（密码策略 change）
- **不实现 2FA / MFA**——留 v2
- **不实现密钥轮换自动化**——只提供文档，运维手动跑
- **不动 JWT token 黑名单**——密钥轮换时直接换 secret，所有现有 token 失效（简单可靠）
- **不动现有 `application-dev.yml` 的 fallback 值**——dev 仍可零配置启动；guard 只在 prod profile 或显式 `PROD_GUARD=true` 时触发
- **不引入 Vault / KMS / Secrets Manager**——客户自运维场景下 env vars 足够，复杂度不划算
- **不动小程序端的 `WX_APPID/WX_SECRET`**——这两个本来就是部署时配置，不是凭据参数化问题
- **不动现有 admin 改密码的 UI**——`/admins` 页面已经有改密码功能，不重复造

## Impact

- **Affected capabilities**: `auth`（admin bootstrap 流程变化）、`infrastructure`（启动校验、profile guard、密钥长度约束）
- **Affected code**:
  - 后端：
    - 改：`db/migration/V2__init_data.sql`（移除 admin INSERT）
    - 改：`common/util/AesUtil.java`（严格 key 长度校验）
    - 新建：`config/AdminBootstrapper.java`（ApplicationRunner）
    - 新建：`config/ProfileGuard.java`（ApplicationListener）
    - 改：`application-prod.yml`（补 rate-limit 段）
    - 不动：`application-dev.yml`（dev fallback 保留）
  - 测试：
    - 改：4 个后端测试去 `admin123` 字面量
    - 改：7 个前端 e2e 测试用 env var
  - 文档：
    - 新建：`.env.example`（仓库根）
    - 新建：`docs/CREDENTIALS.md`
- **Affected API**: 无 — 全部是启动时和配置层变化，对外接口零变化
- **Operational**:
  - 部署流程：客户首次部署必须设置 11 个环境变量；缺失任一致 fail-fast
  - 现有 dev 环境：升级时跑一次 `mvn flyway:repair`，配 `.env` 提供 `INITIAL_ADMIN_PASSWORD`
  - 密钥轮换：按 `docs/CREDENTIALS.md` 走

## Decisions confirmed

- **直接修改 V2，不新建 V9**：项目无生产部署，Flyway checksum 冲突一次 `flyway:repair` 解决，换干净路径值得
- **`AdminBootstrapper` 不分 profile**：dev/prod 都用同一套机制；dev 通过 `.env` 提供 `INITIAL_ADMIN_PASSWORD=devpassword123`
- **AES key 严格长度校验**：必须 16/24/32 字节，fail-fast
- **prod guard 双触发**：`spring.profiles.active=prod` 自动触发；`PROD_GUARD=true` 在 CI/容器环境显式触发（覆盖 dev profile 但部署在 prod 的边缘情况）
- **保留 dev fallback**：dev profile 仍然零配置可跑，降低 dev 环境上手门槛
- **不引入 Vault/KMS**：客户自运维 + 源码交付场景下，env vars + 文档足够

## Open questions

1. **现有 dev 环境升级路径**：dev 们当前 DB 里有 `admin/admin123`，V2 移除后他们 `flyway:repair` + 重启会怎样？
   - 答：`AdminBootstrapper` 检测到 `admin_user` 表非空 → skip bootstrap → 现有 admin 保留。dev 们继续用 `admin123`，等他们手动改密码为止。**这条要写进 migration guide**。
2. **客户首次部署密码生成**：建议在 `.env.example` 注释里写 `openssl rand -base64 24`，但运维会不会照做不可控。要不要在 `AdminBootstrapper` 里加一个「密码强度 warning」（长度 < 12 字符 → warn）？
   - 倾向：加 warning，不强制。客户拿到源码 + 文档，他们对自己负责。
3. **`@PostConstruct` 在 Spring Boot 3.2 的行为**：`AesUtil` 是 `@Component`，`@PostConstruct` 应该在 bean 初始化时触发；但若 ` getKeySpec()` 是 lazy 调用（首次 encrypt/decrypt 时），key 长度问题可能等到运行时才暴露。需要确认 `@PostConstruct` 调用 `getKeySpec()` 一次。
4. **`ProfileGuard` 触发时机**：`ApplicationListener<ApplicationReadyEvent>` vs `@PostConstruct`？前者应用完全启动后才检查（fail-fast 慢一点），后者各 bean 自己检查（更早暴露）。倾向前者，集中化校验。

## Verification（验收标准）

完成本 change 后必须满足：

- [ ] Fresh DB + 完整 11 个 env vars → 应用启动、bootstrap admin 创建、登录正常
- [ ] Fresh DB + 缺 `INITIAL_ADMIN_PASSWORD` → 启动 fail-fast，错误日志清晰
- [ ] Fresh DB + `AES_KEY=short` → 启动 fail-fast
- [ ] `spring.profiles.active=prod` + `JWT_SECRET=HeyPickler2026DevSecretK3y...` → fail-fast
- [ ] `spring.profiles.active=dev` + 任何配置 → 启动正常（保留 dev fallback）
- [ ] 113 个单元测试 + 7 个 e2e 测试全绿（修改后）
- [ ] `.env.example` 存在、11 个变量齐全
- [ ] `docs/CREDENTIALS.md` 至少覆盖：首次部署、密钥生成、轮换流程、紧急响应
