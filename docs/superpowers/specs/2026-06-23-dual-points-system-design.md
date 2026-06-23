# 双积分体系（战力 / 活力）设计

- **日期**：2026-06-23
- **状态**：Draft（待 review）
- **作者**：brainstorming 产物
- **范围**：积分命名 + 体系定稿（比赛分组 / 比分 / 自动发分规则、积分商城商品货架为后续独立 spec）

---

## 1. 背景

Hey Pickler 现有积分体系已按 `event.type` 双轴切分（`STAR` / `PARTY`），数据层地基齐全（`user.starPoints/partyPoints`、`point_record.type`、`ranking.type`），但存在多处不足：

- 面向用户文案是"明星积分 / 派对积分"，缺乏专业感，也不适配将来的积分商城。
- 段位仅 2 档（传奇 / 超级），阈值写死在 `RankingServiceImpl.calculateTier`。
- `point_record` 无来源分类（报名 / 参赛 / 名次 / 兑换等），无法支撑后续比赛自动结算发分。
- 赛季写死 `CURRENT_SEASON = "2026-Q2"`，无切换、无历史。
- 发分逻辑 `enterPoints` 耦合在 `RankingService`，职责混乱；且无"按比赛结果自动发分"的钩子。

本次重构双积分体系的**命名、段位、来源、赛季、商城扩展点**，为后续"比赛自动分组 + 比分 + 结算发分"和"积分商城"两个独立 spec 铺路。

## 2. 范围

**In scope**

- 积分命名（战力 / 活力）+ 赛事/活动称呼（竞技赛事 / 社交活动）
- 段位体系（6 档 + `application.yml` 配置，重启生效）
- 积分来源分类（`point_record.source` 枚举）
- 赛季机制（按类型独立，`season` 表 + 切换 + 历史归档）
- 商城扩展点预留（`PointWallet` 接口 + `exchange_record` 骨架表）
- 发分解耦（抽 `PointService`）

**Out of scope**（后续独立 spec）

- 比赛形式约束（单/双/混打）、自动分组、对阵 / 签表 / 轮次
- 比分记录、按规则自动结算发分（`source=PLACEMENT` 的产生逻辑）
- 积分商城商品 / 货架 / 兑换流程的**实现**（本次仅预留接口与表骨架）
- 赛季自动轮转（本次仅手动切换）

## 3. 命名映射

后端字段名**全部保留不变**，仅替换面向用户文案。

| 后端字段（不变） | 业务语义 | 面向用户文案 |
|---|---|---|
| `STAR` | 赛事 | 竞技赛事 · 战力 · 战力段位 |
| `PARTY` | 活动 | 社交活动 · 活力 · 活力段位 |

保留：`STAR/PARTY`、`starPoints/partyPoints`、`star_tier/party_tier`、`point_record.type`、`ranking.type`。

## 4. 展示层

### 4.1 命名落地：前端常量映射

wxapp 与 admin 各建一个术语常量，所有文案从常量取，不硬编码：

```
STAR  → { type:"竞技赛事", points:"战力",  tier:"战力段位" }
PARTY → { type:"社交活动", points:"活力",  tier:"活力段位" }
```

替换全部历史文案："明星赛事 / 派对活动、明星积分 / 派对积分、明星段位 / 派对段位、明星排名 / 派对排名"。前端落地后需 grep 全量校验无残留。

**理由**：后端字段已决定不动，文案是纯展示层，前端常量最轻、零跨端耦合，将来接 i18n 顺滑。

### 4.2 段位体系

6 档，战力与活力共用档名、阈值不同（战力门槛约为活力 2 倍，延续现有 STAR/PARTY 比例）：

| key | 中文名 | 战力阈值 | 活力阈值 |
|---|---|---|---|
| BRONZE | 青铜 | 0 | 0 |
| SILVER | 白银 | 500 | 200 |
| GOLD | 黄金 | 1200 | 500 |
| PLATINUM | 铂金 | 2500 | 1200 |
| DIAMOND | 钻石 | 5000 | 2500 |
| MASTER | 王者 | 10000 | 5000 |

`ranking.tier` 存英文 key；`calculateTier` 读配置计算；`RankingVO/UserVO` 增 `tierName`（中文）供前端直显。

## 5. 数据层

### 5.1 `point_record` 加来源与赛季

