# Proposal: 场馆预约领域（P1 配置浏览 + P2 预约引擎）

> **Status: ACTIVE** — 代码与测试已在 `feat/venue-booking-p2` 分支完成（73 个 commit、+9738 行），本 change 用于把领域契约回写到 OpenSpec，补齐 archive 漏掉的归档步骤。

## Why

Hey Pickler 长期只做"赛事 + 排名 + 战队"三件事，**场地租赁完全在 Excel 微信群手记**，场地方投诉「黑名单/爽约/收款对不上」、用户投诉「不知道几点有空」。本 change 一次性交付 **P1（场地配置 + 浏览）+ P2（预约引擎 + 后台管理 + 小程序下单）** 两个阶段，统一 venue / court / booking 三个域：

- **运营侧**：admin 配置场馆、营业时间、联系方式、场地定价带；维护联系方式；做营销/审计
- **用户侧**：小程序按日浏览场地可用时段，按价目一键下单预约；查「我的预约」
- **后台侧**：admin 查看预约、强制完成/爽约/强制取消（带原因）

**为什么一次走完两阶段**：P2 强依赖 P1 的 `SlotCalculator` / `PricingBandValidator` / `BookingSlot` 占位模型；分两次 PR 反而增加回归风险。本 PR 在 master 视为单一 feature。

## What Changes

### 后端（hey-pickler-server）

#### P1 — 配置 + 浏览（only read）

- **V22 migration** 一次性建 7 张表：`venue` / `court` / `venue_business_hour` / `venue_contact` / `court_pricing_band` / `booking` / `booking_slot`
- **6 Mapper + 6 Entity**（Venue / Court / VenueBusinessHour / VenueContact / CourtPricingBand / BookingSlot — `Booking` 单独建）
- **`SlotCalculator`（`common/util/`，纯算法）**：锚定 `open_time`、半开 `[t, t+slot_minutes)`、跨午夜守卫、单一 `now` 窗口、`matchBand` 半开 + specific-over-ALL 守卫；`@Component` 注入（让测试 Clock 可控）
- **`PricingBandValidator`（`common/util/`，纯逻辑）**：按"工作日有效集=WEEKDAY∪ALL / 周末=WEEKEND∪ALL"合并半开重叠校验，缺口段格子不可订无价
- **`PricingDayType` 枚举**（`common/enums/`）：`ALL` / `WEEKDAY` / `WEEKEND`
- **VenueService / CourtService**：CRUD + 营业时间 / 联系方式 / 定价带覆盖写入
- **`AdminVenueController` / `AdminCourtController`**：admin 端 CRUD（含定价带编辑回显、闭合编辑）
- **`AppVenueController` / `AppCourtController`**：小程序浏览端点（含 `/api/app/courts/{id}/slots?date=...` 实时格子计算）
- **`court.name_key` STORED 生成列** + 列唯一键 `uk_venue_court_name(venue_id, name_key)` —— 软删后重名复用
- **`AppAuthFilter`** 放行 `/api/app/venues` 与 `/api/app/courts` 匿名浏览（D9 约定）
- **`OperationLogClassifier`** 归类 `venue/court/booking` 模块
- **`GlobalExceptionHandler`** 翻译 `DataIntegrityViolation` → `PARAM_ERROR`（P1 兜底）
- **ErrorCode 新增** venue/court/slot 业务码

#### P2 — 预约引擎

- **`Booking` 实体**（append-only，**无 `@TableLogic`**）
- **`BookingMapper`** + `BookingProperties`（`@ConfigurationProperties` + `@Component`）
- **`BookingStatus` 字符串**：`CONFIRMED` / `CANCELLED` / `COMPLETED` / `NO_SHOW`（无 PENDING，无 Java enum，与 `StatusTransitionValidator` 弱耦合）
- **`booking_no`** = `BK{yyyyMMdd}-{4位序号}`，Redis key `booking:seq:{LocalDate}` 本地日序列
- **`BookingService` 接口 + 实现**：
  - `create` —— 价格快照、并发占用 CAS、并发用户上限校验
  - `cancelMine` / `forceCancel` / `complete` / `markNoShow` —— **4 个写终态方法必须 CAS** `UPDATE … WHERE id=? AND status='CONFIRMED'`；`affectedRows==0` 抛 `INVALID_STATUS_TRANSITION`；`forceCancel/complete/noShow` 先 `selectById` 区分 `BOOKING_NOT_FOUND` 与 CAS 输
  - `list`（admin 列表 + 我的预约 upcoming/history）
