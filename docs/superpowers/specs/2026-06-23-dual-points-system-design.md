# 双积分体系（战力 / 活力）设计

- **日期**：2026-06-23
- **状态**：Draft v2（经 spec-reviewer 修订）
- **范围**：积分命名 + 体系定稿（比赛分组 / 比分 / 自动发分规则为后续独立 spec；积分商城商品/兑换流程为后续独立 spec）

---

## 1. 背景

Hey Pickler 现有积分体系已按 `event.type` 双轴切分（`STAR` / `PARTY`），数据层地基齐全，但存在多处不足：

- 面向用户文案是"明星积分 / 派对积分"，缺乏专业感，也不适配将来的积分商城。
- 段位 **3 档（LEGEND / SUPER / SHINING）**，阈值写死在 `RankingServiceImpl.calculateTier`（`RankingServiceImpl.java:104-114`，STAR: ≥1000 LEGEND / ≥500 SUPER / 否则 SHINING；PARTY: ≥500 / ≥200 / 否则 SHINING）。
- `point_record` 无来源分类（报名 / 参赛 / 名次 / 兑换等），无法支撑后续比赛自动结算发分。
- 赛季写死 `CURRENT_SEASON = "2026-Q2"`（`RankingServiceImpl.java`），无切换、无历史；`refreshRankings` 的 delete 仅按 `type`（`:133-134`），切换赛季后会**物理删除旧赛季排名**。
- 发分逻辑 `enterPoints` 耦合在 `RankingService`（接口 + Impl），职责混乱。
- 段位相关缓存清理硬编码 3 档（`RankingServiceImpl.java:165`），`AdminDashboardController` 段位分组兜底也硬编码 `"SHINING"`（`:94,97`）。

本次重构双积分体系的**命名、段位、来源、赛季（含归档）、发分解耦**，为后续"比赛自动分组 + 比分 + 结算发分"和"积分商城"两个独立 spec 铺路。

## 2. 范围

**In scope**

- 积分命名（战力 / 活力）+ 赛事/活动称呼（竞技赛事 / 社交活动）
- 段位体系（6 档 + `application.yml` 配置，重启生效）
- 积分来源分类（`point_record.source` 枚举）
- 赛季机制（按类型独立，`season` 表 + 切换 + **历史归档可查**）
- 商城扩展点：**仅 `PointWallet` 接口签名预留**（`getBalance` 本次实现；`deduct` 本次只定义签名，实现留商城 spec）
- 发分解耦（抽 `PointService`）

**Out of scope**（后续独立 spec）

- 比赛形式约束（单/双/混打）、自动分组、对阵 / 签表 / 轮次
- 比分记录、按规则自动结算发分（`source=PLACEMENT` 的产生逻辑）
- **`exchange_record` 表与 `PointWallet.deduct` 的实现**（推迟到积分商城 spec，避免本次建无消费方的空表）
- 赛季自动轮转（本次仅手动切换）

## 3. 命名映射

后端字段名**全部保留不变**，仅替换面向用户文案。

| 后端字段（不变） | 业务语义 | 面向用户文案 |
|---|---|---|
| `STAR` | 赛事 | 竞技赛事 · 战力 · 战力段位 |
| `PARTY` | 活动 | 社交活动 · 活力 · 活力段位 |

保留：`STAR/PARTY`、`starPoints/partyPoints`、`star_tier/party_tier`、`point_record.type`、`ranking.type/season`。

## 4. 展示层

### 4.1 命名落地：前端常量映射

wxapp 与 admin 各建术语常量，所有文案从常量取：

```
STAR  → { type:"竞技赛事", points:"战力",  tier:"战力段位" }
PARTY → { type:"社交活动", points:"活力",  tier:"活力段位" }
```

替换全部历史文案："明星赛事 / 派对活动、明星积分 / 派对积分、明星段位 / 派对段位、明星排名 / 派对排名"。

**验收**：`grep -rE "明星|派对" hey-pickler-wxapp/ hey-pickler-admin/src/` 应无残留（step 7/8 的 checklist）。

### 4.2 段位体系

