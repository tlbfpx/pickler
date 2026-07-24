# 场馆与场地预约 · P1 实现计划

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 交付场地预约领域的 P1 基础层——运营方在 admin 配置场馆/场地/营业时间/联系方式/时段价目；wxapp 用户可浏览场馆并查看某场地某日的可预订格子与价格（不含下单）。

**Architecture:** 在 `com.heypickler` 下新增并行领域。终态 schema 用一个 Flyway V22 一次建全 7 张表（P2 的 booking/booking_slot 也建表；P1 仅读 booking_slot 来计算可用性）。核心算法拆成两个**纯类**——`SlotCalculator`（时段生成/定价/可用性）与 `PricingBandValidator`（定价带有效集非重叠校验）；`SlotService`/`CourtService` 作薄数据访问封装，便于像 `GameValidator` 那样无 mock 表驱动测试。复用 `Result<T>`、`@RequireRole`、`OperationLogAspect` 自动审计、MyBatis-Plus 全局软删。

**Tech Stack:** Spring Boot 3.2 / Java 17 / MyBatis-Plus / MySQL 8 / Flyway / Vue 3 + Element Plus + Pinia / 微信小程序原生。

**Spec:** `docs/superpowers/specs/2026-07-22-venue-booking-design.md`（评审通过 ✅）

**P1 不做（留 P2）：** booking 写入、预约状态机、用户并发上限、booking_no Redis 计数器、wxapp 下单/取消/我的预约、BookingStatusScheduler、`Booking` 实体与写侧 mapper。

---

## 约定速查（勘察自现有代码，严格遵守）

- 实体：`@Data @TableName("snake")`、`@TableId(IdType.AUTO)`、`@TableField(fill=INSERT|INSERT_UPDATE)` 时间戳、`@TableLogic private LocalDateTime deletedAt`（仅软删表）。状态字段一律 `String`；DTO 用 jakarta `@Pattern/@NotBlank/@NotNull/@Min/@Max` + 中文 `message`。
- Mapper：`@Mapper public interface XxxMapper extends BaseMapper<Xxx> {}` 空体。
- Service：接口 `service/`，impl `service/impl/` 用 `@Service @RequiredArgsConstructor implements XxxService`，**不用** `ServiceImpl<>`；`private final XxxMapper`；`LambdaQueryWrapper`；多行写 `@Transactional(rollbackFor = Exception.class)`；抛 `BizException(ErrorCode.X[, "msg"])`。
- Controller(admin)：`@RestController @RequestMapping("/api/admin/<plural>") @RequiredArgsConstructor @Tag(name="管理端-...")`；方法 `@Operation`+`@RequireRole`。
- Result：`Result.ok(data)` / `Result.ok()`；分页 `PageResult.of(total, page, size, list)`。
- ErrorCode：HTTP-ish(401/403/404/429/500) 给传输层，`1xxx` 给业务；新码追加在 `INTERNAL_ERROR` 之前，从 1008 起。
- 测试：**不用 MockMvc**。纯逻辑类 `private final Foo foo = new Foo()` 直接测；带 mapper 的 service 用 `@ExtendWith(MockitoExtension.class)` + `@MockitoSettings(strictness=LENIENT)` + `@InjectMocks`，**凡用到 `LambdaQueryWrapper<Entity>` 的实体必须在 `@BeforeAll` 用 `TableInfoHelper.initTableInfo` 预热 lambda cache**（否则 `can not find lambda cache`，见 `PointServiceImplTest`）。Controller 测试直接调方法取 `.getData()`。集成测试 `extends IntegrationTestConfig` + `@ActiveProfiles("integration")`，文件名以 `IntegrationTest.java` 结尾。覆盖率门禁在 `verify` phase：INSTRUCTION≥80% 且 BRANCH≥60%。

---

## 文件结构总览

**后端新建**（`hey-pickler-server/src/main/java/com/heypickler/`）
- `db/migration/V22__venue_court_booking.sql`（7 表终态）
- `entity/`：Venue, Court, VenueBusinessHour, VenueContact, CourtPricingBand, BookingSlot
- `mapper/`：VenueMapper, CourtMapper, VenueBusinessHourMapper, VenueContactMapper, CourtPricingBandMapper, BookingSlotMapper
- `common/enums/PricingDayType.java`
- `common/util/PricingBandValidator.java`、`common/util/SlotCalculator.java`（纯逻辑）
- `dto/admin/`：VenueCreateRequest, VenueQueryRequest, VenueBusinessHourRequest, VenueContactRequest, CourtCreateRequest, CourtPricingBandRequest, CourtPricingBandBatchRequest
- `dto/app/`：（P1 无写 DTO）
- `vo/`：VenueVO, VenueDetailVO, VenueContactVO, CourtVO, SlotVO
- `service/`：VenueService, CourtService, SlotService
- `service/impl/`：VenueServiceImpl, CourtServiceImpl, SlotServiceImpl
- `controller/admin/`：AdminVenueController, AdminCourtController
- `controller/app/`：AppVenueController, AppCourtController

**后端修改**
- `common/exception/ErrorCode.java`（+4 码）
- `common/util/OperationLogClassifier.java`（MODULE_MAP/SINGULAR_MAP +venues/courts）
- `filter/AppAuthFilter.java`（PUBLIC_GET_PREFIXES +`/api/app/venues`、`/api/app/courts`）

**后端测试新建**（`src/test/java/com/heypickler/`）
- `common/util/PricingBandValidatorTest.java`、`SlotCalculatorTest.java`（纯）
- `service/impl/CourtServiceImplTest.java`、`SlotServiceImplTest.java`
- `controller/admin/AdminVenueControllerTest.java`、`controller/app/AppCourtControllerTest.java`
- `integration/VenueBrowseIntegrationTest.java`

**Admin 前端**（`hey-pickler-admin/`）
- 新建 `src/api/venues.ts`、`src/views/venues/{VenueListView,VenueFormView}.vue`、`src/views/venues/components/{BusinessHoursEditor,ContactsEditor,CourtsEditor,PricingBandsEditor}.vue`
- 修改 `src/types/index.ts`（+Venue 类型）、`src/router/index.ts`（+路由）、`src/components/layout/AppSidebar.vue`（GROUP_ORDER +「场馆管理」）

**wxapp**（`hey-pickler-wxapp/`）
- 新建 `components/court-card/`、`pages/venue-list/`、`pages/venue-detail/`
- 修改 `app.json`（pages 注册）、`utils/util.js`+`util.wxs`（+formatPrice/formatSlotTime）、`utils/terms.js`（+COURT_TYPE）、`pages/index/index.{wxml,js}`（+场馆入口）

---

## Chunk 1: 后端基础（迁移 + 实体 + Mapper + ErrorCode + 枚举）

### Task 1.1: Flyway V22 终态建表

**Files:**
- Create: `hey-pickler-server/src/main/resources/db/migration/V22__venue_court_booking.sql`

- [ ] **Step 1: 写迁移（7 张表，终态 schema）**

```sql
-- V22: 场馆与场地预约领域（P1 基础层 + P2 预约引擎终态建表）
-- P1 使用前 5 张表 + booking_slot（读）；booking 写侧在 P2 启用。

CREATE TABLE venue (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  name            VARCHAR(128) NOT NULL COMMENT '场馆名',
  address         VARCHAR(256) NOT NULL COMMENT '地址',
  latitude        DECIMAL(10,7) NULL COMMENT '纬度(可选,地图预留)',
  longitude       DECIMAL(10,7) NULL COMMENT '经度(可选,地图预留)',
  cover_url       VARCHAR(512) NULL COMMENT '封面图',
  description     VARCHAR(1024) NULL COMMENT '描述',
  status          VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/INACTIVE',
  booking_lead_days INT NOT NULL DEFAULT 14 COMMENT '可订窗口(天)',
  created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted_at      DATETIME NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='场馆';

CREATE TABLE court (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  venue_id    BIGINT UNSIGNED NOT NULL COMMENT '所属场馆',
  name        VARCHAR(64) NOT NULL COMMENT '场地名(如 1号场)',
  court_type  VARCHAR(16) NOT NULL DEFAULT 'INDOOR' COMMENT 'INDOOR/OUTDOOR',
  slot_minutes INT NOT NULL DEFAULT 60 COMMENT '单格时长(分钟)',
  status      VARCHAR(16) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/CLOSED/MAINTENANCE',
  sort_order  INT NOT NULL DEFAULT 0,
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted_at  DATETIME NULL,
  PRIMARY KEY (id),
  -- 软删安全唯一键(spec §5.7):STORED 生成列,deleted_at IS NULL 时=name,软删后 NULL 不占位(MySQL 多 NULL 放行)
  -- 注:这是"STORED 生成列 + 列唯一键",与 V17 的"函数表达式索引"是不同机制,勿混淆
  name_key VARCHAR(64) AS (CASE WHEN deleted_at IS NULL THEN name END) STORED,
  UNIQUE KEY uk_venue_court_name (venue_id, name_key),
  KEY idx_court_venue (venue_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='场地';

CREATE TABLE venue_business_hour (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  venue_id    BIGINT UNSIGNED NOT NULL,
  day_of_week TINYINT NOT NULL COMMENT '0=周日..6=周六',
  open_time   TIME NULL COMMENT 'NULL=当日休',
  close_time  TIME NULL,
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_venue_dow (venue_id, day_of_week)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='场馆营业时间(每周7行,整行覆盖,无软删)';

CREATE TABLE venue_contact (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  venue_id   BIGINT UNSIGNED NOT NULL,
  type       VARCHAR(16) NOT NULL COMMENT 'PHONE/WECHAT/LANDLINE/EMAIL',
  value      VARCHAR(128) NOT NULL,
  label      VARCHAR(64) NULL COMMENT '如 前台',
  sort_order INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted_at DATETIME NULL,
  PRIMARY KEY (id),
  KEY idx_contact_venue (venue_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='场馆联系方式';

CREATE TABLE court_pricing_band (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  court_id    BIGINT UNSIGNED NOT NULL,
  day_type    VARCHAR(8) NOT NULL COMMENT 'WEEKDAY/WEEKEND/ALL',
  start_time  TIME NOT NULL,
  end_time    TIME NOT NULL,
  price       DECIMAL(10,2) NOT NULL,
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted_at  DATETIME NULL,
  PRIMARY KEY (id),
  KEY idx_band_court (court_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='场地时段定价带(同court同day_type禁止重叠,app层校验)';

-- ===== P2 预约引擎(P1 只读 booking_slot 算可用性) =====
CREATE TABLE booking (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  booking_no     VARCHAR(32) NOT NULL COMMENT 'BK{yyyyMMdd}-{seq}',
  user_id        BIGINT UNSIGNED NOT NULL,
  venue_id       BIGINT UNSIGNED NOT NULL,
  court_id       BIGINT UNSIGNED NOT NULL,
  slot_date      DATE NOT NULL,
  slot_start     DATETIME NOT NULL,
  slot_end       DATETIME NOT NULL,
  slots_count    INT NOT NULL,
  price_snapshot DECIMAL(10,2) NOT NULL,
  status         VARCHAR(16) NOT NULL COMMENT 'CONFIRMED/CANCELLED/COMPLETED/NO_SHOW',
  cancel_reason  VARCHAR(256) NULL,
  cancelled_at   DATETIME NULL,
  created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_booking_no (booking_no),
  KEY idx_booking_user (user_id, slot_start),
  KEY idx_booking_court (court_id, slot_start),
  KEY idx_booking_venue_date (venue_id, slot_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='预约主记录(append-only,无软删)';

CREATE TABLE booking_slot (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  booking_id BIGINT UNSIGNED NOT NULL,
  court_id   BIGINT UNSIGNED NOT NULL,
  slot_start DATETIME NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_court_slot (court_id, slot_start),
  KEY idx_slot_booking (booking_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='时段占用(取消即物理删除释放唯一键)';
```