- **`BookingSlot` 释放唯一键**：取消 = CAS first → 仅当 rows=1 → 物理删除 `booking_slot` 行（释放 `UNIQUE(court_id, slot_start)`），避免误删 COMPLETED 历史
- **`GlobalExceptionHandler.handleDataIntegrityViolation` 内嵌 root-cause 鉴别**：`uk_court_slot` 或 message 含 `slot_start` → `SLOT_ALREADY_TAKEN`(1012)；其他 `DataIntegrityViolation` 保留 P1 的 `PARAM_ERROR` 兜底
- **`BookingStatusScheduler`**（`@Component` + `@Scheduled fixedDelayString=${hey-pickler.booking.complete-cadence}` + `Clock` 可注入）：阈值 `now(clock).minusHours(grace)` 作 bind param 入 `LambdaUpdateWrapper<Booking>.lt(slotEnd, threshold).last("LIMIT N")`；本批完成/可能还有 三态日志
- **`AdminBookingController`**：列表 + 三项手动（完成/爽约/强制取消带可选 reason）
- **`AppBookingController`**：下单/我的预约/取消
- **ErrorCode 新增** 5 个 P2 专用码（1012-1016）：`SLOT_ALREADY_TAKEN` / `BOOKING_WINDOW_EXCEEDED` / `CANCEL_DEADLINE_PASSED` / `USER_BOOKING_LIMIT_EXCEEDED` / `BOOKING_NOT_FOUND`
- **配置**：`hey-pickler.booking.{cancel-deadline-hours:2, max-concurrent:5, complete-grace-hours:2, complete-cadence:PT5M, complete-batch-size:200, initial-delay-seconds:30}`
- **AppAuthFilter bypass 不含 bookings**（全部 JWT 强制）；wxapp 端加 `@RequireAppUser` 满足 D9 约定

### 前端（hey-pickler-admin）

- **路由 + 侧边栏「场馆管理」分组**（在 `AppSidebar.GROUP_ORDER` 同步加入）
- **`/venues` 列表 + 表单**：基础信息 / 营业时间 / 联系方式 / 场地 / 定价带
- **`/bookings` 列表 + 筛选 + 三项手动**（完成 / 爽约 / 强制取消带可选 reason）
- **`api/venues.ts` + `api/bookings.ts`**：所有 admin API 收口
- **类型集中**（`types/index.ts` 末尾）：Venue / Court / Booking / BookingAdmin 等

### 前端（hey-pickler-wxapp）

- **`venue-list` 列表页 + 首页入口**
- **`venue-detail` 详情页**：选场地 + 选日期 → GET slots（`available` + `price` 由 `SlotCalculator` + `BookingSlot` 占用集算得）
- **`my-bookings`**：upcoming / history 两个 tab
- **一键下单弹窗**：选连续格子 → 提交
- **`court-card` 组件 + `utils/formatPrice` + `utils/formatSlotTime` + 术语更新**

## Capabilities

### New Capabilities

- `venue-booking`：场馆与场地的配置（admin CRUD + 营业时间 + 定价带）、用户浏览场地格子（按日）、用户预约引擎（下单/取消/我的）、admin 预约管理（列表 + 三项手动）、自动 complete scheduler、CAS 一票否决并发契约、BookingSlot 唯一键释放、根因错误鉴别。

### Modified Capabilities

无（本 change 不修改任何既有 spec 的 REQUIREMENTS，只新增 venue-booking 一个能力；infrastructure / auth / audit 不改契约）。

## Impact

