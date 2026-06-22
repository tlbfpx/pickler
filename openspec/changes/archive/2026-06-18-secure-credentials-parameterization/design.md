# Design: 凭据参数化与启动时校验

> 配套：[proposal.md](./proposal.md) · [tasks.md](./tasks.md) · [specs/auth/spec.md](./specs/auth/spec.md) · [specs/infrastructure/spec.md](./specs/infrastructure/spec.md)

## 1. 组件架构

3 个新组件 + 2 处现有改动 + 1 个 migration 修改：

```
┌────────────────────────────────────────────────────────────────┐
│ Application Startup                                            │
│                                                                │
│  1. Flyway runs V1..V8                                         │
│     └─ V2 modified: no longer seeds admin user                 │
│                                                                │
│  2. Spring beans init                                          │
│     ├─ AesUtil @PostConstruct ──► getKeySpec() ──► validate   │
│     │   (fail-fast if AES_KEY length ∉ {16,24,32})             │
│     └─ JwtUtil @PostConstruct ──► init() ──► validate          │
│         (fail-fast if JWT_SECRET < 32 chars)                   │
│                                                                │
│  3. AdminBootstrapper (ApplicationRunner) ──► run()           │
│     ├─ admin_user count == 0?                                  │
│     │   ├─ yes + env password set ──► INSERT admin            │
│     │   ├─ yes + env missing ──► System.exit(1)               │
│     │   └─ no ──► skip                                         │
│                                                                │
│  4. ProfileGuard (ApplicationReadyEvent listener) ──► verify  │
│     ├─ prod profile + dev-only JWT_SECRET ──► exit            │
│     ├─ PROD_GUARD=true + active=dev ──► exit                  │
│     └─ all OK ──► info log "Profile guard passed"             │
│                                                                │
│  5. App ready, accepting traffic                               │
└────────────────────────────────────────────────────────────────┘
```

**为什么这个顺序**：
- Flyway 必须在 Bootstrapper 之前（bootstrapper 要 `SELECT` admin_user）
- AesUtil/JwtUtil @PostConstruct 在 bean 初始化时触发，比 ApplicationRunner 早，fail-fast 更快
- ProfileGuard 在 `ApplicationReadyEvent` 触发，所有 bean 都已就绪，可以读完整环境信息
- 如果 AesUtil @PostConstruct 失败，Spring 上下文启动失败，AdminBootstrapper 不会跑——这是我们想要的（先验密钥，再创建 admin）

## 2. 组件设计

### 2.1 `AdminBootstrapper`

```java
package com.heypickler.config;

import com.heypickler.entity.AdminUser;
import com.heypickler.mapper.AdminUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AdminBootstrapper implements ApplicationRunner {

    private final AdminUserMapper adminUserMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        long count = adminUserMapper.selectCount(null);
        if (count > 0) {
            log.debug("admin_user table has {} rows, skipping bootstrap", count);
            return;
        }

        String username = env("INITIAL_ADMIN_USERNAME", "admin");
        String password = env("INITIAL_ADMIN_PASSWORD", null);

        if (password == null || password.isEmpty()) {
            log.error("""

                ╔══════════════════════════════════════════════════════════════╗
                ║ FATAL: No admin user exists and INITIAL_ADMIN_PASSWORD     ║
                ║        env var is not set. Cannot bootstrap.              ║
                ║                                                            ║
                ║ Set INITIAL_ADMIN_PASSWORD=<strong-password> and restart.   ║
                ╚══════════════════════════════════════════════════════════════╝
                """);
            System.exit(1);
        }

        if (password.length() < 12) {
            log.warn("INITIAL_ADMIN_PASSWORD is only {} chars; recommend at least 12",
                password.length());
        }

        AdminUser admin = new AdminUser();
        admin.setUsername(username);
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setRole("SUPER_ADMIN");
        admin.setStatus("ACTIVE");
        adminUserMapper.insert(admin);
        log.info("Initial admin '{}' created from env vars (id={}, role=SUPER_ADMIN)",
            username, admin.getId());
    }

    private static String env(String name, String fallback) {
        String v = System.getenv(name);
        return (v == null || v.isEmpty()) ? fallback : v;
    }
}
```

