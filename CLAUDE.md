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
  admin/    → AdminAuthController, AdminEventController, ...
  app/      → AppAuthController, AppEventController, ...
filter/     → AppAuthFilter, AdminAuthFilter, RateLimitFilter, XssFilter, SecurityHeadersFilter
service/    → Interface definitions
  impl/     → Service implementations
mapper/     → MyBatis-Plus mappers (one per entity)
entity/     → JPA/MyBatis entities (User, Event, Registration, AdminUser, Banner, Ranking, BanRecord, PointRecord)
dto/        → Request DTOs split by admin/ and app/
vo/         → Response VOs (View Objects)
config/     → SecurityConfig, CorsConfig, RedisConfig, MyBatisPlusConfig, SwaggerConfig, AsyncConfig, AppConfig
common/     → Shared: Result wrapper, PageResult, ErrorCode, BizException, JwtUtil, AesUtil, enums, @RequireRole annotation
scheduler/  → EventStatusScheduler (auto-transitions event statuses)
listener/   → PointChangeListener (Spring event listener for point changes)
```

### Key Patterns

- **API response**: All endpoints return `Result<T>` with `{code: 0, data: ..., message: ...}`. Code 0 = success.
- **Dual auth**: `AppAuthFilter` validates JWT from WeChat users; `AdminAuthFilter` validates JWT from admin users. Both use `JwtUtil` but with different token expiration configs.
- **Role-based access**: `@RequireRole` annotation + `RoleCheckAspect` for admin role checks (SUPER_ADMIN, ADMIN).
- **Rate limiting**: `RateLimitFilter` uses Redis + Lua scripts, per-IP and per-user limits configured in `application.yml` under `hey-pickler.rate-limit`.
- **Soft delete**: MyBatis-Plus logical delete via `deletedAt` field (NULL = not deleted, timestamp = deleted).
- **CORS split**: `CorsConfig` applies different origins for `/api/admin` vs `/api/app` paths.
- **Database migration**: Flyway scripts in `src/main/resources/db/migration/`. Always add new migrations as incremental versions (V4__, V5__, etc.).

### Admin Frontend Structure

```
src/
  api/         → One module per resource (auth, events, users, banners, rankings, etc.), all use axios instance from request.ts
  stores/      → Pinia stores (auth, app)
  router/      → Vue Router with auth guard (redirects to /login if no valid token)
  views/       → One folder per resource page (login/, dashboard/, events/, users/, activities/, rankings/, banners/, admins/, ban-records/)
  components/
    layout/    → AppLayout, AppHeader, AppSidebar
    common/    → Pagination, ImageUpload
  types/       → TypeScript interfaces
```

Admin auth token stored in `localStorage` as `admin_token`. The auth store (`stores/auth.ts`) checks token expiry with 30s clock skew tolerance.

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
