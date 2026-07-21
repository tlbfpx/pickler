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
              AdminBanRecordController, AdminDashboardController, AdminOperationLogController,
              AdminGroupingController (match grouping lifecycle)
  app/      → AppAuthController, AppEventController, AppTeamController (team decline)
filter/     → AppAuthFilter, AdminAuthFilter, RateLimitFilter, XssFilter, SecurityHeadersFilter
service/    → Interface definitions (incl. PointService 发分、PointWallet 商城预留、SeasonService 赛季管理、
              TeamService 队伍状态机、GroupingService 分组生命周期、GroupingStrategy 分组策略接口)
  impl/     → Service implementations (incl. OperationLogService, HeadBasedImageUrlValidator,
              TeamServiceImpl, GroupingServiceImpl, SerpentineStrategy, RandomStrategy)
mapper/     → MyBatis-Plus mappers (one per entity)
entity/     → User, Event, Registration, AdminUser, Banner, Ranking, BanRecord, PointRecord, OperationLog, Season,
              Team (双打/混打 2 人队), MatchGroup, GroupAssignment
dto/        → Request DTOs split by admin/ and app/; common/dto for shared (e.g. OperationLogQuery);
              Participant (grouping userId/teamId + rankScore)
vo/         → Response VOs (View Objects, e.g. OperationLogVO, TeamVO, GroupVO, AssignmentVO)
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
listener/   → PointChangeListener (Spring event listener; PointChangeEvent 携 seasonCode)
```

### Key Patterns

- **API response**: All endpoints return `Result<T>` with `{code: 0, data: ..., message: ...}`. Code 0 = success.
- **Dual auth**: `AppAuthFilter` validates JWT from WeChat users; `AdminAuthFilter` validates JWT from admin users. Both use `JwtUtil` but with different token expiration configs. `AdminAuthFilter` injects `adminId` / `adminRole` as request attributes for downstream use (controllers, aspects).
- **Role-based access**: `@RequireRole` annotation + `RoleCheckAspect` for admin role checks (SUPER_ADMIN, ADMIN, OPERATOR).
- **Rate limiting**: `RateLimitFilter` uses Redis + Lua scripts, per-IP and per-user limits configured in `application.yml` under `hey-pickler.rate-limit`.
- **Soft delete**: MyBatis-Plus logical delete via `deletedAt` field (NULL = not deleted, timestamp = deleted). **Exception**: `operation_log` is append-only (no `deleted_at`) — audit data must never be erased.
- **CORS split**: `CorsConfig` applies different origins for `/api/admin` vs `/api/app` paths.
- **Database migration**: Flyway scripts in `src/main/resources/db/migration/`. Always add new migrations as incremental versions (V9__, V10__, etc.). Current head: V15.

### Match Grouping Lifecycle (Specs 1–3)

Events carry a fixed `format` (SINGLES / DOUBLES / MIXED) plus a `groupingLocked` flag. Doubles/mixed participants build a `Team` via captain-invite → partner-confirm (PENDING → CONFIRMED); dissolution / decline / withdraw are **physical row deletes** to release the V12 UNIQUE KEY (`uk_event_member1` / `uk_event_member2`) so a user can re-form a team.

- **Spec 1 — Format + Teams + Grouping** — `EventServiceImpl.register` branches on `event.format`. `matchType` is forced to `event.format` server-side at registration creation (NOT NULL column). `event.groupingLocked=true` rejects register / cancel / withdraw / re-group / reassign with `INVALID_STATUS_TRANSITION`. `GroupingStrategy` interface + Serpentine/Random — ranked by `event.type` track (STAR→starPoints, PARTY→partyPoints; teams sum members). MANUAL just builds empty groups. Strategy output `groupId` is the group **index** (0..N-1); `GroupingService` resolves it to `match_group.id`. Team status field only carries `PENDING`/`CONFIRMED`. There is no DISSOLVED state — dissolution = row delete.
- **Spec 2 — Match play** — Once grouping is locked, admin generates round-robin matches per group (`MatchService.generate`): N*(N-1)/2 matches, `slot_a_user_id`/`slot_a_team_id` mutually exclusive. Participants self-submit scores (`POST /api/app/matches/{id}/score`) — 3-game best-of-3, GameValidator enforces 21+ points + 2-point margin + ≤30 cap. Admin resets (`POST /api/admin/matches/{id}/reset`) clears score fields. Standings (`GET /api/app/events/{id}/standings`) computed live: wins desc → games-for-minus-against desc → ties share rank. Doubles count wins per team, not per member. `MatchService.complete` blocks with PARAM_ERROR if any match is not COMPLETED.
- **Spec 3 — Placement issuance** — Admin configures `event_placement_points` (per-event rank→points JSON table) via `PUT /api/admin/events/{id}/placement-points`. Refused on COMPLETED events. Falls back to `hey-pickler.placement.defaultPoints` from application.yml. On `complete()`, `PlacementService.issue` runs in the same transaction: writes `point_record` rows with `source=PLACEMENT`, single rows for singles, two rows for doubles (member1=floor(p/2), member2=余数). Idempotent: refuses re-issue. Tolerates missing current season (rows still written with `seasonCode=NULL`). V15 made `point_record.operator_id` NULLABLE for system-driven PLACEMENT writes.
- **MySQL ≥ 8.0.16** required (CHECK on `group_assignment.user_id`/`team_id` mutual exclusivity); on lower versions the constraint is dropped and `GroupingService.assign` enforces it at the application layer.

### App Auth Filter Caveat

`AppAuthFilter.shouldNotFilter` short-circuits all `GET` requests under `/api/app/{events,banners,rankings}` so event browsing works anonymously. User-scoped GETs under those prefixes (currently only `GET /api/app/events/{id}/my-team`) need to be explicitly **excluded from the bypass** so the JWT userId is bound — the filter handles this with an `endsWith("/my-team")` check. Don't add new user-scoped GETs under those prefixes without updating the filter.
- **Async executors** (`AsyncConfig`): `rankingExecutor` (queue=100) for ranking refresh; `auditLogExecutor` (queue=500, `DiscardOldestPolicy`) for audit log writes — must never block an admin request.
- **Audit log capture**: `OperationLogAspect` is an `@Around` aspect on `com.heypickler.controller.admin..*` that records every non-GET admin request to `operation_log`. Captures operator (from request attributes), IP (X-Forwarded-For first hop), params (Jackson-serialized + `SensitiveDataUtil.maskJson` + truncated to 2000 chars), status (1=success / 0=fail with errorCode/errorMsg), latency. Writes are fire-and-forget via `@Async("auditLogExecutor")` — any persistence failure is swallowed and logged.
- **URL classification**: `OperationLogClassifier.classify(method, path)` maps `/api/admin/{resource}[/{id}][/{sub-action}]` to `(module, action, targetType, targetId)`. Unknown resources fall back to `module=RAW, action=RAW`; the full path is still preserved in the `path` column for forensics.
- **Dual points system**: Two independent point tracks keyed by type. **STAR = 战力 (竞技赛事 / competitive events)**; **PARTY = 活力 (社交活动 / social events)**. Backend enum values `STAR` / `PARTY` are unchanged from the legacy single-track schema. Tiers are 6 ranks — `BRONZE`青铜 / `SILVER`白银 / `GOLD`黄金 / `PLATINUM`铂金 / `DIAMOND`钻石 / `MASTER`王者 — with thresholds configured in `application.yml` under `hey-pickler.tier` (changing thresholds requires a restart). `point_record.source` records origin: `REGISTRATION` / `CHECK_IN` / `PLACEMENT` / `MANUAL` / `REDEEM` / `ADJUST`. Seasons are tracked per type in the `season` table (status `CURRENT` / `ARCHIVED`); archiving a season keeps historical rankings queryable. Point issuance is decoupled through `PointService` (formerly `RankingService.enterPoints`); `PointWallet` is a placeholder interface for the planned points-mall redemption. Placement rows are written automatically by `PlacementService.issue` when an event transitions to `COMPLETED` (Spec 3); admin can configure per-event placement tables via `PUT /api/admin/events/{id}/placement-points` before completion. The `MyEventVO` returned by `GET /api/app/user/events` carries `earnedPoints` (sum of PLACEMENT rows for that user+event) for "已获 X 积分" badges on the wxapp event list.

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

## Continuous Integration

GitHub Actions CI runs on every push and every PR to master (`.github/workflows/ci.yml`). Two parallel jobs:

- **`backend`** — JDK 17, runs `mvn clean package` (compile), `mvn test -Dtest='!*IntegrationTest'` (unit), `mvn test -Dtest='*IntegrationTest'` (integration with MySQL 8 + Redis 6 service containers). ~1m30s with Maven cache.
- **`frontend`** — Node 18, runs `npm ci`, `npm run lint:check` (ESLint without `--fix`), `npm run build`. ~30s with npm cache.

PRs require both jobs green to merge. `concurrency.cancel-in-progress: true` cancels superseded runs when a PR receives a new push. CI does **not** deploy — production releases are manual via `deploy/scripts/install.sh` (see `docs/RUNBOOK.md`).

Frontend `npm run lint` (local, with `--fix`) and `npm run lint:check` (CI, no `--fix`) are separate scripts. ESLint config downgrades `no-explicit-any`, `ban-types` to `warn` and allows empty catch — see `.eslintrc.cjs` for the rule set.

## Environment Variables

Full template at `.env.example`; operational guidance (key rotation, emergency response, upgrade path) at `docs/CREDENTIALS.md`. Production deployments must override these (dev defaults work locally):
- `JWT_SECRET` — JWT signing secret, ≥ 32 chars; generate via `openssl rand -base64 48`
- `AES_KEY` — data encryption key, exactly 16/24/32 bytes; AesUtil `@PostConstruct` strictly validates length
- `INITIAL_ADMIN_USERNAME`, `INITIAL_ADMIN_PASSWORD` — bootstrap SUPER_ADMIN on first start when `admin_user` table is empty; fail-fast `exit(1)` if missing
- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` — MySQL connection
- `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`
- `WX_APPID`, `WX_SECRET` — WeChat credentials (set `WX_DEV_MODE=false` in prod)
- `CORS_ADMIN_ORIGINS` — allowed admin origins
- `SPRING_PROFILES_ACTIVE` — must be `prod` in production
- `PROD_GUARD=true` — defense-in-depth check refuses start if `dev` profile active or secrets match dev fallbacks

