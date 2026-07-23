# 场馆与场地预约 · P2 设计（预约引擎）

- **日期**: 2026-07-23
- **状态**: 蓝图（Draft，待评审）
- **前置**: P1 已落地（`feat/venue-booking-p1` 已合并）。本 spec 仅描述 P2 delta —— 与 P1 重复的合同在 §6「P1 已固化的合同」中**以引用形式**列出，避免重复维护。
- **P1 spec**: `docs/superpowers/specs/2026-07-22-venue-booking-design.md`
- **后续**: spec 评审通过后，由 `writing-plans` 出 P2 实现计划

---

## 1. 背景与目标

P1 只交付了配置 + 浏览（P1 spec §9 已固化）。场地领域对用户来说**还不能下单**，admin 不能**事后清场**。P2 启用 P1 已建好但未激活的 `booking` / `booking_slot` 表（V22 终态已建），加上：

- wxapp **自助下单 / 取消 / 我的预约**
- admin **预约列表 + 完成 / 爽约 / 强制取消**
- 一个轻量 **BookingStatusScheduler** 自动把过期未结的预约收尾

**不变**：

- 无在线支付（到场付费，照旧）
- 无赛事打通（赛事仍不占场地库存，照旧）
- 无节假日营业时间 / 无地图 / 无多租户（仍 YAGNI）

---

## 2. 关键决策

| 维度 | 决策 |
|---|---|
| wxapp 下单交互 | **格子点选 → 一键确认弹窗**（场地/日期/起-止/价格快照）→ 提交即下单 |
| admin 预约管理 | **只读列表 + 三项手动动作**（complete / no-show / force-cancel） |
| `COMPLETED` 来源 | **scheduler 自动**：`slot_end < now − 2h` 且 `status=CONFIRMED` → `COMPLETED` |
| `NO_SHOW` 来源 | **仅 admin 手动**（避免 scheduler 误判） |
| Scheduler 节奏 | 每 **5 分钟**探一次（`@Scheduled(fixedDelayString = "PT5M")`）|
| `SLOT_ALREADY_TAKEN` | DB 唯一冲突 `UNIQUE(court_id, slot_start)` 是事实来源；`DataIntegrityViolation` → 友好错误 |
| `CANCEL_DEADLINE_HOURS` | **2** 小时，`hey-pickler.booking.cancel-deadline-hours` 可配（spec §7.1）|
| 用户并发上限 | **5** 单/用户，`hey-pickler.booking.max-concurrent` 可配（spec §7.2）|
| `booking_no` 生成 | Redis 日计数器 `booking:seq:{yyyyMMdd}`（服务端**本地**日期，spec §11）|
| Admin force-cancel | 忽略 `cancel-deadline-hours`，可对**任意非终态**预约强制取消 |

---

## 3. 与 P1 的边界（避免混淆）

- **schema**：V22 已建 `booking` / `booking_slot`，P2 不动表结构。
- **SlotCalculator / PricingBandValidator / SlotService**：P1 的 `getCourtSlots` 是**只读** browse；P2 新增 `BookingService` 写入，不复用 `SlotService`。
- **`booking_no` 不进 P1 的"已知限制"** —— P2 接起。

---

## 4. 总体架构

```
BookingController
   │
   ├─ POST   POST /api/app/bookings            (jwt → userId)
   ├─ POST   POST /api/app/bookings/{id}/cancel (cutoff 检查)
   ├─ GET    GET  /api/app/bookings/my         (按 userId)
   │
   ├─ GET    GET  /api/admin/bookings          (列表 + 筛选 + 分页)
   ├─ POST   POST /api/admin/bookings/{id}/complete
   ├─ POST   POST /api/admin/bookings/{id}/no-show
   └─ POST   POST /api/admin/bookings/{id}/cancel   (force, 忽略 cutoff)

BookingService
   ├─ create(req, userId) → booking + N booking_slot    (@Transactional, 乐观)
   ├─ cancel(id, byAdmin=false)                        (cutoff 检查 + 物理删 booking_slot 行)
   ├─ complete(id) / markNoShow(id) / adminForceCancel(id)
   └─ listMine(userId, statusGroup) / list(filters, page, size)

BookingStatusScheduler (@Scheduled fixedDelay = PT5M)
   └─ 把 slot_end < now − grace 且 CONFIRMED 的改为 COMPLETED

配置: hey-pickler.booking.{cancel-deadline-hours, max-concurrent, complete-grace-hours}
Redis: booking:seq:{localDate} (PV + INCR)
```