由现有 **3 档（LEGEND / SUPER / SHINING）** 扩展为 **6 档**，战力与活力共用档名、阈值不同（战力门槛约为活力 2 倍，延续现有比例）：

| key | 中文名 | 战力阈值 | 活力阈值 | 现有档大致对应 |
|---|---|---|---|---|
| BRONZE | 青铜 | 0 | 0 | SHINING 低段 |
| SILVER | 白银 | 500 | 200 | SUPER 起点 |
| GOLD | 黄金 | 1200 | 500 | LEGEND 起点 |
| PLATINUM | 铂金 | 2500 | 1200 | — |
| DIAMOND | 钻石 | 5000 | 2500 | — |
| MASTER | 王者 | 10000 | 5000 | — |

`ranking.tier` / `user.starTier/partyTier` 存英文 key；`calculateTier` 读配置计算；`RankingVO/UserVO` 增 `tierName`（中文）供前端直显。

> **阈值说明**：以上为初版阈值，上线后按实际积分分布（P50/P90/P99）校准；因段位配置重启生效（见 6.1），调整成本可接受。

## 5. 数据层

### 5.1 `point_record` 加来源与赛季

- 新增 `source VARCHAR(16) NOT NULL DEFAULT 'MANUAL'`
- 新增 `season_code VARCHAR(16)`（与 `type` 共同关联 `season` 表）
- 新增索引：`KEY idx_type_season (type, season_code)`、`KEY idx_user (user_id)`
- `reason` 保留为自由文本备注
- 历史数据：`source` 默认 `MANUAL`，`season_code` 回填 `'2026-Q2'`

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

- 战力（STAR）与活力（PARTY）**各自独立赛季周期**，可各自切换
- 初始化两条 `CURRENT`：`(STAR,'2026-Q2')`、`(PARTY,'2026-Q2')`
- **CURRENT 唯一性**：MySQL 8 不支持部分唯一索引，改由 `activate` 接口在事务内保证（见 6.4）：先 `UPDATE ... SET status='ARCHIVED' WHERE type=? AND status='CURRENT'`（行锁），再置新行为 `CURRENT`
- `start_date/end_date` 按服务器时区（Asia/Shanghai）解释，跨时区问题留待后续

### 5.3 `exchange_record` 表 —— 推迟到积分商城 spec

商城明确 out-of-scope。建无消费方的空表属 YAGNI。`exchange_record` 表与 `PointWallet.deduct` 的实现一并推迟到积分商城 spec；本次仅预留 `PointWallet.deduct` 接口签名（见 6.2）。

### 5.4 V11 migration（执行顺序与约束）

**执行前确认 migration head 仍为 V10**（避免后续 PR 串行撞号）。migration 内按以下顺序：

1. 建 `season` 表
2. 插入 `(STAR,'2026-Q2',CURRENT)`、`(PARTY,'2026-Q2',CURRENT)`
3. `ALTER point_record ADD source VARCHAR(16) NOT NULL DEFAULT 'MANUAL'`
4. `ALTER point_record ADD season_code VARCHAR(16)`，然后 `UPDATE point_record SET season_code='2026-Q2' WHERE season_code IS NULL`
5. 加 `point_record` 索引 `idx_type_season`、`idx_user`
6. `ranking.tier` 重算：覆盖旧值 **SHINING / SUPER / LEGEND 全部三档**——`UPDATE ranking r JOIN user u ON r.user_id=u.id SET r.tier=<按新阈值计算>`（STAR 行按 `u.star_points`、PARTY 行按 `u.party_points`，分别套用新 6 档阈值）；同步 `UPDATE user SET star_tier/party_tier` 与新 tier 一致

> **注意**：`refreshRankings` 的 delete 当前不按 season（`:133-134`），必须随本 spec 改造为按 `(type, season_code)` 删除（见 6.2），否则 V11 后切换赛季会物理删除归档排名。

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

> 项目 `pom.xml` 无 spring-cloud / actuator，`@RefreshScope` 不可用；为段位热加载引入 spring-cloud 属 over-engineer，且段位阈值低频调整，重启可接受。

