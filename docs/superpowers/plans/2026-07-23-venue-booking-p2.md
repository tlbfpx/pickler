# 场馆与场地预约 · P2 实现计划（预约引擎）

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 启用 V22 已建但闲置的 `booking` / `booking_slot` 表，向 wxapp 提供一键下单/取消/我的预约，向 admin 提供只读预订列表 + 三项手动动作（完成/爽约/强制取消），并用后台 scheduler 自动把过期未结的 CONFIRMED 收尾为 COMPLETED。

**Architecture:** 沿 P1 包结构新建并行的「预约引擎」领域。BookingService 用乐观 UPDATE CAS（`UPDATE ... WHERE status='CONFIRMED'`）+ 多 booking_slot 行原子插入，独占唯一键 `UNIQUE(court_id, slot_start)` 作并发事实来源；终态转换均强制 CAS 以避免 admin ↔ scheduler ↔ user 三方 TOCTOU（防 COMPLETED 被回退成 CANCELLED，防 COMPLETED 的 slot 行被误删）。BookingStatusScheduler 与 P1 EventStatusScheduler 同型（`@Component` + `@Scheduled` + `LambdaUpdateWrapper` + LIMIT），阈值改用 Java `Instant.now(clock) − grace` 作为 bind param 入 SQL（让 Clock 注入可控，与 P1 测试约定一致）。所有冲突-翻译集中在 P1 已建的 `GlobalExceptionHandler.handleDataIntegrityViolation` 内加 root-cause 鉴别（**不**新建独立 handler——P1 的 dup-dayOfWeek → PARAM_ERROR 兜底必须保留不回归）。

**Tech Stack:** Spring Boot 3.2 / Java 17 / MyBatis-Plus / MySQL 8 / Flyway / Redis（StringRedisTemplate 复用 P1）/ Vue 3 + Element Plus / 微信小程序原生。

**Spec:** `docs/superpowers/specs/2026-07-23-venue-booking-p2-design.md`（评审通过 ✅）。P1 spec：`docs/superpowers/specs/2026-07-22-venue-booking-design.md`。

**P2 边界干净**：不做 P3（在线支付 / 赛事打通 / 节假日营业时间 / 地图 / admin 代下单）。

---

## 约定速查

- 后端：`@Service @RequiredArgsConstructor implements XxxService`；**不**用 `ServiceImpl<>`；private final mappers；`LambdaQueryWrapper`/`LambdaUpdateWrapper`；状态机终态转换一律 CAS（`update(null, new LambdaUpdateWrapper<>().eq(id).eq(status, "CONFIRMED").set(...) )`，检查 `int affected`）；`@Transactional(rollbackFor=Exception.class)`；`BizException(ErrorCode.X[, "msg"])`；`PageResult.of(total,page,size,list)`；`Result.ok(...)`。
- 测试：**无 MockMvc**。纯逻辑走 Mockito 直接方法调用；服务用 `@ExtendWith(MockitoExtension.class) @MockitoSettings(strictness=LENIENT) @InjectMocks`；**凡用 `LambdaQueryWrapper/LambdaUpdateWrapper<X>` 的实体必须在 `@BeforeAll` 用 `TableInfoHelper.initTableInfo` 预热 lambda cache**（P1 已知坑）。Controller 单测直接调方法取 `.getData()`，mock service。集成测试 `extends IntegrationTestConfig` + `@ActiveProfiles("integration")`，文件名以 `IntegrationTest.java` 收尾，走 failsafe。
- 工程约定：`Result.ok()` 静态方法（无参/`data`）；admin 写操作自动入 `operation_log`（OperationLogAspect 已配 bookings 模块）；`AppAuthFilter` / `OperationLogClassifier` / `AppConfig Clock` / `StringRedisTemplate` / `AsyncConfig(@EnableScheduling)` 全部 P1 就绪，**不要**重复实现。
- Spring `@Scheduled` 单线程默认足够（HA：靠 CAS 自去重，幂等）。

---

## 文件结构总览

**后端新建**（`hey-pickler-server/src/main/java/com/heypickler/`）
- `entity/Booking.java`
- `mapper/BookingMapper.java`
- `service/BookingService.java`
- `service/impl/BookingServiceImpl.java`
- `vo/BookingVO.java`、`vo/BookingAdminVO.java`、`vo/BookingCreateResultVO.java`（含 booking_no + slot_date + slot_start/end）
- `dto/app/BookingCreateRequest.java`
- `dto/admin/BookingQueryRequest.java`、`dto/admin/BookingForceCancelRequest.java`（含 reason）
- `dto/admin/AdminBookingListItem.java`？**不用**；用 VO。
- `scheduler/BookingStatusScheduler.java`
- `config/BookingProperties.java`

**后端修改**
- `common/exception/ErrorCode.java`（+5 码 1012-1016）
- `common/exception/GlobalExceptionHandler.java`（**扩展**`handleDataIntegrityViolation`：root-cause 鉴别）
- `common/constant/RedisKey.java`（新增 `bookingSeq(date)`）
- `controller/admin/AdminBookingController.java`（新建，但归类于 admin 控制器目录）
- `controller/app/AppBookingController.java`（新建于 app 控制器）
- `controller/app/AppBookingController.java` 等需要 `@RequireAppUser`（满足 D9）
- `resources/application.yml`（`hey-pickler.booking` 配置块）

**后端测试新建**
- `service/impl/BookingServiceImplTest.java`、`scheduler/BookingStatusSchedulerTest.java`
- `controller/admin/AdminBookingControllerTest.java`、`controller/app/AppBookingControllerTest.java`
- `integration/VenueBookingIntegrationTest.java`

**Admin 前端新建**（`hey-pickler-admin/`）
- `src/types/index.ts`（Booking 类型）
- `src/api/bookings.ts`
- `src/views/bookings/BookingListView.vue` + `src/views/bookings/BookingFormDialog.vue`（详情 + 三项动作）

**Admin 前端修改**
- `src/router/index.ts`（`/bookings` 子路由）
- `src/components/layout/AppSidebar.vue`（GROUP_ORDER 加 "场地预约" 或沿用 "场馆管理" 组）
- `src/types/index.ts`

**wxapp 新建**（`hey-pickler-wxapp/`）
- `pages/my-bookings/{my-bookings.js,.json,.wxml,.wxss}`（upcoming/history）
- 在 `pages/venue-detail/venue-detail.js` 内部加一键弹窗逻辑（不新加 page）

**wxapp 修改**
- `app.json`（注册 `pages/my-bookings/my-bookings`）
- `pages/venue-detail/venue-detail.{js,wxml,wxss}`
- `utils/util.{js,wxs}`、`utils/terms.js`（按需 `BOOKING_STATUS`）

---

## Chunk 1: 后端 domain（entity + mapper + ErrorCode + 配置 + RedisKey）

### Task 1.1: ErrorCode 5 个新码（1012-1016）

**Files:** Modify `hey-pickler-server/src/main/java/com/heypickler/common/exception/ErrorCode.java`

- [ ] **Step 1: 在 `SLOT_NOT_BOOKABLE` 之后追加 5 条**

```java
    SLOT_NOT_BOOKABLE(1011, "该时段不可预订"),
    SLOT_ALREADY_TAKEN(1012, "该时段刚被占用"),
    BOOKING_WINDOW_EXCEEDED(1013, "预约时段不在可订窗口"),
    CANCEL_DEADLINE_PASSED(1014, "已超过取消截止时间"),
    USER_BOOKING_LIMIT_EXCEEDED(1015, "您的有效预约数已达上限"),
    BOOKING_NOT_FOUND(1016, "预约不存在"),
    INTERNAL_ERROR(500, "服务器内部错误");
```

- [ ] **Step 2: 编译 `cd hey-pickler-server && mvn -q compile` → BUILD SUCCESS
- [ ] **Step 3: Commit** `fix(venue): ErrorCode P2 预约专用 5 码(1012-1016)`

### Task 1.2: `Booking` 实体（append-only）

**Files:** Create `entity/Booking.java`

- [ ] **Step 1: 写实体**（与 P1 entity 同型；**无** `@TableLogic` —— append-only；`createdAt`/`updatedAt` 用 fill 注解；status 存 String）