### 4.1 与 P1 已建对象的接口
- **依赖**：复用 P1 的 `Court`、`CourtPricingBand`、`VenueBusinessHour` mapper（只读，校验"格子能不能订"）。**不**直接复用 `SlotService`（语义不同）。
- **不依赖**：BookingStatusAspect（admin 写仍走原 `OperationLogAspect`，classifier 已扩展 `bookings→BOOKING/Booking`，P1 已落）。

---

## 5. 数据模型

V22 已建表，**P2 零迁移**。两表要点回顾（与 P1 spec §5.6 一致，实施严格照办）：

**`booking`**（append-only，无 `deleted_at`，成对写 + 历史完整）
- `booking_no` UNIQUE —— Redis 日计数器
- `user_id`/`venue_id`/`court_id`/`slot_date`/`slot_start`/`slot_end`/`slots_count`
- `price_snapshot`（下单时锁定）
- `status` ∈ {`CONFIRMED`, `CANCELLED`, `COMPLETED`, `NO_SHOW`}
- `cancel_reason`/`cancelled_at`（admin force 与用户取消共用）

**`booking_slot`**（冲突 guard，**取消即物理删除**释放唯一键 — 复用 P1 §5.6 + 项目 Team/V12 套路）
- `booking_id`/`court_id`/`slot_start`
- `UNIQUE(court_id, slot_start)` 是 P2 唯一的并发事实来源

---

## 6. P1 已固化的合同（在本 spec 里以引用形式列出，实施不再讨论）

- **§6.1 SlotCalculator 算法**：锚定 open_time / 半开 / matchBand 等 —— `create(courtId, slotStart, slotsCount)` 在「逐格独立校验 + 整单拒绝」时复用其等价流程（见 §8.1）。
- **§6.2 多格子跨 band 行为**：每格独立定价，整单拒绝若任一无 band / 被占 —— 同样在 §8.1 复用。
- **§7 状态机（不变）**：book→CONFIRMED（无支付即时确认）→ CANCELLED / COMPLETED / NO_SHOW。
- **§7.2 错误码**：增 5 个预约专用码（见 §9）。
- **§8.1 / §8.2 接口契约**：保持 P1 列出，新增 §4 列出的 8 条 P2 端点。
- **§11 集成**：redis 类型用 `StringRedisTemplate`（`booking:seq` 是纯 String）；D9 / GlobalExceptionHandler（已落地）继续生效。

---

## 7. 预约引擎算法（P2 独有）

### 7.1 `BookingService.create`

输入 `{courtId, slotStart, slotsCount}`，目标：原子写 1 个 `booking` + N 个 `booking_slot`。

1. 读 `court`、`venue`（含 `bookingLeadDays`）、`venue_businessHour`、`courtPricingBand`（effBands）、`bookingSlot` 已占用集 —— 与 P1 `SlotService.getCourtSlots` 同一组数据的**写**视角读取。
2. **逐格独立校验**（与 P1 §6.2 写法一致）：
   - 所有格子 `slot_date = date(slotStart)`，同一日期；
   - 每格 `>= now + leadMin` 且 `< now + leadDays·24h`；
   - 每格落在营业时段；
   - 每格 `slot_start` 命中一个 `band`（按 `day_type` 有效集） —— **无 band 即整单拒绝**；
   - 每格 `slot_start` 未被 `booking_slot` 占用 —— 任一被占即整单拒绝。
3. 取每个 band 的 `price`，累加 → `price_snapshot`。
4. 拼 `booking_no = "BK" + yyyyMMdd + "-" + padded(redis.INCR("booking:seq:" + LocalDate.now()))`（长度对齐到 4 位）。
5. 同事务：
   - `bookingMapper.insert(booking)` → 取 id；
   - N 次 `bookingSlotMapper.insert(bookingSlot)`。