## MCP Tools: code-review-graph (multi-repo)

Three CRG MCP servers are registered at `.mcp.json` for each subsystem:

- **`mcp__crg-server__*`** — `hey-pickler-server/` (Java/Spring Boot, ~2364 nodes / 31600 edges)
- **`mcp__crg-admin__*`** — `hey-pickler-admin/` (Vue 3 / TS, ~633 nodes / 5498 edges)
- **`mcp__crg-wxapp__*`** — `hey-pickler-wxapp/` (WeChat Mini Program, ~244 nodes / 2275 edges)

Graphs auto-update via `crg-daemon` (running, PID managed by launchd-style supervisor) — file edits under any subsystem trigger incremental `update`. To force a full re-parse: `code-review-graph build --repo=<path>`.

**When reviewing changes or exploring this codebase, prefer CRG tools over raw Grep/Glob/Read** — they are faster, cheaper, and give structural context (callers, dependents, test coverage, community structure) that file scanning cannot.

### When to use graph tools FIRST

- **Exploring code**: `mcp__crg-*__semantic_search_nodes_tool` or `mcp__crg-*__query_graph_tool` instead of Grep
- **Understanding impact**: `mcp__crg-*__get_impact_radius_tool` instead of manually tracing imports
- **Code review**: `mcp__crg-*__detect_changes_tool` + `mcp__crg-*__get_review_context_tool` instead of reading entire files
- **Finding relationships**: `mcp__crg-*__query_graph_tool` with `pattern="callers_of|callees_of|imports_of|tests_for"`
- **Architecture questions**: `mcp__crg-*__get_architecture_overview_tool` + `mcp__crg-*__list_communities_tool`
- **Dead code / oversized functions**: `mcp__crg-*__find_dead_code_tool` + `mcp__crg-*__find_large_functions_tool`

Fall back to Grep/Glob/Read **only** when the graph doesn't cover what you need. The CLI equivalents (`code-review-graph query|impact|dead-code|large-functions --repo=<server|admin|wxapp>`) work without MCP.

### Multi-repo workflow

For cross-subsystem questions (e.g., "does the wxapp call the same event API that the admin uses?"), query each subsystem's MCP server separately and reconcile. There is no cross-graph join — CRG treats each `--repo` as an isolated graph.

### Daemon management

```bash
crg-daemon status       # Show watched repos + PIDs
crg-daemon logs server  # Tail log for one repo
crg-daemon stop         # Stop all watchers
crg-daemon start        # Start (auto-loads ~/.code-review-graph/watch.toml)
```

If a graph appears stale after large rebases, run `code-review-graph update --repo=<path> --base=master` to force a re-parse.