- [ ] **Step 2: 启动验证迁移通过**

Run: `mvn -q -pl hey-pickler-server spring-boot:run`（或本地 dev-up.sh；Flyway 自动执行）
Expected: 启动无 Flyway 报错；`SHOW TABLES LIKE 'venue';` 等返回对应表。
**额外验证**：该项目首次在**实体字段**用 `LocalTime`↔MySQL `TIME`——插一条 `venue_business_hour(open_time='09:00:00')` 后查回应得 `LocalTime.of(9,0)`；若往返失败需配 MyBatis-Plus `LocalTimeTypeHandler`。`Ctrl+C` 停止。

- [ ] **Step 3: Commit**

```bash
git add hey-pickler-server/src/main/resources/db/migration/V22__venue_court_booking.sql
git commit -m "feat(venue): V22 终态建表(场馆/场地/营业时间/联系方式/定价带/预约)"
```

### Task 1.2: ErrorCode 扩展

**Files:** Modify `hey-pickler-server/src/main/java/com/heypickler/common/exception/ErrorCode.java`

- [ ] **Step 1: 在 `INSUFFICIENT_POINTS(1007,...)` 之后、`INTERNAL_ERROR` 之前追加 4 个码**

```java
    INSUFFICIENT_POINTS(1007, "积分不足"),
    VENUE_NOT_FOUND(1008, "场馆不存在"),
    COURT_NOT_FOUND(1009, "场地不存在"),
    COURT_NOT_AVAILABLE(1010, "场地不可预订"),
    SLOT_NOT_BOOKABLE(1011, "该时段不可预订"),
    INTERNAL_ERROR(500, "服务器内部错误");
```

- [ ] **Step 2: 编译**

Run: `mvn -q -pl hey-pickler-server compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit** `fix(venue): ErrorCode 新增 venue/court/slot 业务码`

### Task 1.3: PricingDayType 枚举

**Files:** Create `common/enums/PricingDayType.java`

- [ ] **Step 1: 写枚举**（仅这一个；venue/court 状态走 String+@Pattern，沿用 Banner 约定）

```java
package com.heypickler.common.enums;

