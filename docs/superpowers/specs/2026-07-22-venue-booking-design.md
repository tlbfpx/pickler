# 场馆与场地预约 设计

- **日期**: 2026-07-22
- **状态**: 蓝图（Draft，待评审）
- **范围**: 整体领域设计 + 分阶段交付路线图（P1 基础层 / P2 预约引擎），本文件不含实现
- **后续**: 本 spec 评审通过后，由 `writing-plans` 为「P1 · 场馆/场地/时段配置 + 浏览」出具实现计划

---

## 1. 背景与目标

Hey Pickler 目前是**赛事/活动管理**平台，核心数据流围绕 Event。`Event.location` 仅是 `VARCHAR(256)` 自由文本，全库无场馆 / 场地 / 预约 / 时段定价的任何实体。

本设计新增一个**并行的「场地预约」领域**：运营方维护几十个合作场馆，每个场馆有多块场地，每块场地有独立的时长粒度与时段分档价目；用户在 wxapp 自助选场地 + 时段下单预约，到场付费。

**目标**：
- 运营方在 admin 完整管理场馆 / 场地 / 营业时间 / 联系方式 / 时段价目。
- 用户在 wxapp 浏览场馆、查看某场地某日的可预订格子与价格、一键下单、自助取消。
- 预约引擎在 DB 层保证同一场地同时段不重号（并发安全）。
- v1 与赛事解耦，互不影响；不做在线支付（到场付费）。

## 2. 关键决策（已与产品负责人确认）

| 维度 | 决策 |
|---|---|
| 预约深度 | **用户自助在线预约**（wxapp 完整闭环：选时段→冲突检测→下单→取消/状态机） |
| 场馆规模 | **几十个合作场馆，运营方后台维护**（无场馆方自助入驻、无所有权模型） |
| 费用流转 | **预约不付款，到场付费**——无订单/支付/退款实体；Booking 记录价格快照 |
| 时段模型 | **固定时长格子（`slot_minutes`，默认 60）+ 时段分档定价**（早/午/晚 × 工作日/周末） |
| 时长粒度归属 | **挂 Court**（每块场地自带粒度，不同场地可不同） |
| 定价归属 | **挂 Court**（每块场地独立价目；同馆不同场地可不同价） |
| 营业时间 / 联系方式 | 挂 Venue（场馆级） |
| 冲突检测 | `booking_slot` 子表 + `UNIQUE(court_id, slot_start)`；取消**物理删除 slot 行**释放唯一键（复用 Team/V12 套路） |
| 预约状态机 | book → `CONFIRMED`（无支付即时确认）→ `CANCELLED` / `COMPLETED` / `NO_SHOW` |
| 与赛事关系 | v1 解耦，赛事不占场地库存（已知限制，见 §12） |
| 交付路径 | **一份 spec + 分阶段**（P1 基础层 / P2 预约引擎）；终态 schema 一次建全 |

## 3. 现状分析

- 平台核心为赛事/活动，`Event.location` 自由文本，无场馆实体。
- 双轨积分（STAR/PARTY）、段位、字典平台、品牌等已模块化；本领域与它们正交，不依赖。
- 软删约定：MyBatis-Plus `@TableLogic` on `deletedAt`（NULL = 未删）；`operation_log` 是 append-only 例外。
- 已有并发安全套路：Team 实体靠**物理行删除**释放 V12 唯一键（`uk_event_member1/2`）；V17 用函数唯一索引做 `point_record` 幂等。本设计复用前者。
- 已有 Redis + Lua 基础设施（限流），可复用于 `booking_no` 日计数器。
- `AppAuthFilter` 对 `GET /api/app/{events,banners,rankings}` 匿名放行——新场馆浏览需同样处理（CLAUDE.md 标注的坑）。
- DB 迁移头部：V21。本设计新增 **V22**。
- Admin 模块按组划分（运营管理 / 积分与排名 / 内容运营 / 系统），新增「场馆管理」组。
- 项目方向：**只做本机部署、不公网上线**——不投入支付通道、多租户、高可用等生产级设施。

## 4. 总体架构

新增并行的场地预约领域，包结构与现有约定一致（`com.heypickler` 下）：

