# 管理后台运营体验优化设计（方案 A：办赛指挥中心 + 信息架构重构）

- **日期**：2026-06-29
- **状态**：待评审
- **范围**：`hey-pickler-admin`（Vue 3 管理后台）为主，含 1 处 `hey-pickler-server` 后端小改
- **北极星**：让运营在一个地方顺畅走完「办一场完整赛事」的全流程——建赛 → 报名 → 分组 → 对阵 → 发分，不跳页、不漏步、状态清晰。

---

## 1. 背景与动机

以运营视角体验现有后台（10 个菜单 + 办赛闭环 + 双积分页）后，定位到三类问题。其中最严重的是：**办赛后半程在前端是断的。**

### 1.1 后端能力 vs 前端现状（核心证据）

| 阶段 | 后端接口 | 前端现状 |
|---|---|---|
| 建赛/编辑 | ✓ | ✓ `EventFormDialog` |
| 报名/签到 | ✓ | ✓ `RegistrationDrawer`（无批量/导出） |
| 分组 | ✓ grouping | ✓ `GroupingPanel` |
| **生成对阵** | ✓ `POST /api/admin/events/{id}/matches/generate` | ✗ **未接** |
| **查对阵/比分** | ✓ `GET /api/admin/events/{id}/matches` | ✗ **未接**（无 `api/matches.ts`） |
| **录入/重置比分** | ✓ `POST /api/admin/matches/{id}/score`、`/reset` | ✗ **未接** |
| 配置加分表 | ✓ `PUT /api/admin/events/{id}/placement-points` | ✓ `PlacementPointsDialog` |
| **完成赛事+发分** | ✓ `POST /api/admin/events/{id}/complete` | ✗ **未接** |

**结论**：运营目前在 admin 端走不完一场赛事——分组之后（对阵 → 比赛 → 完成发分）前端完全缺失。这不是"操作散"，而是"后半程断了"。补齐这三块缺失前端（接已存在的后端接口）是本设计的核心增量。

### 1.2 其他痛点

- **菜单扁平堆叠**：10 项无分组，高频（赛事/用户/排名）与低频（Banner/管理员/日志）平级。
- **命名混淆**：「用户日志」（实为封禁记录）vs「操作日志」（系统审计），CLAUDE.md 已警告。
- **状态变更入口隐蔽**：藏在状态徽章的小弹窗里；且前端 `EventListView.STATUS_TRANSITIONS` 漏写了 `OPEN→IN_PROGRESS`、`FULL→IN_PROGRESS`、`FULL→OPEN`，运营无法手动开赛（目前只能靠 `EventStatusScheduler` 定时自动转）。
- **两套重复页面**：`EventListView`（STAR）与 `ActivityListView`（PARTY）底层同表、代码大段重复。
- **无工作台待办**：首页只是只读 KPI 看板。
- **签到/名单**：逐个签到、无法导出参赛名单。
- **排名无分页无搜索**：`RankingView` 写死 `size:100`。

---

## 2. 领域事实（已核实，作为设计依据）

### 2.1 事件状态机（`StatusTransitionValidator`，7 条规则）

```
DRAFT      → OPEN | CANCELLED
OPEN       → FULL | IN_PROGRESS | CANCELLED
FULL       → OPEN | IN_PROGRESS | CANCELLED
IN_PROGRESS→ COMPLETED | CANCELLED
COMPLETED  → （终态）
CANCELLED  → （终态）
```

> 后端**支持**手动开赛（OPEN/FULL → IN_PROGRESS）。前端漏写是 bug，本设计在详情页补齐。

### 2.2 发分机制（只有两条路径）

1. **手动批量发分** `PointService.enterPoints(source=MANUAL)` —— 由 `POST /api/admin/events/{id}/points`（或 `/rankings/points`）触发，即现有「录入成绩」按钮。
2. **名次自动发分** `PlacementService.issue(source=PLACEMENT)` —— 由 `POST /api/admin/events/{id}/complete` → `MatchServiceImpl.complete()` 触发，按 `event_placement_points` 表发放。