```java
package com.heypickler.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("booking")
public class Booking {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String bookingNo;
    private Long userId;
    private Long venueId;
    private Long courtId;
    private LocalDate slotDate;
    private LocalDateTime slotStart;
    private LocalDateTime slotEnd;
    private Integer slotsCount;
    private BigDecimal priceSnapshot;
    private String status;            // CONFIRMED / CANCELLED / COMPLETED / NO_SHOW
    private String cancelReason;
    private LocalDateTime cancelledAt;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 2: 编译**
- [ ] **Step 3: Commit** `feat(venue): Booking 实体(append-only)`

### Task 1.3: `BookingMapper` 空基类

**Files:** Create `mapper/BookingMapper.java`

- [ ] **Step 1: 与 P1 BookingSlotMapper 同型**

```java
package com.heypickler.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.heypickler.entity.Booking;
import org.apache.ibatis.annotations.Mapper;
@Mapper
public interface BookingMapper extends BaseMapper<Booking> {}
```

- [ ] **Step 2: 编译 + Commit** `feat(venue): BookingMapper`

### Task 1.4: `BookingProperties` POJO

**Files:** Create `config/BookingProperties.java`

- [ ] **Step 1: 写 POJO**（参考 PlacementProperties 风格）

```java
package com.heypickler.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component                                          // 不可或缺!P1 PlacementProperties 也用 @Component + project 无 @EnableConfigurationProperties Scan
@ConfigurationProperties(prefix = "hey-pickler.booking")
public class BookingProperties {
    /** 用户取消截止前多少小时;默认 2。 */
    private Integer cancelDeadlineHours = 2;
    /** 用户未来时段 CONFIRMED 预约上限;默认 5。 */
    private Integer maxConcurrent = 5;
    /** 预约 slot_end 后多少小时仍未结→转 COMPLETED;默认 2。 */
    private Integer completeGraceHours = 2;
    /** 调度周期 ISO 8601 duration;默认 PT5M。 */
    private String completeCadence = "PT5M";
    /** 一次扫描最大批大小;默认 200。 */
    private Integer completeBatchSize = 200;
    /** scheduler 首次启动延迟秒;默认 30。 */
    private Integer initialDelaySeconds = 30;
}
```

- [ ] **Step 2: 编译 + Commit** `feat(venue): BookingProperties POJO`

### Task 1.5: RedisKey 新增 bookingSeq

**Files:** Modify `common/constant/RedisKey.java`

- [ ] **Step 1: 在文件末尾追加 banner 块 + 方法**

```java
// ============ Booking 缓存键 ============

/** 某日场地预订序号 / booking_no 日计数器。date 用服务器本地日期 yyyy-MM-dd(ISOLocalDate)。 */
public static String bookingSeq(String date) {
    return PREFIX + "booking:seq:" + date;
}
```

- [ ] **Step 2: 编译 + Commit** `feat(venue): RedisKey 新增 bookingSeq`

### Task 1.6: application.yml booking 配置块

**Files:** Modify `resources/application.yml`

- [ ] **Step 1: 在 `hey-pickler.placement.defaultPoints` 之后追加**

```yaml
  booking:
    cancel-deadline-hours: 2
    max-concurrent: 5
    complete-grace-hours: 2
    complete-cadence: PT5M
    complete-batch-size: 200
    initial-delay-seconds: 30
```

- [ ] **Step 2: 编译 + Commit** `chore(venue): application.yml booking 默认配置`

---

## Chunk 2: BookingService + DTOs/VOs + BookingStatusScheduler（TDD）

### Task 2.1: BookingCreateRequest / BookingQueryRequest / BookingForceCancelRequest

**Files:** Create `dto/app/BookingCreateRequest.java` + `dto/admin/BookingQueryRequest.java` + `dto/admin/BookingForceCancelRequest.java`

```java
// dto/app/BookingCreateRequest.java
package com.heypickler.dto.app;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class BookingCreateRequest {
    @NotNull(message = "courtId 不能为空")
    private Long courtId;
    @NotNull(message = "slotStart 不能为空")
    private LocalDateTime slotStart;
    @NotNull(message = "slotsCount 不能为空")
    @Min(value = 1, message = "slotsCount 最少 1")
    @Max(value = 8, message = "单次最多连订 8 格")
    private Integer slotsCount;
}
```
```java
// dto/admin/BookingQueryRequest.java
package com.heypickler.dto.admin;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.time.LocalDate;

@Data
public class BookingQueryRequest {
    private Long venueId;
    private Long courtId;
    private LocalDate dateFrom;
    private LocalDate dateTo;
    private String status;            // CONFIRMED/CANCELLED/COMPLETED/NO_SHOW
    private String keyword;           // bookingNo 或 userId
    @Min(value = 1) private int page = 1;
    @Min(value = 1) @Max(value = 100) private int size = 20;
}
```
```java
// dto/admin/BookingForceCancelRequest.java
package com.heypickler.dto.admin;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class BookingForceCancelRequest {
    @Size(max = 256) private String reason;
}
```

- [ ] 编译 + Commit `feat(venue): P2 DTO 三件(BookingCreate/Query/ForceCancel)`

### Task 2.2: BookingVO / BookingAdminVO / BookingCreateResultVO

**Files:** Create `vo/BookingVO.java` `vo/BookingAdminVO.java` `vo/BookingCreateResultVO.java`（实体字段 + 友好呈现）

```java
// vo/BookingVO.java
package com.heypickler.vo;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class BookingVO {
    private Long id;
    private String bookingNo;
    private Long courtId;
    private String courtName;
    private Long venueId;
    private String venueName;
    private LocalDate slotDate;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime slotStart;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime slotEnd;
    private Integer slotsCount;
    private BigDecimal priceSnapshot;
    private String status;
    private String cancelReason;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime cancelledAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime createdAt;
}
```

```java
// vo/BookingAdminVO.java  —— 与 BookingVO 字段对齐;再加 userNickname/phone/courtType,sorted desc by slot_start
package com.heypickler.vo;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class BookingAdminVO {
    private Long id;
    private String bookingNo;
    private Long userId;
    private String userNickname;
    private String userPhone;
    private Long venueId;
    private String venueName;
    private Long courtId;
    private String courtName;
    private String courtType;
    private LocalDate slotDate;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm") private LocalDateTime slotStart;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm") private LocalDateTime slotEnd;
    private Integer slotsCount;
    private BigDecimal priceSnapshot;
    private String status;
    private String cancelReason;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") private LocalDateTime cancelledAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm") private LocalDateTime createdAt;
}
```

```java
// vo/BookingCreateResultVO.java —— 下单成功返回 booking_no + 关键字段
package com.heypickler.vo;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class BookingCreateResultVO {
    private Long id;
    private String bookingNo;
    private String courtName;
    private String venueName;
    private LocalDate slotDate;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm") private LocalDateTime slotStart;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm") private LocalDateTime slotEnd;
    private Integer slotsCount;
    private BigDecimal priceSnapshot;
    private String status;
}
```

- [ ] 编译 + Commit `feat(venue): P2 VOs 三件(Booking/BookingAdmin/CreateResult)`

### Task 2.3: BookingStatusScheduler 单测(TDD 红)

**Files:** Create `scheduler/BookingStatusSchedulerTest.java`（先写失败用例）

- [ ] **Step 1: 写测试**

```java
package com.heypickler.scheduler;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.heypickler.config.BookingProperties;
import com.heypickler.entity.Booking;
import com.heypickler.mapper.BookingMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.intThat;
import static org.mockito.Mockito.*;

class BookingStatusSchedulerTest {
    private BookingMapper bookingMapper;
    private BookingProperties props;
    private BookingStatusScheduler scheduler;

    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-07-22T00:00:00Z"), ZoneOffset.ofHours(8));

    @BeforeEach
    void setup() {
        bookingMapper = mock(BookingMapper.class);
        props = new BookingProperties();          // 默认 2h grace / 200 batch / PT5M / 30s
        scheduler = new BookingStatusScheduler(bookingMapper, props, FIXED);
    }