**设计要点**：
- `ApplicationRunner` 而非 `CommandLineRunner`：可以拿到完整 Spring 上下文（`PasswordEncoder` 依赖）
- `System.exit(1)` 而非 throw：Spring Boot 启动失败时 exit code 区分明显，部署脚本可识别
- 密码 < 12 字符 → warn 不强制（Open question #2 决议，见 §5）
- 不区分 profile：dev/prod 都用同一套，dev 通过 `.env` 提供

### 2.2 `ProfileGuard`

```java
package com.heypickler.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProfileGuard implements ApplicationListener<ApplicationReadyEvent> {

    private final Environment env;

    // Known dev-only fallback values that MUST NOT appear in prod
    private static final Set<String> KNOWN_DEV_JWT_SECRETS = Set.of(
        "HeyPickler2026DevSecretK3y!MustChangeInProd!!");
    private static final Set<String> KNOWN_DEV_AES_KEYS = Set.of(
        "PicklerDevAesKey16");

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        boolean isProd = Set.of(env.getActiveProfiles()).contains("prod");
        boolean prodGuard = "true".equalsIgnoreCase(env.getProperty("PROD_GUARD"));
        boolean devActive = Set.of(env.getActiveProfiles()).contains("dev");

        if (prodGuard && devActive) {
            failFast("PROD_GUARD=true but spring.profiles.active contains 'dev'. " +
                     "This looks like a production environment running dev profile.");
        }

        if (isProd) {
            String jwt = env.getProperty("hey-pickler.jwt.secret");
            if (KNOWN_DEV_JWT_SECRETS.contains(jwt)) {
                failFast("Active profile is 'prod' but JWT_SECRET matches known dev fallback. " +
                         "Set a unique JWT_SECRET env var (>= 32 chars, generate via `openssl rand -base64 48`).");
            }
            String aes = env.getProperty("hey-pickler.aes.key");
            if (KNOWN_DEV_AES_KEYS.contains(aes)) {
                failFast("Active profile is 'prod' but AES_KEY matches known dev fallback. " +
                         "Set a unique AES_KEY env var (exactly 16, 24, or 32 bytes).");
            }
            log.info("Profile guard passed (prod profile, secrets verified unique)");
        } else {
            log.info("Profile guard passed (non-prod profile, skipped strict checks)");
        }
    }

    private void failFast(String reason) {
        log.error("""

            ╔══════════════════════════════════════════════════════════════╗
            ║ FATAL: Profile guard violation.                            ║
            ║                                                            ║
            ║ {}                                       ║
            ║                                                            ║
            ║ Refusing to start in this state. Fix the configuration     ║
            ║ and restart.                                               ║
            ╚══════════════════════════════════════════════════════════════╝
            """, reason);
        System.exit(2);
    }
}
```

**为什么 `ApplicationReadyEvent` 不 `@PostConstruct`**：
- 集中化校验，一处看清所有规则
- 完整环境信息（profile + properties 已解析）
- 即使错过 AesUtil 的早 fail-fast，这里还能再 catch 一层（深度防御）
- Open question #4 决议（见 §5）

### 2.3 `AesUtil` 严格校验

```java
@PostConstruct
void validate() {
    byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
    if (keyBytes.length != 16 && keyBytes.length != 24 && keyBytes.length != 32) {
        throw new IllegalStateException(
            "AES_KEY must be exactly 16, 24, or 32 bytes for AES-128/192/256. " +
            "Got " + keyBytes.length + " bytes. " +
            "Generate via `openssl rand -base64 32` and trim to 32 chars, or set raw 16/24/32 bytes.");
    }
}

private SecretKeySpec getKeySpec() {
    // No more zero-padding — key length already validated
    return new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES");
}
```

**注意**：现有数据（用零填充 key 加密的手机号）将无法解密！

**Migration 风险**：dev 环境当前用 `PicklerDevAesKey16` 加密过手机号。改成严格校验后，这个 key（16 字节）仍然合法，但任何之前用 `AES_KEY=abc`（3 字节）跑过的环境，加密数据无法解密。

**缓解**：
- 项目尚无生产数据，影响面仅限 dev
- `.env.example` 推荐 `PicklerDevAesKey16` 作为 dev fallback（保持兼容）
- `docs/CREDENTIALS.md` 写明：如果 dev 用过短 key 加密过数据，需要清理 `user.phone` 重新走绑定流程

## 3. V2 Migration 修改