6. 任一 `booking_slot.insert` 撞唯一键 → `DataIntegrityViolationException` → 翻译为 `SLOT_ALREADY_TAKEN`。
7. 返回 `booking_no`。

校验阈值的**单一参考点 = now**（spec §6.1 §7.1 修复过的歧义）。**Cross-band + 跨天**边界由 SLOT_NOT_BOOKABLE 拒，**跨午夜** SlotCalculator 那侧的守卫在 P1，P2 复用同一计算路径所以同语义。

### 7.2 `cancel(id, byAdmin)` / `adminForceCancel(id)`

- 非 byAdmin：检查 `now < slot_start − cancel-deadline-hours` —— 否则 `CANCEL_DEADLINE_PASSED`。
- byAdmin: 跳过截止检查。
- `status = CONFIRMED` 才允许取消；其他状态 → `INVALID_STATUS_TRANSITION`。
- 写入 `status=CANCELLED`、`cancelled_at=now`、`cancel_reason=(bookerType:reason)`。
- **删除全部 `booking_slot` 行**释放 `UNIQUE(court_id, slot_start)`。
- 同事务。

### 7.3 `complete(id)` / `markNoShow(id)`

- 仅允许 `CONFIRMED` → 终态。
- `slot_end > now` 时由 admin 标记 `NO_SHOW` 仍允许（极少数情况：admin 现场决定"这人没来")。
- `COMPLETED` 由 scheduler 自动处理（见 §10），admin 也能手动覆盖（`complete` 校验 `status == CONFIRMED`，如果已自动 COMPLETED 则 `INVALID_STATUS_TRANSITION`，admin 先 no-show 再 complete 也是 `INVALID_STATUS_TRANSITION` —— 见 §9 边界）。

### 7.4 我的预约「即将开始 / 历史」分组（wxapp 端约定，服务端不存视图态）

- 即将开始：`status=CONFIRMED` 且 `slot_start >= now`。
- 历史：其他。
- 服务端提供两个查询入口或一个带分组参数：
   - 选一：`GET /api/app/bookings/my?group=upcoming|history`，**前端按 group 切片**，P2 不存视图态。

---

## 8. 接口契约

### 8.1 Admin（`/api/admin/bookings/**`，写 SUPER_ADMIN/ADMIN，读 +OPERATOR）

```
GET    /bookings
  query: venueId? courtId? dateFrom? dateTo? status? keyword? page? size?
  → Result<PageResult<BookingAdminVO>>   (联 venue/court/user 名称+电话)
GET    /bookings/{id}?                  → Result<BookingAdminVO> (含 listSlots)
POST   /bookings/{id}/complete          → Result<Void>
POST   /bookings/{id}/no-show           → Result<Void>
POST   /bookings/{id}/cancel            body: { reason? } → Result<Void>   (force, 忽略 cutoff)
```

### 8.2 App（`/api/app/bookings/**`，需 JWT；`@RequireAppUser`）

```
POST   /bookings         body: { courtId, slotStart, slotsCount } → Result<BookingVO>
GET    /bookings/my?group=upcoming|history&page&size        → Result<PageResult<BookingVO>>
POST   /bookings/{id}/cancel   (受 cutoff 约束；超截止返 CANCEL_DEADLINE_PASSED)
```

### 8.3 现有 AppAuthFilter 变更
- `bookings` **不**加进 `PUBLIC_GET_PREFIXES`（写端必须 JWT，读端也是）。
- 读取 `/api/app/bookings/my` —— 没有"匿名 GET 落到某前缀下"的冲突，D9 注解加 `@RequireAppUser` 即可。

---

## 9. 错误码

| 错误 | 触发 |
|---|---|
| `SLOT_ALREADY_TAKEN` (1012) | `UNIQUE(court_id, slot_start)` 冲突；并发抢号失败 |
| `BOOKING_WINDOW_EXCEEDED` (1013) | 写入时 `slot_start` 不在 `now+30min .. now+leadDays·24h` |
| `SLOT_NOT_BOOKABLE` (1011, P1) | 格子无 band / 营业时间外 / 多格中任一无 band |
| `CANCEL_DEADLINE_PASSED` (1014) | 用户过了 `cancel-deadline-hours` 且非 admin |
| `INVALID_STATUS_TRANSITION` (1006, P1) | 终态再操作 / 跨非法路径 |
| `USER_BOOKING_LIMIT_EXCEEDED` (1015) | 用户未来时段 CONFIRMED 数 ≥ `max-concurrent` |
| `BOOKING_NOT_FOUND` (1016) | id 不存在或硬删 |

