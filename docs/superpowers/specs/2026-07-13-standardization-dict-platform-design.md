# 标准化产品 · 字典驱动配置平台 设计

- **日期**: 2026-07-13
- **状态**: 蓝图（Draft，待评审）
- **范围**: 整体架构设计（路线图阶段 0），不含实现
- **后续**: 本 spec 评审通过后，由 `writing-plans` 为「阶段 1 · 字典基础设施」出具实现计划

---

## 1. 背景与目标

Hey Pickler 当前是定制系统，段位 / 枚举 / 术语 / 品牌散落在 `application.yml` + 三端硬编码（后端 `TierProperties`、admin `constants/terms.ts` 与 `utils/index.ts`、wxapp `utils/util.js` 与 `terms.js`、`components/tier-badge`）。痛点：

- 改段位阈值要**重启**（`hey-pickler.tier` 在 yml）。
- 改一处段位定义要**同步改三端**，易漂移（如「形式」列曾因 `:color`+`plain` 冲突导致字看不清）。
- PARTY 轨没有独立命名体系，与 STAR 共用「青铜…王者」。
- 私有化交付给不同客户时，品牌（Logo / 产品名 / 主题色）无法定制。

**目标**：重构为**字典驱动的标准化配置平台**——运营方在 admin 自行定义段位体系、枚举展示、积分命名与品牌视觉，无需改代码、无需重启。

## 2. 关键决策（已与产品负责人确认）

| 维度 | 决策 |
|---|---|
| 范围 | 最大：双轨段位 + 积分命名 + 通用枚举字典 + 完整品牌定制 |
| 部署形态 | **独立部署 · 单套配置**（单租户）。字典 / 品牌全局一套，运营方在 admin 配置。不预留 `tenant_id`。 |
| STAR 轨 | 「积分排名」+ 青铜…王者（名称 / 颜色 / 阈值 / 图标可配） |
| PARTY 轨 | 积分单位「匹克豆」+ **球友称号系**：见习 → 活力 → 热血 → 资深 → 明星 → 传奇球友；暖色活力渐变；奖牌 / 星星徽章 |
| 字典存储 | **方案③ 混合**：通用字典（简单枚举）+ `tier_config`（段位）+ `brand_config`（品牌） |
| `tier_code` | 双轨统一 `BRONZE..MASTER`（内部 6 档语义），双轨仅 `name/color/threshold/icon` 不同 → **`Ranking` 表零改动** |
| 术语 / 积分命名 | 不单列模块，归并进通用字典（`track_term` 字典，`extra_json` 存单位） |

## 3. 现状分析

- 双轨积分已存在（STAR 战力 / PARTY 活力），段位阈值在 `application.yml` 的 `hey-pickler.tier`，STAR `[0,500,1200,2500,5000,10000]` / PARTY `[0,200,500,1200,2500,5000]`。
- 段位 `keys/names` **单套**（STAR/PARTY 共用青铜…王者），仅 thresholds 分轨。
- 段位定义**三端硬编码重复**：后端 `TierProperties` + admin（`constants/terms.ts`、`utils/index.ts`）+ wxapp（`utils/util.js`、`terms.js`、`tier-badge`）。
- 无任何 dict / config 表；无多租户概念（单部署）。
- admin `constants/terms.ts` 算术语集中点的雏形。

## 4. 总体架构：方案③（混合存储）

三类配置按"结构强度"分流：

- **简单枚举**（名 / 色 / 排序的键值对）→ 通用字典 `sys_dict` + `sys_dict_item`，灵活，加枚举不建表。
- **段位**（双轨、多字段、有序、阈值数值校验）→ 专用表 `tier_config`，强结构防呆。
- **品牌**（分组键值，色板 / Logo / 文案）→ 专用表 `brand_config`，分组清晰。

核心原则：**系统逻辑绑定的"编码"永不可改，运营方只动"展示名 / 颜色 / 阈值"等可配字段。**

## 5. 数据模型

### 5.1 通用字典 `sys_dict` + `sys_dict_item`

```
sys_dict        id · dict_code【不可改】 · dict_name · description · status · created_at/updated_at/deleted_at
sys_dict_item   id · dict_code · item_key【不可改】 · item_label【可配】 · item_color【可配】
                · sort【可配】 · status · extra_json · 软删
                UNIQUE(dict_code, item_key)
```

例：`event_type` 字典下 `STAR→竞技赛事(#F59E0B)` / `PARTY→社交活动(#8B5CF6)`；`track_term` 字典下 `STAR→积分排名` / `PARTY→匹克豆`（`extra_json={unit:"匹克豆"}`）。

### 5.2 段位配置 `tier_config`（双轨专用）

```
tier_config     id · track(STAR/PARTY) · tier_code【不可改: BRONZE..MASTER 统一6档】
                · tier_name【可配】 · tier_color【可配】 · threshold【可配,数值校验】
                · icon(emoji) · description · sort · status · 软删
                UNIQUE(track, tier_code)
```

