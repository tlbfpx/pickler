# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Hey Pickler is a Pickleball event management platform with three subsystems in a single monorepo:

- **`hey-pickler-server/`** — Spring Boot 3.2 backend (Java 17, MyBatis-Plus, MySQL 8, Redis, Flyway)
- **`hey-pickler-admin/`** — Vue 3 admin panel (Element Plus, Pinia, Vite, TypeScript)
- **`hey-pickler-wxapp/`** — WeChat Mini Program (native WXML/WXSS/JS)

The backend serves two API prefixes: `/api/app/**` (WeChat users) and `/api/admin/**` (admin panel). Each has its own auth filter chain.

## Build & Run Commands

### Backend (hey-pickler-server)

```bash
# Build
mvn clean package -DskipTests

# Run (dev profile is default)
mvn spring-boot:run
# or: java -jar target/hey-pickler-server-1.0.0.jar

# Tests
mvn test                                          # All unit tests
mvn test -Dtest=AuthServiceTest                   # Single test class
mvn test -Dtest="!*IntegrationTest"               # Unit tests only (skip integration)

# API docs: http://localhost:8080/doc.html
```

Requires MySQL 8 (`hey_pickler` database) and Redis running locally. Flyway auto-migrates on startup. Dev defaults are in `application-dev.yml` — no env vars needed for local development.

### Admin (hey-pickler-admin)

```bash
npm install
npm run dev          # http://localhost:5173, proxies /api → localhost:8080
npm run build        # Output to dist/
npm run lint         # ESLint

# E2E tests (requires backend running)
npx playwright install                  # First time only
npm run test:e2e                        # Headless
npm run test:e2e:headed                 # With browser UI
```

Default admin login: `admin` / `admin123`

### WeChat Mini Program (hey-pickler-wxapp)

Open in WeChat DevTools. API base URL configured in `app.js` → `globalData.baseUrl`. Backend `WX_DEV_MODE=true` (default) bypasses WeChat auth for local development.

## Architecture

### Backend Package Structure (`com.heypickler`)

```
controller/
  admin/    → AdminAuthController, AdminUserController, AdminEventController,
              AdminBannerController, AdminRankingController, AdminAdminController,
              AdminBanRecordController, AdminDashboardController, AdminOperationLogController
  app/      → AppAuthController, AppEventController, ...
filter/     → AppAuthFilter, AdminAuthFilter, RateLimitFilter, XssFilter, SecurityHeadersFilter
service/    → Interface definitions
  impl/     → Service implementations (incl. OperationLogService, HeadBasedImageUrlValidator)
mapper/     → MyBatis-Plus mappers (one per entity)
entity/     → User, Event, Registration, AdminUser, Banner, Ranking, BanRecord, PointRecord, OperationLog
dto/        → Request DTOs split by admin/ and app/; common/dto for shared (e.g. OperationLogQuery)
vo/         → Response VOs (View Objects, e.g. OperationLogVO)
config/     → SecurityConfig, CorsConfig, RedisConfig, MyBatisPlusConfig, SwaggerConfig, AsyncConfig, AppConfig
common/
  annotation/ → @RequireRole
  aspect/     → RoleCheckAspect (role enforcement), OperationLogAspect (audit capture)
  exception/  → BizException, ErrorCode
  result/     → Result wrapper, PageResult
  util/       → JwtUtil, AesUtil, StatusTransitionValidator, WxBizDataCrypt,
                OperationLogClassifier (URL → module/action), SensitiveDataUtil (JSON field masking)
  enums/      → UserRole, etc.
scheduler/  → EventStatusScheduler (auto-transitions event statuses)
listener/   → PointChangeListener (Spring event listener for point changes)
```

### Key Patterns