- `CHECK_IN` / `REGISTRATION` 两个 `PointSource` 枚举**当前无调用方**，签到/报名不发分（预留）。
- **STAR 与 PARTY 发分机制完全相同**，系统不区分；差异仅在文案（`constants/terms.ts`：STAR=竞技/战力，PARTY=社交/活力）。

> 因此详情页**不按 type 分化阶段**，改为按赛事实际数据自适应（见 §4.2）。

### 2.3 自动状态流转

- `EventStatusScheduler`：到 `eventTime` 的 **OPEN/FULL** 赛事自动转 `IN_PROGRESS`（`ACTIVE_STATUSES=[OPEN, FULL]`）。
- 报名退赛（WITHDRAWN）使人数低于上限时，`FULL` 自动转回 `OPEN`。

---

## 3. 目标与非目标

### 目标
1. 新增**赛事详情页**作为办赛指挥中心，聚合全流程、补齐对阵/比赛/完成发分三块前端。
2. 状态变更显式化、对齐后端 7 条规则，修复手动开赛入口缺失。
3. 菜单分组 + 命名消歧；赛事/活动命名统一（**不合并入口**）。
4. 工作台加待办；报名加批量签到 + 名单导出；排名加分页 + 搜索。
5. 列表信息密度提升；抽取共享子组件消除两份重复代码的漂移。

### 非目标（留待 Roadmap / 方案 B·C）
- 工作台待办的**后端聚合 API**（本设计用前端聚合现有接口）。
- 群发通知推送、用户双积分明细页/趋势（需新表/新服务）。
- 对阵表增强可视化（本设计先做基础按组对阵表）。

---

## 4. 设计

### 4.1 菜单信息架构（P0）

用 `el-sub-menu` 将 10 项扁平菜单归为 4 组，高频前置：

```
▾ 运营管理
    工作台（原"首页"，加待办，见 §4.3）
    竞技赛事（原"赛事管理"，STAR/战力）
    社交活动（原"活动管理"，PARTY/活力）
    用户管理
▾ 积分与赛季
    排名管理
    赛季管理
▾ 内容运营
    Banner 管理
▾ 系统
    管理员管理
    封禁记录（原"用户日志"）
    操作日志
```

**命名消歧**：
- 「用户日志」→「**封禁记录**」（数据源是 ban/unban，名副其实）。
- 「操作日志」保留，与「封禁记录」同入「系统」组，结构上杜绝点错。

**赛事/活动**：**只统一命名、不合并入口**（用户决策）。两份列表页保留，命名对仗为「竞技赛事」/「社交活动」，与 `TERMS` 对齐；如希望保留原名，改为加「战力/活力」副标签亦可。

> 实现备注：菜单项当前散落在 `AppSidebar.vue` 硬编码，建议改为由 `router/index.ts` 的 `meta`（`title` / `icon` / `group`）驱动渲染，便于维护与未来按角色过滤。

### 4.2 赛事详情页 —— 办赛指挥中心（P1，核心）

**路由**：新增 `/events/:id`（`name: EventDetail`）。入口：列表「标题」点击 / 新增「管理」按钮。赛事与活动列表都跳此同一详情页。

**布局**：
1. **页头**：标题 + 类型/形式/状态徽章 + 地点·时间 + 报名进度条 + [编辑][删除]。
2. **生命周期 Stepper**：`草稿 → 报名 → 分组 → 对阵/比赛 → 已结束`。当前阶段高亮、已完成打勾、可点跳转。
3. **阶段内容区**（Tab 或锚点滚动），每阶段聚合该步操作：

| 阶段 | 详情页动作 | 来源 |
|---|---|---|
| ① 基本信息 | 编辑表单 | 复用 `EventFormDialog` 内嵌 |
| ② 报名 | 报名表 + 批量签到 + 名单导出 | 复用 `RegistrationDrawer` 内容 + §4.3 新增 |
| ③ 分组 | 策略/锁定/换组 | 复用 `GroupingPanel` 内嵌 |
| ④ 对阵/比赛 | 生成对阵 → 按组对阵表 → 录入/重置比分 → 实时排名 | **新增**（新建 `api/matches.ts`，接 §2.1 表中 matches 接口） |
| ⑤ 发分 | 加分表配置 + 「完成赛事并发分」 | 复用 `PlacementPointsDialog` + **新增** complete 入口 |

