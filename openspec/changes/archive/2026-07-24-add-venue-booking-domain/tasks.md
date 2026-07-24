# Tasks: add-venue-booking-domain

## 1. 后端 — Schema 与错误码

- [x] 1.1 V22 一次性建 7 张表：`venue` / `court` / `venue_business_hour` / `venue_contact` / `court_pricing_band` / `booking` / `booking_slot`（含 `name_key` STORED 生成列 + `uk_venue_court_name` + `uk_court_slot`）
- [x] 1.2 `ErrorCode` 新增 venue/court/slot 业务码（P1）
- [x] 1.3 `ErrorCode` 新增 P2 专用 5 码（1012-1016）：`SLOT_ALREADY_TAKEN` / `BOOKING_WINDOW_EXCEEDED` / `CANCEL_DEADLINE_PASSED` / `USER_BOOKING_LIMIT_EXCEEDED` / `BOOKING_NOT_FOUND`

## 2. 后端 — 纯算法 / 枚举（P1）

- [x] 2.1 `PricingDayType` 枚举（`ALL` / `WEEKDAY` / `WEEKEND`）
- [x] 2.2 `SlotCalculator`（纯类，`@Component` 注入 Clock）：锚定 `open_time`、半开 `[t, t+slot_minutes)`、跨午夜守卫、单一 `now` 窗口、`matchBand` 半开 + specific-over-ALL 守卫
- [x] 2.3 `PricingBandValidator`（纯类）：半开重叠校验，缺口段不可订无价
- [x] 2.4 `SlotCalculator` 单测（覆盖：常规日 / 跨午夜 / 缺口段 / 多 band 嵌套 / specific-over-ALL / 当前时刻截断）
- [x] 2.5 `PricingBandValidator` 单测（覆盖：合法集 / 缺口 / 半开重叠 / 全覆盖）

## 3. 后端 — Mapper / Entity / DTO / VO

- [x] 3.1 6 个 P1 entity（Venue / Court / VenueBusinessHour / VenueContact / CourtPricingBand / BookingSlot）+ Booking（P2，append-only，无 `@TableLogic`）
- [x] 3.2 7 个 Mapper（含 `BookingMapper`）
- [x] 3.3 admin DTO 与 VO（Venue / Court 系列）
- [x] 3.4 P2 DTO 三件（BookingCreate / BookingQuery / ForceCancel）+ VOs 三件（Booking / BookingAdmin / BookingCreateResult）
- [x] 3.5 `BookingProperties`（`@ConfigurationProperties` + `@Component`）

## 4. 后端 — Service 层

- [x] 4.1 `VenueService` + `VenueServiceImpl`：CRUD + 营业时间 / 联系方式
- [x] 4.2 `CourtService` + `CourtServiceImpl`：CRUD + 定价带覆盖 / 复制
- [x] 4.3 `SlotService` + `SlotServiceImpl`：注入 Clock、组装数据 + 委派 `SlotCalculator` + `SlotServiceImplTest` 消除墙上时钟依赖
- [x] 4.4 `BookingService` 接口
- [x] 4.5 `BookingService` 实现：`create` / `cancel` / `forceCancel` / `complete` / `markNoShow` / `list`（写侧 4 个方法全部 CAS `UPDATE ... WHERE id=? AND status='CONFIRMED'`，`affectedRows==0` 抛 `INVALID_STATUS_TRANSITION`）
- [x] 4.6 `BookingService` 取消次序：CAS first → 失败不删 slot row → 否则按 `booking_id` 物理删 `booking_slot` 释放 `uk_court_slot`
- [x] 4.7 `BookingServiceImpl` 单测（happy + concurrent + cancel/release + forceCancel 完成/爽约 CAS）

## 5. 后端 — Controller 层

- [x] 5.1 `AdminVenueController` + `AdminCourtController`（admin CRUD）
- [x] 5.2 `AppVenueController` + `AppCourtController`（小程序浏览，含 `/api/app/courts/{id}/slots?date=...`）
- [x] 5.3 `AdminBookingController`（admin 列表 + 完成 / 爽约 / 强制取消带可选 reason）
- [x] 5.4 `AppBookingController`（下单 / 我的预约 / 取消）
- [x] 5.5 浏览端点补 `@PublicAnonymousAccess`（D9 约定）；AppAuthFilter bypass `/api/app/venues` `/api/app/courts`，但 **不** bypass `/api/app/bookings`
- [x] 5.6 admin/app controller 单测（RBAC + 200/403/404 + happy + 异常路径）
- [x] 5.7 浏览端到端 + 角色守卫集成测试