`tier_code` 双轨统一为 `BRONZE/SILVER/GOLD/PLATINUM/DIAMOND/MASTER`，`Ranking.tier` 值域不变 → **Ranking 表零改动**。PARTY 的「见习球友」即 PARTY 轨 BRONZE 档的展示名。档位数量锁定 6（与历史数据一致）。

### 5.3 品牌配置 `brand_config`（分组键值）

```
brand_config    id · group_code(palette/logo/copy/login_visual)
                · config_key【不可改】 · config_value【可配】 · value_type(color/image_url/text)
                · description · sort · status · 软删
                UNIQUE(config_key)
```

- `palette`：`primary_color` / `success_color` / `warning_color` / `danger_color` …
- `logo`：`admin_logo` / `wxapp_logo` / `favicon` / `login_logo`
- `copy`：`product_name` / `product_slogan` / `login_subtitle`
- `login_visual`：`login_bg_image`

### 5.4 迁移

新增 `V18__dict_platform.sql`（+ 段位用 `V19__tier_config.sql`；当前 Flyway head 为 **V17**，V18/V19 为 next-available）：将 yml tier 阈值、三端硬编码的枚举名色、前端 tier 颜色 map seed 进表。`TierProperties` 改为读 `tier_config`（带 Redis 缓存），yml 仅留兜底默认。

## 6. 枚举归属清单

| 进通用字典（展示名 / 色可配） | 不进字典（系统逻辑绑定） |
|---|---|
| `event_type` 赛事类型 | `user_role` 角色（权限判定） |
| `event_format` 形式（单 / 双 / 混打） | `ban_action` 封禁动作（审计） |
| `event_status` 赛事状态 | `grouping_strategy` 分组策略（算法） |
| `user_status` / `registration_status` | **所有枚举的「值 / 流转逻辑」本身** |
| `team_status` / `match_status` | （状态机 DRAFT→OPEN 流转永不可改） |
| `point_source` 积分来源 / `notification_type` | |

铁律：进字典的枚举，`item_key`（STAR / OPEN / SINGLES…）永不可改，运营方只动 `label/color/sort/status`。

## 7. 段位体系设计

- **icon**：存 emoji 字符串（三端通用）。
  - STAR 默认：🥉 🥈 🥇 🏆 💎 👑
  - PARTY 默认：🌟 ⭐ ✨ ⭐⭐ 🏅 👑（见习 → 传奇球友）
- **description**：段位 tooltip 文案。
- **阈值校验**（admin 保存强校验）：每轨内 `threshold` 严格递增、`BRONZE=0`、`>=0`。
- **迁移 seed**：V19 把 PARTY 轨 name/color/icon 落成球友称号系默认值，STAR 沿用青铜…王者。
- **颜色是后端净新增字段**：现状后端 `TierProperties` 只有名称与阈值，**无颜色**；tier 颜色仅存在于前端（admin `constants/terms.ts` `TIER_COLOR`、`utils/index.ts` `colorMap`、wxapp `tier-badge.wxss`）。`tier_config.tier_color` 迁移时从前端 map seed，不是拆分既有后端颜色。

## 8. 品牌层设计

- **主题色板（admin）**：Element Plus **2.5+** CSS 变量模式，运行时注入 `--el-color-primary` 等 → 主色即时换肤；状态色单独配，避开运行时派生明暗色的难题。
- **wxapp**：wxss 不支持动态变量，走 `globalData.brand` + wxml inline style 绑定；Logo 换 tabBar icon / 页面 logo。
- **产品名 / 文案**：admin 改 `document.title` + 登录页文案；wxapp 改导航栏标题 + 页面文案。
- **Logo / favicon**：admin 替换 `<link rel="icon">` + header `<img>`；wxapp 替换 tabBar icon。

## 9. 生效 / 缓存 / 冻结策略

### 9.1 后端缓存
`TierProperties` 从「读 yml」改为「读 `tier_config` + Redis 缓存」，key：`dict:tier:{track}`、`dict:enum:{dict_code}`、`brand:all`。admin 写操作 → 同事务清对应 key + 全局 `dict_version` +1。

### 9.2 前端拉取（admin + wxapp）
- 启动拉全量 `GET /api/.../dict/bundle`（字典 + 段位 + 品牌），存 Pinia（admin）/ `globalData`（wxapp）+ localStorage 兜底。
- **版本号增量刷新**：进关键页面（排名 / 赛事列表）时轻量查 `dict/version`，变了才重拉 bundle。不轮询，省流量。
- admin 后台自己改了 → 本地立即生效。

### 9.3 赛季冻结
- 段位**不存储**，是「积分 → 阈值」的实时映射。阈值改了，段位即时跟着变——符合直觉，不是 bug。
- 历史**积分榜**本身冻结（season `ARCHIVED` 时排名定格）。
- **MVP 不做段位快照表**（YAGNI）。若日后要精确回溯历史赛季当时的段位标签，再加 `season_tier_snapshot`，列 **P1**。枚举 / 品牌纯展示层，无需冻结。

## 10. 子系统划分