**阶段自适应**（不按 type 写死）：③④阶段按赛事实际数据呈现——若未分组/不办对阵（如纯社交聚会），运营可跳过，直接在⑤发分（手动录入或配 placement 表）。Stepper 对未走的阶段标记「跳过」而非「未完成」。

**状态机显式化（核心修复）**：每阶段底部放显式「进入下一阶段」主按钮，**严格按 §2.1 的 7 条规则**渲染当前状态的所有合法目标态，补齐前端漏掉的 `OPEN→IN_PROGRESS`、`FULL→IN_PROGRESS`、`FULL→OPEN`。一个按钮 = 一次状态推进，附一句话提示（如「锁定后不可再改报名」）。彻底取代列表里藏在状态徽章中的小弹窗。

> 列表行的状态徽章保留只读展示；状态变更统一收敛到详情页，避免两处入口不一致。

**对阵/比赛阶段交互**：对阵表按组展示 round-robin 配对，每场显示双方 + 比分 + 状态。正常由用户在小程序自助提交比分；admin 端提供**代录 / 重置**作为兜底（用户未及时提交时运营兜底），并展示实时 standings。

> ⚠ **实时排名来源**：admin 端目前**无 standings 接口**（仅小程序端 `GET /api/app/events/{id}/standings` 有，由 `matchService.standings` 计算）；admin `GET matches` 只返回逐场 `MatchVO`、**不含计算排名**。为保证 admin 看到的排名与用户端一致、避免在 TS 重算规则（胜场 desc → 净胜局 desc → 同分并列）导致漂移，**新增 admin standings endpoint**：`AdminMatchController` 加 `GET /events/{id}/standings`，复用 `matchService.standings(eventId)`。

**发分阶段**：
- 显示当前 `event_placement_points` 配置（`PlacementPointsDialog` 内嵌编辑）。
- 「完成赛事并发分」按钮调 `POST /api/admin/events/{id}/complete`（前置：所有 match COMPLETED，否则后端返回 PARAM_ERROR，前端需提示具体未完成场次）。
- 完成后展示发分结果（按 `source=PLACEMENT` 的 `point_record`），并提供「手动补录」（`enterPoints`）兜底。

### 4.3 工作台 + 高频操作（P2/P3）

#### 4.3.1 工作台待办（首页改造）
首页保留现有 KPI/趋势图，顶部加「待办」区，用现有 `adminListEvents(status=...)` 前端聚合，每条带直达 `/events/:id` 的按钮：

| 待办 | 判定 | 动作 |
|---|---|---|
| 草稿待发布 | status=DRAFT | 去发布 |
| 即将开赛 | OPEN/FULL 且 eventTime 近 3 天 | 去管理 |
| 待开赛 | 已过 eventTime 仍 OPEN/FULL | 手动开赛 |
| 进行中 | IN_PROGRESS | 去录比分 / 完成 |
| 已结束 | COMPLETED | 查看发分 |

> STAR/PARTY 各拉一遍合并展示，按时间排序。

#### 4.3.2 批量签到（RegistrationDrawer / 详情页报名阶段）
报名表加多选 +「批量签到」：循环调 `updateRegistrationStatus(CHECKED_IN)`，**串行**执行 + 进度提示。失败项汇总提示。

> 限流说明：批量调用来自单一 admin 会话/IP，串行可规避 `RateLimitFilter` 的 **per-IP** 限流；但 **per-admin-user** 限流仍可能成为瓶颈（admin 一次代签几十人）。第一版用串行 + 适度间隔；若实测触发限流，退路是后端加批量签到接口（见 Roadmap）。

#### 4.3.3 名单导出（CSV）
报名表加「导出名单」：分页循环拉全量报名数据，前端生成 CSV 下载。字段：昵称 / ID / 城市 / 比赛类型 / 搭档 / 状态 / 报名时间。大体量给 loading。