**段位缓存清理参数化**：`refreshRankings` 现硬编码 `Arrays.asList("LEGEND","SUPER","SHINING",null)`（`:165`）清缓存，必须改为遍历 `tier.keys` 配置 + `null`，否则旧 key 残留、新 key 清不掉。

### 6.2 `PointService`（发分解耦）与 `PointWallet`

**`PointService`（新增，承接发分）**：
- `enterPoints(type, records, source)` —— 按 `type` 查 `season where status='CURRENT'` 取当前赛季 code，写 `point_record.source/season_code`；累加 `user` 余额、重算 tier、`publishEvent(PointChangeEvent)`
- `deduct(...)` —— **本次不实现**（留积分商城 spec）

**`PointWallet`（新增窄接口，给商城依赖）**：
- `int getBalance(userId, type)` —— 本次实现，读 `user.starPoints/partyPoints`
- `void deduct(userId, type, amount, itemRef)` —— **本次仅定义签名**（实现留商城 spec；届时需 `@Transactional`、行级条件更新 `UPDATE user SET ... WHERE points>=?` 防并发、余额不足抛 `BizException(INSUFFICIENT_BALANCE)`、`@TransactionalEventListener(AFTER_COMMIT)` 发事件）

**`RankingService` 改造**：
- **接口删 `enterPoints` 方法签名**（`RankingService.java`），`RankingServiceImpl` 移除实现——两个调用点 `AdminEventController.enterPoints`（`:104`）、`AdminRankingController.enterPoints`（`:59`）改调 `PointService`
- `refreshRankings` 签名改为 `refreshRankings(type, seasonCode)`：delete 加 `.eq(season, seasonCode)` **只删当前赛季**（保留旧赛季归档）；插入的 ranking 写入 `seasonCode`
- `PointChangeEvent` 携带 `seasonCode`，`PointChangeListener` 据此调 `refreshRankings(type, seasonCode)`

**source 安全边界**：`PointEntryRequest` **不新增 `source` 字段**（不信任前端）；admin 手填入口在 `PointService.enterPoints` 服务端**强制 `source=MANUAL`**；`PLACEMENT` 由后续比赛结算 service 内部设置；`REDEEM` 由商城 deduct 设置。

### 6.3 `PointSource` 枚举

`REGISTRATION`（报名）/ `CHECK_IN`（签到）/ `PLACEMENT`（名次）/ `MANUAL`（管理员手动）/ `REDEEM`（商城兑换）/ `ADJUST`（系统纠错）

### 6.4 赛季管理 API（admin，`@RequireRole ADMIN+`）

- `GET /api/admin/seasons?type=STAR` —— 列出某类型赛季
- `POST /api/admin/seasons` —— 新建（type/code/name/start/end，默认 `ARCHIVED`）
- `POST /api/admin/seasons/{id}/activate` —— 切换为 `CURRENT`（`@Transactional`：先 `UPDATE season SET status='ARCHIVED' WHERE type=? AND status='CURRENT'` 行锁，再置新行 `CURRENT`；保证同类型时刻唯一 CURRENT）
- `GET /api/admin/seasons/{id}/rankings` —— **归档排名查询**（按 season id 直查 `ranking` 表，走 DB 不走缓存）

切换后新赛季排名从 0 累计；旧赛季因 `refreshRankings` 按 season 删除而保留为归档，可经上述接口查询。app 端 `GET /api/app/rankings` 默认仍查当前赛季（不变）。

## 7. 改动面、兼容性、风险

### 7.1 改动面

| 层 | 新增 | 修改 |
|---|---|---|
| entity | `Season` | — |
| enum/config | `PointSource`, `TierProperties` | — |
| service | `PointService(+Impl)`, `PointWallet` | **`RankingService` 接口删 `enterPoints`**；`RankingServiceImpl` 移 `enterPoints`、`calculateTier` 读配置、`refreshRankings` 加 season 维度 + 清缓存参数化 |
| mapper/dto/vo | `SeasonMapper`, `SeasonCreateRequest`, `SeasonVO` | `RankingVO/UserVO +tierName` |
| controller | `AdminSeasonController` | `AdminEvent/AdminRankingController.enterPoints` 改调 `PointService`；**`AdminDashboardController` tier 分组兜底 `SHINING` 改读配置/`BRONZE`** |
| migration | `V11` | — |
| 配置 | — | `application.yml +tier` |
| wxapp | 术语常量 | profile/ranking/event-detail/my-events 文案；段位 6 档展示 |
| admin | 术语常量 + 赛季管理页（含归档排名） | EventFormDialog/UserDetailDrawer/PointEntryDialog/Rankings 文案；段位 6 档 |