## 6. 后端 — Scheduler / 异常处理 / 审计

- [x] 6.1 `BookingStatusScheduler`（`@Component` + `@Scheduled fixedDelayString=${hey-pickler.booking.complete-cadence}` + `Clock` 可注入）：阈值 `now(clock).minusHours(grace)` 作 bind param 入 `LambdaUpdateWrapper<Booking>.lt(slotEnd, threshold).last("LIMIT N")`
- [x] 6.2 `GlobalExceptionHandler` 翻译 `DataIntegrityViolation` → `PARAM_ERROR`（P1 兜底）
- [x] 6.3 `GlobalExceptionHandler.handleDataIntegrityViolation` 内嵌 root-cause 鉴别：`uk_court_slot` 或 message 含 `slot_start` → `SLOT_ALREADY_TAKEN`(1012)；其他保留 PARAM_ERROR 兜底
- [x] 6.4 `OperationLogClassifier` 归类 venue / court / booking 模块（admin 端写操作自动入 `operation_log`）
- [x] 6.5 `BookingStatusScheduler` 单测（Clock 可控、batch 大小、已 CAS 完成的不再二次扫描）

## 7. 后端 — 配置与 Redis

- [x] 7.1 `application.yml` 加 `hey-pickler.booking.{cancel-deadline-hours:2, max-concurrent:5, complete-grace-hours:2, complete-cadence:PT5M, complete-batch-size:200, initial-delay-seconds:30}`
- [x] 7.2 `RedisKey` 新增 `bookingSeq` 常量（key = `booking:seq:{LocalDate}`，TTL=24h）

## 8. 前端 admin — 路由 / 列表 / 表单

- [x] 8.1 admin 路由 + 侧边栏「场馆管理」分组（`AppSidebar.GROUP_ORDER` 同步）
- [x] 8.2 admin 场馆列表页 + 场馆表单（基础信息 / 营业时间 / 联系方式 / 场地 / 定价带）
- [x] 8.3 admin 类型 + `api/venues.ts` 模块
- [x] 8.4 admin 场地定价带读取端点 + 编辑器加载既有 bands（闭合编辑功能）
- [x] 8.5 admin 预约管理（列表 / 筛选 / 完成 / 爽约 / 强制取消）
- [x] 8.6 `api/bookings.ts` 模块

## 9. 前端 wxapp — 列表 / 详情 / 下单

- [x] 9.1 wxapp 工具函数（`formatPrice` / `formatSlotTime`）+ 术语更新
- [x] 9.2 wxapp `court-card` 组件
- [x] 9.3 wxapp 场馆列表页 + 首页入口
- [x] 9.4 wxapp 场馆详情页（选日期 + 格子网格 + 价格）
- [x] 9.5 wxapp 一键弹窗下单 + 我的预约（upcoming / history）
- [x] 9.6 wxapp 端到端 E2E（happy / concurrent / cancel / release）

## 10. 端到端测试与回归

- [x] 10.1 `mvn test -Dtest='!*IntegrationTest'` 全绿（包含 `BookingServiceImplTest` / `SlotCalculatorTest` / `PricingBandValidatorTest` / `AdminBookingControllerTest` / `AppBookingControllerTest`）
- [x] 10.2 `npm run lint:check`（admin 前端）全绿
- [x] 10.3 wxapp 端到端用例（happy / concurrent / cancel / release）
- [x] 10.4 回归 V22 之前的 21 个 Flyway migration 全绿启动

## 11. 文档

- [x] 11.1 `CLAUDE.md` 增补场馆预约领域（P1+P2）章节
- [x] 11.2 `CLAUDE.md` "Venue Booking Lifecycle (Specs P1 + P2)" 写明 CAS 一票否决 / 取消次序 / root-cause 鉴别 / Scheduler 阈值
- [x] 11.3 `CLAUDE.md` "App Auth Filter Caveat" 增补 venues/courts 匿名 bypass + bookings 不 bypass

## 12. PR 与 archive

- [ ] 12.1 推 PR（`feat/venue-booking-p2` → `master`），title 引用本 change；CI 全绿后合入
- [ ] 12.2 `openspec archive add-venue-booking-domain`（proposal + tasks + specs → archive/，spec delta 合入 `openspec/specs/venue-booking/spec.md`）
- [ ] 12.3 打 tag `v4.0.0`（venue-booking 完整领域 PR 合并里程碑）