- **Affected code（后端）**：
  - 新增：`entity/{Venue,Court,VenueBusinessHour,VenueContact,CourtPricingBand,Booking,BookingSlot}.java`（7 个）
  - 新增：`mapper/*`（7 个）
  - 新增：`service/{VenueService,CourtService,BookingService}.java` + `service/impl/...Impl.java`（6 个）
  - 新增：`controller/{admin/AdminVenueController, admin/AdminCourtController, admin/AdminBookingController, app/AppVenueController, app/AppCourtController, app/AppBookingController}.java`（6 个）
  - 新增：`common/util/{SlotCalculator,PricingBandValidator}.java`
  - 新增：`common/enums/PricingDayType.java`
  - 新增：`config/BookingProperties.java`
  - 新增：`scheduler/BookingStatusScheduler.java`
  - 新增：`dto/{VenueDTO,CourtDTO,BookingCreateDTO,BookingQueryDTO,ForceCancelDTO}.java`
  - 新增：`vo/{VenueVO,VenueDetailVO,CourtVO,SlotVO,BookingVO,BookingAdminVO,BookingCreateResultVO}.java`
  - 新增：`common/redis/RedisKey.java` 新增 `bookingSeq` 常量
  - 改：`common/exception/ErrorCode.java`（+8 码）
  - 改：`common/exception/GlobalExceptionHandler.java`（root-cause 鉴别）
  - 改：`filter/AppAuthFilter.java`（bypass venues/courts 不含 bookings）
  - 改：`common/util/OperationLogClassifier.java`（+venue/court/booking 模块）
  - 改：`common/util/HeadBasedImageUrlValidator.java` 不相关
  - 改：`application.yml`（+`hey-pickler.booking.*` 默认配置）
  - 改：`src/main/resources/db/migration/`（V22__*.sql）
- **Affected code（admin 前端）**：
  - 新增：`src/views/venues/`、`src/views/bookings/`
  - 新增：`src/api/venues.ts`、`src/api/bookings.ts`
  - 改：`src/router/index.ts`（+2 路由）
  - 改：`src/components/layout/AppSidebar.vue`（GROUP_ORDER 加新分组）
  - 改：`src/types/index.ts`（末尾加 Venue/Court/Booking 类型）
- **Affected code（wxapp 前端）**：
  - 新增：`pages/venue-list/`、`pages/venue-detail/`、`pages/my-bookings/`
  - 新增：`components/court-card/`
  - 改：`pages/index/index.*`（首页入口）
  - 改：`utils/request.js`、`utils/util.js`、`utils/util.wxs`、`utils/terms.js`
- **Affected API**：
  - 新增：`/api/admin/venues/**`、`/api/admin/courts/**`、`/api/admin/bookings/**`
  - 新增：`/api/app/venues/**`、`/api/app/courts/**`、`/api/app/bookings/**`
  - 无破坏：既有 endpoint 全部不变
- **Operational**：
  - 数据库：V22 一次性建 7 张表（无破坏，无回退脚本）
  - Redis：新增 `booking:seq:{LocalDate}` key（每日 4 位本地序号，TTL=24h）
  - Scheduler：新增 `BookingStatusScheduler` 自动 COMPLETED（fixedDelay=PT5M，initialDelay=30s）

## Non-goals

- **不做 P3**：在线支付 / 赛事打通 / 节假日营业时间 / 地图 / admin 代下单（CLAUDE.md 明确 wontfix）
- **不做场地评分 / 评论**：单纯场地预约，无社交层
- **不做会员价 / 折扣**：定价带只有 ALL/WEEKDAY/WEEKEND 三种日类型，不做人群分层
- **不做取消原因的多语言**：单中文，文案固定
- **不做 CalendarView 全月视图**：仅按日浏览，admin 排期另议
- **不做场馆级别通知（推送/短信）**：依赖 V13+ notification 平台扩展，本 change 不碰
- **不做 wxapp 端 admin 代下单入口**：admin 后端 API 已在，wxapp 端不暴露
- **不修改赛事/战队/分组/排位任何 spec**

## Open questions

（这些已经在代码评审中闭合，无遗留 design 争议）

1. ~~Booking 状态要不要 Java enum~~ → 与 `StatusTransitionValidator` 等弱耦合保持一致，用 String
2. ~~booking_no 用 UUID 还是可读号~~ → 可读号 `BK{yyyyMMdd}-{4位序号}`，Redis 日序列
3. ~~BookingSlot 用逻辑删还是物理删~~ → 物理删（释放唯一键，与 Team/V12 `uk_event_member` 一致）
4. ~~CAS 失败用 409 还是 1006~~ → 用既有 `INVALID_STATUS_TRANSITION` 1006 不新增码
5. ~~Scheduler 万一批量 200 条全失败怎么办~~ → 本批完成/可能还有 两态日志，下批重试；monitor 配 threshold 告警