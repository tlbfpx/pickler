# Proposal: CI/CD 流水线（add-cicd-pipeline）

## Why

当前仓库没有任何 CI：`.github/workflows/` 目录不存在，所有 push / PR 都靠手工 `mvn package` + `npm run build` 在本地验证。这意味着：

1. **回归无门禁**：合并到 master 的代码可能编译失败 / 单测红 / lint 报错，要等下次本地跑才暴露
2. **客户开发上手有风险**：客户方开发 fork 仓库后，PR 没有「自动绿勾」，无法判断改动是否破坏既有功能
3. **集成测试只在作者机器上跑**：5 个 `*IntegrationTest` 类（18 个 case）依赖本地 MySQL + Redis，其他贡献者环境不一致会出诡异问题
4. **临交付前最高 ROI 的工程化**：相比 #3 测试覆盖（补 service 单测），CI 把「已有的 80+ 单测 + 18 个集成测试」变成 push 时自动跑的门槛，每个 PR 都受益

这是 5 个 Phase 1 交付变更中的第 5 个（前 4 个：`secure-credentials-parameterization` PR#10 待 sign-off、`deployment-automation` PR#11 stacked 待 sign-off、可观测性已跳过、核心服务测试覆盖待启动）。本变更是 Phase 1 收尾，完成后客户的开发者可以 fork → PR → 自动验证 → 合并的完整闭环。

**范围确认（用户已签字）**：

- ✅ GitHub Actions CI workflow
- ❌ API `/api/v1/` 版本化（推迟 Phase 2 — 单客户 B2B 项目，低 ROI；blast radius 涉及 18 个 controller + 前端 axios + 小程序 baseUrl + 全部 IntegrationTest URL）
- ⏭️ Swagger prod 关闭（已完成 — `SwaggerConfig` 已 `@Profile("dev")`，零工作）

## What Changes

### 新增

**`.github/workflows/ci.yml`** — 单一 workflow 文件，含两个并行 job：

| Job | 触发条件 | 内容 |
|-----|---------|------|
| `backend` | push 到任意分支、PR 到 master | JDK 17 + Maven 缓存 → `mvn -B -ntp clean package`（编译验证）→ `mvn -B -ntp test -Dtest='!*IntegrationTest'`（单测）→ MySQL 8 + Redis 6 service container → `mvn -B -ntp test -Dtest='*IntegrationTest'`（集成测试）|
| `frontend` | push 到任意分支、PR 到 master | Node 18 + npm 缓存 → `npm ci` → `npm run lint:check`（无 `--fix`）→ `npm run build` |

**`hey-pickler-admin/package.json`** — 新增 `lint:check` script，与 `lint` 区分：

```json
"lint": "eslint . --ext ... --fix ...",          // 现有：开发者本地用，自动修复
"lint:check": "eslint . --ext ... ..."            // 新增：CI 用，仅检查不修复
```

### 不在范围

- **E2E 测试（Playwright）**：需要 backend + frontend 同时启动 + 浏览器，复杂度高，留给 Phase 2 单独交付
- **CD（自动部署到 ECS）**：当前部署走 `install.sh` 手工流程；自动 CD 需要 OSS 凭据管理 + ECS SSH key 注入，留给客户接入自己的 CD 体系
- **Test coverage 报告 / 阈值**：留给变更 #3
- **代码质量扫描（SonarQube / CodeQL）**：留给后续

## Impact

### Affected specs

- `openspec/specs/infrastructure/spec.md` — 新增 1 个 requirement：CI pipeline

### Affected code

- `hey-pickler-admin/package.json` — 新增 `lint:check` script（无破坏性，新增字段）
- `.github/workflows/ci.yml` — 新文件

### 不受影响

- 后端 Java 代码（零改动）
- 前端 Vue 代码（零改动）
- 小程序代码（零改动）
- 现有部署工件（`deploy/` 不动）
- 现有 OpenSpec specs（除 infrastructure 外）

## Decisions

### D1: 触发条件 — push + PR，所有分支

**选择**：`on: [push, pull_request]`，**不**做路径过滤（path filter）。

**理由**：
- v1 简单优先，避免「为什么这个 PR 没跑 CI」的疑惑
- backend + frontend job 并行跑，单 job ~3-5 分钟，可接受
- 路径过滤可在 Phase 2 优化（如果跑得太频繁再加）

### D2: 集成测试用 service container，不用 Testcontainers

**选择**：GitHub Actions 原生 `services:` block + MySQL 8 + Redis 6 镜像。

**理由**：
- 集成测试配置 `src/test/resources/application-integration.yml` 已硬编码 `localhost:3306` + `localhost:6379` + `root/root`，service container 直接暴露在 runner 的 localhost 上零配置即可对接
- Testcontainers 需要 Docker-in-Docker，GitHub-hosted runner 支持但启动慢 + 复杂
- 现有 18 个集成测试 case 已经按这个模式设计（参见 `IntegrationTestConfig.java`）

### D3: 不跑 Playwright E2E

**选择**：CI 跳过 `npm run test:e2e`。

**理由**：
- E2E 需要 backend 启动（8080）+ frontend dev server（5173）+ Playwright 浏览器，job 复杂度爆炸
- 18 个 IntegrationTest 已经覆盖核心 API 路径（auth / user / event / banner / credential hardening）
- 客户当前阶段优先「能交付」，E2E CI 化属于工程化优化，留 Phase 2

### D4: 不在 CI 里做 deploy

**选择**：CI 只到 test + build，不做 `git push` / `scp` / `ssh deploy`。

**理由**：
- 部署目标（阿里云 ECS）的 SSH key、OSS 凭据不该进 GitHub Actions secrets（客户运维流程不一定走 GitHub）
- 当前部署流程是 `install.sh` + RUNBOOK 手工操作，可重复、可审计
- 自动 CD 应该和客户的发布流程（可能用阿里云效 / 自建 Jenkins）对齐，不是仓库该决策的事

## Risks

| 风险 | 缓解 |
|------|------|
| 集成测试在 CI 跑得比本地慢（service container 启动开销）| 单 job timeout 15 分钟，超时自动 cancel |
| MySQL service container 首次启动 race condition（应用先于 DB ready）| workflow 加 `healthcheck` + `wait-for-it` 循环等 3306 可连 |
| Maven 依赖下载慢（每次 CI 拉全部 jar）| `actions/cache@v4` 缓存 `~/.m2/repository`，key 按 `pom.xml` hash |
| `npm ci` 在 lockfile 漂移时失败 | 这正是我们想要的 — 强制 lockfile 与 package.json 同步 |
| lint 在 CI 报红但本地 `--fix` 能修 | 接受这个 — 开发者本地跑 `npm run lint` 自动修，CI 是门禁 |