```sql
-- Before (V2__init_data.sql)
INSERT INTO `admin_user` (`username`, `password_hash`, `role`, `status`)
VALUES ('admin', '$2a$10$YZPJw5WLWFVP1IsHDZmsFetU.vUeJoxqbQm1Mdd/d0QjaFj7Cw76G', 'SUPER_ADMIN', 'ACTIVE');

-- After
-- No admin seed. Initial admin is created at application startup by AdminBootstrapper,
-- reading INITIAL_ADMIN_USERNAME / INITIAL_ADMIN_PASSWORD from environment.
-- See docs/CREDENTIALS.md for first-deployment instructions.
```

V2 文件保留（不删），仅清空敏感内容并加注释。Flyway checksum 会变 → dev 跑 `mvn flyway:repair` 一次。

## 4. .env.example 设计

```bash
# Hey Pickler — Production Environment Variables Template
# Copy to .env (gitignored) and fill in real values for deployment.
# See docs/CREDENTIALS.md for generation guidance.

# === Database ===
DB_URL=jdbc:mysql://your-mysql-host:3306/hey_pickler?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai
DB_USERNAME=hey_pickler_app
DB_PASSWORD=                                          # openssl rand -base64 24

# === Redis ===
REDIS_HOST=your-redis-host
REDIS_PORT=6379
REDIS_PASSWORD=                                       # openssl rand -base64 24

# === Crypto keys (CRITICAL) ===
JWT_SECRET=                                           # openssl rand -base64 48  (>= 32 chars)
AES_KEY=                                              # exactly 16, 24, or 32 bytes (raw bytes recommended)

# === Initial admin (first-deployment only) ===
INITIAL_ADMIN_USERNAME=admin
INITIAL_ADMIN_PASSWORD=                               # openssl rand -base64 18  (>= 12 chars recommended)

# === WeChat Mini-Program ===
WX_APPID=your-real-appid
WX_SECRET=your-real-secret

# === CORS (comma-separated list) ===
CORS_ADMIN_ORIGINS=https://admin.your-domain.com

# === Profile (always 'prod' in production deployments) ===
SPRING_PROFILES_ACTIVE=prod
PROD_GUARD=true                                       # Extra safety: refuse to start with dev profile
```

## 5. Open questions 决议

| # | Question | Decision | Rationale |
|---|----------|----------|-----------|
| 1 | 现有 dev 升级路径 | **保留现有 admin 行**。AdminBootstrapper 检测到 `count > 0` → skip。dev 们继续用 `admin123`（或他们之前改过的密码），不受影响。Migration guide 写明：升级步骤 = `mvn flyway:repair` + 重启。 | 最小破坏；dev 数据是 dev 自己的责任。 |
| 2 | 密码强度校验 | **加 warn，不强制**。`< 12` 字符 → warn 日志，但仍允许启动。 | 客户拿到源码对自己负责；强制反而阻碍测试环境快速搭建。 |
| 3 | `@PostConstruct` 行为 | **确认有效**。`AesUtil` 是 `@Component`，Spring 在 bean 初始化时调用 `@PostConstruct`，早于 `ApplicationRunner`。如果校验失败，上下文启动失败，fail-fast。 | Spring Boot 3.2 + `@Component` + `@PostConstruct` 标准行为。 |
| 4 | `ProfileGuard` 触发时机 | **`ApplicationListener<ApplicationReadyEvent>`**。集中化校验，所有 bean 就绪后触发，可读完整 environment。`@PostConstruct` 分散在各 bean 里，校验逻辑难追踪。 | 集中化 + 可审计 > 早 fail-fast 几毫秒。AesUtil 已经 `@PostConstruct` 兜底了真正致命的 key 长度问题。 |

## 6. 测试策略

### 6.1 单元测试

| Class | Test | 覆盖 |
|-------|------|------|
| `AesUtilTest` | 新增 | 16/24/32 字节 OK；其他长度 `IllegalStateException`；encrypt → decrypt 往返 |
| `AdminBootstrapperTest` | 新建 | (a) 表非空 → skip; (b) 表空 + env 全 → INSERT; (c) 表空 + env 缺密码 → System.exit; (d) 密码 < 12 → warn 但仍 INSERT |
| `ProfileGuardTest` | 新建 | (a) prod + 默认 JWT → exit; (b) PROD_GUARD=true + dev profile → exit; (c) prod + unique JWT → pass; (d) dev → pass |

