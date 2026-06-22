# Tasks: add-cicd-pipeline

## 实施计划

按依赖顺序 3 个原子 commit + 1 个归档 commit。每个 commit 必须独立可验证。

### Day 1 — CI 落地（3 commits）

---

#### Commit 1: `hey-pickler-admin/package.json` 新增 `lint:check` script

**文件**: `hey-pickler-admin/package.json`

**改动**: 在 `scripts` 块新增一行 `lint:check`，去掉 `--fix` flag。

**Before**:
```json
"lint": "eslint . --ext .vue,.js,.jsx,.cjs,.mjs,.ts,.tsx,.cts,.mts --fix --ignore-path .gitignore",
```

**After**:
```json
"lint": "eslint . --ext .vue,.js,.jsx,.cjs,.mjs,.ts,.tsx,.cts,.mts --fix --ignore-path .gitignore",
"lint:check": "eslint . --ext .vue,.js,.jsx,.cjs,.mjs,.ts,.tsx,.cts,.mts --ignore-path .gitignore",
```

**验证**:
```bash
cd hey-pickler-admin
npm run lint:check   # 必须能跑（可能报已存在的 lint 问题，那也算正常输出）
```

**Commit message**: `feat(admin): 新增 lint:check script 供 CI 使用`

---

#### Commit 2: `.github/workflows/ci.yml` 新增 CI workflow

**文件**: `.github/workflows/ci.yml`（新文件）

**内容**: 参见 `design.md §1` 的完整 YAML。

**关键结构**:
- `name: CI`
- `on: [push, pull_request]`（所有分支）
- `concurrency.cancel-in-progress: true`
- `jobs.backend`：JDK 17 + Maven cache + MySQL 8 + Redis 6 service containers
  - `mvn -B -ntp clean package -DskipTests`（编译）
  - `mvn -B -ntp test -Dtest='!*IntegrationTest'`（单测）
  - `mvn -B -ntp test -Dtest='*IntegrationTest'`（集成）
- `jobs.frontend`：Node 18 + npm cache
  - `npm ci`
  - `npm run lint:check`
  - `npm run build`

**本地验证（不能完全验证，但能 sanity check）**:
```bash
# 1. YAML 语法
python3 -c "import yaml; yaml.safe_load(open('.github/workflows/ci.yml'))" && echo OK

# 2. 本地能跑通 lint:check（验证 script 路径对）
cd hey-pickler-admin && npm run lint:check && cd ..

# 3. 本地能跑通单测（已有命令，验证 CI 命令对）
cd hey-pickler-server && mvn -B -ntp test -Dtest='!*IntegrationTest' && cd ..
```

**真正的验证**：push 后到 GitHub Actions UI 看 workflow 跑起来。

**Commit message**: `feat(ci): 新增 GitHub Actions CI workflow（backend + frontend 双 job）`

---

#### Commit 3: lint 债务清理（实施时发现必须加）

**背景**：实施 Commit 2 后跑 `npm run lint:check` 暴露 72 个预存 lint 错误（40 `no-explicit-any` + 27 `no-empty` + 3 `no-unused-vars` + 2 `ban-types`）。CI 上线后第一条 PR 会红，必须先清理。

**文件改动**:
- `hey-pickler-admin/.eslintrc.cjs`：新增 3 个规则覆盖
- `hey-pickler-admin/src/components/common/ImageUpload.vue`：移除未用的 `props` 赋值
- `hey-pickler-admin/src/views/banners/BannerListView.vue`：移除未用的 `formatDate` import + 空 if 块加注释
- `hey-pickler-admin/src/views/users/UserDetailDrawer.vue`：移除未用的 `ElMessage` import
- `hey-pickler-admin/src/views/activities/ActivityListView.vue`：空 if 块加注释
- `hey-pickler-admin/src/views/events/EventListView.vue`：空 if 块加注释

**验证**:
```bash
cd hey-pickler-admin
npm run lint:check   # 期望：0 errors, 42 warnings
```

**Commit message**: `fix(admin): 清理预存 lint 债务让 CI lint:check 可作门禁`

---

### Day 1 收尾 — OpenSpec 归档（1 commit）

#### Commit 3: 归档 OpenSpec change

**前置条件**:
1. Commit 1 + 2 已 push
2. CI 在 GitHub Actions UI 跑绿（backend + frontend 双 job 全过）
3. 故意 push 一行 lint 违规代码到测试分支，验证 `lint:check` 能拒绝（可选但推荐）

**步骤**:
```bash
# 1. 移动 change 到 archive（日期戳前缀）
mv openspec/changes/add-cicd-pipeline openspec/changes/archive/2026-06-18-add-cicd-pipeline

# 2. 应用 spec delta 到 infrastructure spec
# 手工把 specs/infrastructure/spec.md 里的 ## ADDED Requirements 内容合并到
# openspec/specs/infrastructure/spec.md 的末尾（保留原有 ## ADDED/REMOVED 标记
# 删除，把 ### Requirement: 段落直接 append）
```

**验证**:
```bash
# 1. archive 目录四件套齐全
ls openspec/changes/archive/2026-06-18-add-cicd-pipeline/
# 期望：design.md  proposal.md  specs/  tasks.md

# 2. infrastructure spec 含新 requirement
grep -c "CI pipeline" openspec/specs/infrastructure/spec.md
# 期望：≥1

# 3. changes 目录干净（没有未归档的 add-cicd-pipeline）
ls openspec/changes/ | grep add-cicd-pipeline || echo "已归档"
```

**Commit message**: `chore(openspec): 归档 add-cicd-pipeline 变更`

---

## 验收清单（delivery checklist）

实施完成后，逐项验证：

- [ ] `hey-pickler-admin/package.json` 含 `lint:check` script
- [ ] `.github/workflows/ci.yml` 文件存在，YAML 语法合法
- [ ] push 到任意分支 → GitHub Actions UI 看到 CI workflow 触发
- [ ] backend job：Maven 编译 + 单测 + 集成测试全绿（18 case 全过）
- [ ] backend job：日志能看到 MySQL 8 + Redis 6 service container 启动 + healthcheck 通过
- [ ] frontend job：`npm ci` + `lint:check` + `build` 全绿
- [ ] 故意推一个 ESLint 违规（如 `var x=1`）→ frontend job 红
- [ ] 故意推一个 Java compile error → backend job 红
- [ ] PR 推新 commit → 旧 run 自动 cancelled（concurrency 生效）
- [ ] OpenSpec archive 目录 `2026-06-18-add-cicd-pipeline/` 四件套齐全
- [ ] `openspec/specs/infrastructure/spec.md` 含「CI pipeline」requirement
- [ ] `openspec/changes/` 不含 `add-cicd-pipeline`（已归档）

## 风险与回滚

| 风险 | 触发条件 | 回滚方式 |
|------|---------|---------|
| CI workflow 把 master 跑红 | 现有代码有 lint 问题或单测 flaky | 立刻修；如严重可临时改 `on: [pull_request]` 跳过 push 触发 |
| 集成测试在 service container 下行为不一致（端口/host/race）| CI 失败但本地通过 | 加 debug step 打印日志；最坏情况临时改成 `mvn test -Dtest='!*IntegrationTest'` 跳过集成（留下 TODO）|
| `npm ci` 在 lockfile 漂移时报错 | 开发者改 package.json 忘了 `npm install` | 这是预期行为，强制 PR author 修 lockfile |

## 不在范围

参见 `proposal.md` 的「不在范围」章节：E2E、CD、API 版本化、test coverage、代码质量扫描 — 全部 Phase 2 或更晚。