/** 定价带适用日类型。SlotCalculator 用它判断有效集。 */
public enum PricingDayType {
    WEEKDAY,
    WEEKEND,
    ALL
}
```

- [ ] **Step 2: 编译 + Commit** `feat(venue): PricingDayType 枚举`

### Task 1.4: 6 个实体

**Files:** Create `entity/{Venue,Court,VenueBusinessHour,VenueContact,CourtPricingBand,BookingSlot}.java`

- [ ] **Step 1: 写 Venue**

```java
package com.heypickler.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("venue")
public class Venue {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String address;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String coverUrl;
    private String description;
    private String status;
    private Integer bookingLeadDays;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    @TableLogic
    private LocalDateTime deletedAt;
}
```

- [ ] **Step 2: 写 Court**（注意：DB 生成列 `name_key` **不映射**进实体）

```java
package com.heypickler.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("court")
public class Court {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long venueId;
    private String name;
    private String courtType;
    private Integer slotMinutes;
    private String status;
    private Integer sortOrder;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    @TableLogic
    private LocalDateTime deletedAt;
}
```

- [ ] **Step 3: 写 VenueBusinessHour**（`LocalTime` ↔ MySQL `TIME`；无软删——整行覆盖）

```java
package com.heypickler.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@TableName("venue_business_hour")
public class VenueBusinessHour {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long venueId;
    private Integer dayOfWeek;
    private LocalTime openTime;
    private LocalTime closeTime;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 4: 写 VenueContact**（标准软删实体）

```java
package com.heypickler.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("venue_contact")
public class VenueContact {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long venueId;
    private String type;
    private String value;
    private String label;
    private Integer sortOrder;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    @TableLogic
    private LocalDateTime deletedAt;
}
```

- [ ] **Step 5: 写 CourtPricingBand**

```java
package com.heypickler.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@TableName("court_pricing_band")
public class CourtPricingBand {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long courtId;
    private String dayType;
    private LocalTime startTime;
    private LocalTime endTime;
    private BigDecimal price;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    @TableLogic
    private LocalDateTime deletedAt;
}
```

- [ ] **Step 6: 写 BookingSlot**（P1 只读；无软删，物理删除释放唯一键）

```java
package com.heypickler.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("booking_slot")
public class BookingSlot {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long bookingId;
    private Long courtId;
    private LocalDateTime slotStart;
}
```

- [ ] **Step 7: 编译 + Commit** `feat(venue): 6 实体(venue/court/business-hour/contact/pricing-band/booking-slot)`

### Task 1.5: 6 个 Mapper

**Files:** Create `mapper/{Venue,Court,VenueBusinessHour,VenueContact,CourtPricingBand,BookingSlot}Mapper.java`

- [ ] **Step 1: 写 6 个空 Mapper**（统一模板，逐个建文件）

```java
package com.heypickler.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.heypickler.entity.Venue;
import org.apache.ibatis.annotations.Mapper;
@Mapper
public interface VenueMapper extends BaseMapper<Venue> {}
```
对 Court/VenueBusinessHour/VenueContact/CourtPricingBand/BookingSlot 各建一份，换类名。

- [ ] **Step 2: 编译 + Commit** `feat(venue): 6 Mapper`

---

## Chunk 2: 后端纯逻辑 + Service

### Task 2.1: PricingBandValidator（纯，TDD）

**Files:** Create `common/util/PricingBandValidator.java` + test `src/test/java/com/heypickler/common/util/PricingBandValidatorTest.java`

校验规则（spec §5.5）：band 半开 `[start,end)`；重叠 ⟺ `a.start < b.end && b.start < a.end`；按**有效集**校验——工作日集=`{WEEKDAY}∪{ALL}`、周末集=`{WEEKEND}∪{ALL}`，各自内部禁止重叠（即 ALL 不得与 WEEKDAY/WEEKEND 重叠）。

- [ ] **Step 1: 先写失败测试**（模型参考 `GameValidatorTest`）

```java
package com.heypickler.common.util;

import com.heypickler.common.exception.BizException;
import com.heypickler.entity.CourtPricingBand;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PricingBandValidatorTest {
    private final PricingBandValidator validator = new PricingBandValidator();

    private CourtPricingBand band(String dayType, String s, String e, int price) {
        CourtPricingBand b = new CourtPricingBand();
        b.setDayType(dayType);
        b.setStartTime(LocalTime.parse(s));
        b.setEndTime(LocalTime.parse(e));
        b.setPrice(new BigDecimal(price));
        return b;
    }

    @Test void validate_adjacentBandsTouching_ok() {
        // 半开区间: [09:00,12:00) 与 [12:00,15:00) 相邻不重叠
        assertDoesNotThrow(() -> validator.validate(List.of(
                band("WEEKDAY", "09:00", "12:00", 40),
                band("WEEKDAY", "12:00", "15:00", 60))));
    }

    @Test void validate_weekdayOverlap_throws() {
        assertThrows(BizException.class, () -> validator.validate(List.of(
                band("WEEKDAY", "09:00", "12:00", 40),
                band("WEEKDAY", "11:00", "14:00", 60))));
    }

    @Test void validate_allOverlapsWeekday_throws() {
        // ALL 与 WEEKDAY 同属工作日有效集,重叠必须拒
        assertThrows(BizException.class, () -> validator.validate(List.of(
                band("WEEKDAY", "09:00", "12:00", 40),
                band("ALL", "11:00", "14:00", 60))));
    }

    @Test void validate_weekdayAndWeekendSameTime_ok() {
        // 工作日带与周末带时间相同不冲突(分属不同有效集)
        assertDoesNotThrow(() -> validator.validate(List.of(
                band("WEEKDAY", "09:00", "12:00", 40),
                band("WEEKEND", "09:00", "12:00", 80))));
    }

    @Test void validate_gapAllowed_ok() {
        // 缺口允许:12:00-14:00 无 band,校验通过(运行期那两格不可订)
        assertDoesNotThrow(() -> validator.validate(List.of(
                band("WEEKDAY", "09:00", "12:00", 40),
                band("WEEKDAY", "14:00", "18:00", 60))));
    }

    @Test void validate_empty_ok() { assertDoesNotThrow(() -> validator.validate(List.of())); }
}
```

- [ ] **Step 2: 跑测试确认失败** — Run: `mvn -q -pl hey-pickler-server test -Dtest=PricingBandValidatorTest` → FAIL（类不存在）

- [ ] **Step 3: 写实现**

```java
package com.heypickler.common.util;

import com.heypickler.common.exception.BizException;
import com.heypickler.common.exception.ErrorCode;
import com.heypickler.entity.CourtPricingBand;
import java.util.List;

/** 定价带有效集非重叠校验(spec §5.5)。纯逻辑,无 Spring 依赖。 */
public class PricingBandValidator {

    public void validate(List<CourtPricingBand> bands) {
        if (bands == null || bands.isEmpty()) return;
        validateEffectiveSet(bands, "工作日", "WEEKDAY", "ALL");
        validateEffectiveSet(bands, "周末", "WEEKEND", "ALL");
    }

    private void validateEffectiveSet(List<CourtPricingBand> bands, String label, String... dayTypes) {
        List<CourtPricingBand> eff = bands.stream()
                .filter(b -> java.util.Arrays.asList(dayTypes).contains(b.getDayType()))
                .sorted(java.util.Comparator.comparing(CourtPricingBand::getStartTime))
                .toList();
        for (int i = 0; i < eff.size(); i++) {
            for (int j = i + 1; j < eff.size(); j++) {
                if (overlaps(eff.get(i), eff.get(j))) {
                    throw new BizException(ErrorCode.PARAM_ERROR,
                            label + "定价带时间段重叠：" + eff.get(i).getStartTime() + "-" + eff.get(i).getEndTime()
                                    + " 与 " + eff.get(j).getStartTime() + "-" + eff.get(j).getEndTime());
                }
            }
        }
    }

    /** 半开区间重叠:a.start < b.end && b.start < a.end */
    private boolean overlaps(CourtPricingBand a, CourtPricingBand b) {
        return a.getStartTime().isBefore(b.getEndTime()) && b.getStartTime().isBefore(a.getEndTime());
    }
}
```

- [ ] **Step 4: 跑测试确认通过** — Run 同上 → PASS（6 用例）

- [ ] **Step 5: Commit** `feat(venue): PricingBandValidator 纯逻辑 + 测试`

### Task 2.2: SlotCalculator（纯，TDD）

**Files:** Create `common/util/SlotCalculator.java` + test `src/test/java/com/heypickler/common/util/SlotCalculatorTest.java`

算法（spec §6.1）：锚定 `openTime`、半开 `[t, t+slot)`、末尾不足丢弃；早于 `now+30min` 跳过、达到/超过 `now+leadDays·24h` 停；价格查命中 band（半开区间，WEEKDAY/WEEKEND 优先 ALL）；可用 = 有 band 且未被占用。

- [ ] **Step 1: 先写失败测试**（用固定 `now` 与 `date` 锁死时间断言）

```java
package com.heypickler.common.util;

import com.heypickler.entity.CourtPricingBand;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class SlotCalculatorTest {
    private final SlotCalculator calc = new SlotCalculator();

    private CourtPricingBand band(String s, String e, int price) {
        CourtPricingBand b = new CourtPricingBand();
        b.setDayType("WEEKDAY");
        b.setStartTime(LocalTime.parse(s));
        b.setEndTime(LocalTime.parse(e));
        b.setPrice(new BigDecimal(price));
        return b;
    }

    private final LocalDateTime NOW = LocalDateTime.of(2026, 7, 22, 8, 0); // 周三 08:00
    private final LocalDate DATE = LocalDate.of(2026, 7, 22);

    @Test void closedDay_returnsEmpty() {
        // openTime=null 表示当日休
        assertTrue(calc.generate(null, null, 60, List.of(), Set.of(), DATE, NOW, 14).isEmpty());
    }

    @Test void happyPath_generatesHourlySlotsWithPrice() {
        // 09:00-11:00 营业,1h 格,价 40 → 09:00/10:00 两格
        var r = calc.generate(LocalTime.of(9, 0), LocalTime.of(11, 0), 60,
                List.of(band("09:00", "11:00", 40)), Set.of(), DATE, NOW, 14);
        assertEquals(2, r.size());
        assertEquals(LocalDateTime.of(2026, 7, 22, 9, 0), r.get(0).start());
        assertTrue(r.get(0).available());
        assertEquals(new BigDecimal("40"), r.get(0).price());
    }

    @Test void trailingPartialSlot_dropped() {
        // 09:00-10:30, 1h 格 → 仅 09:00 一格(10:00-11:00 越过 10:30 丢弃)
        var r = calc.generate(LocalTime.of(9, 0), LocalTime.of(10, 30), 60,
                List.of(band("09:00", "11:00", 40)), Set.of(), DATE, NOW, 14);
        assertEquals(1, r.size());
    }

    @Test void pastSlot_skipped() {
        // now=10:00+，09:00 格早于 now+30min 跳过
        LocalDateTime now = LocalDateTime.of(2026, 7, 22, 10, 0);
        var r = calc.generate(LocalTime.of(9, 0), LocalTime.of(11, 0), 60,
                List.of(band("09:00", "11:00", 40)), Set.of(), DATE, now, 14);
        // 09:00 < 10:30 跳过; 10:00 < 10:30 跳过 → 空
        assertTrue(r.isEmpty());
    }

    @Test void gapBand_slotUnbookableNoPrice() {
        // band 只覆盖 09:00-10:00,10:00-11:00 无 band → 不可订且无价
        var r = calc.generate(LocalTime.of(9, 0), LocalTime.of(11, 0), 60,
                List.of(band("09:00", "10:00", 40)), Set.of(), DATE, NOW, 14);
        assertTrue(r.get(0).available());
        assertFalse(r.get(1).available());
        assertNull(r.get(1).price());
    }

    @Test void occupiedSlot_unavailable() {
        var occ = Set.of(LocalDateTime.of(2026, 7, 22, 9, 0));
        var r = calc.generate(LocalTime.of(9, 0), LocalTime.of(11, 0), 60,
                List.of(band("09:00", "11:00", 40)), occ, DATE, NOW, 14);
        assertFalse(r.get(0).available()); // 09:00 被占
        assertTrue(r.get(1).available());
    }

    @Test void windowEnd_stopsGeneration() {
        // leadDays=0 → windowEnd=now,当日格子全部 >= windowEnd → 空
        var r = calc.generate(LocalTime.of(9, 0), LocalTime.of(11, 0), 60,
                List.of(band("09:00", "11:00", 40)), Set.of(), DATE, NOW, 0);
        assertTrue(r.isEmpty());
    }

    @Test void bandBoundary_routesToSecondBand() {
        // [09:00,10:00)=40, [10:00,11:00)=80:10:00 格落第二带
        var r = calc.generate(LocalTime.of(9, 0), LocalTime.of(11, 0), 60,
                List.of(band("09:00", "10:00", 40), band("10:00", "11:00", 80)), Set.of(), DATE, NOW, 14);
        assertEquals(new BigDecimal("40"), r.get(0).price());
        assertEquals(new BigDecimal("80"), r.get(1).price());
    }

    @Test void midnightWrap_terminatesNoInfiniteLoop() {
        // 22:00-23:00 营业,1h 格:22:00 格有效;下一格 23:00 → endT=00:00 回绕,应停而非产出越界格
        var r = calc.generate(LocalTime.of(22, 0), LocalTime.of(23, 0), 60,
                List.of(band("22:00", "23:00", 40)), Set.of(), DATE, NOW, 14);
        assertEquals(1, r.size()); // 无回绕守卫时会多产出一个 23:00 越界格 → 2,故此用例锁死守卫
    }
}
```

- [ ] **Step 2: 跑确认失败** — Run: `mvn -q -pl hey-pickler-server test -Dtest=SlotCalculatorTest` → FAIL

- [ ] **Step 3: 写实现**

```java
package com.heypickler.common.util;

import com.heypickler.entity.CourtPricingBand;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** 时段生成/定价/可用性纯算法(spec §6.1)。不查库,全部入参注入,便于表驱动测试。 */
public class SlotCalculator {

    public record SlotRange(LocalDateTime start, LocalDateTime end, boolean available, BigDecimal price) {}

    private static final Duration MIN_LEAD = Duration.ofMinutes(30);

    public List<SlotRange> generate(LocalTime openTime, LocalTime closeTime, int slotMinutes,
                                    List<CourtPricingBand> effBands, Set<LocalDateTime> occupied,
                                    LocalDate date, LocalDateTime now, int bookingLeadDays) {
        List<SlotRange> out = new ArrayList<>();
        if (openTime == null || closeTime == null || slotMinutes <= 0) return out;

        LocalDateTime earliestStart = now.plus(MIN_LEAD);
        LocalDateTime windowEnd = now.plusDays(bookingLeadDays);

        LocalTime t = openTime;
        while (true) {
            LocalTime endT = t.plusMinutes(slotMinutes);
            if (!endT.isAfter(t)) break;          // 跨午夜回绕(LocalTime 无日期)→ 停;v1 不支持跨夜营业
            if (endT.isAfter(closeTime)) break;   // 半开,末尾不足丢弃

            LocalDateTime slotStart = LocalDateTime.of(date, t);
            if (!slotStart.isBefore(earliestStart)) {         // >= now+30min
                if (!slotStart.isBefore(windowEnd)) break;    // 达到/超过 now+leadDays 停
                CourtPricingBand band = matchBand(effBands, t);
                boolean available = band != null && !occupied.contains(slotStart);
                out.add(new SlotRange(slotStart, slotStart.plusMinutes(slotMinutes),
                        available, band != null ? band.getPrice() : null));
            }
            t = endT;
        }
        return out;
    }

    /** 返回 t 落入半开 [start,end) 的唯一 band;有效集保存时已去重,至多 1 个命中。 */
    private CourtPricingBand matchBand(List<CourtPricingBand> effBands, LocalTime t) {
        CourtPricingBand hit = null;
        for (CourtPricingBand b : effBands) {
            if (!t.isBefore(b.getStartTime()) && t.isBefore(b.getEndTime())) {
                if (hit == null) hit = b;
                else if (!"ALL".equals(b.getDayType()) && "ALL".equals(hit.getDayType())) hit = b; // 防御:specific 优先 ALL
            }
        }
        return hit;
    }
}
```

- [ ] **Step 4: 跑确认通过** — Run 同上 → PASS（8 用例）

- [ ] **Step 5: Commit** `feat(venue): SlotCalculator 纯算法 + 测试`

### Task 2.3: DTO 与 VO

**Files:** Create under `dto/admin/` 与 `vo/`

- [ ] **Step 1: 写 admin DTO**

`dto/admin/VenueCreateRequest.java`:
```java
package com.heypickler.dto.admin;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class VenueCreateRequest {
    @NotBlank(message = "场馆名不能为空") @Size(max = 128, message = "场馆名过长")
    private String name;
    @NotBlank(message = "地址不能为空") @Size(max = 256, message = "地址过长")
    private String address;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String coverUrl;
    private String description;
    @Pattern(regexp = "^(ACTIVE|INACTIVE)$", message = "无效的状态") private String status;
    @Min(value = 1, message = "可订窗口至少 1 天") private Integer bookingLeadDays;
}
```

`dto/admin/VenueQueryRequest.java`（分页 + 搜索；仿 `UserQueryRequest`）:
```java
package com.heypickler.dto.admin;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class VenueQueryRequest {
    private String keyword;
    private String status;
    @Min(value = 1) private int page = 1;
    @Min(value = 1) @Max(value = 100) private int size = 10;
}
```

`dto/admin/VenueBusinessHourRequest.java`（整批 7 行覆盖）:
```java
package com.heypickler.dto.admin;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.time.LocalTime;
import java.util.List;

@Data
public class VenueBusinessHourRequest {
    @NotEmpty(message = "营业时间不能为空") @Size(max = 7, message = "至多 7 天")
    private List<Item> hours;

    @Data
    public static class Item {
        @Min(value = 0) @Max(value = 6) private Integer dayOfWeek; // 0=日..6=六
        private LocalTime openTime;  // null=当日休
        private LocalTime closeTime;
    }
}
```

`dto/admin/VenueContactRequest.java`:
```java
package com.heypickler.dto.admin;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class VenueContactRequest {
    @NotBlank @Pattern(regexp = "^(PHONE|WECHAT|LANDLINE|EMAIL)$") private String type;
    @NotBlank @Size(max = 128) private String value;
    @Size(max = 64) private String label;
    private Integer sortOrder;
}
```

`dto/admin/CourtCreateRequest.java`:
```java
package com.heypickler.dto.admin;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class CourtCreateRequest {
    @NotBlank @Size(max = 64) private String name;
    @Pattern(regexp = "^(INDOOR|OUTDOOR)$") private String courtType;
    @Min(value = 15) @Max(value = 240) private Integer slotMinutes; // 15min..4h
    @Pattern(regexp = "^(OPEN|CLOSED|MAINTENANCE)$") private String status;
    private Integer sortOrder;
    private Long venueId; // create 时必填;update 忽略
}
```

`dto/admin/CourtPricingBandRequest.java`（单 band）:
```java
package com.heypickler.dto.admin;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalTime;

@Data
public class CourtPricingBandRequest {
    @NotBlank @Pattern(regexp = "^(WEEKDAY|WEEKEND|ALL)$") private String dayType;
    @NotNull private LocalTime startTime;
    @NotNull private LocalTime endTime;
    @NotNull @DecimalMin(value = "0.0") private BigDecimal price;
}
```

`dto/admin/CourtPricingBandBatchRequest.java`（整批覆盖，带校验）:
```java
package com.heypickler.dto.admin;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import java.util.List;

@Data
public class CourtPricingBandBatchRequest {
    @NotEmpty(message = "定价带不能为空") @Valid
    private List<CourtPricingBandRequest> bands;
}
```

- [ ] **Step 2: 写 VO**

`vo/VenueContactVO.java`:
```java
package com.heypickler.vo;
import lombok.Data;
@Data
public class VenueContactVO {
    private Long id;
    private String type;
    private String value;
    private String label;
    private Integer sortOrder;
}
```

`vo/CourtVO.java`:
```java
package com.heypickler.vo;
import lombok.Data;
@Data
public class CourtVO {
    private Long id;
    private Long venueId;
    private String name;
    private String courtType;
    private Integer slotMinutes;
    private String status;
    private Integer sortOrder;
}
```

`vo/VenueVO.java`:
```java
package com.heypickler.vo;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

@Data
public class VenueVO {
    private Long id;
    private String name;
    private String address;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String coverUrl;
    private String description;
    private String status;
    private Integer bookingLeadDays;
    private List<VenueContactVO> contacts;
}
```

`vo/VenueDetailVO.java`（含营业时间 + 场地概要）:
```java
package com.heypickler.vo;
import lombok.Data;
import java.util.List;

@Data
public class VenueDetailVO extends VenueVO {
    private List<BusinessHourVO> businessHours;
    private List<CourtVO> courts;

    @Data
    public static class BusinessHourVO {
        private Integer dayOfWeek;
        private LocalTime openTime;
        private LocalTime closeTime;
    }
}
```

`vo/SlotVO.java`:
```java
package com.heypickler.vo;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class SlotVO {
    private LocalDateTime start;
    private LocalDateTime end;
    private boolean available;
    private BigDecimal price;
}
```

- [ ] **Step 3: 编译 + Commit** `feat(venue): admin DTO 与 VO`

### Task 2.4: VenueService（CRUD + 营业时间 + 联系方式）

**Files:** Create `service/VenueService.java`、`service/impl/VenueServiceImpl.java`

- [ ] **Step 1: 写接口**

```java
package com.heypickler.service;
import com.heypickler.common.result.PageResult;
import com.heypickler.dto.admin.*;
import com.heypickler.vo.VenueDetailVO;
import com.heypickler.vo.VenueVO;
import java.util.List;

public interface VenueService {
    PageResult<VenueVO> adminList(VenueQueryRequest req);
    VenueDetailVO adminGet(Long id);
    Long create(VenueCreateRequest req);
    void update(Long id, VenueCreateRequest req);
    void delete(Long id);
    void replaceBusinessHours(Long id, VenueBusinessHourRequest req);
    // contacts
    Long addContact(Long venueId, VenueContactRequest req);
    void updateContact(Long contactId, VenueContactRequest req);
    void deleteContact(Long contactId);
    // app
    PageResult<VenueVO> appList(VenueQueryRequest req);
    VenueDetailVO appGet(Long id);
}
```

- [ ] **Step 2: 写 impl**（仿 `BannerServiceImpl`；`@RequiredArgsConstructor`；`LambdaQueryWrapper`；`toVO` 手填；营业时间整批 `@Transactional` 先 delete-by-venueId 再 insert）

```java
package com.heypickler.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.heypickler.common.exception.BizException;
import com.heypickler.common.exception.ErrorCode;
import com.heypickler.common.result.PageResult;
import com.heypickler.dto.admin.*;
import com.heypickler.entity.*;
import com.heypickler.mapper.*;
import com.heypickler.service.VenueService;
import com.heypickler.vo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VenueServiceImpl implements VenueService {
    private final VenueMapper venueMapper;
    private final VenueBusinessHourMapper businessHourMapper;
    private final VenueContactMapper contactMapper;
    private final CourtMapper courtMapper;

    @Override
    public PageResult<VenueVO> adminList(VenueQueryRequest req) {
        LambdaQueryWrapper<Venue> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(req.getKeyword())) {
            w.and(x -> x.like(Venue::getName, req.getKeyword())
                    .or().like(Venue::getAddress, req.getKeyword()));
        }
        if (StringUtils.hasText(req.getStatus())) w.eq(Venue::getStatus, req.getStatus());
        w.orderByDesc(Venue::getCreatedAt);
        Page<Venue> p = venueMapper.selectPage(new Page<>(req.getPage(), req.getSize()), w);
        List<VenueVO> vos = p.getRecords().stream().map(this::toListVO).collect(Collectors.toList());
        return PageResult.of(p.getTotal(), req.getPage(), req.getSize(), vos);
    }

    @Override
    public VenueDetailVO adminGet(Long id) {
        Venue v = mustExist(id);
        return toDetailVO(v);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(VenueCreateRequest req) {
        Venue v = new Venue();
        applyCreate(v, req);
        if (v.getStatus() == null) v.setStatus("ACTIVE");
        if (v.getBookingLeadDays() == null) v.setBookingLeadDays(14);
        venueMapper.insert(v);
        return v.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, VenueCreateRequest req) {
        Venue v = mustExist(id);
        applyCreate(v, req);
        venueMapper.updateById(v);
    }

    @Override
    public void delete(Long id) {
        mustExist(id);
        venueMapper.deleteById(id); // @TableLogic 软删
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void replaceBusinessHours(Long id, VenueBusinessHourRequest req) {
        mustExist(id);
        businessHourMapper.delete(new LambdaQueryWrapper<VenueBusinessHour>().eq(VenueBusinessHour::getVenueId, id));
        for (VenueBusinessHourRequest.Item it : req.getHours()) {
            VenueBusinessHour bh = new VenueBusinessHour();
            bh.setVenueId(id);
            bh.setDayOfWeek(it.getDayOfWeek());
            bh.setOpenTime(it.getOpenTime());
            bh.setCloseTime(it.getCloseTime());
            businessHourMapper.insert(bh);
        }
    }

    @Override public Long addContact(Long venueId, VenueContactRequest req) {
        mustExist(venueId);
        VenueContact c = new VenueContact();
        c.setVenueId(venueId); c.setType(req.getType()); c.setValue(req.getValue());
        c.setLabel(req.getLabel()); c.setSortOrder(req.getSortOrder() == null ? 0 : req.getSortOrder());
        contactMapper.insert(c);
        return c.getId();
    }
    @Override public void updateContact(Long contactId, VenueContactRequest req) {
        VenueContact c = contactMapper.selectById(contactId);
        if (c == null) throw new BizException(ErrorCode.NOT_FOUND);
        c.setType(req.getType()); c.setValue(req.getValue()); c.setLabel(req.getLabel());
        c.setSortOrder(req.getSortOrder() == null ? 0 : req.getSortOrder());
        contactMapper.updateById(c);
    }
    @Override public void deleteContact(Long contactId) {
        if (contactMapper.selectById(contactId) == null) throw new BizException(ErrorCode.NOT_FOUND);
        contactMapper.deleteById(contactId);
    }

    @Override public PageResult<VenueVO> appList(VenueQueryRequest req) {
        LambdaQueryWrapper<Venue> w = new LambdaQueryWrapper<Venue>()
                .eq(Venue::getStatus, "ACTIVE");
        if (StringUtils.hasText(req.getKeyword())) {
            w.and(x -> x.like(Venue::getName, req.getKeyword()).or().like(Venue::getAddress, req.getKeyword()));
        }
        w.orderByDesc(Venue::getCreatedAt);
        Page<Venue> p = venueMapper.selectPage(new Page<>(req.getPage(), req.getSize()), w);
        List<VenueVO> vos = p.getRecords().stream().map(this::toListVO).collect(Collectors.toList());
        return PageResult.of(p.getTotal(), req.getPage(), req.getSize(), vos);
    }
    @Override public VenueDetailVO appGet(Long id) {
        Venue v = venueMapper.selectById(id);
        if (v == null) throw new BizException(ErrorCode.VENUE_NOT_FOUND);
        return toDetailVO(v);
    }

    // ---- helpers ----
    private Venue mustExist(Long id) {
        Venue v = venueMapper.selectById(id);
        if (v == null) throw new BizException(ErrorCode.VENUE_NOT_FOUND);
        return v;
    }
    private void applyCreate(Venue v, VenueCreateRequest req) {
        v.setName(req.getName()); v.setAddress(req.getAddress());
        v.setLatitude(req.getLatitude()); v.setLongitude(req.getLongitude());
        v.setCoverUrl(req.getCoverUrl()); v.setDescription(req.getDescription());
        if (req.getStatus() != null) v.setStatus(req.getStatus());
        if (req.getBookingLeadDays() != null) v.setBookingLeadDays(req.getBookingLeadDays());
    }
    private VenueVO toListVO(Venue v) {
        VenueVO vo = new VenueVO();
        copyBase(v, vo);
        vo.setContacts(loadContacts(v.getId()));
        return vo;
    }
    private VenueDetailVO toDetailVO(Venue v) {
        VenueDetailVO vo = new VenueDetailVO();
        copyBase(v, vo);
        vo.setContacts(loadContacts(v.getId()));
        vo.setBusinessHours(businessHourMapper.selectList(
                new LambdaQueryWrapper<VenueBusinessHour>().eq(VenueBusinessHour::getVenueId, v.getId())
                        .orderByAsc(VenueBusinessHour::getDayOfWeek)).stream().map(bh -> {
            VenueDetailVO.BusinessHourVO b = new VenueDetailVO.BusinessHourVO();
            b.setDayOfWeek(bh.getDayOfWeek()); b.setOpenTime(bh.getOpenTime()); b.setCloseTime(bh.getCloseTime());
            return b;
        }).collect(Collectors.toList()));
        vo.setCourts(courtMapper.selectList(
                new LambdaQueryWrapper<Court>().eq(Court::getVenueId, v.getId()).orderByAsc(Court::getSortOrder))
                .stream().map(this::toCourtVO).collect(Collectors.toList()));
        return vo;
    }
    private void copyBase(Venue v, VenueVO vo) {
        vo.setId(v.getId()); vo.setName(v.getName()); vo.setAddress(v.getAddress());
        vo.setLatitude(v.getLatitude()); vo.setLongitude(v.getLongitude()); vo.setCoverUrl(v.getCoverUrl());
        vo.setDescription(v.getDescription()); vo.setStatus(v.getStatus()); vo.setBookingLeadDays(v.getBookingLeadDays());
    }
    private List<VenueContactVO> loadContacts(Long venueId) {
        return contactMapper.selectList(new LambdaQueryWrapper<VenueContact>()
                .eq(VenueContact::getVenueId, venueId).orderByAsc(VenueContact::getSortOrder))
                .stream().map(c -> { VenueContactVO vo = new VenueContactVO();
                    vo.setId(c.getId()); vo.setType(c.getType()); vo.setValue(c.getValue());
                    vo.setLabel(c.getLabel()); vo.setSortOrder(c.getSortOrder()); return vo;
                }).collect(Collectors.toList());
    }
    private CourtVO toCourtVO(Court c) {
        CourtVO vo = new CourtVO();
        vo.setId(c.getId()); vo.setVenueId(c.getVenueId()); vo.setName(c.getName());
        vo.setCourtType(c.getCourtType()); vo.setSlotMinutes(c.getSlotMinutes());
        vo.setStatus(c.getStatus()); vo.setSortOrder(c.getSortOrder());
        return vo;
    }
}
```

（`CourtVO`/`VenueContactVO` 字段照上面 VO 说明补全。）

- [ ] **Step 3: 编译 + Commit** `feat(venue): VenueService CRUD + 营业时间/联系方式`

### Task 2.5: CourtService（CRUD + 定价带覆盖 + 复制价目）

**Files:** Create `service/CourtService.java`、`service/impl/CourtServiceImpl.java` + test `service/impl/CourtServiceImplTest.java`

- [ ] **Step 1: 写接口**

```java
package com.heypickler.service;
import com.heypickler.dto.admin.*;
import com.heypickler.vo.CourtVO;
import java.util.List;

public interface CourtService {
    List<CourtVO> listByVenue(Long venueId);
    CourtVO get(Long id);
    Long create(CourtCreateRequest req);
    void update(Long id, CourtCreateRequest req);
    void delete(Long id);
    void replacePricingBands(Long courtId, CourtPricingBandBatchRequest req); // 校验+整批覆盖
    void copyPricingBands(Long targetCourtId, Long fromCourtId);
}
```

- [ ] **Step 2: 先写关键失败测试**（`replacePricingBands` 校验路径；`@MockitoSettings(strictness=LENIENT)`；预热仅 `CourtPricingBand`——测试路径里唯一用到 `LambdaQueryWrapper` 的实体，`mustExist` 走 `selectById` 不需预热）

```java
package com.heypickler.service.impl;

import com.heypickler.common.exception.BizException;
import com.heypickler.dto.admin.*;
import com.heypickler.entity.*;
import com.heypickler.mapper.*;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CourtServiceImplTest {
    @Mock CourtMapper courtMapper;
    @Mock CourtPricingBandMapper bandMapper;
    @Mock PricingBandValidator validator; // 注入校验器
    @InjectMocks CourtServiceImpl service;

    @BeforeAll
    static void warm() {
        Configuration cfg = new Configuration();
        for (Class<?> c : List.of(CourtPricingBand.class)) {
            MapperBuilderAssistant a = new MapperBuilderAssistant(cfg, "");
            a.setCurrentNamespace("com.heypickler.mapper." + c.getSimpleName() + "Mapper");
            com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(a, c);
        }
    }

    @Test
    void replacePricingBands_validatorRejects_throwsAndNoWrite() {
        CourtPricingBandBatchRequest req = new CourtPricingBandBatchRequest();
        CourtPricingBandRequest b = new CourtPricingBandRequest();
        b.setDayType("WEEKDAY"); b.setStartTime(LocalTime.of(9,0)); b.setEndTime(LocalTime.of(12,0)); b.setPrice(new BigDecimal("40"));
        req.setBands(List.of(b));
        doThrow(new BizException(com.heypickler.common.exception.ErrorCode.PARAM_ERROR, "重叠"))
                .when(validator).validate(anyList());

        assertThrows(BizException.class, () -> service.replacePricingBands(1L, req));
        verify(bandMapper, never()).insert(any());
    }

    @Test
    void replacePricingBands_ok_deletesOldInsertsNew() {
        CourtPricingBandBatchRequest req = new CourtPricingBandBatchRequest();
        CourtPricingBandRequest b = new CourtPricingBandRequest();
        b.setDayType("WEEKDAY"); b.setStartTime(LocalTime.of(9,0)); b.setEndTime(LocalTime.of(12,0)); b.setPrice(new BigDecimal("40"));
        req.setBands(List.of(b));
        when(courtMapper.selectById(1L)).thenReturn(new Court());
        doNothing().when(validator).validate(anyList());

        service.replacePricingBands(1L, req);

        verify(bandMapper).delete(any());     // 先清旧
        verify(bandMapper, times(1)).insert(any()); // 再写新
    }

    @Test
    void replacePricingBands_courtNotFound_throws() {
        when(courtMapper.selectById(99L)).thenReturn(null);
        assertThrows(BizException.class, () -> service.replacePricingBands(99L, new CourtPricingBandBatchRequest()));
    }
}
```

> 注意：`PricingBandValidator` 作为可注入依赖（`@Component` 或直接 `new`）。在 impl 里 `private final PricingBandValidator validator`。若用 `@RequiredArgsConstructor`，给 `PricingBandValidator` 加 `@Component`。

- [ ] **Step 3: 跑确认失败** — Run: `mvn -q -pl hey-pickler-server test -Dtest=CourtServiceImplTest` → FAIL

- [ ] **Step 4: 写实现**（`PricingBandValidator` 加 `@Component`；校验在删除前先跑，避免误删后校验失败留下空集）

```java
package com.heypickler.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.heypickler.common.exception.BizException;
import com.heypickler.common.exception.ErrorCode;
import com.heypickler.common.util.PricingBandValidator;
import com.heypickler.dto.admin.*;
import com.heypickler.entity.*;
import com.heypickler.mapper.*;
import com.heypickler.service.CourtService;
import com.heypickler.vo.CourtVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CourtServiceImpl implements CourtService {
    private final CourtMapper courtMapper;
    private final CourtPricingBandMapper bandMapper;
    private final PricingBandValidator validator;

    @Override
    public List<CourtVO> listByVenue(Long venueId) {
        return courtMapper.selectList(new LambdaQueryWrapper<Court>()
                .eq(Court::getVenueId, venueId).orderByAsc(Court::getSortOrder))
                .stream().map(this::toVO).collect(Collectors.toList());
    }
    @Override
    public CourtVO get(Long id) { return toVO(mustExist(id)); }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(CourtCreateRequest req) {
        Court c = new Court();
        apply(req, c);
        if (c.getVenueId() == null) throw new BizException(ErrorCode.PARAM_ERROR, "venueId 不能为空");
        if (c.getSlotMinutes() == null) c.setSlotMinutes(60);
        if (c.getStatus() == null) c.setStatus("OPEN");
        if (c.getCourtType() == null) c.setCourtType("INDOOR");
        courtMapper.insert(c);
        return c.getId();
    }
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, CourtCreateRequest req) {
        Court c = mustExist(id);
        apply(req, c);
        courtMapper.updateById(c);
    }
    @Override
    public void delete(Long id) {
        mustExist(id);
        courtMapper.deleteById(id); // 软删; name_key 释放
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void replacePricingBands(Long courtId, CourtPricingBandBatchRequest req) {
        mustExist(courtId);
        List<CourtPricingBand> bands = req.getBands().stream().map(this::toEntity).collect(Collectors.toList());
        validator.validate(bands); // 先校验,通过后再清旧写新
        bandMapper.delete(new LambdaQueryWrapper<CourtPricingBand>().eq(CourtPricingBand::getCourtId, courtId));
        for (CourtPricingBand b : bands) { b.setCourtId(courtId); bandMapper.insert(b); }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void copyPricingBands(Long targetCourtId, Long fromCourtId) {
        mustExist(targetCourtId);
        List<CourtPricingBand> src = bandMapper.selectList(
                new LambdaQueryWrapper<CourtPricingBand>().eq(CourtPricingBand::getCourtId, fromCourtId));
        validator.validate(src);
        bandMapper.delete(new LambdaQueryWrapper<CourtPricingBand>().eq(CourtPricingBand::getCourtId, targetCourtId));
        for (CourtPricingBand b : src) {
            CourtPricingBand nb = new CourtPricingBand();
            nb.setDayType(b.getDayType()); nb.setStartTime(b.getStartTime());
            nb.setEndTime(b.getEndTime()); nb.setPrice(b.getPrice());
            nb.setCourtId(targetCourtId);
            bandMapper.insert(nb);
        }
    }

    private Court mustExist(Long id) {
        Court c = courtMapper.selectById(id);
        if (c == null) throw new BizException(ErrorCode.COURT_NOT_FOUND);
        return c;
    }
    private void apply(CourtCreateRequest req, Court c) {
        if (req.getVenueId() != null) c.setVenueId(req.getVenueId());
        c.setName(req.getName());
        if (req.getCourtType() != null) c.setCourtType(req.getCourtType());
        if (req.getSlotMinutes() != null) c.setSlotMinutes(req.getSlotMinutes());
        if (req.getStatus() != null) c.setStatus(req.getStatus());
        if (req.getSortOrder() != null) c.setSortOrder(req.getSortOrder());
    }
    private CourtPricingBand toEntity(CourtPricingBandRequest r) {
        CourtPricingBand b = new CourtPricingBand();
        b.setDayType(r.getDayType()); b.setStartTime(r.getStartTime());
        b.setEndTime(r.getEndTime()); b.setPrice(r.getPrice());
        return b;
    }
    private CourtVO toVO(Court c) {
        CourtVO vo = new CourtVO();
        vo.setId(c.getId()); vo.setVenueId(c.getVenueId()); vo.setName(c.getName());
        vo.setCourtType(c.getCourtType()); vo.setSlotMinutes(c.getSlotMinutes());
        vo.setStatus(c.getStatus()); vo.setSortOrder(c.getSortOrder());
        return vo;
    }
}
```

给 `PricingBandValidator` 加 `@Component`（`@Component public class PricingBandValidator {`）。

- [ ] **Step 5: 跑确认通过** — Run 同上 → PASS（3 用例）

- [ ] **Step 6: Commit** `feat(venue): CourtService CRUD + 定价带覆盖/复制`

### Task 2.6: SlotService（组装数据 + 委派 SlotCalculator）

**Files:** Create `service/SlotService.java`、`service/impl/SlotServiceImpl.java` + test `service/impl/SlotServiceImplTest.java`

- [ ] **Step 1: 写接口 + impl**

```java
package com.heypickler.service;
import com.heypickler.vo.SlotVO;
import java.time.LocalDate;
import java.util.List;

public interface SlotService {
    List<SlotVO> getCourtSlots(Long courtId, LocalDate date);
}
```

```java
package com.heypickler.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.heypickler.common.exception.BizException;
import com.heypickler.common.exception.ErrorCode;
import com.heypickler.common.util.SlotCalculator;
import com.heypickler.entity.*;
import com.heypickler.mapper.*;
import com.heypickler.service.SlotService;
import com.heypickler.vo.SlotVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SlotServiceImpl implements SlotService {
    private final CourtMapper courtMapper;
    private final VenueMapper venueMapper;
    private final VenueBusinessHourMapper businessHourMapper;
    private final CourtPricingBandMapper bandMapper;
    private final BookingSlotMapper bookingSlotMapper;
    private final SlotCalculator calculator;

    @Override
    public List<SlotVO> getCourtSlots(Long courtId, LocalDate date) {
        Court court = courtMapper.selectById(courtId);
        if (court == null) throw new BizException(ErrorCode.COURT_NOT_FOUND);
        if (!"OPEN".equals(court.getStatus())) throw new BizException(ErrorCode.COURT_NOT_AVAILABLE);

        Venue venue = venueMapper.selectById(court.getVenueId());
        int leadDays = (venue != null && venue.getBookingLeadDays() != null) ? venue.getBookingLeadDays() : 14;

        // LocalDate dow(Mon=1..Sun=7) → schema(0=Sun..6=Sat): getValue()%7
        int schemaDow = date.getDayOfWeek().getValue() % 7;
        VenueBusinessHour bh = businessHourMapper.selectOne(new LambdaQueryWrapper<VenueBusinessHour>()
                .eq(VenueBusinessHour::getVenueId, court.getVenueId())
                .eq(VenueBusinessHour::getDayOfWeek, schemaDow));
        LocalTime open = bh != null ? bh.getOpenTime() : null;
        LocalTime close = bh != null ? bh.getCloseTime() : null;

        String dayType = (schemaDow == 0 || schemaDow == 6) ? "WEEKEND" : "WEEKDAY";
        List<CourtPricingBand> effBands = bandMapper.selectList(new LambdaQueryWrapper<CourtPricingBand>()
                .eq(CourtPricingBand::getCourtId, courtId)
                .in(CourtPricingBand::getDayType, dayType, "ALL"));

        Set<LocalDateTime> occupied = bookingSlotMapper.selectList(new LambdaQueryWrapper<BookingSlot>()
                .eq(BookingSlot::getCourtId, courtId)
                .between(BookingSlot::getSlotStart, date.atStartOfDay(), date.atTime(LocalTime.MAX)))
                .stream().map(BookingSlot::getSlotStart).collect(Collectors.toSet());

        return calculator.generate(open, close, court.getSlotMinutes(), effBands, occupied,
                date, LocalDateTime.now(), leadDays).stream().map(r -> {
            SlotVO vo = new SlotVO();
            vo.setStart(r.start()); vo.setEnd(r.end()); vo.setAvailable(r.available()); vo.setPrice(r.price());
            return vo;
        }).collect(Collectors.toList());
    }
}
```

- [ ] **Step 2: 写 service 测试**（mock 5 mapper + calculator；预热 Court/Venue/VenueBusinessHour/CourtPricingBand/BookingSlot 的 lambda cache；仿 `MatchServiceImplTest` 多实体循环预热）

断言三件事：closed day（bh.openTime=null）→ 空；court 非 OPEN → 抛 COURT_NOT_AVAILABLE；OPEN 场地返回 calculator 的结果数。`calculator` 用真实 `new SlotCalculator()`（纯，不必 mock）。

- [ ] **Step 3: 跑 + 编译 + Commit** `feat(venue): SlotService 组装数据 + 委派 SlotCalculator + 测试`

---

## Chunk 3: 后端 Controller + 基础设施改动 + 测试

### Task 3.1: AppAuthFilter 匿名放行

**Files:** Modify `filter/AppAuthFilter.java`

- [ ] **Step 1: 在 `PUBLIC_GET_PREFIXES` 加 2 行**

```java
    private static final Set<String> PUBLIC_GET_PREFIXES = Set.of(
            "/api/app/banners",
            "/api/app/events",
            "/api/app/rankings",
            "/api/app/dict",
            "/api/app/brand",
            "/api/app/venues",
            "/api/app/courts"
    );
```

> P1 场馆/场地的 GET 全部匿名浏览，无用户态 GET 落在这两个前缀下，故无需新增 `endsWith` 守卫（与 events 的 `/my-team` 不同）。P2 若加 `GET /api/app/bookings/my` 则落在 `/api/app/bookings` 前缀——届时单独处理，不在 P1。

- [ ] **Step 2: 编译 + Commit** `feat(venue): AppAuthFilter 放行 venues/courts 匿名浏览`

### Task 3.2: OperationLogClassifier 扩展

**Files:** Modify `common/util/OperationLogClassifier.java`

- [ ] **Step 1: MODULE_MAP/SINGULAR_MAP 各加 3 条**

```java
        MODULE_MAP.put("operation-logs", "OPERATION_LOG");
        MODULE_MAP.put("venues", "VENUE");
        MODULE_MAP.put("courts", "COURT");
        MODULE_MAP.put("bookings", "BOOKING");

        SINGULAR_MAP.put("operation-logs", "OperationLog");
        SINGULAR_MAP.put("venues", "Venue");
        SINGULAR_MAP.put("courts", "Court");
        SINGULAR_MAP.put("bookings", "Booking");
```

> controller 路径段用 `venues`/`courts`（复数），与 map key 一致。

- [ ] **Step 2: 编译 + Commit** `feat(venue): OperationLogClassifier 归类 venue/court/booking 模块`

### Task 3.3: AdminVenueController

**Files:** Create `controller/admin/AdminVenueController.java`（仿 `AdminBannerController`）

- [ ] **Step 1: 写 controller**

```java
package com.heypickler.controller.admin;

import com.heypickler.common.annotation.RequireRole;
import com.heypickler.common.enums.UserRole;
import com.heypickler.common.result.PageResult;
import com.heypickler.common.result.Result;
import com.heypickler.dto.admin.*;
import com.heypickler.service.VenueService;
import com.heypickler.vo.VenueDetailVO;
import com.heypickler.vo.VenueVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/venues")
@RequiredArgsConstructor
@Tag(name = "管理端-场馆管理")
public class AdminVenueController {
    private final VenueService venueService;

    @GetMapping @Operation(summary = "场馆列表")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN, UserRole.OPERATOR})
    public Result<PageResult<VenueVO>> list(VenueQueryRequest req) { return Result.ok(venueService.adminList(req)); }

    @GetMapping("/{id}") @Operation(summary = "场馆详情")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN, UserRole.OPERATOR})
    public Result<VenueDetailVO> get(@PathVariable Long id) { return Result.ok(venueService.adminGet(id)); }

    @PostMapping @Operation(summary = "新建场馆")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN})
    public Result<Map<String, Object>> create(@RequestBody @Valid VenueCreateRequest req) {
        return Result.ok(Map.of("id", venueService.create(req)));
    }

    @PutMapping("/{id}") @Operation(summary = "更新场馆")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN})
    public Result<Void> update(@PathVariable Long id, @RequestBody @Valid VenueCreateRequest req) {
        venueService.update(id, req); return Result.ok();
    }

    @DeleteMapping("/{id}") @Operation(summary = "删除场馆")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN})
    public Result<Void> delete(@PathVariable Long id) { venueService.delete(id); return Result.ok(); }

    @PutMapping("/{id}/business-hours") @Operation(summary = "覆盖营业时间(7行)")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN})
    public Result<Void> replaceBusinessHours(@PathVariable Long id, @RequestBody @Valid VenueBusinessHourRequest req) {
        venueService.replaceBusinessHours(id, req); return Result.ok();
    }

    @PostMapping("/{id}/contacts") @Operation(summary = "新增联系方式")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN})
    public Result<Map<String, Object>> addContact(@PathVariable Long id, @RequestBody @Valid VenueContactRequest req) {
        return Result.ok(Map.of("id", venueService.addContact(id, req)));
    }
    @PutMapping("/contacts/{contactId}") @Operation(summary = "更新联系方式")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN})
    public Result<Void> updateContact(@PathVariable Long contactId, @RequestBody @Valid VenueContactRequest req) {
        venueService.updateContact(contactId, req); return Result.ok();
    }
    @DeleteMapping("/contacts/{contactId}") @Operation(summary = "删除联系方式")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN})
    public Result<Void> deleteContact(@PathVariable Long contactId) {
        venueService.deleteContact(contactId); return Result.ok();
    }
}
```

- [ ] **Step 2: 编译 + Commit** `feat(venue): AdminVenueController`

### Task 3.4: AdminCourtController

**Files:** Create `controller/admin/AdminCourtController.java`

- [ ] **Step 1: 写 controller**（`GET /courts?venueId=`、CRUD、`PUT /courts/{id}/pricing-bands`、`POST /courts/{id}/pricing-bands/copy?from=`）

```java
package com.heypickler.controller.admin;

import com.heypickler.common.annotation.RequireRole;
import com.heypickler.common.enums.UserRole;
import com.heypickler.common.result.Result;
import com.heypickler.dto.admin.*;
import com.heypickler.service.CourtService;
import com.heypickler.vo.CourtVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin/courts")
@RequiredArgsConstructor
@Tag(name = "管理端-场地管理")
public class AdminCourtController {
    private final CourtService courtService;

    @GetMapping @Operation(summary = "场地列表(按场馆)")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN, UserRole.OPERATOR})
    public Result<List<CourtVO>> list(@RequestParam Long venueId) { return Result.ok(courtService.listByVenue(venueId)); }

    @GetMapping("/{id}") @Operation(summary = "场地详情")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN, UserRole.OPERATOR})
    public Result<CourtVO> get(@PathVariable Long id) { return Result.ok(courtService.get(id)); }

    @PostMapping @Operation(summary = "新建场地")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN})
    public Result<java.util.Map<String, Object>> create(@RequestBody @Valid CourtCreateRequest req) {
        return Result.ok(java.util.Map.of("id", courtService.create(req)));
    }
    @PutMapping("/{id}") @Operation(summary = "更新场地")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN})
    public Result<Void> update(@PathVariable Long id, @RequestBody @Valid CourtCreateRequest req) {
        courtService.update(id, req); return Result.ok();
    }
    @DeleteMapping("/{id}") @Operation(summary = "删除场地")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN})
    public Result<Void> delete(@PathVariable Long id) { courtService.delete(id); return Result.ok(); }

    @PutMapping("/{id}/pricing-bands") @Operation(summary = "覆盖时段定价带(带重叠校验)")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN})
    public Result<Void> replacePricingBands(@PathVariable Long id, @RequestBody @Valid CourtPricingBandBatchRequest req) {
        courtService.replacePricingBands(id, req); return Result.ok();
    }
    @PostMapping("/{id}/pricing-bands/copy") @Operation(summary = "从指定场地复制价目")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN})
    public Result<Void> copyPricingBands(@PathVariable Long id, @RequestParam Long from) {
        courtService.copyPricingBands(id, from); return Result.ok();
    }
}
```

- [ ] **Step 2: 编译 + Commit** `feat(venue): AdminCourtController`

### Task 3.5: AppVenueController + AppCourtController

**Files:** Create `controller/app/AppVenueController.java`、`controller/app/AppCourtController.java`

- [ ] **Step 1: AppVenueController**（匿名浏览；经 Task 3.1 的 bypass 放行）

```java
package com.heypickler.controller.app;

import com.heypickler.common.result.PageResult;
import com.heypickler.common.result.Result;
import com.heypickler.dto.admin.VenueQueryRequest;
import com.heypickler.service.VenueService;
import com.heypickler.vo.VenueDetailVO;
import com.heypickler.vo.VenueVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/app/venues")
@RequiredArgsConstructor
@Tag(name = "小程序-场馆")
public class AppVenueController {
    private final VenueService venueService;

    @GetMapping @Operation(summary = "场馆列表(匿名)")
    public Result<PageResult<VenueVO>> list(VenueQueryRequest req) { return Result.ok(venueService.appList(req)); }

    @GetMapping("/{id}") @Operation(summary = "场馆详情(匿名)")
    public Result<VenueDetailVO> get(@PathVariable Long id) { return Result.ok(venueService.appGet(id)); }
}
```

- [ ] **Step 2: AppCourtController**（核心：`GET /courts/{id}/slots?date=`）

```java
package com.heypickler.controller.app;

import com.heypickler.common.result.Result;
import com.heypickler.service.CourtService;
import com.heypickler.service.SlotService;
import com.heypickler.vo.CourtVO;
import com.heypickler.vo.SlotVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/app/courts")
@RequiredArgsConstructor
@Tag(name = "小程序-场地")
public class AppCourtController {
    private final CourtService courtService;
    private final SlotService slotService;

    @GetMapping @Operation(summary = "场地列表(按场馆,匿名)")
    public Result<List<CourtVO>> list(@RequestParam Long venueId) { return Result.ok(courtService.listByVenue(venueId)); }

    @GetMapping("/{id}/slots") @Operation(summary = "某场地某日可订格子+价格(匿名)")
    public Result<List<SlotVO>> slots(@PathVariable Long id,
                                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.ok(slotService.getCourtSlots(id, date));
    }
}
```

- [ ] **Step 3: 编译 + Commit** `feat(venue): AppVenue/AppCourt 浏览端点(含 slots)`

### Task 3.6: Controller 单测

**Files:** Create `controller/admin/AdminVenueControllerTest.java`、`controller/app/AppCourtControllerTest.java`（仿 `AdminEventControllerTest`，直接调方法取 `.getData()`，无 MockMvc）

- [ ] **Step 1: AdminVenueControllerTest** 断言 list/create 委派到 service（`doReturn(...).when(venueService).adminList(any())`，`controller.list(req).getData()`）。
- [ ] **Step 2: AppCourtControllerTest** 断言 `slots` 把 date 透传给 `slotService.getCourtSlots` 并包 `Result`。
- [ ] **Step 3: 跑** — `mvn -q -pl hey-pickler-server test -Dtest='AdminVenueControllerTest,AppCourtControllerTest'` → PASS
- [ ] **Step 4: Commit** `test(venue): admin/app controller 单测`

### Task 3.7: 集成测试（匿名浏览 + 角色守卫）

**Files:** Create `integration/VenueBrowseIntegrationTest.java`（`extends IntegrationTestConfig`，`@ActiveProfiles("integration")`）

- [ ] **Step 1: 写端到端**（用 `JdbcTemplate` 建场馆+场地+定价带+营业时间；匿名 GET `/api/app/venues` 返回 0；`GET /api/app/courts/{id}/slots?date=...` 返回格子；`POST /api/admin/venues` 无 token → 非 0；`@Transactional` 清理）

关键断言：
- `resultCode(restTemplate.getForEntity("/api/app/venues", Map.class)) == 0`（匿名放行生效）
- admin `POST /api/admin/venues` 用 `adminAuthHeaders()` → code 0；不带 header → 非 0（401/403）
- 一个配了 09:00-11:00 band 的 OPEN 场地，`slots?date=<未来某工作日>` 返回 2 格且 `available==true`、`price==40`。

- [ ] **Step 2: 跑** — `mvn -q -pl hey-pickler-server test -Dtest=VenueBrowseIntegrationTest`（需本地 MySQL+Redis 跑着，dev-up.sh）→ PASS

- [ ] **Step 3: 跑全量门禁** — `mvn -q -pl hey-pickler-server verify` → BUILD SUCCESS（INSTRUCTION≥80% BRANCH≥60%）。若覆盖率不足，优先补 `VenueServiceImpl`/`SlotServiceImpl` 分支测试。

- [ ] **Step 4: Commit** `test(venue): 浏览端到端 + 角色守卫集成测试`

---

## Chunk 4: Admin 前端

> 约定（勘察自现有代码）：`src/api/request.ts` 拦截器已把响应解包成 `Result<T>`（`{code,message,data}`），baseURL=`/api/admin`，故 api 模块 URL 写 `/venues` 即可。侧边栏分组由 `AppSidebar.vue` 的 `GROUP_ORDER` 数组驱动——**必须**把 `'场馆管理'` 加进去，否则菜单不渲染。空 `catch{}` 允许（拦截器统一 toast）。`@typescript-eslint/no-explicit-any` 是 warn 不挡 CI。

### Task 4.1: 类型 + API 模块

**Files:** Modify `src/types/index.ts`；Create `src/api/venues.ts`

- [ ] **Step 1: types 追加**（`src/types/index.ts` 末尾，仿既有分节）

```ts
// ==================== Venue Types ====================
export interface VenueContact { id: number; type: string; value: string; label?: string; sortOrder: number }
export interface BusinessHour { dayOfWeek: number; openTime?: string; closeTime?: string }
export interface Court {
  id: number; venueId: number; name: string; courtType: 'INDOOR' | 'OUTDOOR'
  slotMinutes: number; status: 'OPEN' | 'CLOSED' | 'MAINTENANCE'; sortOrder: number
}
export interface Venue {
  id: number; name: string; address: string; latitude?: string; longitude?: string
  coverUrl?: string; description?: string; status: 'ACTIVE' | 'INACTIVE'; bookingLeadDays: number
  contacts?: VenueContact[]
}
export interface VenueDetail extends Venue { businessHours: BusinessHour[]; courts: Court[] }
export interface CreateVenueRequest {
  name: string; address: string; latitude?: string; longitude?: string
  coverUrl?: string; description?: string; status?: 'ACTIVE' | 'INACTIVE'; bookingLeadDays?: number
}
export interface CourtPricingBand { id?: number; dayType: 'WEEKDAY' | 'WEEKEND' | 'ALL'; startTime: string; endTime: string; price: number }
```

- [ ] **Step 2: api/venues.ts**（仿 `banners.ts` + `ban-records.ts` 分页）

```ts
import request from './request'
import type { Venue, VenueDetail, CreateVenueRequest, Court, CourtPricingBand, ApiResponse, PageResult } from '@/types'

export const getVenueList = (params: { page?: number; size?: number; keyword?: string; status?: string }) =>
  request.get<unknown, ApiResponse<PageResult<Venue>>>('/venues', { params })
export const getVenueDetail = (id: number) =>
  request.get<unknown, ApiResponse<VenueDetail>>(`/venues/${id}`)
export const createVenue = (data: CreateVenueRequest) =>
  request.post<unknown, ApiResponse<{ id: number }>>('/venues', data)
export const updateVenue = (id: number, data: CreateVenueRequest) =>
  request.put<unknown, ApiResponse<void>>(`/venues/${id}`, data)
export const deleteVenue = (id: number) =>
  request.delete<unknown, ApiResponse<void>>(`/venues/${id}`)
export const replaceBusinessHours = (id: number, hours: { dayOfWeek: number; openTime?: string; closeTime?: string }[]) =>
  request.put<unknown, ApiResponse<void>>(`/venues/${id}/business-hours`, { hours })
export const addContact = (venueId: number, data: { type: string; value: string; label?: string; sortOrder?: number }) =>
  request.post<unknown, ApiResponse<{ id: number }>>(`/venues/${venueId}/contacts`, data)
export const updateContact = (contactId: number, data: { type: string; value: string; label?: string; sortOrder?: number }) =>
  request.put<unknown, ApiResponse<void>>(`/venues/contacts/${contactId}`, data)
export const deleteContact = (contactId: number) =>
  request.delete<unknown, ApiResponse<void>>(`/venues/contacts/${contactId}`)
// courts
export const getCourts = (venueId: number) =>
  request.get<unknown, ApiResponse<Court[]>>('/courts', { params: { venueId } })
export const createCourt = (data: Partial<Court> & { venueId: number; name: string }) =>
  request.post<unknown, ApiResponse<{ id: number }>>('/courts', data)
export const updateCourt = (id: number, data: Partial<Court> & { name: string }) =>
  request.put<unknown, ApiResponse<void>>(`/courts/${id}`, data)
export const deleteCourt = (id: number) =>
  request.delete<unknown, ApiResponse<void>>(`/courts/${id}`)
export const replacePricingBands = (courtId: number, bands: CourtPricingBand[]) =>
  request.put<unknown, ApiResponse<void>>(`/courts/${courtId}/pricing-bands`, { bands })
export const copyPricingBands = (courtId: number, from: number) =>
  request.post<unknown, ApiResponse<void>>(`/courts/${courtId}/pricing-bands/copy`, undefined, { params: { from } })
```

- [ ] **Step 3: lint** — `cd hey-pickler-admin && npm run lint` → 0 error
- [ ] **Step 4: Commit** `feat(venue): admin 类型 + api/venues 模块`

### Task 4.2: 路由 + 侧边栏分组

**Files:** Modify `src/router/index.ts`、`src/components/layout/AppSidebar.vue`

- [ ] **Step 1: router 加子路由**（插在 events 相关子路由之后）

```ts
{
  path: 'venues',
  name: 'Venues',
  component: () => import('@/views/venues/VenueListView.vue'),
  meta: { title: '场馆', icon: 'Location', group: '场馆管理' }
},
{
  path: 'venues/:id',
  name: 'VenueDetail',
  component: () => import('@/views/venues/VenueFormView.vue'),
  meta: { title: '场馆编辑', hidden: true }
}
```

- [ ] **Step 2: AppSidebar.vue 的 `GROUP_ORDER`** 加 `'场馆管理'`（放 `'运营管理'` 之后）

```ts
const GROUP_ORDER = ['运营管理', '场馆管理', '积分与排名', '内容运营', '数据', '系统']
```

- [ ] **Step 3: Commit** `feat(venue): admin 路由 + 侧边栏「场馆管理」分组`

### Task 4.3: VenueListView（列表 + 新建入口）

**Files:** Create `src/views/venues/VenueListView.vue`（仿 `ban-records/BanRecordListView.vue` 分页 + 搜索；新建跳 `VenueFormView`）

- [ ] **Step 1: 写列表页**——`el-table` 列：名称/地址/状态/联系方式数/可订窗口；顶部搜索（keyword + status select）+ `Pagination`；「新建场馆」按钮 `router.push('/venues/0')`（0=新建）；行操作「编辑」`/venues/${id}`、「删除」`ElMessageBox.confirm` → `deleteVenue`。
- [ ] **Step 2: 手测** `npm run dev` → 登录 → 左侧出现「场馆管理」组与「场馆」项 → 列表空态正常。
- [ ] **Step 3: Commit** `feat(venue): admin 场馆列表页`

### Task 4.4: VenueFormView（表单 + 4 Tab）

**Files:** Create `src/views/venues/VenueFormView.vue` + 子组件 `components/{BusinessHoursEditor,ContactsEditor,CourtsEditor,PricingBandsEditor}.vue`

- [ ] **Step 1: 主表单（基础信息）**——`ElForm` 字段 name/address/coverUrl(ImageUpload)/description/status(select ACTIVE/INACTIVE)/bookingLeadDays(number)。`route.params.id`：`'0'` 走 create，否则 `getVenueDetail` 回填。保存调 `createVenue`/`updateVenue`。
- [ ] **Step 2: `BusinessHoursEditor.vue`**——7 行（日~六），每行 `el-time-select` 开/结（clearable 留空=当日休）。保存调 `replaceBusinessHours`。
- [ ] **Step 3: `ContactsEditor.vue`**——列表 + 增删改（type select + value input + label）。
- [ ] **Step 4: `CourtsEditor.vue`**——某场馆下场地列表（名称/类型/时长/状态/排序）增删改；每行点「定价」打开 `PricingBandsEditor`。
- [ ] **Step 5: `PricingBandsEditor.vue`**——某 court 的定价带表格（dayType select / 起止 time-select / price number）；顶部「从其他场地复制」`el-select` 选源 court → `copyPricingBands`；保存 `replacePricingBands`。**前端不做重叠校验**——后端 `PricingBandValidator` 是唯一事实来源，返回 `PARAM_ERROR` 由拦截器 toast。
- [ ] **Step 6: lint + 手测**（新建场馆→设营业时间→加场地→配价目→wxapp 能看到，跨端联通验证留 Task 5.x）
- [ ] **Step 7: Commit** `feat(venue): admin 场馆表单(基础信息/营业时间/联系方式/场地/定价带)`

### Task 4.5: 构建校验

- [ ] **Step 1:** `cd hey-pickler-admin && npm run lint:check && npm run build` → 全绿
- [ ] **Step 2: Commit**（若有 fix）`fix(venue): admin lint/build 修复`

---

## Chunk 5: wxapp 浏览

> 约定：`utils/request.js` resolve 整个 `Result` 信封，调用点判断 `res.code === 0`；路径拼到 `baseUrl`（dev = `http://localhost:8080/api/app`）。组件仿 `components/event-card`（`properties`/`observers`/`triggerEvent`）。新页面要在 `app.json` 的 `pages` 注册，`lazyCodeLoading:"requiredComponents"` 要求页面 `.json` 声明 `usingComponents`。

### Task 5.1: 工具函数 + 术语

**Files:** Modify `utils/util.js`、`utils/util.wxs`、`utils/terms.js`

- [ ] **Step 1: util.js 加**
```js
const formatPrice = (n) => (n === null || n === undefined) ? '' : '¥' + Number(n).toFixed(2)
// 后端返回 ISO 'YYYY-MM-DDTHH:mm:ss' → 取 'HH:mm'
const formatSlotTime = (dt) => { const s = String(dt || ''); return s.length >= 16 ? s.slice(11, 16) : s }
```
- [ ] **Step 2: util.wxs 镜像** `formatPrice`/`formatSlotTime`（WXS 语法：`var`、无 ES6）。
- [ ] **Step 3: terms.js 加** `COURT_TYPE = { INDOOR:{label:'室内'}, OUTDOOR:{label:'室外'} }` 与 `VENUE_STATUS`。
- [ ] **Step 4: Commit** `feat(venue): wxapp 工具函数(formatPrice/formatSlotTime) + 术语`

### Task 5.2: court-card 组件

**Files:** Create `components/court-card/{court-card.js,.json,.wxml,.wxss}`（仿 `event-card`）

- [ ] **Step 1: properties** `{ court:{type:Object,value:null} }`；`observers.court` 派生 `typeText`（COURT_TYPE）、`slotText`（`${slotMinutes}分钟/格`）、`statusText`。
- [ ] **Step 2: wxml** 卡片：名称 + 类型 tag + 时长 + 状态；`bindtap` → `triggerEvent('select', { court })`。
- [ ] **Step 3: Commit** `feat(venue): wxapp court-card 组件`

### Task 5.3: venue-list 页

**Files:** Create `pages/venue-list/{venue-list.js,.json,.wxml,.wxss}`；Modify `app.json`（pages 注册）；Modify `pages/index/index.{wxml,js}`（入口）

- [ ] **Step 1: app.json pages** 加 `"pages/venue-list/venue-list"`、`"pages/venue-detail/venue-detail"`（在 my-events 之后）。
- [ ] **Step 2: 首页入口** `index.wxml` tab-bar 旁加 `<view class="venue-entry" bindtap="goVenues">🏟 场馆</view>`；`index.js` 加 `goVenues(){ wx.navigateTo({ url:'/pages/venue-list/venue-list' }) }`。
- [ ] **Step 3: venue-list.js** `onLoad`/`onReachBottom`/`onPullDownRefresh` 调 `request.get('/venues',{keyword,page,size,status:'ACTIVE'})`，`res.code===0` → `setData({venues:res.data.list})`；点项 `wx.navigateTo('/pages/venue-detail/venue-detail?id='+id)`。
- [ ] **Step 4: venue-list.wxml** `wx:for="{{venues}}"` 卡片（名称/地址/联系方式图标）；空态。
- [ ] **Step 5: 手测**（微信开发者工具）首页 → 场馆入口 → 列表渲染 Task 4 建的场馆。
- [ ] **Step 6: Commit** `feat(venue): wxapp 场馆列表页 + 首页入口`

### Task 5.4: venue-detail 页（选日期 + 格子网格 + 价格）

**Files:** Create `pages/venue-detail/{venue-detail.js,.json,.wxml,.wxss}`

- [ ] **Step 1: venue-detail.js**
- `onLoad(options)` 取 `id` → `request.get('/venues/'+id)` 回填场馆信息 + `courts`。
- 选日期：默认今天；用 `picker` 或横向日期条选未来 7 天。
- `loadSlots(courtId, date)`：`request.get('/courts/'+courtId+'/slots',{date})` → `res.data` 存 `slots`。
- 切换 court / 日期 → 重载 slots。
- [ ] **Step 2: venue-detail.wxml**——场馆头（名称/地址/营业时间/联系方式）+ 场地横向列表（选中态）+ 日期条 + 格子网格：每格显示 `formatSlotTime(start)` 与 `price`（`util.formatPrice`），`available===false` 置灰标「不可订」。**P1 格子仅展示，无下单按钮**（P2 加）。
- [ ] **Step 3: 手测端到端**——admin 建场馆→配场地→设 09:00-11:00 band→wxapp 详情选该场地今日→出现 2 格 ¥40 可订。
- [ ] **Step 4: Commit** `feat(venue): wxapp 场馆详情页(选日期+格子网格+价格)`

### Task 5.5: P1 收尾

- [ ] **Step 1: 全量后端门禁** — `mvn -q -pl hey-pickler-server verify` → SUCCESS（覆盖率达标）
- [ ] **Step 2: admin 构建** — `cd hey-pickler-admin && npm run lint:check && npm run build` → 全绿
- [ ] **Step 3: 端到端手测**——admin 全流程 + wxapp 浏览全流程打通。
- [ ] **Step 4: 更新 CLAUDE.md**（可选，若架构摘要需要补充 venue 领域；由人决定）。
- [ ] **Step 5: 最终 Commit** `docs(venue): P1 交付完成`

---

## 验收清单（P1 Definition of Done）

- [ ] V22 7 张表存在；court 的 `uk_venue_court_name` 函数唯一索引软删安全。
- [ ] admin 可建/改/删场馆、覆盖营业时间、管理联系方式、管理场地、配/复制定价带。
- [ ] 定价带重叠（含 ALL 与 WEEKDAY/WEEKEND 冲突）保存被拒。
- [ ] wxapp 匿名浏览场馆列表/详情、选场地+日期看到格子与价格；无 band 的格子不可订无价；当日休无格子。
- [ ] `GET /api/app/venues` 匿名 200；`POST /api/admin/venues` 无 token 非 0。
- [ ] admin 写操作进 `operation_log`（module=VENUE/COURT）。
- [ ] `mvn verify` 绿（INSTRUCTION≥80% BRANCH≥60%）；`npm run lint:check && npm run build` 绿。
- [ ] 未引入 P2 内容（无 booking 写入、无 Booking 实体、无 wxapp 下单按钮）。