`GlobalExceptionHandler` 已有 `DataIntegrityViolation` → `PARAM_ERROR`，需**新增专属 `SQLIntegrityConstraintViolationException`（嵌套 root）→ `SLOT_ALREADY_TAKEN`** 这一条。简单 guard：在 handler 判断 root cause 是不是 `UNIQUE(court_id, slot_start)` 字段名匹配（id + message 含 `slot_start`），匹配即 `SLOT_ALREADY_TAKEN`；否则保留原 PARAM_ERROR 兜底。

---

## 10. BookingStatusScheduler

- `@Scheduled(fixedDelayString = "${hey-pickler.booking.complete-cadence:PT5M}")`（首次延迟 30s，避免启动雷击）。
- 实现：批量（in 一次 SELECT … FOR UPDATE? **不** —— MySQL InnoDB 下 SELECT...FOR UPDATE 在分页和锁粒度上得不偿失，**靠乐观 UPDATE + affected rows 判定并发**）：
  ```sql
  UPDATE booking
  SET status='COMPLETED', updated_at=NOW()
  WHERE status='CONFIRMED'
    AND slot_end < (NOW() - INTERVAL :graceHours HOUR)
  LIMIT 200;
  ```
- 一次扫描 ≤ 200（避免长事务），下一轮再扫剩余。scheduler 自身的并发：`@Scheduled` 单线程默认。
- **不是写侧** → 不参与并发抢号；即使两个 scheduler 实例同时跑（HA 下），最差重复写一次同状态（CONFIRMED→COMPLETED 是幂等的）。

---

## 11. 配置 (`application.yml` `hey-pickler:` 块)

```yaml
hey-pickler:
  booking:
    cancel-deadline-hours: 2
    max-concurrent: 5
    complete-grace-hours: 2
    complete-cadence: PT5M
    complete-batch-size: 200
    initial-delay-seconds: 30    # scheduler 启动后等多少秒
```

`BookingProperties.java` POJO + `@ConfigurationProperties(prefix="hey-pickler.booking")`，与 P1 `TierProperties`(已删) / `PlacementProperties` 同模式。

---

## 12. Redis 键

`common/constant/RedisKey.java` 新增：

```
public static String bookingSeq(String date) {
    return PREFIX + "booking:seq:" + date;   // date = LocalDate.now().toString() (本地)
}
```

`booking_no` 组成：`"BK" + yyyyMMdd + "-" + String.format("%04d", INCR)`（本地日跨界由 date key 自然滚动）。

---

## 13. 分阶段交付

P2 不再切子阶段（不像 P1 切配置 / 浏览 / 预约）—— 预约引擎整体一气呵成，按代码层自然分层即可：

| 层 | 内容 |
|---|---|
| Domain | Booking 实体 + mapper、VOs、RedisKey、BookingProperties |
| Service | BookingService + 状态机 + 并发抢号 + cancel cutoff + 用户并发上限 |
| Controller | Admin 4 端点 + App 3 端点（写） |
| Scheduler | BookingStatusScheduler + AsyncConfig 注入 |
| 测试 | BookingService 单测（含 lambda cache 预热 Booking/BookingSlot）；controller 单测；集成测试（create→conflict→cancel→re-create happy path） |
| wxapp | booking-confirm 弹窗逻辑、my-events 复用为 my-bookings 或新加 pages/my-bookings/（轻量） |
| admin | `/bookings` 列表页 + 完成/爽约/取消弹窗 |

---

## 14. 测试策略