```
entity/        Venue · Court · VenueBusinessHour · VenueContact · CourtPricingBand · Booking · BookingSlot
mapper/        一实体一 Mapper
dto/admin      Venue/Court/Contact/PricingBand 的 Request DTO；VenueQuery/CourtQuery/BookingQuery
dto/app        BookingCreateRequest（slotStart + slotsCount）
vo/            VenueVO · VenueDetailVO · CourtVO · SlotVO（含 available + price）· BookingVO
service/       VenueService · CourtService · SlotService（时段生成+定价+可用性）· BookingService（P2）
service/impl   实现
controller/admin  AdminVenueController · AdminCourtController · AdminBookingController（P2）
controller/app    AppVenueController（匿名浏览）· AppBookingController（P2，需 JWT）
common/enums/  VenueStatus · CourtStatus · DayType · BookingStatus
common/constant/ RedisKey（booking:seq:{yyyyMMdd}）
scheduler/     BookingStatusScheduler（P2 可选，自动 COMPLETED 过期预约）
```

**Slots 不落表**：`SlotService` 按 Court + 当日营业时间 + `court.slot_minutes` 实时生成；价格查该 court 的 `CourtPricingBand` 命中；可用性 = 生成结果 − `booking_slot` 已占用。纯计算、无冗余存储、无定时同步。

**复用现有基础设施**：
- `Result<T>` 统一响应；admin 写操作经 `OperationLogAspect` 自动审计；`@RequireRole` + `RoleCheckAspect` 鉴权。
- 软删用于 venue / court / contact / pricing_band；**booking / booking_slot 不软删**（前者 append-only，后者物理删除释放唯一键）。
- Redis 复用于 booking_no 日计数器（P2）。

## 5. 数据模型

一个 Flyway 迁移 **`V22__venue_court_booking.sql`**，终态 schema 一次建全。P1 用前 5 张表，P2 启用 booking 两张（建表在 P1 完成以避免后续迁移返工）。

### 5.1 `venue` 场馆
```
id BIGINT PK AI
name VARCHAR(128) NOT NULL
address VARCHAR(256) NOT NULL
latitude  DECIMAL(10,7) NULL          -- 可选,未来地图；v1 不建地图 UX
longitude DECIMAL(10,7) NULL
cover_url VARCHAR(512) NULL
description VARCHAR(1024) NULL
status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'   -- ACTIVE / INACTIVE
booking_lead_days INT NOT NULL DEFAULT 14      -- 可订窗口（天）
created_at · updated_at · deleted_at【软删】
```

### 5.2 `court` 场地
```
id BIGINT PK AI
venue_id BIGINT NOT NULL              -- INDEX
name VARCHAR(64) NOT NULL             -- "1号场"
court_type VARCHAR(16) NOT NULL DEFAULT 'INDOOR'   -- INDOOR / OUTDOOR
slot_minutes INT NOT NULL DEFAULT 60  -- 时长粒度（下沉到 court）
status VARCHAR(16) NOT NULL DEFAULT 'OPEN'         -- OPEN / CLOSED / MAINTENANCE
sort_order INT NOT NULL DEFAULT 0
created_at · updated_at · deleted_at【软删】
UNIQUE(venue_id, name)                -- 同馆场地名不重（软删重名见 §5.7）
```

### 5.3 `venue_business_hour` 营业时间（场馆级，每周 7 行）
```
id BIGINT PK AI
venue_id BIGINT NOT NULL
day_of_week TINYINT NOT NULL          -- 0=周日 .. 6=周六
open_time  TIME NULL                  -- NULL = 当日休
close_time TIME NULL
created_at · updated_at
UNIQUE(venue_id, day_of_week)         -- 每天 1 行，admin 整行覆盖
```

### 5.4 `venue_contact` 联系方式（多条）
```
id BIGINT PK AI
venue_id BIGINT NOT NULL
type  VARCHAR(16) NOT NULL            -- PHONE / WECHAT / LANDLINE / EMAIL
value VARCHAR(128) NOT NULL
label VARCHAR(64) NULL                -- "前台"
sort_order INT NOT NULL DEFAULT 0
created_at · updated_at · deleted_at【软删】
```