| 模块 | 职责 | 关键接口 | 依赖 | 落点 |
|---|---|---|---|---|
| **A. 字典基础设施**〔地基〕 | 通用字典 CRUD + Redis 缓存 + 版本号 + bundle 聚合 | `GET/PUT /api/admin/dict/{code}/items`<br>`GET /api/admin/dict/bundle` · `/version`<br>`GET /api/app/dict/bundle`〔匿名〕 | 无 | `DictService` `DictCacheService` `AdminDictController` |
| **B. 段位体系** | `tier_config` 双轨 CRUD（强校验）+ 积分→段位计算 | `GET/PUT /api/admin/tier/{track}`<br>`TierResolver.resolve(track, points)` | A | `TierConfigService` `TierResolver`（替换 `TierProperties`） |
| **D. 三端运行时拉取** | 启动拉 bundle + 版本号校验 + 替换硬编码 | 调 A 的 bundle/version | A、B | admin `stores/dict.ts` · wxapp `app.js` + `globalData` |
| **E. PARTY 趣味视觉** | 段位徽章组件（球友称号 · 奖牌星星 · 暖色渐变） | 读 D 的 tier 配置渲染 | B、D | admin `TierBadge.vue` · wxapp `components/tier-badge` 重构 |
| **品牌层** | `brand_config` CRUD + 换肤 / Logo / 文案 | `GET/PUT /api/admin/brand/{group}` | A、D | `BrandService` `AdminBrandController` · admin `stores/brand.ts` + CSS var · wxapp `globalData.brand` |

> C（术语 / 积分命名）归并进 A，不单列。

## 11. 集成关键点

1. **`/api/app/dict/bundle` 必须匿名可访问**——wxapp 登录页就要显示品牌 Logo / 产品名 / 背景，那时没 token。加进 `AppAuthFilter.shouldNotFilter` 的 bypass（与 events/banners/rankings 同列）。
2. **`TierResolver` 是 `TierProperties` 的等价替换**——所有 `tierProperties` 调用点统一改走 `TierResolver`：`RankingServiceImpl`、`PointServiceImpl`、`UserServiceImpl`、`SeasonServiceImpl`、`AdminDashboardController`（`RankingVO` 是被动 DTO，实际重写点是组装它的 services）。行为不变、数据源换了，`Ranking` 表零改动。

## 12. 分阶段路线图

| 阶段 | 交付物 | 依赖 | 风险 |
|---|---|---|---|
| **0. 整体蓝图 spec**（本文档） | 标准化产品设计文档，不写代码 | — | 无 |
| **1. A 字典基础设施** | `sys_dict`/`sys_dict_item`（V18）+ Service/Controller + admin 字典管理页 + seed 现有枚举与 `track_term` | 无 | 低 |
| **2. B 段位体系字典化** | `tier_config`（V19）+ `TierConfigService`/`TierResolver` + admin 双轨段位页 + PARTY 球友称号 seed + `TierProperties`→`TierResolver` 替换 | A | 中 |
| **3. D 三端拉取 + C 术语** | admin `stores/dict.ts` + wxapp `globalData` + 版本号校验 + 替换 `terms.ts`/`util.js` + app bundle 匿名 bypass | A、B | 低 |
| **4. E PARTY 趣味视觉** | admin `TierBadge.vue` + wxapp `tier-badge` 重构 | B、D | 低 |
| **5. 品牌层** | `brand_config` + `BrandService` + admin 品牌管理页（4 tab）+ CSS var 换肤 + wxapp inline/tabBar | A、D | 中 |
| **6. 收尾** | 下线 yml tier（留兜底）+ 清三端残留硬编码 + 全链路 E2E + 更新 CLAUDE.md/AGENTS.md | 全部 | 低 |

每阶段独立 spec → plan → 实现 → 测试 → 合并。本次只交付阶段 0；下一步 `writing-plans` 为**阶段 1（A）**出具实现计划。

## 13. 非目标（YAGNI）

- 多租户 SaaS（本次单租户，不预留 `tenant_id`；未来若需另起 spec）。
- 段位赛季快照（MVP 不做，P1）。
- 段位数量可变（锁定 6 档）。
- 通用 KV 塞复杂结构（强结构走专用表）。
- 运营方改枚举 `item_key` / 状态机流转值（永不可改）。

## 14. 风险与对策

| 风险 | 对策 |
|---|---|
| B 阶段替换段位判定逻辑，可能改变段位计算行为 | 先固化 `TierResolver` 单测（对齐 `TierProperties` 现有阈值表行为），再替换调用点 |
| Element Plus 运行时换肤派生色（hover/lighten）不准 | 状态色单独配；主色用 EP 2.5+ CSS var；不依赖运行时明暗派生 |
| wxapp 动态主题色受限 | 仅关键色走 inline style；品牌色对 wxapp 影响收敛到徽章 + 少量组件 |
| 三端硬编码替换遗漏 | 每阶段 grep 清理 + E2E 覆盖；保留 fallback 默认值防 bundle 拉取失败 |
| `V1__init_schema` 的 `user.star_tier/party_tier` 默认 `'SHINING'`（不在 BRONZE..MASTER） | 阶段 2 迁移对齐列默认值为 `BRONZE`，避免新用户落库段位非法 |