#### 4.3.4 排名分页 + 搜索（含后端小改）
- **分页**：`RankingView` 去掉写死的 `size:100`，接入分页（`getRankings` 后端已支持 `page/size`）。
- **搜索**：后端 `RankingQuery` 加 `keyword` 字段；`getRankings` 在已缓存的全量 `List<RankingVO>` 上按 `nickname` 模糊匹配后分页（不破坏 Redis 缓存结构）。前端加搜索框。
  - 这是本设计**唯一**的后端改动。

#### 4.3.5 列表信息密度 + 共享组件抽取
- 报名进度条（mini bar）替换纯数字；顶部加场景化快捷筛选 chip（草稿/报名中/进行中/已结束）。
- 抽取共享子组件 `EventStatusBadge`（状态徽章 + 合法转换，对齐 §2.1）、`EventFilterBar`（筛选栏），让赛事/活动两份列表复用，消除重复代码漂移。

---

## 5. 边界与依赖

- **前端为主 + 2 处后端小改**：① ranking `keyword`（§4.3.4）；② admin `standings` endpoint（§4.2 阶段④，复用 `matchService.standings`）。两处都是轻量只读增强，无 DB 迁移。
- 详情页补齐的「对阵/比赛/完成发分」三块，**接的全是已存在的后端接口**（standings 除外，需新增 endpoint 但复用已有 service），无新业务逻辑、无 DB 迁移。
- 依赖现有：`api/grouping.ts`、`api/placement.ts`；**新建** `api/matches.ts` 封装 matches 系列接口。

---

## 6. 风险与对策

| 风险 | 对策 |
|---|---|
| 详情页状态按钮与后端规则不一致 | 严格按 §2.1 七条规则；列表与详情页状态来源统一为共享常量 |
| `complete()` 因存在未完成 match 而失败 | 前端先校验/提示未完成场次，再调 complete |
| 批量签到触发限流 | 串行调用 + 进度提示 |
| 名单导出全量拉取性能 | 分页循环 + loading；超大体量可后续改为后端导出接口 |
| 共享组件抽取引发两页回归 | 抽取后对两份列表做回归（筛选/状态变更/分页） |
| 菜单由 meta 驱动后的路由守卫 | 保持 `requiresAuth` 逻辑不变，仅改渲染数据源 |

---

## 7. 落地拆分（供 writing-plans）

- **P0 菜单 IA**：菜单分组 + 命名消歧（封禁记录）+ 赛事/活动命名统一 + 菜单改 meta 驱动。
- **P1 详情页（核心）**：`/events/:id` + Stepper + 补齐对阵/比赛/完成发分（`api/matches.ts`）+ 状态显式化。
- **P2 提效**：工作台待办 + 批量签到 + 名单导出。
- **P3 数据**：排名分页+搜索（含后端 `keyword`）+ 列表信息密度 + 共享组件抽取。

每阶段独立可上线、可回归。

---

## 8. Roadmap（方案 B / C，不在本次范围）

- **方案 B**：工作台待办后端聚合 API；对阵表查询优化；排名搜索后端增强。
- **方案 C**：群发通知推送；用户双积分明细页/趋势；对阵表增强可视化（需新表/新服务/DB 迁移 V16+）。

---

## 9. 验证策略

- **详情页全流程 E2E**（建赛 → 发布 → 报名 → 分组 → 对阵 → 录分 → 完成发分）在 STAR 单打/双打/混双各走通一遍。
- 状态显式化：7 条转换每条都可点、非法转换不出现。
- 两份列表（赛事/活动）增强后回归：筛选、状态、分页。
- 批量签到 / 导出边界：空选、全选、部分失败、限流。
- 排名分页/搜索：翻页、keyword 命中/未命中、与缓存兼容。
- 菜单：4 组渲染、命名正确、路由守卫不受影响。

---

## 10. 开放问题

- 赛事/活动最终命名：采用「竞技赛事/社交活动」，还是保留「赛事管理/活动管理」+ 副标签？（§4.1，可在 review 时定）