### 5.5 `court_pricing_band` 时段定价带（每 court 独立，二维价目）
```
id BIGINT PK AI
court_id  BIGINT NOT NULL
day_type  VARCHAR(8) NOT NULL         -- WEEKDAY / WEEKEND / ALL
start_time TIME NOT NULL
end_time   TIME NOT NULL
price DECIMAL(10,2) NOT NULL
created_at · updated_at · deleted_at【软删】
```
**校验（app 层，保存时强校验）**：同 `court_id` 同 `day_type` 的 band **禁止时间段重叠**；允许有缺口——缺口段的格子**不可订**（不是免费）。

### 5.6 P2：`booking` 预约主记录 + `booking_slot` 时段占用

`booking`（历史/状态，**append-only，无 deleted_at**）：
```
id BIGINT PK AI
booking_no VARCHAR(32) NOT NULL UNIQUE            -- "BK20260722-0001"，Redis 日计数器生成
user_id  BIGINT NOT NULL
venue_id BIGINT NOT NULL                          -- 冗余(从 court.venue_id)，便于按场馆查询
court_id BIGINT NOT NULL
slot_date DATE NOT NULL
slot_start DATETIME NOT NULL
slot_end   DATETIME NOT NULL
slots_count INT NOT NULL                          -- 连续格子数，可 >1
price_snapshot DECIMAL(10,2) NOT NULL             -- 下单时锁定的应付总额
status VARCHAR(16) NOT NULL                       -- CONFIRMED / CANCELLED / COMPLETED / NO_SHOW
cancel_reason VARCHAR(256) NULL
cancelled_at DATETIME NULL
created_at · updated_at
INDEX(user_id, slot_start) · INDEX(court_id, slot_start) · INDEX(venue_id, slot_date)
```

`booking_slot`（冲突 guard，1 预约 N 行）：
```
id BIGINT PK AI
booking_id BIGINT NOT NULL
court_id   BIGINT NOT NULL
slot_start DATETIME NOT NULL
UNIQUE(court_id, slot_start)                      -- 硬冲突防线
INDEX(booking_id)
```
**取消 = 物理删除该 booking 的所有 `booking_slot` 行**释放唯一键（复用 Team 实体套路）；`booking` 主记录保留 `status=CANCELLED` + `cancelled_at` + `cancel_reason`，审计完整。

### 5.7 软删 + UNIQUE 的老问题
`court` 的 `UNIQUE(venue_id, name)` 与软删冲突（删了又建同名）。按 V17 套路用函数唯一索引兜底，实现阶段处理，不阻塞设计。`venue_business_hour` 用整行覆盖（无软删），无此问题。

## 6. 预约引擎算法（SlotService 核心）

### 6.1 时段生成 + 定价 + 可用性
```
generateSlots(court, date) -> List<SlotVO>:
  bh = VenueBusinessHour(venue_id=court.venue_id, day_of_week=date.dayOfWeek)
  if bh == null or bh.open_time == null: return []          // 当日休
  dayType = isWeekend(date) ? WEEKEND : WEEKDAY
  bands  = CourtPricingBand(court_id=court.id) where day_type IN (dayType, ALL)
  occupied = set( slot_start where court_id=court.id
                  and DATE(slot_start)=date
                  and exists booking.status=CONFIRMED )     // 即 booking_slot 全集
  leadMin = 30; leadDays = venue.booking_lead_days
  for t = alignUp(bh.open_time, slot_minutes);
       t + slot_minutes <= bh.close_time;
       t += slot_minutes:
    slotStart = date + t
    if slotStart <= now + leadMin:        continue           // 过去/即将开始
    if slotStart >  today + leadDays:     break              // 超出可订窗口
    band = matchBand(bands, t)                              // t∈[start_time,end_time)
    available = (band != null) && (slotStart not in occupied)
    slots.push({ slotStart, slotEnd: slotStart+slot_minutes,
                 available, price: band?.price })
  return slots
```
`matchBand`：返回 `t` 落入的 band；无命中 → `null` → 该格子 `available=false` 且无价格。