`AdminBootstrapper` 和 `ProfileGuard` 测试需 mock `System.exit`（用 `SecurityManager` 或 `checkExit`），稍麻烦。备选：抽出一个 `ExitAction` 函数式接口，测试时注入 mock。**采用 mock System.exit 方案**——常规 Spring Boot 测试套路（`ExpectedSystemExit` 等价物，手写 `SecurityManager`）。

### 6.2 集成测试

`CredentialHardeningIntegrationTest`（新建）：
- Spring Boot 上下文 + H2 或 Testcontainers MySQL
- Case 1: Fresh DB + 完整 env → bootstrap 成功
- Case 2: Fresh DB + 缺 `INITIAL_ADMIN_PASSWORD` → 启动失败
- Case 3: Prod profile + dev JWT secret → 启动失败

### 6.3 改造现有测试

- `OperationLogAspectTest.java:152,168`：替换 `admin123` → `test-password`（语义不变）
- `AuthServiceTest.java:78,82`：同上，mock 中用任意值
- `IntegrationTestConfig.java:55` + `AdminAuthIntegrationTest.java:14`：用 `@DynamicPropertySource` 注入 `INITIAL_ADMIN_PASSWORD`，bootstrap 后用同密码登录
- 7 个 e2e 用例：`process.env.E2E_ADMIN_PASSWORD || 'e2e-default'` + 文档说明 CI 必须设置 env

### 6.4 手动验证

跑完单测后，按 proposal §Verification 那 8 条 checklist 跑一遍：

```bash
# Fresh DB scenario
docker run --rm --name mysql-test -e MYSQL_ROOT_PASSWORD=test -p 3307:3306 -d mysql:8
# create hey_pickler db on 3307
JWT_SECRET=$(openssl rand -base64 48) \
AES_KEY=PicklerDevAesKey16 \
DB_PASSWORD=test \
INITIAL_ADMIN_PASSWORD=$(openssl rand -base64 18) \
# ... etc
SPRING_PROFILES_ACTIVE=prod mvn spring-boot:run
```

## 7. Migration Guide（写入 docs/CREDENTIALS.md）

```markdown
## Upgrading from pre-secure-credentials version

If you have an existing dev environment:

1. Pull master
2. Run `mvn flyway:repair` once (V2 checksum changed)
3. Create `.env` at repo root with at least:
   INITIAL_ADMIN_PASSWORD=devpassword123
   JWT_SECRET=HeyPickler2026DevSecretK3y!MustChangeInProd!!
   AES_KEY=PicklerDevAesKey16
4. Restart app — your existing admin user is preserved (AdminBootstrapper skips when table not empty)
5. (Optional) Change your admin password via the admin panel

If you have a fresh deployment (new client env):

1. Copy `.env.example` to `.env`
2. Fill in all values following the generation commands
3. Start the app — AdminBootstrapper creates initial admin from INITIAL_ADMIN_*
4. Login with that admin, immediately change password
```

## 8. 风险与缓解

| 风险 | 严重度 | 缓解 |
|------|--------|------|
| 现有 dev 加密过的手机号无法解密（如果用过短 key） | 低 | 项目无生产数据；`docs/CREDENTIALS.md` 警告说明 |
| 客户首次部署忘记设 `INITIAL_ADMIN_PASSWORD` → 启动失败 | 中 | 这是 by design；fail-fast 错误日志超清晰（ASCII 框 + 指引） |
| `System.exit` 在测试中难 mock | 低 | 用 `SecurityManager` 老套路；或重构为可注入 ExitAction（v2 优化） |
| Flyway checksum 变化打懵客户运维 | 中 | `docs/CREDENTIALS.md` 写明 `flyway:repair` 步骤；V2 注释写清楚为何修改 |
| 测试代码改动量较大（11 处 admin123） | 低 | 全部机械化替换 + env 注入；2 小时内可完成 |

## 9. 工作量分解

参考 [tasks.md](./tasks.md)，预计 9 个 atomic commits，2 天工作量。

- Day 1（5 commits）：V2 修改、AesUtil 严格校验、AdminBootstrapper、ProfileGuard、prod rate-limit 配置
- Day 2（4 commits）：测试改造、`.env.example` + `docs/CREDENTIALS.md`、`application-prod.yml` 完善、最终验证 + OpenSpec 归档