- **API response**: All endpoints return `Result<T>` with `{code: 0, data: ..., message: ...}`. Code 0 = success.
- **Dual auth**: `AppAuthFilter` validates JWT from WeChat users; `AdminAuthFilter` validates JWT from admin users. Both use `JwtUtil` but with different token expiration configs. `AdminAuthFilter` injects `adminId` / `adminRole` as request attributes for downstream use (controllers, aspects).
- **Role-based access**: `@RequireRole` annotation + `RoleCheckAspect` for admin role checks (SUPER_ADMIN, ADMIN, OPERATOR).
- **Rate limiting**: `RateLimitFilter` uses Redis + Lua scripts, per-IP and per-user limits configured in `application.yml` under `hey-pickler.rate-limit`.
- **Soft delete**: MyBatis-Plus logical delete via `deletedAt` field (NULL = not deleted, timestamp = deleted). **Exception**: `operation_log` is append-only (no `deleted_at`) — audit data must never be erased.
- **CORS split**: `CorsConfig` applies different origins for `/api/admin` vs `/api/app` paths.
- **Database migration**: Flyway scripts in `src/main/resources/db/migration/`. Always add new migrations as incremental versions (V9__, V10__, etc.). Current head: V8.
- **Async executors** (`AsyncConfig`): `rankingExecutor` (queue=100) for ranking refresh; `auditLogExecutor` (queue=500, `DiscardOldestPolicy`) for audit log writes — must never block an admin request.
- **Audit log capture**: `OperationLogAspect` is an `@Around` aspect on `com.heypickler.controller.admin..*` that records every non-GET admin request to `operation_log`. Captures operator (from request attributes), IP (X-Forwarded-For first hop), params (Jackson-serialized + `SensitiveDataUtil.maskJson` + truncated to 2000 chars), status (1=success / 0=fail with errorCode/errorMsg), latency. Writes are fire-and-forget via `@Async("auditLogExecutor")` — any persistence failure is swallowed and logged.
- **URL classification**: `OperationLogClassifier.classify(method, path)` maps `/api/admin/{resource}[/{id}][/{sub-action}]` to `(module, action, targetType, targetId)`. Unknown resources fall back to `module=RAW, action=RAW`; the full path is still preserved in the `path` column for forensics.

### Admin Frontend Structure

```
src/
  api/         → One module per resource (auth, users, events, banners, rankings, admins,
                 ban-records, admin-logs, dashboard, files), all use axios instance from request.ts
  stores/      → Pinia stores (auth, app)
  router/      → Vue Router with auth guard (redirects to /login if no valid token)
  views/       → One folder per resource page (login/, dashboard/, events/, users/, activities/,
                 rankings/, banners/, admins/, ban-records/, admin-logs/)
  components/
    layout/    → AppLayout, AppHeader, AppSidebar
    common/    → Pagination, ImageUpload
  types/       → TypeScript interfaces
```

Admin auth token stored in `localStorage` as `admin_token`. The auth store (`stores/auth.ts`) checks token expiry with 30s clock skew tolerance.

**Sidebar note**: `/ban-records` (label "用户日志") shows user ban/unban records. `/admin-logs` (label "操作日志") shows the system operation audit log. Don't confuse them — the names sound similar but the data sources are completely different.

### WeChat Mini Program Structure

Pages: `index` (home), `login`, `profile`, `my-events`. Components: `event-card`, `ranking-item`, `tier-badge`. HTTP wrapper in `utils/request.js` handles token injection, 401/403 redirect, and token refresh.

## Workflow (from AGENTS.md)

This project uses an OpenSpec + Superpowers workflow:
1. Superpowers exploration → 2. OpenSpec spec lock → 3. Superpowers TDD execution → 4. OpenSpec archive

Do not write code before OpenSpec design is confirmed. Do not archive before code/tests/spec are aligned.

## Environment Variables

Production deployments must override these (dev defaults work locally):
- `JWT_SECRET`, `AES_KEY` — crypto keys
- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` — MySQL connection
- `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`
- `WX_APPID`, `WX_SECRET` — WeChat credentials (set `WX_DEV_MODE=false` in prod)
- `CORS_ADMIN_ORIGINS` — allowed admin origins