### 6.2 多格子下单（P2）
用户提交 `slotStart` + `slotsCount`：
1. 生成连续 N 个格子，校验每个格子 `available && band!=null` 且在可订窗口内。
2. `price_snapshot` = Σ 各格子命中 band 的 price。
3. 同事务写 `booking`（CONFIRMED）+ N 行 `booking_slot`。
4. 任一 `booking_slot` 撞 `UNIQUE(court_id, slot_start)` → `DataIntegrityViolation` → 翻译为 `SLOT_ALREADY_TAKEN` 友好错误（并发下另一请求刚抢到）。

**并发原则**：可用性查询是 advisory，唯一键才是事实来源；不依赖事务内 SELECT 持锁。

## 7. 预约状态机与错误处理

### 7.1 状态机
```
                ┌──────────── POST /bookings ────────────┐
                ▼                                        │
            CONFIRMED ──┐                                │
              │     │   │                                │
   user<cutoff>/admin   admin                          (即时确认,无 PENDING/无支付态)
        ▼         ▼         ▼
   CANCELLED  COMPLETED  NO_SHOW        （三者皆为终态,不可再流转）
```
- 下单即 `CONFIRMED`（无支付 gate）。
- 用户取消截止：`slot_start − 2h`；过截止仅 admin 可强制取消。
- `COMPLETED` / `NO_SHOW` 由 admin 在 `slot_end` 后标记（P2 可选 `BookingStatusScheduler` 自动把过期 CONFIRMED 转 COMPLETED）。

### 7.2 错误码（新增 ErrorCode）
| code | 触发 |
|---|---|
| `VENUE_NOT_FOUND` / `COURT_NOT_FOUND` | 资源不存在或已软删 |
| `COURT_NOT_AVAILABLE` | court.status ≠ OPEN |
| `SLOT_NOT_BOOKABLE` | 无定价 band / 超出营业时间 / 超出可订窗口 |
| `SLOT_ALREADY_TAKEN` | UNIQUE 冲突（并发抢号失败） |
| `BOOKING_WINDOW_EXCEEDED` | 早于 now+30min 或晚于 today+leadDays |
| `CANCEL_DEADLINE_PASSED` | 用户过了取消截止且非 admin |
| `USER_BOOKING_LIMIT_EXCEEDED` | 该用户并发有效预约 > 上限（默认 5） |
| `INVALID_STATUS_TRANSITION` | 终态再流转 / 非法操作 |

## 8. 接口契约

### 8.1 Admin（`/api/admin/**`，`ADMIN+` 角色，写操作自动审计）
```
GET    /venues                           分页+搜索(name/address/status)
POST   /venues                           新建
GET    /venues/{id}                      详情(含 businessHours + contacts)
PUT    /venues/{id}                      基础信息更新
DELETE /venues/{id}                      软删
PUT    /venues/{id}/business-hours       整体覆盖 7 行营业时间
GET    /venues/{id}/contacts · POST · PUT · DELETE    联系方式 CRUD

GET    /courts?venueId=                  场地列表
POST   /courts · PUT /courts/{id} · DELETE /courts/{id}
PUT    /courts/{id}/pricing-bands        覆盖该 court 定价带(带重叠校验)
POST   /courts/{id}/pricing-bands/copy?from={courtId}   复制价目(批量降配置成本)

# P2
GET    /bookings                         列表(筛选 venue/court/date/status)
POST   /bookings/{id}/complete           标记完成
POST   /bookings/{id}/no-show            标记爽约
POST   /bookings/{id}/cancel             强制取消(忽略截止)
```

### 8.2 App（`/api/app/**`）
```
GET    /venues                           匿名,列表+搜索
GET    /venues/{id}                      匿名,详情(含 courts 概要)
GET    /courts?venueId=                  匿名,场地列表
GET    /courts/{id}/slots?date=          匿名,核心:某日可订格子+价格  ← SlotService.generateSlots

# P2(需 JWT)
POST   /bookings                         下单 {courtId, slotStart, slotsCount}
GET    /bookings/my                      我的预约(含历史)
POST   /bookings/{id}/cancel             自助取消(受截止约束)
```