- 新增 `source VARCHAR(16) NOT NULL DEFAULT 'MANUAL'`
- 新增 `season_code VARCHAR(16)`（与 `type` 共同关联 `season` 表）
- `reason` 保留为自由文本备注
- 历史数据：`source` 默认 `MANUAL`，`season_code` 回填当前赛季

### 5.2 `season` 表（按类型独立结算周期）

```sql
CREATE TABLE season (
  id         BIGINT NOT NULL AUTO_INCREMENT,
  type       VARCHAR(8)  NOT NULL,        -- STAR | PARTY
  code       VARCHAR(16) NOT NULL,        -- 业务码，如 2026-Q2
  name       VARCHAR(32),
  start_date DATE,
  end_date   DATE,
  status     VARCHAR(8)  NOT NULL,        -- CURRENT | ARCHIVED
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_type_code (type, code)
);
```

- 战力（STAR）与活力（PARTY）**各自独立赛季周期**，可各自切换（如战力按季、活力按月）
- 初始化两条 `CURRENT`：`(STAR,'2026-Q2')`、`(PARTY,'2026-Q2')`——code 可同名，type 不同即独立
- 每个类型同一时刻最多一个 `CURRENT`
- 关联：`ranking (user_id, type, season_code)`；`point_record (type, season_code)`

### 5.3 `exchange_record` 表（商城预留骨架）

```sql
CREATE TABLE exchange_record (
  id         BIGINT NOT NULL AUTO_INCREMENT,
  user_id    BIGINT NOT NULL,
  type       VARCHAR(8) NOT NULL,         -- STAR | PARTY
  cost       INT NOT NULL,                -- 消耗积分
  item_ref   VARCHAR(64),                 -- 商品引用，商城未实现时 NULL
  status     VARCHAR(8) NOT NULL,         -- PENDING | SUCCESS | FAILED | REFUNDED
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user (user_id)
);
```

本次仅建表骨架；商品 / 货架 / 兑换流程不在本 spec。

### 5.4 V11 migration

- `point_record` +`source`（默认 `MANUAL`）、+`season_code`（回填当前赛季 code）
- 建 `season` 表 + 插入 `STAR/PARTY` 各一条 `2026-Q2 / CURRENT`
- 建 `exchange_record` 表
- `ranking.tier` 重算：`UPDATE ranking JOIN user` 按新 6 档阈值（基于 `starPoints/partyPoints`）重算 tier，`LEGEND/SUPER → BRONZE…MASTER`

## 6. 接口与逻辑层

### 6.1 段位配置（降级：重启生效）

```yaml
hey-pickler.tier:
  keys:  [BRONZE, SILVER, GOLD, PLATINUM, DIAMOND, MASTER]
  names: [青铜, 白银, 黄金, 铂金, 钻石, 王者]
  star:  { thresholds: [0, 500, 1200, 2500, 5000, 10000] }
  party: { thresholds: [0, 200, 500, 1200, 2500, 5000] }
```

`TierProperties`（`@ConfigurationProperties("hey-pickler.tier")`，**无 `@RefreshScope`**）。改阈值需重启。

> **降级说明**：项目 `pom.xml` 未引入 `spring-cloud-context` / `actuator`，`@RefreshScope` 不可用。为段位热加载引入 spring-cloud 属 over-engineer，且段位阈值是低频运营操作，重启可接受。后续若引入配置中心可再升级为热加载。

### 6.2 `PointService`（发分解耦）

现状 `enterPoints` 耦合在 `RankingService`。本次加 `source` / 赛季正好拆开：

- 新增 `PointService` + Impl，承接发分：
  - `enterPoints(type, records, source)` —— 按 `type` 取当前赛季 code 写 `point_record.source/season_code`
  - `deduct(userId, type, amount, itemRef)` —— 商城扣减（写 `point_record(source=REDEEM, points=-amount)` + `exchange_record(SUCCESS)` + `publishEvent(PointChangeEvent)`）
- `PointWallet`（窄接口，供商城依赖）：`getBalance(userId, type)` + `deduct(...)`，由 `PointService` 实现
- `RankingService` 删 `enterPoints`，仅留 `refreshRankings / getRankings / getTop5`
- `PointChangeListener` 不变（仍监听 `PointChangeEvent` 异步刷榜单）
- 现有手填入口（`PointEntryDialog`）→ `source=MANUAL`；后续比赛结算 spec → `source=PLACEMENT`