- **BookingService 单测**：状态机合法/非法（CONFIRMED→[CANCELLED|COMPLETED|NO_SHOW] → 拒绝再操作）、用户并发上限（5+1 拒第 6）、cancel-cutoff 校验、booking_no 格式校验、conflict 路径（DB UNIQUE 模拟 — 用 Mockito + `thenAnswer` 抛 `DataIntegrityViolation` 然后 service 翻译）。
- **BookingService 多格子测试**：2 格跨 band 求和；任一格无 band 整单拒；任一格被占整单拒。
- **BookingStatusScheduler 单测**：clock fixed at T，fixed clock → 跑 scanner → 期望 UPDATE WHERE clause 的参数精确 (intercept SqlSession/Mapper or use Mockito `verify(bookingMapper, times(1)).update(...)`)。
- **Controller 单测**：直接方法调用；与 P1 AdminVenueControllerTest 同型。
- **集成测试 VenueBookingIntegrationTest**：
  - 完整 happy path（create→my bookings→admin list→complete→history 看到）；
  - 并发抢号（两线程创建同一 `court+slot_start`，一胜一败，败者 `SLOT_ALREADY_TAKEN`）；
  - cancel cutoff（用户过截止 `CANCEL_DEADLINE_PASSED`、admin force 成功）；
  - 取消释放（取消后另一用户能抢到同格子）。

---

## 15. 与现有系统的集成要点

- **Redis**：`StringRedisTemplate`（P1 RedisConfig 提供）—— `booking:seq:{date}` 是纯 String。
- **AppAuthFilter**：不修改 `PUBLIC_GET_PREFIXES`；`/api/app/bookings/**` 全凭 JWT 强制；标记 `@RequireAppUser` 满足 D9。
- **OperationLogClassifier**：P1 已加 `bookings → BOOKING / Booking`，P2 自动生效。
- **GlobalExceptionHandler**：增 `SQLIntegrityConstraintViolationException`（或更窄的兜底）→ `SLOT_ALREADY_TAKEN`。
- **AsyncConfig**：scheduler 复用同 `@EnableScheduling`；不必新建 executor。
- **wxapp**：UI 复用 P1 已建的 `court-card` 视觉；新增 `pages/booking-confirm` **或**在 `venue-detail` 内嵌 `wx.showModal` —— 本 spec 选**弹窗**（轻量、不新加页），仅逻辑 + 动效需要的 CSS 即可。

---

## 16. 已知限制与未来工作

- **多端时区**：服务端 `LocalDateTime.now()`、`LocalDate.now()` 按 server TZ，wxapp 也是设备本地 + 上行 ISO。设备/服务端 TZ 不一致时会出"边界时辰偏差"，本机部署场景保留已知风险（P1 同一行为）。
- **payment / refund**：永远不接（直到产品方向变了）。
- **赛事打通 / booking_slot 由 EVENT 占用**：留作未来 hook（参见 P1 spec §12）。
- **admin 代下单 / 转单**：P2 不做；如需，复用 `BookingService.create` 加 `bookedByAdmin=true` 一行 flag 即可。
- **NO_SHOW 自动**：当前 P2 不自动（用户决定）；未来若要，按 `slot_end < now − X 小时且 status=CONFIRMED` 同一 UPDATE 但 status='NO_SHOW'。

---

## 17. 验收清单（P2 Definition of Done）

- [ ] `wxapp` 登录用户在 venue-detail 点可订格 → 弹窗确认 → 提交，跳到我的预约看到该单。
- [ ] `wxapp` 我的预约 → 取消（截止前成功，截止后 `CANCEL_DEADLINE_PASSED`，admin force 后看不到）。
- [ ] `wxapp` 同格子两次下单仅一胜一败。
- [ ] `admin` `/bookings` 列出全部预约，筛选 venue/court/date/status 正确。
- [ ] `admin` 完成 / 爽约 / 强制取消 三项可点、幂等、留痕。
- [ ] scheduler 每 5 分钟扫一次，2h grace 后的 CONFIRMED 自动 → COMPLETED。
- [ ] 用户并发上限生效。
- [ ] `booking_no` 每日从 0001 起，跨日归 1。
- [ ] `mvn verify` 持续绿（INSTRUCTION≥80% / BRANCH≥60%），重点 BookingService + scheduler 覆盖率。
- [ ] `npm run lint:check && npm run build` 绿。
- [ ] 未做：在线支付、赛事打通、节假日营业时间、地图、admin 代下单。