### 8.3 AppAuthFilter 变更
把 `GET /api/app/venues/**` 与 `GET /api/app/courts/**/slots` 加入匿名 bypass（同 events 的匿名浏览模式）。预约写端点保留 JWT 强制。**不新增用户态 GET 到这些前缀下**，避免破坏现有 bypass 语义（CLAUDE.md 坑）。

### 8.4 前端
- **Admin**：新增侧边栏组「场馆管理」→ 场馆（列表 + 表单，表单内 Tab：基础信息 / 营业时间 / 联系方式 / 场地）；P2 增「预约管理」。新增 `api/venues.ts`、`api/bookings.ts`。
- **wxapp**：新增页面 `venue-list`（首页入口）→ `venue-detail`（场地列表 + 选日期 + 格子网格）→ `booking-confirm`；P2 增 `my-bookings`。

## 9. 分阶段交付

| 阶段 | 后端 | 前端 | 验收 |
|---|---|---|---|
| **P1 基础层** | venue/court/hours/contact/pricing_band 实体 + Mapper + Service + admin CRUD + SlotService（生成/定价/可用性，只读）+ app 浏览端点 + V22 全量建表 | admin 场馆/场地/营业时间/联系方式/定价配置（含复制价目）；wxapp 场馆浏览 + 格子价格展示 | admin 能配出一个可预订场馆；wxapp 能看到某场地某日的格子与价格 |
| **P2 预约引擎** | BookingService（下单/取消/状态机/并发）+ booking_no Redis 计数器 + app 预约端点 + admin 预约管理 + 可选 BookingStatusScheduler + 用户并发上限 | wxapp 下单/取消/我的预约；admin 预约管理（标记完成/爽约/强制取消） | 端到端预约闭环；并发抢号仅一单成功 |

**writing-plans 首次只对准 P1。** P2 待 P1 验收后另行规划。

## 10. 测试策略

- **SlotService（纯逻辑，重点）**：当日休、部分 band 缺口（缺口段不可订）、周末 vs 工作日定价、过去格子跳过、可订窗口边界、occupied 过滤、多格子连续可用性。
- **定价带校验**：保存时拒绝重叠；缺口语义。
- **BookingService（P2）**：下单 happy path、并发冲突（DB UNIQUE）、取消截止前/后、状态流转非法拒绝、用户上限。
- **Controller 集成**：匿名浏览放行、预约写端点 JWT 强制、admin 角色守卫、OperationLogAspect 自动审计断言。
- **并发用例**：两线程抢同一 `(court_id, slot_start)` → 恰好一单一成功，另一单 `SLOT_ALREADY_TAKEN`。
- **覆盖率**：遵循项目 jacoco 门禁（80% instruction / 60% line）。

## 11. 与现有系统的集成要点

- **迁移**：V22 一次性建全 7 张表；MyBatis-Plus `@TableLogic` 用于 5 张配置表，booking/booking_slot 不软删。
- **鉴权**：`@RequireRole`（ADMIN+）于 admin venue/court 控制器；app 预约写端点靠 `AppAuthFilter` 绑定 JWT userId。
- **审计**：admin 写操作经 `OperationLogAspect` 自动落 `operation_log`（URL 经 `OperationLogClassifier` 归类为 venue/court/booking 模块）。
- **Redis**：复用现有 Redis 基础设施做 `booking:seq:{yyyyMMdd}` 日计数器（P2）。
- **错误**：统一走 `BizException` + `ErrorCode` + `Result<T>`。

## 12. 已知限制与未来工作

- **v1 与赛事解耦**：赛事不占场地库存。若某场馆某场地同时在办赛事，用户理论上仍能预约该时段。本机部署体量下可接受；未来需打通时，让赛事在选定场地时段写 `booking_slot`（source=EVENT）即可复用同一冲突防线。
- **节假日/特殊日期营业时间**：v1 不做（运营改 business hour 手动应付）。未来按需加 `venue_special_date` 表。
- **地图 / 经纬度 UX**：`lat/lng` 列保留 nullable，v1 不建地图页面。
- **定价为纯 court 级**：不做"场馆默认 + 场地覆盖"二级继承；用 admin「复制价目」降配置成本。若未来多数场地同价、少数覆盖，再评估两层模型。
- **无在线支付**：到场付费。未来接微信支付时引入 order/payment 实体与状态扩展。