### 6.3 `PointSource` 枚举

`REGISTRATION`（报名）/ `CHECK_IN`（签到）/ `PLACEMENT`（名次）/ `MANUAL`（管理员手动）/ `REDEEM`（商城兑换）/ `ADJUST`（系统纠错）

### 6.4 赛季管理 API（admin，`@RequireRole ADMIN+`）

- `GET /api/admin/seasons?type=STAR` —— 列出某类型赛季
- `POST /api/admin/seasons` —— 新建（type/code/name/start/end）
- `POST /api/admin/seasons/{id}/activate` —— 切换为 `CURRENT`（事务：旧 `CURRENT→ARCHIVED` + 新 `CURRENT`）

切换后新赛季排名从 0 累计，旧赛季归档可查。

## 7. 改动面、兼容性、风险

### 7.1 改动面

| 层 | 新增 | 修改 |
|---|---|---|
| entity | `Season`, `ExchangeRecord` | — |
| enum/config | `PointSource`, `TierProperties` | — |
| service | `PointService(+Impl)`, `PointWallet`, `SeasonService(+Impl)` | `RankingServiceImpl` 移 `enterPoints`、`calculateTier` 读配置 |
| mapper/dto/vo | `SeasonMapper`, `ExchangeRecordMapper`, `SeasonCreateRequest`, `SeasonVO` | `PointEntryRequest +source`；`RankingVO/UserVO +tierName` |
| controller | `AdminSeasonController` | `AdminEvent/AdminRankingController.enterPoints` 改调 `PointService` 传 `source` |
| migration | `V11` | — |
| 配置 | — | `application.yml +tier` |
| wxapp | 术语常量 | profile/ranking/event-detail/my-events 文案；段位 6 档展示 |
| admin | 术语常量 + 赛季管理页 | EventFormDialog/UserDetailDrawer/PointEntryDialog/Rankings 文案；`PointEntryDialog +source` 下拉 |

### 7.2 兼容性

- `STAR/PARTY` 后端字段不变 → API / 数据无破坏
- `point_record.source` 历史默认 `MANUAL`；`season_code` 回填当前赛季
- `ranking.tier`：V11 按 `user.starPoints/partyPoints` + 新阈值重算
- `ranking.season='2026-Q2'` 保留；`season` 表初始化 STAR/PARTY 各一条 `CURRENT`
- `user` 余额不变（积分连续）
- `enterPoints` 签名变（+`source`），2 个调用点（`AdminEventController` / `AdminRankingController`）同步改

### 7.3 风险

- 段位配置重启生效（6.1 已述，不引入 spring-cloud）
- 发分解耦回归：靠 `PointService` 单测 + 现有 ranking 测试保证 `enterPoints` 行为不变（除新增 source/赛季）
- 赛季切换并发：`activate` 用事务保证"旧 ARCHIVED + 新 CURRENT"原子
- 前端文案遗漏：grep 全量"明星/派对"确保无残留

## 8. 分阶段落地（原子提交序列，供 writing-plans）

1. `PointSource` 枚举 + `TierProperties` + yml tier 配置
2. `Season`/`ExchangeRecord` entity + mapper + `V11` migration（tier 重算、source/season_code 回填、season 初始化）
3. `PointService` 抽取 + 发分入口改造（source/赛季）+ 单测
4. `calculateTier` 读配置 + `RankingService` 瘦身 + `tierName` VO + 单测
5. `SeasonService` + `AdminSeasonController` + 切换事务 + 单测
6. `PointWallet` 接口预留（`getBalance`/`deduct` 骨架）+ 单测
7. wxapp 术语常量 + 全量文案替换 + 段位展示
8. admin 术语常量 + 文案替换 + `PointEntryDialog` source 下拉 + 赛季管理页
9. 集成测试 + 文档（README / CLAUDE.md 积分体系说明）

## 9. 后续 spec（不在本范围）

- **比赛闭环**：赛事形式约束（单/双/混打）+ 自动分组（随机/按排名等策略）+ 对阵/签表/轮次 + 比分记录 + 按规则自动结算发分（`source=PLACEMENT`）
- **积分商城**：商品/货架 + 兑换流程（调用 `PointWallet.deduct` + `exchange_record`）