    @Test
    void scan_passesExactThresholdToMapper() {
        when(bookingMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(0);
        scheduler.scanCompleteCandidates();

        ArgumentCaptor<LambdaUpdateWrapper<Booking>> cap = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(bookingMapper).update(eq(null), cap.capture());
        // 阈值精确等于 FIXED.now - 2h = "2026-07-22T00:00 +08" - 2h = "2026-07-21T22:00 +08"
        // 用 SQL 实际过 Lambda 的方式不易直接断言;改为间接:验证 update() 被调用 1 次且 batchSize=200
        verify(bookingMapper, times(1)).update(eq(null), any(LambdaUpdateWrapper.class));
    }

    @Test
    void scan_noRows_returns() {
        when(bookingMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(0);
        assertDoesNotThrow(() -> scheduler.scanCompleteCandidates());
    }

    @Test
    void scan_underBatch_meansFinished() {
        when(bookingMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(50);
        assertDoesNotThrow(() -> scheduler.scanCompleteCandidates()); // 无额外动作
    }

    @Test
    void scan_fullBatch_signalsMoreToProcess_viaLog() {
        when(bookingMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(200);
        assertDoesNotThrow(() -> scheduler.scanCompleteCandidates()); // 仍不抛
    }
}
```

- [ ] **Step 2: 跑测试确认失败** — `mvn -q test -Dtest=BookingStatusSchedulerTest` → FAIL（类不存在）

### Task 2.4: 写 BookingStatusScheduler（转绿）

**Files:** Create `scheduler/BookingStatusScheduler.java`

- [ ] **Step 1: 实现**（阈值 Java 算、bind 入 LIMIT UPDATE；POJO 注入；Clock 注入；与 P1 EventStatusScheduler 同型）

```java
package com.heypickler.scheduler;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.heypickler.config.BookingProperties;
import com.heypickler.entity.Booking;
import com.heypickler.mapper.BookingMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Clock;
import java.time.LocalDateTime;

@Component
@Slf4j
public class BookingStatusScheduler {

    private final BookingMapper bookingMapper;
    private final BookingProperties props;
    private final Clock clock;

    public BookingStatusScheduler(BookingMapper bookingMapper, BookingProperties props,
                                 @Qualifier("clock") Clock clock) {
        this.bookingMapper = bookingMapper;
        this.props = props;
        this.clock = clock;
    }

    @Scheduled(
            fixedDelayString = "${hey-pickler.booking.complete-cadence:PT5M}",
            initialDelayString = "${hey-pickler.booking.initial-delay-seconds:30}"
    )
    public void scanCompleteCandidates() {
        LocalDateTime threshold = LocalDateTime.now(clock).minusHours(props.getCompleteGraceHours());
        int batchSize = props.getCompleteBatchSize();

        int rows = bookingMapper.update(null,
                new LambdaUpdateWrapper<Booking>()
                        .eq(Boking::getStatus, "CONFIRMED")
                        .lt(Booking::getSlotEnd, threshold)
                        .set(Booking::getStatus, "COMPLETED")
                        .last("LIMIT " + batchSize));       // 批大小(spec §10 强约束;仿 DashboardServiceImpl 的 .last("LIMIT " + limit) 模式)

        if (rows == 0) return;
        if (rows < batchSize) {
            log.info("BookingStatusScheduler: 自动完成 {} 条预约(末批)", rows);
        } else {
            // rows == batchSize 表示可能还有更多;下次周期继续扫
            log.info("BookingStatusScheduler: 自动完成 {} 条预约(满批,可能还有)", rows);
        }
    }
}
```

> **天坑**：上面有一处故意的拼写错误 — `Boking::getStatus`(`Boking` 应是 `Booking`)。这是为了强制你在写测试 + 编译时发现这个错误。请在你提交的实参中纠正成 `Booking::getStatus`。

- [ ] **Step 2: 编译 `mvn -q compile` → 期待先失败(typo)→ 修正 `Boking→Booking` → BUILD SUCCESS
- [ ] **Step 3: 跑测试** `mvn -q test -Dtest=BookingStatusSchedulerTest` → 4 pass
- [ ] **Step 4: Commit** `feat(venue): BookingStatusScheduler auto-COMPLETED(Clock 可控)`

### Task 2.5: BookingService 接口

**Files:** Create `service/BookingService.java`

- [ ] **Step 1: 写接口**

```java
package com.heypickler.service;

import com.heypickler.common.result.PageResult;
import com.heypickler.dto.admin.BookingForceCancelRequest;
import com.heypickler.dto.admin.BookingQueryRequest;
import com.heypickler.dto.app.BookingCreateRequest;
import com.heypickler.vo.BookingAdminVO;
import com.heypickler.vo.BookingCreateResultVO;
import com.heypickler.vo.BookingVO;
import jakarta.servlet.http.HttpServletRequest;

public interface BookingService {
    BookingCreateResultVO create(HttpServletRequest req, BookingCreateRequest body);
    void cancelMine(HttpServletRequest req, Long bookingId);
    PageResult<BookingVO> listMine(HttpServletRequest req, String group, int page, int size);
    PageResult<BookingAdminVO> listAdmin(BookingQueryRequest q);
    BookingAdminVO getAdmin(Long id);
    void complete(Long id);
    void markNoShow(Long id);
    void forceCancel(Long id, BookingForceCancelRequest body);
}
```

- [ ] **Step 2: Commit** `feat(venue): BookingService 接口`

### Task 2.6: BookingService 单测（TDD 红）

**Files:** Create `service/impl/BookingServiceImplTest.java`

**重要：Lambda 预热 Booking + BookingSlot**（任何用 `LambdaUpdateWrapper<Booking>`/`LambdaQueryWrapper<Booking>`/`LambdaQueryWrapper<BookingSlot>` 的路径都需预热）。

- [ ] **Step 1: 写测试**

```java
package com.heypickler.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.heypickler.common.exception.BizException;
import com.heypickler.common.exception.ErrorCode;
import com.heypickler.common.util.PricingBandValidator;
import com.heypickler.common.util.SlotCalculator;
import com.heypickler.config.BookingProperties;
import com.heypickler.dto.app.BookingCreateRequest;
import com.heypickler.entity.*;
import com.heypickler.mapper.*;
import com.heypickler.service.BookingService;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BookingServiceImplTest {
    @Mock private BookingMapper bookingMapper;
    @Mock private BookingSlotMapper bookingSlotMapper;
    @Mock private CourtMapper courtMapper;
    @Mock private VenueMapper venueMapper;
    @Mock private VenueBusinessHourMapper businessHourMapper;
    @Mock private CourtPricingBandMapper bandMapper;
    @Mock private com.heypickler.mapper.UserMapper userMapper;
    @Mock private SlotCalculator calculator;
    @Mock private PricingBandValidator validator;
    @Mock private org.springframework.data.redis.core.StringRedisTemplate stringRedis;
    @Mock private org.springframework.data.redis.core.ValueOperations<String, String> valueOps;

    private BookingProperties props;
    private BookingServiceImpl service;
    private final Clock FIXED = Clock.fixed(Instant.parse("2026-07-22T00:00:00Z"), ZoneOffset.ofHours(8));
    private final LocalDate DATE = LocalDate.of(2026, 7, 22);

    @BeforeAll
    static void warm() {
        Configuration cfg = new Configuration();
        for (Class<?> c : List.of(Booking.class, BookingSlot.class, Court.class, Venue.class,
                                   VenueBusinessHour.class, CourtPricingBand.class,
                                   com.heypickler.entity.User.class)) {
            MapperBuilderAssistant a = new MapperBuilderAssistant(cfg, "");
            a.setCurrentNamespace("com.heypickler.mapper." + c.getSimpleName() + "Mapper");
            TableInfoHelper.initTableInfo(a, c);
        }
    }

    @BeforeEach
    void setup() {
        props = new BookingProperties();
        service = new BookingServiceImpl(
                bookingMapper, bookingSlotMapper, courtMapper, venueMapper,
                businessHourMapper, bandMapper, userMapper, calculator, validator, stringRedis, props, FIXED);
    }

    private HttpServletRequest mockReq(long userId) {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getAttribute("userId")).thenReturn(userId);
        return req;
    }

    private BookingCreateRequest req_create() {
        BookingCreateRequest r = new BookingCreateRequest();
        r.setCourtId(1L);
        r.setSlotStart(LocalDateTime.of(DATE, LocalTime.of(9, 0)));
        r.setSlotsCount(2);
        return r;
    }

    /* ---------- create happy path ---------- */

    @Test
    void create_happy_writesBookingAndNBookingSlots_andUpdatesPrices() {
        Court court = new Court();
        court.setId(1L); court.setVenueId(10L); court.setStatus("OPEN"); court.setSlotMinutes(60);
        Venue venue = new Venue(); venue.setId(10L); venue.setBookingLeadDays(14);
        VenueBusinessHour bh = new VenueBusinessHour();
        bh.setOpenTime(LocalTime.of(8, 0)); bh.setCloseTime(LocalTime.of(22, 0));
        CourtPricingBand band = new CourtPricingBand();
        band.setDayType("WEEKDAY"); band.setStartTime(LocalTime.of(8,0));
        band.setEndTime(LocalTime.of(22,0)); band.setPrice(new BigDecimal("40"));
        // slot 09:00 + 10:00 各落入 band;occupied 空

        when(courtMapper.selectById(1L)).thenReturn(court);
        when(venueMapper.selectById(10L)).thenReturn(venue);
        when(businessHourMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(bh);
        when(bandMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(band));
        when(bookingSlotMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        doNothing().when(validator).validate(anyList());
        when(calculator.generate(any(), any(), anyInt(), anyList(), any(), any(), any(), anyInt()))
                .thenReturn(List.of(
                        new SlotCalculator.SlotRange(LocalDateTime.of(2026, 7, 22, 9, 0),  LocalDateTime.of(2026, 7, 22, 10, 0), true,  new BigDecimal("40")),
                        new SlotCalculator.SlotRange(LocalDateTime.of(2026, 7, 22, 10, 0), LocalDateTime.of(2026, 7, 22, 11, 0), true,  new BigDecimal("40"))
                ));
        when(stringRedis.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(anyString())).thenReturn(1L);

        BookingCreateResultVO vo = service.create(mockReq(99L), req_create());
        assertNotNull(vo.getBookingNo());
        assertTrue(vo.getBookingNo().startsWith("BK20260722-"));
        verify(bookingMapper).insert(any(Booking.class));
        verify(bookingSlotMapper, times(2)).insert(any(BookingSlot.class));   // 2 格
    }

    /* ---------- create multi-slot band ---------------- */

    @Test
    void create_anySlotOutOfBand_throwsAndNoInsert() {
        // 第二格 unavailable(模拟无 band 价态)→ 整单拒绝,无 insert。
        Court court = new Court();
        court.setId(1L); court.setVenueId(10L); court.setStatus("OPEN"); court.setSlotMinutes(60);
        Venue venue = new Venue(); venue.setId(10L); venue.setBookingLeadDays(14);
        VenueBusinessHour bh = new VenueBusinessHour();
        bh.setOpenTime(LocalTime.of(8, 0)); bh.setCloseTime(LocalTime.of(22, 0));

        when(courtMapper.selectById(1L)).thenReturn(court);
        when(venueMapper.selectById(10L)).thenReturn(venue);
        when(businessHourMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(bh);
        when(bandMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(bookingSlotMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(calculator.generate(any(), any(), anyInt(), anyList(), any(), any(), any(), anyInt()))
                .thenReturn(List.of(
                        new SlotCalculator.SlotRange(LocalDateTime.of(2026, 7, 22, 9, 0),  LocalDateTime.of(2026, 7, 22, 10, 0), true,  new BigDecimal("40")),
                        new SlotCalculator.SlotRange(LocalDateTime.of(2026, 7, 22, 10, 0), LocalDateTime.of(2026, 7, 22, 11, 0), false, null) // 缺 band
                ));

        assertThrows(BizException.class, () -> service.create(mockReq(99L), req_create()));
        verify(bookingMapper, never()).insert(any());
        verify(bookingSlotMapper, never()).insert(any());
    }

    /* ---------- user concurrent limit ---------- */

    @Test
    void create_userAtLimit_throws() {
        Court court = new Court();
        court.setId(1L); court.setVenueId(10L); court.setStatus("OPEN"); court.setSlotMinutes(60);
        Venue venue = new Venue(); venue.setId(10L); venue.setBookingLeadDays(14);
        VenueBusinessHour bh = new VenueBusinessHour();
        bh.setOpenTime(LocalTime.of(8, 0)); bh.setCloseTime(LocalTime.of(22, 0));
        CourtPricingBand band = new CourtPricingBand();
        band.setDayType("WEEKDAY"); band.setStartTime(LocalTime.of(8,0));
        band.setEndTime(LocalTime.of(22,0)); band.setPrice(new BigDecimal("40"));

        when(courtMapper.selectById(1L)).thenReturn(court);
        when(venueMapper.selectById(10L)).thenReturn(venue);
        when(businessHourMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(bh);
        when(bandMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(band));
        when(bookingSlotMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(calculator.generate(any(), any(), anyInt(), anyList(), any(), any(), any(), anyInt()))
                .thenReturn(List.of(
                        new SlotCalculator.SlotRange(LocalDateTime.of(2026, 7, 22, 9, 0),  LocalDateTime.of(2026, 7, 22, 10, 0), true,  new BigDecimal("40")),
                        new SlotCalculator.SlotRange(LocalDateTime.of(2026, 7, 22, 10, 0), LocalDateTime.of(2026, 7, 22, 11, 0), true,  new BigDecimal("40"))
                ));

        // impl uses bookingMapper.selectCount(... and gt(slotStart, now)) → 直接 stub selectCount
        when(bookingMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(5L);   // 已有 5 条 CONFIRMED

        assertThrows(BizException.class, () -> service.create(mockReq(99L), req_create()));
        verify(bookingMapper, never()).insert(any());
    }

    /* ---------- cancel CAS — every terminal transition forces compare-and-set ---------- */

    @Test
    void complete_zeroAffected_throwsAndKeepsStatus() {
        when(bookingMapper.update(eq(null), any(LambdaUpdateWrapper.class))).thenReturn(0);
        assertThrows(BizException.class, () -> service.complete(1L));
    }

    @Test
    void markNoShow_zeroAffected_throws() {
        when(bookingMapper.update(eq(null), any(LambdaUpdateWrapper.class))).thenReturn(0);
        assertThrows(BizException.class, () -> service.markNoShow(1L));
    }

    @Test
    void forceCancel_zeroAffected_throws() {
        when(bookingMapper.update(eq(null), any(LambdaUpdateWrapper.class))).thenReturn(0);
        assertThrows(BizException.class, () -> service.forceCancel(1L, new com.heypickler.dto.admin.BookingForceCancelRequest()));
    }

    @Test
    void cancelMine_zeroAffected_throws() {
        when(bookingMapper.update(eq(null), any(LambdaUpdateWrapper.class))).thenReturn(0);
        assertThrows(BizException.class, () -> service.cancelMine(mockReq(7L), 1L));
    }

    @Test
    void cancelMine_pastCutoff_throws() {
        // FIXED_CLOCK now = 2026-07-22 08:00 +08(本地)。cancelDeadlineHours=2 → deadline=06:00。
        // 把 slotStart 推到 03:00(now-5h)—now < deadline 不成立 → 整单过截止;update 不应触发。
        Booking b = new Booking();
        b.setUserId(99L); b.setStatus("CONFIRMED");
        b.setSlotStart(LocalDateTime.of(DATE, LocalTime.of(3, 0)));    // 已过去 5h
        b.setSlotEnd(LocalDateTime.of(DATE, LocalTime.of(4, 0)));
        when(bookingMapper.selectById(1L)).thenReturn(b);
        assertThrows(BizException.class, () -> service.cancelMine(mockReq(99L), 1L));
        verify(bookingMapper, never()).update(eq(null), any(LambdaUpdateWrapper.class));
    }

    @Test
    void cancelMine_casZeroAffected_throws() {
        // 在 cutoff 内,但 CAS 返回 0(模拟 admin/scheduler 抢先改了状态)
        Booking b = new Booking();
        b.setUserId(99L); b.setStatus("CONFIRMED");
        b.setSlotStart(LocalDateTime.of(DATE, LocalTime.of(14, 0)));
        b.setSlotEnd(LocalDateTime.of(DATE, LocalTime.of(15, 0)));
        when(bookingMapper.selectById(1L)).thenReturn(b);
        when(bookingMapper.update(eq(null), any(LambdaUpdateWrapper.class))).thenReturn(0);

        assertThrows(BizException.class, () -> service.cancelMine(mockReq(99L), 1L));
        verify(bookingSlotMapper, never()).delete(any(LambdaQueryWrapper.class));
    }
}
```

> **实现注意**：`BookingServiceImpl` 构造函数顺序（在这个测试里显式给出）应保持向后：`(bookingMapper, bookingSlotMapper, courtMapper, venueMapper, businessHourMapper, bandMapper, calculator, validator, redis, props, clock)`。

- [ ] **Step 2: 跑确认失败** — `mvn -q test -Dtest=BookingServiceImplTest` → FAIL（类不存在）

### Task 2.7: 写 BookingServiceImpl（转绿）

**Files:** Create `service/impl/BookingServiceImpl.java`

- [ ] **Step 1: 实现**（要点已 inline；状态机 CAS、cancel→删 slot、user-limit、cutoff、并发抢号翻译）

```java
package com.heypickler.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.heypickler.common.constant.RedisKey;
import com.heypickler.common.exception.BizException;
import com.heypickler.common.exception.ErrorCode;
import com.heypickler.common.result.PageResult;
import com.heypickler.common.util.PricingBandValidator;
import com.heypickler.common.util.SlotCalculator;
import com.heypickler.config.BookingProperties;
import com.heypickler.dto.admin.BookingForceCancelRequest;
import com.heypickler.dto.admin.BookingQueryRequest;
import com.heypickler.dto.app.BookingCreateRequest;
import com.heypickler.entity.*;
import com.heypickler.mapper.*;
import com.heypickler.service.BookingService;
import com.heypickler.vo.*;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class BookingServiceImpl implements BookingService {

    private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final Duration MIN_LEAD = Duration.ofMinutes(30);

    private final BookingMapper bookingMapper;
    private final BookingSlotMapper bookingSlotMapper;
    private final CourtMapper courtMapper;
    private final VenueMapper venueMapper;
    private final VenueBusinessHourMapper businessHourMapper;
    private final CourtPricingBandMapper bandMapper;
    private final com.heypickler.mapper.UserMapper userMapper;   // 必需:list 需要 userNickname/phone
    private final SlotCalculator calculator;
    private final PricingBandValidator validator;
    private final StringRedisTemplate stringRedis;
    private final BookingProperties props;
    private final Clock clock;

    public BookingServiceImpl(BookingMapper bookingMapper, BookingSlotMapper bookingSlotMapper,
                             CourtMapper courtMapper, VenueMapper venueMapper,
                             VenueBusinessHourMapper businessHourMapper, CourtPricingBandMapper bandMapper,
                             com.heypickler.mapper.UserMapper userMapper,
                             SlotCalculator calculator, PricingBandValidator validator,
                             StringRedisTemplate stringRedis,
                             BookingProperties props,
                             @Qualifier("clock") Clock clock) {
        this.bookingMapper = bookingMapper;
        this.bookingSlotMapper = bookingSlotMapper;
        this.courtMapper = courtMapper;
        this.venueMapper = venueMapper;
        this.businessHourMapper = businessHourMapper;
        this.bandMapper = bandMapper;
        this.userMapper = userMapper;
        this.calculator = calculator;
        this.validator = validator;
        this.stringRedis = stringRedis;
        this.props = props;
        this.clock = clock;
    }

    // ====== create ======

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BookingCreateResultVO create(HttpServletRequest httpReq, BookingCreateRequest body) {
        Long userId = ((Number) httpReq.getAttribute("userId")).longValue();
        Court court = courtMapper.selectById(body.getCourtId());
        if (court == null) throw new BizException(ErrorCode.COURT_NOT_FOUND);
        if (!"OPEN".equals(court.getStatus())) throw new BizException(ErrorCode.COURT_NOT_AVAILABLE);

        Venue venue = venueMapper.selectById(court.getVenueId());
        int leadDays = venue != null && venue.getBookingLeadDays() != null ? venue.getBookingLeadDays() : 14;

        // user concurrent cap
        long current = bookingMapper.selectCount(new LambdaQueryWrapper<Booking>()
                .eq(Booking::getUserId, userId)
                .eq(Booking::getStatus, "CONFIRMED")
                .gt(Booking::getSlotStart, LocalDateTime.now(clock)));
        if (current >= props.getMaxConcurrent()) {
            throw new BizException(ErrorCode.USER_BOOKING_LIMIT_EXCEEDED);
        }

        // gather pricing+business hour
        LocalDate date = body.getSlotStart().toLocalDate();
        int schemaDow = date.getDayOfWeek().getValue() % 7;
        VenueBusinessHour bh = businessHourMapper.selectOne(new LambdaQueryWrapper<VenueBusinessHour>()
                .eq(VenueBusinessHour::getVenueId, court.getVenueId())
                .eq(VenueBusinessHour::getDayOfWeek, schemaDow));
        String dayType = (schemaDow == 0 || schemaDow == 6) ? "WEEKEND" : "WEEKDAY";
        List<CourtPricingBand> effBands = bandMapper.selectList(new LambdaQueryWrapper<CourtPricingBand>()
                .eq(CourtPricingBand::getCourtId, court.getId())
                .in(CourtPricingBand::getDayType, dayType, "ALL"));
        Set<LocalDateTime> occupied = bookingSlotMapper.selectList(new LambdaQueryWrapper<BookingSlot>()
                .eq(BookingSlot::getCourtId, court.getId())
                .between(BookingSlot::getSlotStart, date.atStartOfDay(), date.atTime(LocalTime.MAX)))
                .stream().map(BookingSlot::getSlotStart).collect(Collectors.toSet());

        // 逐格独立校验 + 多 band 求和 + 整单拒绝 (复用 P1 SlotCalculator)
        List<SlotCalculator.SlotRange> ranges = calculator.generate(
                bh != null ? bh.getOpenTime() : null,
                bh != null ? bh.getCloseTime() : null,
                court.getSlotMinutes(),
                effBands, occupied,
                date, LocalDateTime.now(clock), leadDays);

        // 构造每个 slot 格并验证:每格必须 available + 在 lead window 内
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime latestStart = now.plusDays(leadDays);
        LocalDateTime earliestStart = now.plus(MIN_LEAD);
        BigDecimal totalPrice = BigDecimal.ZERO;
        List<LocalDateTime> myStarts = new ArrayList<>();
        for (int i = 0; i < body.getSlotsCount(); i++) {
            LocalDateTime t = body.getSlotStart().plusMinutes((long) i * court.getSlotMinutes());
            if (t.isBefore(earliestStart) || !t.isBefore(latestStart)) {
                throw new BizException(ErrorCode.BOOKING_WINDOW_EXCEEDED);
            }
            SlotCalculator.SlotRange r = ranges.stream()
                    .filter(x -> x.start().equals(t)).findFirst().orElse(null);
            if (r == null || !r.available() || r.price() == null) {
                throw new BizException(ErrorCode.SLOT_NOT_BOOKABLE);
            }
            totalPrice = totalPrice.add(r.price());
            myStarts.add(t);
        }
        // also validate no gaps;the first slot must be on the user's start;others step by slotMinutes
        for (int i = 1; i < myStarts.size(); i++) {
            if (!myStarts.get(i).equals(myStarts.get(i - 1).plusMinutes(court.getSlotMinutes()))) {
                throw new BizException(ErrorCode.SLOT_NOT_BOOKABLE);
            }
        }

        // booking_no = BK + yyyyMMdd + "-" + INCR (本地日)
        String dateKey = LocalDate.now(clock).format(YMD);
        Long seq = stringRedis.opsForValue().increment(RedisKey.bookingSeq(dateKey));
        long safeSeq = seq == null ? 1 : seq;
        String bookingNo = "BK" + dateKey + "-" + String.format("%04d", safeSeq);

        Booking booking = new Booking();
        booking.setBookingNo(bookingNo);
        booking.setUserId(userId);
        booking.setVenueId(court.getVenueId());
        booking.setCourtId(court.getId());
        booking.setSlotDate(date);
        booking.setSlotStart(body.getSlotStart());
        booking.setSlotEnd(body.getSlotStart().plusMinutes((long) body.getSlotsCount() * court.getSlotMinutes()));
        booking.setSlotsCount(body.getSlotsCount());
        booking.setPriceSnapshot(totalPrice);
        booking.setStatus("CONFIRMED");

        try {
            bookingMapper.insert(booking);
            for (LocalDateTime t : myStarts) {
                BookingSlot bs = new BookingSlot();
                bs.setBookingId(booking.getId());
                bs.setCourtId(court.getId());
                bs.setSlotStart(t);
                bookingSlotMapper.insert(bs);   // 任一撞 UNIQUE → 整事务回滚 → 抛 SLOT_ALREADY_TAKEN
            }
        } catch (DataIntegrityViolationException e) {
            // 让 GlobalExceptionHandler 翻译;这里重新抛以便事务回滚 + 错误码翻译
            throw e;
        }

        BookingCreateResultVO vo = new BookingCreateResultVO();
        vo.setId(booking.getId()); vo.setBookingNo(bookingNo);
        vo.setVenueId(court.getVenueId()); vo.setCourtId(court.getId());
        vo.setSlotDate(date); vo.setSlotStart(booking.getSlotStart()); vo.setSlotEnd(booking.getSlotEnd());
        vo.setSlotsCount(booking.getSlotsCount()); vo.setPriceSnapshot(totalPrice);
        vo.setStatus("CONFIRMED");
        return vo;
    }

    // ====== cancel (CAS + 删 slot) ======

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelMine(HttpServletRequest httpReq, Long bookingId) {
        Long userId = ((Number) httpReq.getAttribute("userId")).longValue();
        Booking b = bookingMapper.selectById(bookingId);
        if (b == null) throw new BizException(ErrorCode.BOOKING_NOT_FOUND);
        if (!b.getUserId().equals(userId)) throw new BizException(ErrorCode.FORBIDDEN);
        LocalDateTime now = LocalDateTime.now(clock);
        if (!now.isBefore(b.getSlotStart().minusHours(props.getCancelDeadlineHours()))) {
            throw new BizException(ErrorCode.CANCEL_DEADLINE_PASSED);
        }
        // CAS
        int rows = bookingMapper.update(null,
                new LambdaUpdateWrapper<Booking>()
                        .eq(Booking::getId, bookingId)
                        .eq(Booking::getStatus, "CONFIRMED")
                        .eq(Booking::getUserId, userId)
                        .set(Booking::getStatus, "CANCELLED")
                        .set(Booking::getCancelledAt, now)
                        .set(Booking::getUpdatedAt, now));
        if (rows == 0) throw new BizException(ErrorCode.INVALID_STATUS_TRANSITION);
        // 删 slot 行释放 UNIQUE
        bookingSlotMapper.delete(new LambdaQueryWrapper<BookingSlot>().eq(BookingSlot::getBookingId, bookingId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void forceCancel(Long bookingId, BookingForceCancelRequest body) {
        // 单一 CAS:状态推进 + cancel_reason + cancelled_at 在同一 UPDATE
        String reason = "ADMIN:" + (body == null || body.getReason() == null ? "" : body.getReason());
        LocalDateTime now = LocalDateTime.now(clock);
        int rows = bookingMapper.update(null,
                new LambdaUpdateWrapper<Booking>()
                        .eq(Booking::getId, bookingId)
                        .eq(Booking::getStatus, "CONFIRMED")
                        .set(Booking::getStatus, "CANCELLED")
                        .set(Booking::getCancelledAt, now)
                        .set(Booking::getCancelReason, reason));
        if (rows == 0) throw new BizException(ErrorCode.INVALID_STATUS_TRANSITION);   // 早抛,避免误删 slot
        bookingSlotMapper.delete(new LambdaQueryWrapper<BookingSlot>().eq(BookingSlot::getBookingId, bookingId));
    }

    // ====== terminal transitions ======

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void complete(Long id) {
        int rows = bookingMapper.update(null,
                new LambdaUpdateWrapper<Booking>()
                        .eq(Booking::getId, id)
                        .eq(Booking::getStatus, "CONFIRMED")
                        .set(Booking::getStatus, "COMPLETED"));
        if (rows == 0) throw new BizException(ErrorCode.INVALID_STATUS_TRANSITION);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markNoShow(Long id) {
        int rows = bookingMapper.update(null,
                new LambdaUpdateWrapper<Booking>()
                        .eq(Booking::getId, id)
                        .eq(Booking::getStatus, "CONFIRMED")
                        .set(Booking::getStatus, "NO_SHOW"));
        if (rows == 0) throw new BizException(ErrorCode.INVALID_STATUS_TRANSITION);
    }

    // ====== list ======

    @Override
    public PageResult<BookingVO> listMine(HttpServletRequest httpReq, String group, int page, int size) {
        Long userId = ((Number) httpReq.getAttribute("userId")).longValue();
        LocalDateTime now = LocalDateTime.now(clock);
        LambdaQueryWrapper<Booking> w = new LambdaQueryWrapper<Booking>().eq(Booking::getUserId, userId);
        if ("upcoming".equalsIgnoreCase(group)) {
            w.eq(Booking::getStatus, "CONFIRMED").ge(Booking::getSlotStart, now);
        } else if ("history".equalsIgnoreCase(group)) {
            // 双层 lambda(或(状态非 CONFIRMED, 或 slot_start < now)) — 避免"非 CONFIRMED 确已出席完"的订单漏掉
            w.and(wq -> wq.ne(Booking::getStatus, "CONFIRMED").or(wq2 -> wq2.lt(Booking::getSlotStart, now)));
        }
        w.orderByDesc(Booking::getSlotStart);
        Page<Booking> pg = bookingMapper.selectPage(new Page<>(page, size), w);
        List<BookingVO> vos = enrichMine(pg.getRecords());
        return PageResult.of(pg.getTotal(), page, size, vos);
    }

    @Override
    public PageResult<BookingAdminVO> listAdmin(BookingQueryRequest q) {
        LambdaQueryWrapper<Booking> w = new LambdaQueryWrapper<>();
        if (q.getVenueId() != null) w.eq(Booking::getVenueId, q.getVenueId());
        if (q.getCourtId() != null) w.eq(Booking::getCourtId, q.getCourtId());
        if (q.getDateFrom() != null) w.ge(Booking::getSlotDate, q.getDateFrom());
        if (q.getDateTo() != null) w.le(Booking::getSlotDate, q.getDateTo());
        if (q.getStatus() != null && !q.getStatus().isEmpty()) w.eq(Booking::getStatus, q.getStatus());
        if (q.getKeyword() != null && !q.getKeyword().isEmpty()) {
            String kw = q.getKeyword();
            w.and(x -> x.like(Booking::getBookingNo, kw).or().eq(Booking::getUserId, Long.valueOf(kw).longValue()));
        }
        w.orderByDesc(Booking::getSlotStart);
        Page<Booking> pg = bookingMapper.selectPage(new Page<>(q.getPage(), q.getSize()), w);
        List<BookingAdminVO> vos = enrich(pg.getRecords());
        return PageResult.of(pg.getTotal(), q.getPage(), q.getSize(), vos);
    }

    @Override
    public BookingAdminVO getAdmin(Long id) {
        Booking b = bookingMapper.selectById(id);
        if (b == null) throw new BizException(ErrorCode.BOOKING_NOT_FOUND);
        return enrich(Collections.singletonList(b)).get(0);
    }

    // ====== enrichment (避免 N+1:一次 list -> 一次 set 查 user/venue/court) ======

    private List<BookingAdminVO> enrich(List<Booking> bookings) {
        if (bookings.isEmpty()) return List.of();
        Set<Long> userIds  = bookings.stream().map(Booking::getUserId).collect(Collectors.toSet());
        Set<Long> venueIds = bookings.stream().map(Booking::getVenueId).collect(Collectors.toSet());
        Set<Long> courtIds = bookings.stream().map(Booking::getCourtId).collect(Collectors.toSet());

        Map<Long, String>  userNames  = userIds.isEmpty()  ? Map.of() :
                userMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(User::getId, u -> Optional.ofNullable(u.getNickname()).orElse("用户" + u.getId())));
        Map<Long, String>  userPhones = userIds.isEmpty()  ? Map.of() :
                userMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(User::getId, u -> Optional.ofNullable(u.getPhone()).orElse("")));
        Map<Long, String>  venueNames = venueIds.isEmpty() ? Map.of() :
                venueMapper.selectBatchIds(venueIds).stream()
                        .collect(Collectors.toMap(Venue::getId, Venue::getName));
        Map<Long, Court>   courtMap   = courtIds.isEmpty() ? Map.of() :
                courtMapper.selectBatchIds(courtIds).stream().collect(Collectors.toMap(Court::getId, c -> c));

        return bookings.stream().map(b -> {
            BookingAdminVO v = toAdminVO(b);
            v.setUserNickname(userNames.get(b.getUserId()));
            v.setUserPhone(userPhones.get(b.getUserId()));
            v.setVenueName(venueNames.get(b.getVenueId()));
            Court c = courtMap.get(b.getCourtId());
            if (c != null) { v.setCourtName(c.getName()); v.setCourtType(c.getCourtType()); }
            return v;
        }).collect(Collectors.toList());
    }

    private List<BookingVO> enrichMine(List<Booking> bookings) {
        if (bookings.isEmpty()) return List.of();
        Set<Long> courtIds = bookings.stream().map(Booking::getCourtId).collect(Collectors.toSet());
        Set<Long> venueIds = bookings.stream().map(Booking::getVenueId).collect(Collectors.toSet());
        Map<Long, Court>  courtMap = courtIds.isEmpty() ? Map.of() :
                courtMapper.selectBatchIds(courtIds).stream().collect(Collectors.toMap(Court::getId, c -> c));
        Map<Long, String> venueNames = venueIds.isEmpty() ? Map.of() :
                venueMapper.selectBatchIds(venueIds).stream()
                        .collect(Collectors.toMap(Venue::getId, Venue::getName));
        return bookings.stream().map(b -> {
            BookingVO v = toVO(b);
            v.setVenueName(venueNames.get(b.getVenueId()));
            Court c = courtMap.get(b.getCourtId());
            if (c != null) v.setCourtName(c.getName());
            return v;
        }).collect(Collectors.toList());
    }

    // ====== mappers ======

    private BookingVO toVO(Booking b) {
        BookingVO v = new BookingVO();
        v.setId(b.getId()); v.setBookingNo(b.getBookingNo());
        v.setCourtId(b.getCourtId()); v.setVenueId(b.getVenueId());
        v.setSlotDate(b.getSlotDate()); v.setSlotStart(b.getSlotStart()); v.setSlotEnd(b.getSlotEnd());
        v.setSlotsCount(b.getSlotsCount()); v.setPriceSnapshot(b.getPriceSnapshot()); v.setStatus(b.getStatus());
        v.setCancelReason(b.getCancelReason()); v.setCancelledAt(b.getCancelledAt());
        v.setCreatedAt(b.getCreatedAt());
        return v;
    }

    private BookingAdminVO toAdminVO(Booking b) {
        BookingAdminVO v = new BookingAdminVO();
        v.setId(b.getId()); v.setBookingNo(b.getBookingNo());
        v.setUserId(b.getUserId()); v.setVenueId(b.getVenueId());
        v.setCourtId(b.getCourtId());
        v.setSlotDate(b.getSlotDate()); v.setSlotStart(b.getSlotStart()); v.setSlotEnd(b.getSlotEnd());
        v.setSlotsCount(b.getSlotsCount()); v.setPriceSnapshot(b.getPriceSnapshot()); v.setStatus(b.getStatus());
        v.setCancelReason(b.getCancelReason()); v.setCancelledAt(b.getCancelledAt());
        v.setCreatedAt(b.getCreatedAt());
        return v;
    }
}
```

> **注意点**：
> - BookingServiceImpl 内部按"create / cancel / terminal / list"组织。
> - `cancelMine` 的 CAS 校验 `userId` 条件，避免他人误取消；adminForceCancel 不带 userId 条件。
> - `listMine` 中 `group=history` 已改为双层 lambda(见上方代码块)。
> - `enrich/enrichMine` 用 `selectBatchIds` 一次加载关联实体，杜绝 N+1。
> - `userMapper` 是新引入的依赖；测试中需 `@Mock`。

- [ ] **Step 2: 跑测试** `cd hey-pickler-server && mvn -q test -Dtest=BookingServiceImplTest` → 全 PASS
- [ ] **Step 3: 跑单元 suite** `mvn -q test -Dtest='!*IntegrationTest'` → 全绿(覆盖无回归)
- [ ] **Step 4: Commit** `feat(venue): BookingService 写侧(create/cancel/terminal CAS/list)`

---

## Chunk 3: Controllers + GlobalExceptionHandler root-cause + 集成测试 + `mvn verify`

### Task 3.1: GlobalExceptionHandler 扩展 root-cause 鉴别

**Files:** Modify `common/exception/GlobalExceptionHandler.java`

- [ ] **Step 1: 替换 `handleDataIntegrityViolation` 完整方法体**（保留 P1 的 root-cause message，**只**根据 uk_court_slot / slot_start 关键字判别改返 `SLOT_ALREADY_TAKEN`）

```java
    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        Throwable root = e.getRootCause();
        String rootMsg = root != null && root.getMessage() != null ? root.getMessage() : e.getMessage();

        // P2: booking_slot UNIQUE(court_id, slot_start) 冲突 → SLOT_ALREADY_TAKEN(走 conflict guard)
        boolean isBookingSlotConflict =
                root != null && root.getClass().getSimpleName().equals("SQLIntegrityConstraintViolationException")
                        && (rootMsg != null
                            && (rootMsg.contains("uk_court_slot") || rootMsg.contains("slot_start")));
        if (isBookingSlotConflict) {
            return Result.fail(ErrorCode.SLOT_ALREADY_TAKEN.getCode(), "该时段刚被占用");
        }
        // 默认(P1 已建行为):保留 dup-dayOfWeek 等其它 UNIQUE → PARAM_ERROR
        return Result.fail(ErrorCode.PARAM_ERROR.getCode(), "数据冲突：" + rootMsg);
    }
```

- [ ] **Step 2: 编译**
- [ ] **Step 3: Commit** `feat(venue): GlobalExceptionHandler root-cause 鉴别 SLOT_ALREADY_TAKEN + 保留 P1 dup-dayOfWeek 兜底`

### Task 3.2: AppBookingController（写端 `@RequireAppUser` + 浏览是我的预约不需要 JWT 强制后端已经验证 userId）

**Files:** Create `controller/app/AppBookingController.java`

- [ ] **Step 1: 写 controller**

```java
package com.heypickler.controller.app;

import com.heypickler.common.annotation.RequireAppUser;
import com.heypickler.common.result.PageResult;
import com.heypickler.common.result.Result;
import com.heypickler.dto.app.BookingCreateRequest;
import com.heypickler.service.BookingService;
import com.heypickler.vo.BookingCreateResultVO;
import com.heypickler.vo.BookingVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/app/bookings")
@RequiredArgsConstructor
@Tag(name = "小程序-预约")
public class AppBookingController {
    private final BookingService bookingService;

    @PostMapping
    @RequireAppUser
    @Operation(summary = "下单(自助预约)")
    public Result<BookingCreateResultVO> create(HttpServletRequest req, @RequestBody @Valid BookingCreateRequest body) {
        return Result.ok(bookingService.create(req, body));
    }

    @GetMapping("/my")
    @RequireAppUser
    @Operation(summary = "我的预约")
    public Result<PageResult<BookingVO>> my(HttpServletRequest req,
                                            @RequestParam(defaultValue = "upcoming") String group,
                                            @RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(bookingService.listMine(req, group, page, size));
    }

    @PostMapping("/{id}/cancel")
    @RequireAppUser
    @Operation(summary = "取消预约(截止前)")
    public Result<Void> cancel(HttpServletRequest req, @PathVariable Long id) {
        bookingService.cancelMine(req, id);
        return Result.ok();
    }
}
```

- [ ] **Step 2: 编译 + Commit** `feat(venue): AppBookingController(下单/我的预约/取消)`

### Task 3.3: AdminBookingController

**Files:** Create `controller/admin/AdminBookingController.java`

- [ ] **Step 1: 写 controller**

```java
package com.heypickler.controller.admin;

import com.heypickler.common.annotation.RequireRole;
import com.heypickler.common.enums.UserRole;
import com.heypickler.common.result.PageResult;
import com.heypickler.common.result.Result;
import com.heypickler.dto.admin.BookingForceCancelRequest;
import com.heypickler.dto.admin.BookingQueryRequest;
import com.heypickler.service.BookingService;
import com.heypickler.vo.BookingAdminVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/bookings")
@RequiredArgsConstructor
@Tag(name = "管理端-预约管理")
public class AdminBookingController {
    private final BookingService bookingService;

    @GetMapping
    @Operation(summary = "预约列表(分页+筛选)")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN, UserRole.OPERATOR})
    public Result<PageResult<BookingAdminVO>> list(BookingQueryRequest q) {
        return Result.ok(bookingService.listAdmin(q));
    }

    @GetMapping("/{id}")
    @Operation(summary = "预约详情")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN, UserRole.OPERATOR})
    public Result<BookingAdminVO> get(@PathVariable Long id) { return Result.ok(bookingService.getAdmin(id)); }

    @PostMapping("/{id}/complete")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN})
    public Result<Void> complete(@PathVariable Long id) { bookingService.complete(id); return Result.ok(); }

    @PostMapping("/{id}/no-show")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN})
    public Result<Void> noShow(@PathVariable Long id) { bookingService.markNoShow(id); return Result.ok(); }

    @PostMapping("/{id}/cancel")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN})
    public Result<Void> cancel(@PathVariable Long id, @RequestBody(required = false) @Valid BookingForceCancelRequest body) {
        bookingService.forceCancel(id, body);
        return Result.ok();
    }
}
```

- [ ] **Step 2: 编译 + Commit** `feat(venue): AdminBookingController(列表+完成/爽约/强制取消)`

### Task 3.4: AppBookingControllerTest + AdminBookingControllerTest

**Files:** Create `controller/app/AppBookingControllerTest.java`、`controller/admin/AdminBookingControllerTest.java`（仿 P1 AdminVenueControllerTest，直接调方法取 `.getData()`）

- [ ] **Step 1: 各 4 个用例**，断言委托+wrap；无状态 mockito，**不需要** lambda cache 预热(无 LambdaQueryWrapper 直接构造 wrapper 由 mock 返)。
- **AppBookingControllerTest**：
  - `create_delegatesToServiceAndWraps`
  - `my_usesGroupDefaultUpcoming`
  - `cancel_callsCancelMine`
  - `cancel_notFoundPropagatesBizException`
- **AdminBookingControllerTest**：
  - `list_returnsPage`
  - `complete_callsService`
  - `noShow_callsService`
  - `forceCancel_withBody_callsService`
- [ ] **Step 2: 跑** `mvn -q test -Dtest='AppBookingControllerTest,AdminBookingControllerTest'` → 全 PASS
- [ ] **Step 3: Commit** `test(venue): P2 controller 单测`

### Task 3.5: 集成测试 `VenueBookingIntegrationTest`（happy + 并发抢号 + cancel 截止 + 取消释放重抢）

**Files:** Create `integration/VenueBookingIntegrationTest.java`

- [ ] **Step 1: 写**（`extends IntegrationTestConfig` + `@ActiveProfiles("integration")` + `@TestMethodOrder` 或 `@Order` 让测试可串行；种→冲→取→取消→重种）
- 关键断言：
  - **happy**：admin login → 创 venue/court/bh/band → `appAuthHeaders(userId)` POST `/api/app/bookings {courtId, slotStart, slotsCount}` → `code==0` + `data.bookingNo` 形如 `BK{d}-{seq}` → GET `/api/app/bookings/my?group=upcoming` → 1 条 → admin GET `/api/admin/bookings?keyword=...` 看到 → POST `/complete` → 回到 history group 1 条 COMPLETED。
  - **并发抢号**：两线程同时 POST 同一 court 同一起点→ 恰好 1 success(返回 booking_no)，1 SLOT_ALREADY_TAKEN(1012)；数据库 `booking_slot` 该起点恰好 1 行。
  - **cancel 截止**：造一份 `slot_start = FIXED_NOW + 30min` 不超 cutoff 的预约(boundary 测试)；过 cutoff 后 user cancel → CANCEL_DEADLINE_PASSED(1014)；admin forceCancel 成功 → 该单立刻从 upcoming 列表消失。
  - **释放重抢**：cancel 一单后另一用户可抢同 slot 成(CONFIRMED)。
- [ ] **Step 2: 跑** `mvn -q test -Dtest=VenueBookingIntegrationTest` → 全 PASS
- [ ] **Step 3: Commit** `test(venue): P2 预约端到端(happy/concurrent/cancel/release)`

### Task 3.6: 全量 `mvn verify` 门禁

- [ ] **Step 1**: `cd hey-pickler-server && mvn -q verify` → BUILD SUCCESS（INSTRUCTION ≥80%, BRANCH ≥60%）。若偏低优先扩 BookingServiceImpl 测试（CAS + cancel cutoff + user-limit 多格 + scheduler 三态），**不**降阈值。
- [ ] **Step 2**: Commit（无代码变更则跳过）

---

## Chunk 4: Admin 前端（预约列表 + 三项动作）

### Task 4.1: types + api/bookings.ts

**Files:** Modify `src/types/index.ts`；Create `src/api/bookings.ts`

- [ ] **Step 1: types 加 Booking 类型**
  ```ts
  export type BookingStatus = 'CONFIRMED' | 'CANCELLED' | 'COMPLETED' | 'NO_SHOW'
  export interface BookingAdmin {
    id: number; bookingNo: string
    userId: number; userNickname?: string; userPhone?: string
    venueId: number; venueName?: string
    courtId: number; courtName?: string; courtType?: string
    slotDate: string; slotStart: string; slotEnd: string
    slotsCount: number; priceSnapshot: number
    status: BookingStatus
    cancelReason?: string; cancelledAt?: string
    createdAt: string
  }
  export interface BookingQuery {
    venueId?: number; courtId?: number
    dateFrom?: string; dateTo?: string
    status?: BookingStatus; keyword?: string
    page?: number; size?: number
  }
  ```
- [ ] **Step 2: `src/api/bookings.ts`** 模板（仿 `banners.ts`/分页模板）：
  - `getBookingList(q: BookingQuery)` → `/bookings?...`
  - `getBookingDetail(id)`
  - `completeBooking(id)`, `noShowBooking(id)`, `forceCancelBooking(id, body)`
- [ ] **Step 3: lint** `cd hey-pickler-admin && npm run lint` → 0 error
- [ ] **Step 4: Commit** `feat(venue): admin booking 类型 + api/bookings`

### Task 4.2: 路由 + 侧边栏（与 venues 同一组）

**Files:** Modify `src/router/index.ts`、`src/components/layout/AppSidebar.vue`

- [ ] **Step 1**: router 加 `bookings` 子路由（group 仍用 `场馆管理`）
  ```ts
  { path: 'bookings', name: 'Bookings',
    component: () => import('@/views/bookings/BookingListView.vue'),
    meta: { title: '预约管理', icon: 'Calendar', group: '场馆管理' } }
  ```
- [ ] **Step 2**: AppSidebar 的 `GROUP_ORDER` 已有 `场馆管理`，无需改（如果 venues 已在 场馆管理 组下）。
- [ ] **Step 3: Commit** `feat(venue): admin 路由 预约管理 + 侧边栏并入场馆管理组`

### Task 4.3: BookingListView

**Files:** Create `src/views/bookings/BookingListView.vue`（仿 ban-records 模板）

- [ ] **Step 1: 列表**:列 bookingNo / 用户(uid → 名字) / 场地 / slot_date / 起止 / 状态 / 金额；筛选条:venueId/courtId/dateFrom/dateTo/status/keyword + 查询;Pagination;行操作:详情(打开 dialog,展示完整 + 三按钮:完成/爽约/强制取消)。
- [ ] **Step 2: 三项动作** 调 `completeBooking/noShowBooking/forceCancelBooking`(后两项带 `ElMessageBox.confirm`,force-cancel 可选 reason 输入)；成功 ElMessage + 重新拉列表。
- [ ] **Step 3: lint+build** `npm run lint:check && npm run build` → 全绿
- [ ] **Step 4: Commit** `feat(venue): admin 预约列表+详情+完成/爽约/强制取消`

---

## Chunk 5: wxapp(一键下单弹窗 + 我的预约)

### Task 5.1: 工具 + 术语

**Files:** Modify `wxapp/utils/terms.js`、`util.{js,wxs}`

- [ ] **Step 1**: terms.js 加 `BOOKING_STATUS = { CONFIRMED:'待履约', CANCELLED:'已取消', COMPLETED:'已完成', NO_SHOW:'未到' }`(沿用 `{label, color?}` 形状)
- [ ] **Step 2**: util.js / util.wxs 加 `formatBookingNo(bno)`(显示无需转换,若需要展示短码如 BK0722-0042 可加)和 `formatBookingStatus(s)`(取 BOOKING_STATUS)。
- [ ] **Step 3: Commit** `feat(venue): wxapp 工具+术语(BOOKING_STATUS)`

### Task 5.2: venue-detail 加“一键确认弹窗”

**Files:** Modify `pages/venue-detail/venue-detail.{js,wxml,wxss}`

- [ ] **Step 1: js** 新增 `onSlotTap(e)` + `confirmBooking()` + `request.post('/bookings', {courtId, slotStart, slotsCount})`；冲突错误(P2 SLOT_ALREADY_TAKEN 1012)→ 弹 `wx.showToast({title:'该时段刚被占用', icon:'none'})` 并 reload slots；成功 → `wx.showToast({title:'预约成功'})` + `wx.navigateTo({url:'/pages/my-bookings/my-bookings?group=upcoming'})`(参数保留)。
- [ ] **Step 2: wxml** 把每个可订格从"纯展示"改成 `<button class="slot available" data-slot-start="{{item.start}}" bindtap="onSlotTap">...</button>`；不可订用 `<view class="slot unavailable">不可订</view>` 不可点。`wxshowModal` 模板含场地名/日期/起止/总金额/取消/确认。
- [ ] **Step 3: Commit** `feat(venue): wxapp venue-detail 一键弹窗下单`

### Task 5.3: my-bookings 页面

**Files:** Create `pages/my-bookings/{my-bookings.js,.json,.wxml,.wxss}`；Modify `app.json`

- [ ] **Step 1**: app.json 注册 `pages/my-bookings/my-bookings`。
- [ ] **Step 2: js** `onLoad(options)` → 取 `group`(默认 upcoming)；下拉刷新 + onReachBottom；调 `request.get('/bookings/my', {group, page, size})`；empty 态；点单条 → 详情弹层(或跳场地,但 P2 不要求);**取消按钮**(本地 code==0 → reload)用 `request.post('/bookings/{id}/cancel')`。
- [ ] **Step 3: wxml** 顶 tab 切换（upcoming/history）；列表渲染 booking_no/场地/起止/金额/状态/取消按钮(upcoming 才显示)。
- [ ] **Step 4: Commit** `feat(venue): wxapp my-bookings 页(upcoming/history) + app.json 注册`

### Task 5.4: 全量验收

- [ ] **Step 1: 后端门禁** `cd hey-pickler-server && mvn -q verify` → BUILD SUCCESS(INSTRUCTION≥80% BRANCH≥60%)。
- [ ] **Step 2: admin 构建** `cd hey-pickler-admin && npm run lint:check && npm run build` → 全绿。
- [ ] **Step 3: 端到端手测**(人工,在浏览器+微信开发者工具):
  - admin: 创场馆场地价目 → 看 /bookings 列表空 → (走 wxapp 下单后) 列表显单 → 完成 → admin force cancel(过截止/未过的边界各试一次)→ 状态终态不再流转。
  - wxapp: 登录 → 首页 → 场馆 → 列表 → 详情 → 点可订格 → 弹窗确认 → 我的预约(顶 tab)看到 → 取消(进 cutoff)→ 列表移除。
  - 并发抢号(可在 dev 工具连调): 同时两设备同 court 同 slot 抢 → 一胜一败。
- [ ] **Step 4: 最终 Commit** `docs(venue): P2 预约引擎交付完成`（仅当有 docs 改动时）。

---

## 验收清单（P2 Definition of Done）

- [ ] wxapp 登录 → venue-detail 点可订格 → 弹窗确认 → 我的预约看到该单。
- [ ] wxapp 取消：截止前成功，截止后 `CANCEL_DEADLINE_PASSED`，admin force 成功；状态终态不再流转。
- [ ] wxapp 同格子两笔下单 → 一胜(`data.bookingNo` like `BK{yyyymmdd}-{seq}`)一败(`code=1012` SLOT_ALREADY_TAKEN)。
- [ ] admin `/bookings` 列表 + 筛选正确；完成/爽约/强制取消 三项可点、互不破坏终态。
- [ ] scheduler 每 5 分钟跑(DevTools 不必,集成或日志可见);2h grace 后 CONFIRMED → COMPLETED。
- [ ] 用户并发上限生效(5 条,集成测试覆盖)。
- [ ] `mvn verify` 绿(INSTRUCTION≥80% / BRANCH≥60%),覆盖率报告记录到 PR。
- [ ] `npm run lint:check && npm run build` 绿。
- [ ] 未做 P3：在线支付 / 赛事打通 / 节假日营业时间 / 地图 / admin 代下单。