> `exchange_record` 表与 `PointEntryRequest +source` 均**不在本次**（前者推迟商城 spec，后者因 source 服务端强制而不暴露）。

### 7.2 兼容性

- `STAR/PARTY` 后端字段不变 → API/数据无破坏
- `point_record.source` 历史默认 `MANUAL`；`season_code` 回填 `'2026-Q2'`
- `ranking.tier` / `user.starTier/partyTier`：V11 覆盖旧值 **SHINING/SUPER/LEGEND 三档**重算
- `ranking.season='2026-Q2'` 保留；`season` 表初始化 STAR/PARTY 各一条 `CURRENT`
- `user` 余额不变（积分连续）
- `enterPoints` 从 `RankingService` 接口移除 → 2 个 controller 调用点同步改调 `PointService`

### 7.3 风险

- 段位配置重启生效（6.1，不引入 spring-cloud）
- 发分解耦回归：靠 `PointService` 单测 + 现有 ranking 测试保证 `enterPoints` 行为不变（除新增 source/赛季）
- **段位降级**：积分被 `ADJUST` 扣减或（未来）兑换导致 tier 下降——`calculateTier` 每次按当前积分重算，自然处理；`refreshRankings` 随之刷新
- 赛季切换并发：`activate` 事务 + 行锁保证同类型唯一 CURRENT（6.4）
- `refreshRankings` 改造风险：delete 必须加 season 维度，否则归档被删（5.4 注）
- 前端文案遗漏：grep 全量"明星/派对"校验（4.1）

## 8. 分阶段落地（原子提交序列，供 writing-plans）

> **强依赖**：step 2（V11 含 tier 重算）与 step 4（`calculateTier` 读配置 + 清缓存参数化）**必须在同一 PR**，否则 migration 执行后 tier 已是 6 档但代码仍按 3 档清缓存/计算，产生混乱。

1. `PointSource` 枚举 + `TierProperties` + yml tier 配置
2. `Season` entity + mapper + `V11` migration（建 season 表 + 插入 CURRENT、point_record +source/season_code + 索引 + 回填、tier 重算覆盖三档）—— **与 step 4 同 PR**
3. `PointService` 抽取 + `RankingService` 接口删 `enterPoints` + 2 个 controller 调用点改造 + `enterPoints` 写 source/赛季（服务端强制 MANUAL）+ 单测
4. `calculateTier` 读配置 + `refreshRankings(type, seasonCode)` season 维度 delete + 清缓存参数化 + `PointChangeListener` 传 seasonCode + `tierName` VO + 单测 —— **与 step 2 同 PR**
5. `SeasonService` + `AdminSeasonController`（列表/新建/activate 事务/归档排名查询）+ 单测
6. `PointWallet` 接口签名（`getBalance` 实现 / `deduct` 留签名）+ 单测
7. wxapp 术语常量 + 全量文案 + 段位 6 档展示（grep 验收）
8. admin 术语常量 + 文案 + `AdminDashboardController` tier 兜底改配置 + 段位 6 档 + 赛季管理页（含归档排名）
9. 集成测试 + 文档（README / CLAUDE.md 积分体系；顺带修正 CLAUDE.md「Current head: V8」→V10）

## 9. 后续 spec（不在本范围）

- **比赛闭环**：赛事形式约束（单/双/混打）+ 自动分组 + 对阵/签表/轮次 + 比分记录 + 按规则自动结算发分（`source=PLACEMENT`）
- **积分商城**：商品/货架 + `exchange_record` 表 + `PointWallet.deduct` 实现（事务/并发/余额不足）+ 兑换流程
