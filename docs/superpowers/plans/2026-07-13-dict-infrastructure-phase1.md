# 字典基础设施（阶段 1 · 模块 A）实现计划

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 落地字典驱动配置平台的地基——`sys_dict`/`sys_dict_item` 表 + 后端 CRUD/缓存/聚合服务 + admin 字典管理页 + 小程序 bundle 匿名拉取，使枚举展示（名/色/排序/启停）可在 admin 配置、三端运行时读取。

**Architecture:** 方案③混合存储的最底层。通用字典两张表（`sys_dict` 字典目录 + `sys_dict_item` 字典项），后端 `DictService`（CRUD）+ `DictCacheService`（Redis 缓存 + 全局版本号）+ `AdminDictController`（管理端读写）/ `AppDictController`（小程序匿名只读 bundle）。铁律：`item_key` 永不可改，运营方只动 `label/color/sort/status`。

**Tech Stack:** Spring Boot 3.2 / Java 17 / MyBatis-Plus / MySQL 8 / Redis / Flyway；admin: Vue 3 + Element Plus 2.5+ + Pinia + Vite + TS。

**Spec:** `docs/superpowers/specs/2026-07-13-standardization-dict-platform-design.md`（已评审通过）。本计划严格限定阶段 1 范围，**不触及** 段位（B）、三端硬编码替换（D）、徽章视觉（E）、品牌层。

---

## 全局约定（每个任务都遵循）

- **后端模式参考**：`Banner` / `BannerService` / `BannerServiceImpl` / `AdminBannerController` / `BannerMapper` 是标准 CRUD 全栈样板。新代码严格对齐其注解、命名、分层。
- **TDD**：每个任务先写失败测试 → 跑红 → 最小实现 → 跑绿 → commit。
- **测试模式**：`@ExtendWith(MockitoExtension.class)` + `@InjectMocks` Service + `@Mock` Mapper；凡用 `LambdaQueryWrapper`/`LambdaUpdateWrapper` 必须在 `@BeforeAll` 用 `TableInfoHelper.initTableInfo` 预热 LambdaCache（参考 `NotificationServiceImplTest`）。
- **commit**：每个任务结束 commit 一次，message 用 `<type>: <desc>`（feat/fix/test/refactor/docs）。无 attribution（项目全局禁用）。
- **覆盖率门禁**：jacoco 80% instruction / 60% line（项目既有门禁）。每个 service 测试到位。
- **运行测试**：`mvn test -Dtest=<TestClass>` 单类；`mvn test -Dtest='!*IntegrationTest'` 全量单测。
- **构建**：`mvn clean package -DskipTests` 必须通过；CI 两个 job（backend/frontend）都要绿。

---

## File Structure

### 后端新增（`hey-pickler-server/src/main/java/com/heypickler/`）

| 文件 | 职责 |
|---|---|
| `entity/SysDict.java` | 字典目录实体（dict_code/name/status/软删） |
| `entity/SysDictItem.java` | 字典项实体（dict_code/item_key/item_label/item_color/sort/status/extra_json/软删） |
| `mapper/SysDictMapper.java` | `BaseMapper<SysDict>` |
| `mapper/SysDictItemMapper.java` | `BaseMapper<SysDictItem>` |
| `service/DictService.java` | 接口：list dicts / list items / update items / bundle / version |
| `service/DictCacheService.java` | 接口：Redis 读写 + 版本号自增 |
| `service/impl/DictServiceImpl.java` | CRUD + 缓存联动 + bundle 聚合 |
| `service/impl/DictCacheServiceImpl.java` | RedisTemplate 封装 |
| `controller/admin/AdminDictController.java` | `/api/admin/dict/**` 管理端 |
| `controller/app/AppDictController.java` | `/api/app/dict/bundle` 匿名只读 |
| `dto/admin/DictItemUpdateRequest.java` | 批量改 item 的 label/color/sort/status（不含 item_key） |
| `vo/SysDictVO.java` / `vo/SysDictItemVO.java` / `vo/DictBundleVO.java` | 响应 VO |

### 后端修改

| 文件 | 改动 |
|---|---|
| `common/constant/RedisKey.java` | 加 `dictEnum(code)` / `dictVersion()` / `dictBundle()` key 工厂 |
| `filter/AppAuthFilter.java` | `PUBLIC_GET_PREFIXES` 加 `/api/app/dict`（bundle 匿名） |

### 后端迁移

| 文件 | 职责 |
|---|---|
| `src/main/resources/db/migration/V18__dict_platform.sql` | 建 `sys_dict`/`sys_dict_item` + seed 9 个字典与 `track_term` |

### 后端测试新增（`src/test/java/com/heypickler/`）

| 文件 | 职责 |
|---|---|
| `service/DictServiceImplTest.java` | CRUD + bundle + 缓存联动 + item_key 不可改 |
| `service/DictCacheServiceImplTest.java` | Redis 读写 + version 自增 |
| `controller/admin/AdminDictControllerTest.java` | 端点 + 角色校验 |
| `controller/app/AppDictControllerTest.java` | 匿名 bundle 可访问 |

### admin 前端新增（`hey-pickler-admin/src/`）

| 文件 | 职责 |
|---|---|
| `api/dict.ts` | bundle / version / list / update 调用 |
| `types/index.ts`（追加） | `SysDictItem` interface |
| `views/dict/DictListView.vue` | 字典管理页：左选字典、右编辑 item（label/color/sort/status） |
| `router/index.ts`（改） | 注册 `/dict` 路由 + 侧栏分组「系统」 |

---

## Chunk 1: 数据层（迁移 + Entity + Mapper）

### Task 1: Flyway V18 迁移——建表 + seed

**Files:**
- Create: `hey-pickler-server/src/main/resources/db/migration/V18__dict_platform.sql`

- [ ] **Step 1: 写迁移脚本**

```sql
-- 字典平台：通用枚举字典（方案③ 基础设施）
-- sys_dict      字典目录（一类枚举一行）
-- sys_dict_item 字典项（每个枚举值一行；item_key 系统绑定不可改，label/color/sort/status 可配）
-- 软删：deleted_at NULL=未删（@TableLogic）

CREATE TABLE sys_dict (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  dict_code    VARCHAR(64)     NOT NULL,
  dict_name    VARCHAR(64)     NOT NULL,
  description  VARCHAR(255)    NULL,
  status       VARCHAR(16)     NOT NULL DEFAULT 'ENABLED',
  created_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted_at   DATETIME        NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_dict_code (dict_code),
  KEY idx_dict_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sys_dict_item (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  dict_code    VARCHAR(64)     NOT NULL,
  item_key     VARCHAR(64)     NOT NULL,
  item_label   VARCHAR(64)     NOT NULL,
  item_color   VARCHAR(16)     NULL,
  sort         INT             NOT NULL DEFAULT 0,
  status       VARCHAR(16)     NOT NULL DEFAULT 'ENABLED',
  extra_json   JSON            NULL,
  created_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted_at   DATETIME        NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_dict_item (dict_code, item_key),
  KEY idx_dict_item_code (dict_code, sort)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- seed：9 个枚举字典 + track_term（STAR=积分排名 / PARTY=匹克豆）
-- item_key 与后端 enum 值严格对齐，永不可改；label/color 运营方可调

INSERT INTO sys_dict (dict_code, dict_name, description) VALUES
  ('event_type',          '赛事类型', 'STAR 竞技 / PARTY 社交'),
  ('event_format',        '赛事形式', '单打 / 双打 / 混打'),
  ('event_status',        '赛事状态', '赛事生命周期状态（仅展示名/色可配，状态机值不可改）'),
  ('user_status',         '用户状态', '正常 / 已封禁'),
  ('registration_status', '报名状态', '已报名 / 已签到 / 已退出'),
  ('team_status',         '队伍状态', '待确认 / 已确认'),
  ('match_status',        '比赛状态', '待开打 / 进行中 / 已结束'),
  ('point_source',        '积分来源', '积分明细展示名'),
  ('notification_type',   '通知类型', '通知分类展示名'),
  ('track_term',          '积分体系命名', 'STAR=积分排名 / PARTY=匹克豆；extra_json 含 unit/pointsName/tierName/rankingName');

INSERT INTO sys_dict_item (dict_code, item_key, item_label, item_color, sort) VALUES
  ('event_type','STAR','竞技赛事','#F59E0B',0),
  ('event_type','PARTY','社交活动','#8B5CF6',1),
  ('event_format','SINGLES','单打','#3B82F6',0),
  ('event_format','DOUBLES','双打','#10B981',1),
  ('event_format','MIXED','混打','#EC4899',2),
  ('event_status','DRAFT','草稿','#909399',0),
  ('event_status','OPEN','报名中','#10B981',1),
  ('event_status','FULL','已满员','#F59E0B',2),
  ('event_status','IN_PROGRESS','进行中','#3B82F6',3),
  ('event_status','COMPLETED','已结束','#8B5CF6',4),
  ('event_status','CANCELLED','已取消','#EF4444',5),
  ('user_status','NORMAL','正常','#10B981',0),
  ('user_status','BANNED','已封禁','#EF4444',1),
  ('registration_status','REGISTERED','已报名','#3B82F6',0),
  ('registration_status','CHECKED_IN','已签到','#10B981',1),
  ('registration_status','WITHDRAWN','已退出','#9CA3AF',2),
  ('team_status','PENDING','待确认','#F59E0B',0),
  ('team_status','CONFIRMED','已确认','#10B981',1),
  ('match_status','SCHEDULED','待开打','#909399',0),
  ('match_status','IN_PROGRESS','进行中','#3B82F6',1),
  ('match_status','COMPLETED','已结束','#10B981',2),
  ('point_source','REGISTRATION','报名','#3B82F6',0),
  ('point_source','CHECK_IN','签到','#10B981',1),
  ('point_source','PLACEMENT','名次发分','#8B5CF6',2),
  ('point_source','MANUAL','管理员手动','#F59E0B',3),
  ('point_source','REDEEM','商城兑换','#06B6D4',4),
  ('point_source','ADJUST','系统纠错','#EF4444',5),
  ('notification_type','EVENT_IN_PROGRESS','赛事开始','#3B82F6',0),
  ('notification_type','EVENT_COMPLETED','赛事结束','#8B5CF6',1),
  ('notification_type','TEAM_INVITED','组队邀请','#10B981',2),
  ('notification_type','BANNER_PUBLISHED','Banner 发布','#06B6D4',3),
  ('notification_type','SYSTEM','系统通知','#EF4444',4);

-- track_term：extra_json 携带单位与各展示名（阶段 D 前端拉取后替换硬编码 terms）
INSERT INTO sys_dict_item (dict_code, item_key, item_label, sort, extra_json) VALUES
  ('track_term','STAR','积分排名',0,'{"unit":"积分","pointsName":"积分","tierName":"段位","rankingName":"积分排名"}'),
  ('track_term','PARTY','匹克豆',1,'{"unit":"匹克豆","pointsName":"匹克豆","tierName":"球友等级","rankingName":"匹克豆排名"}');
```

- [ ] **Step 2: 本地起服务验证迁移生效**

Run: `mvn spring-boot:run`（Flyway 自动执行 V18）
Expected: 启动无报错；`SELECT COUNT(*) FROM sys_dict_item;` = 34（9 个枚举字典共 32 项 + track_term 2 项）；`SELECT COUNT(*) FROM sys_dict;` = 10。

- [ ] **Step 3: Commit**

```bash
git add hey-pickler-server/src/main/resources/db/migration/V18__dict_platform.sql
git commit -m "feat(dict): V18 sys_dict/sys_dict_item 建表 + seed 9 字典与 track_term"
```

### Task 2: Entity——SysDict + SysDictItem（@TableLogic 软删）

**Files:**
- Create: `entity/SysDict.java`
- Create: `entity/SysDictItem.java`

- [ ] **Step 1: 写 SysDict entity**（对齐 `Banner` 注解模式 + `@TableLogic`）

```java
package com.heypickler.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_dict")
public class SysDict {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String dictCode;
    private String dictName;
    private String description;
    private String status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    @TableLogic
    private LocalDateTime deletedAt;
}
```

- [ ] **Step 2: 写 SysDictItem entity**

```java
package com.heypickler.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_dict_item")
public class SysDictItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String dictCode;
    private String itemKey;
    private String itemLabel;
    private String itemColor;
    private Integer sort;
    private String status;
    /** JSON 字符串：track_term 的 unit/pointsName/tierName/rankingName 等 */
    private String extraJson;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    @TableLogic
    private LocalDateTime deletedAt;
}
```

> 项目已在 `application.yml` 配置全局 `mybatis-plus.global-config.db-config.logic-delete-field: deletedAt`（`logic-delete-value: NOW()` / `logic-not-delete-value: NULL`）。字段级 `@TableLogic` 与全局配置叠加生效（参考 `entity/Event.java` 的 `deletedAt` 写法），新实体对齐该模式即可——`@TableLogic private LocalDateTime deletedAt` 会被 MyBatis-Plus 自动按 `IS NULL` 过滤、删除时写 `NOW()`。

- [ ] **Step 3: 编译验证**

Run: `mvn clean package -DskipTests`
Expected: BUILD SUCCESS。

- [ ] **Step 4: Commit**

```bash
git add hey-pickler-server/src/main/java/com/heypickler/entity/SysDict.java hey-pickler-server/src/main/java/com/heypickler/entity/SysDictItem.java
git commit -m "feat(dict): SysDict/SysDictItem 实体（@TableLogic 软删）"
```

### Task 3: Mapper——SysDictMapper + SysDictItemMapper

**Files:**
- Create: `mapper/SysDictMapper.java`
- Create: `mapper/SysDictItemMapper.java`

- [ ] **Step 1: 写两个 mapper**（对齐 `BannerMapper`）

```java
package com.heypickler.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.heypickler.entity.SysDict;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SysDictMapper extends BaseMapper<SysDict> {
}
```

```java
package com.heypickler.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.heypickler.entity.SysDictItem;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SysDictItemMapper extends BaseMapper<SysDictItem> {
}
```

- [ ] **Step 2: 编译 + Commit**

```bash
mvn clean package -DskipTests
git add hey-pickler-server/src/main/java/com/heypickler/mapper/SysDictMapper.java hey-pickler-server/src/main/java/com/heypickler/mapper/SysDictItemMapper.java
git commit -m "feat(dict): SysDict/SysDictItem Mapper"
```

**Chunk 1 review gate** → 派 plan-document-reviewer 评审 Chunk 1（见文末 review loop）。

---

## Chunk 2: 后端服务层（DictCacheService + DictService + 测试）

### Task 4: RedisKey 加字典 key 工厂

**Files:**
- Modify: `common/constant/RedisKey.java`

- [ ] **Step 1: 加三个静态方法**（对齐既有 `heypickler:` 前缀风格）

```java
    /** 全局字典版本号（任何字典写操作自增，前端据此增量刷新）。
     *  注：字典表数据量小（< 50 行），本期不做 per-dict / bundle 对象缓存——
     *  RedisConfig 关闭了 default typing，Jackson2JsonRedisSerializer 反序列化会得到
     *  LinkedHashMap 而非实体，访问 getter 会 ClassCastException，对象缓存坑大收益低。
     *  仅靠 version 让前端增量重拉 bundle；数据量增长时再以 StringRedisTemplate +
     *  显式反序列化加类型安全缓存。 */
    public static String dictVersion() {
        return PREFIX + "dict:version";
    }
```

- [ ] **Step 2: 编译 + Commit**

```bash
mvn clean package -DskipTests
git add hey-pickler-server/src/main/java/com/heypickler/common/constant/RedisKey.java
git commit -m "feat(dict): RedisKey 加 dictVersion 版本号工厂"
```

### Task 5: DictCacheService（全局版本号）

**Files:**
- Create: `service/DictCacheService.java`
- Create: `service/impl/DictCacheServiceImpl.java`
- Test: `src/test/java/com/heypickler/service/DictCacheServiceImplTest.java`

- [ ] **Step 1: 写接口**

```java
package com.heypickler.service;

/**
 * 字典版本号服务（详见 RedisKey.dictVersion 的设计说明）。
 * 本期不做 per-dict / bundle 对象缓存，只维护全局版本号。
 */
public interface DictCacheService {
    /** 全局版本号；首次返回 0 */
    long getVersion();
    /** 版本号自增，返回新值 */
    long incrementVersion();
}
```

- [ ] **Step 2: 写失败测试**（Mock RedisTemplate）

```java
package com.heypickler.service;

import com.heypickler.service.impl.DictCacheServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DictCacheServiceImplTest {

    @InjectMocks DictCacheServiceImpl service;
    @Mock RedisTemplate<String, Object> redisTemplate;
    @Mock ValueOperations<String, Object> valueOps;

    @Test
    void getVersion_absentReturnsZero() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("heypickler:dict:version")).thenReturn(null);
        assertEquals(0L, service.getVersion());
    }

    @Test
    void getVersion_numericValue() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("heypickler:dict:version")).thenReturn(7);
        assertEquals(7L, service.getVersion());
    }

    @Test
    void incrementVersion_returnsNewValue() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment("heypickler:dict:version")).thenReturn(8L);
        assertEquals(8L, service.incrementVersion());
    }
}
```

- [ ] **Step 3: 跑测试确认失败**

Run: `mvn test -Dtest=DictCacheServiceImplTest`
Expected: FAIL（DictCacheServiceImpl 未实现 / 符号缺失）。

- [ ] **Step 4: 写实现**

```java
package com.heypickler.service.impl;

import com.heypickler.common.constant.RedisKey;
import com.heypickler.service.DictCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DictCacheServiceImpl implements DictCacheService {

    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public long getVersion() {
        Object v = redisTemplate.opsForValue().get(RedisKey.dictVersion());
        if (v == null) return 0L;
        if (v instanceof Number) return ((Number) v).longValue();
        return Long.parseLong(v.toString());
    }

    @Override
    public long incrementVersion() {
        Long v = redisTemplate.opsForValue().increment(RedisKey.dictVersion());
        return v == null ? 0L : v;
    }
}
```

- [ ] **Step 5: 跑测试确认通过 + Commit**

```bash
mvn test -Dtest=DictCacheServiceImplTest
git add hey-pickler-server/src/main/java/com/heypickler/service/DictCacheService.java hey-pickler-server/src/main/java/com/heypickler/service/impl/DictCacheServiceImpl.java hey-pickler-server/src/test/java/com/heypickler/service/DictCacheServiceImplTest.java
git commit -m "feat(dict): DictCacheService 全局版本号（对象缓存 YAGNI 略）"
```

### Task 6: DictService 接口 + DTO/VO

**Files:**
- Create: `service/DictService.java`
- Create: `dto/admin/DictItemUpdateRequest.java`
- Create: `vo/SysDictVO.java` / `vo/SysDictItemVO.java` / `vo/DictBundleVO.java`

- [ ] **Step 1: 写接口**

```java
package com.heypickler.service;

import com.heypickler.dto.admin.DictItemUpdateRequest;
import com.heypickler.vo.DictBundleVO;
import com.heypickler.vo.SysDictItemVO;
import com.heypickler.vo.SysDictVO;
import java.util.List;

public interface DictService {
    List<SysDictVO> listDicts();
    List<SysDictItemVO> listItems(String dictCode);
    /** 批量更新某字典的 items（仅 label/color/sort/status 可改；item_key 不可改） */
    void updateItems(String dictCode, List<DictItemUpdateRequest> items);
    /** 聚合 bundle：全部字典 + items（带缓存） */
    DictBundleVO getBundle();
    /** 全局版本号 */
    long getVersion();
}
```

- [ ] **Step 2: 写 DTO**（item_key 透传用于定位，但 service 不接受改它）

```java
package com.heypickler.dto.admin;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DictItemUpdateRequest {
    @NotBlank
    private String itemKey;        // 仅用于定位行，不会写回
    private String itemLabel;
    private String itemColor;
    private Integer sort;
    private String status;         // ENABLED / DISABLED
}
```

- [ ] **Step 3: 写三个 VO**

```java
// vo/SysDictVO.java
package com.heypickler.vo;
import lombok.Data;
@Data
public class SysDictVO {
    private String dictCode;
    private String dictName;
    private String description;
    private String status;
}
```
```java
// vo/SysDictItemVO.java
package com.heypickler.vo;
import lombok.Data;
@Data
public class SysDictItemVO {
    private String itemKey;
    private String itemLabel;
    private String itemColor;
    private Integer sort;
    private String status;
    private String extraJson;
}
```
```java
// vo/DictBundleVO.java
package com.heypickler.vo;
import lombok.Data;
import java.util.List;
import java.util.Map;
@Data
public class DictBundleVO {
    private long version;
    /** dictCode → items */
    private Map<String, List<SysDictItemVO>> dicts;
}
```

- [ ] **Step 4: 编译 + Commit**

```bash
mvn clean package -DskipTests
git add hey-pickler-server/src/main/java/com/heypickler/service/DictService.java hey-pickler-server/src/main/java/com/heypickler/dto/admin/DictItemUpdateRequest.java hey-pickler-server/src/main/java/com/heypickler/vo/SysDictVO.java hey-pickler-server/src/main/java/com/heypickler/vo/SysDictItemVO.java hey-pickler-server/src/main/java/com/heypickler/vo/DictBundleVO.java
git commit -m "feat(dict): DictService 接口 + DTO/VO"
```

### Task 7: DictServiceImpl（CRUD + 缓存联动 + bundle）+ 测试

**Files:**
- Create: `service/impl/DictServiceImpl.java`
- Test: `src/test/java/com/heypickler/service/DictServiceImplTest.java`

- [ ] **Step 1: 写失败测试**（覆盖核心路径 + item_key 不可改 + 版本自增 + @Valid 兜底）

```java
package com.heypickler.service;

import com.heypickler.dto.admin.DictItemUpdateRequest;
import com.heypickler.entity.SysDict;
import com.heypickler.entity.SysDictItem;
import com.heypickler.mapper.SysDictItemMapper;
import com.heypickler.mapper.SysDictMapper;
import com.heypickler.service.impl.DictServiceImpl;
import com.heypickler.vo.DictBundleVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DictServiceImplTest {

    @BeforeAll
    static void warmLambdaCache() {
        Configuration cfg = new Configuration();
        MapperBuilderAssistant a = new MapperBuilderAssistant(cfg, "");
        a.setCurrentNamespace("com.heypickler.mapper.SysDictItemMapper");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(a, SysDictItem.class);
        a.setCurrentNamespace("com.heypickler.mapper.SysDictMapper");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(a, SysDict.class);
    }

    @InjectMocks DictServiceImpl service;
    @Mock SysDictMapper dictMapper;
    @Mock SysDictItemMapper itemMapper;
    @Mock DictCacheService cacheService;

    @Test
    void listDicts_returnsAllDicts() {
        SysDict d = new SysDict(); d.setDictCode("event_type"); d.setDictName("赛事类型");
        when(dictMapper.selectList(any())).thenReturn(List.of(d));
        assertEquals(1, service.listDicts().size());
    }

    @Test
    void listItems_readsFromDb() {
        SysDictItem item = new SysDictItem();
        item.setDictCode("event_type"); item.setItemKey("STAR");
        item.setItemLabel("竞技赛事"); item.setSort(0); item.setStatus("ENABLED");
        when(itemMapper.selectList(any())).thenReturn(List.of(item));

        var vos = service.listItems("event_type");

        assertEquals(1, vos.size());
        assertEquals("STAR", vos.get(0).getItemKey());
    }

    @Test
    void updateItems_onlyMutatesLabelColorSortStatus_keyNeverWritten() {
        SysDictItem existing = new SysDictItem();
        existing.setId(1L); existing.setDictCode("event_type");
        existing.setItemKey("STAR"); existing.setItemLabel("竞技赛事");
        when(itemMapper.selectOne(any())).thenReturn(existing);

        DictItemUpdateRequest req = new DictItemUpdateRequest();
        req.setItemKey("STAR"); req.setItemLabel("竞技"); req.setItemColor("#123456");
        req.setSort(5); req.setStatus("DISABLED");

        service.updateItems("event_type", List.of(req));

        ArgumentCaptor<SysDictItem> cap = ArgumentCaptor.forClass(SysDictItem.class);
        verify(itemMapper).updateById(cap.capture());
        SysDictItem saved = cap.getValue();
        assertEquals("竞技", saved.getItemLabel());   // 可改
        assertEquals("#123456", saved.getItemColor());
        assertEquals(5, saved.getSort());
        assertEquals("DISABLED", saved.getStatus());
        assertNull(saved.getItemKey());                 // 铁律：item_key 不得回写（防回归）
        verify(cacheService).incrementVersion();
    }

    @Test
    void updateItems_blankKey_throwsParamError() {
        // @Valid 对 List 元素不级联，service 兜底校验 itemKey 非空
        DictItemUpdateRequest req = new DictItemUpdateRequest();
        req.setItemKey("   ");
        assertThrows(com.heypickler.common.exception.BizException.class,
                () -> service.updateItems("event_type", List.of(req)));
        verifyNoInteractions(itemMapper);
    }

    @Test
    void updateItems_unknownKey_throwsNotFound() {
        when(itemMapper.selectOne(any())).thenReturn(null);
        DictItemUpdateRequest req = new DictItemUpdateRequest();
        req.setItemKey("NOPE");
        assertThrows(com.heypickler.common.exception.BizException.class,
                () -> service.updateItems("event_type", List.of(req)));
    }

    @Test
    void getBundle_aggregatesAllDictsAndVersion() {
        SysDict d = new SysDict(); d.setDictCode("event_type"); d.setDictName("赛事类型"); d.setStatus("ENABLED");
        when(dictMapper.selectList(any())).thenReturn(List.of(d));
        when(itemMapper.selectList(any())).thenReturn(List.of());
        when(cacheService.getVersion()).thenReturn(3L);

        DictBundleVO bundle = service.getBundle();

        assertEquals(3L, bundle.getVersion());
        assertTrue(bundle.getDicts().containsKey("event_type"));
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `mvn test -Dtest=DictServiceImplTest`
Expected: FAIL（DictServiceImpl 未实现）。

- [ ] **Step 3: 写实现**

```java
package com.heypickler.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.heypickler.common.exception.BizException;
import com.heypickler.common.exception.ErrorCode;
import com.heypickler.dto.admin.DictItemUpdateRequest;
import com.heypickler.entity.SysDict;
import com.heypickler.entity.SysDictItem;
import com.heypickler.mapper.SysDictItemMapper;
import com.heypickler.mapper.SysDictMapper;
import com.heypickler.service.DictCacheService;
import com.heypickler.service.DictService;
import com.heypickler.vo.DictBundleVO;
import com.heypickler.vo.SysDictItemVO;
import com.heypickler.vo.SysDictVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DictServiceImpl implements DictService {

    private final SysDictMapper dictMapper;
    private final SysDictItemMapper itemMapper;
    private final DictCacheService cacheService;

    @Override
    public List<SysDictVO> listDicts() {
        return dictMapper.selectList(new LambdaQueryWrapper<SysDict>()
                        .orderByAsc(SysDict::getId))
                .stream().map(this::toDictVO).collect(Collectors.toList());
    }

    @Override
    public List<SysDictItemVO> listItems(String dictCode) {
        // 字典表小（< 50 行），直接查 DB；对象缓存见 RedisKey.dictVersion 说明（YAGNI）
        return itemMapper.selectList(new LambdaQueryWrapper<SysDictItem>()
                .eq(SysDictItem::getDictCode, dictCode)
                .orderByAsc(SysDictItem::getSort))
                .stream().map(this::toItemVO).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void updateItems(String dictCode, List<DictItemUpdateRequest> items) {
        for (DictItemUpdateRequest req : items) {
            // @Valid 对 List 元素不级联，service 兜底校验 itemKey 非空
            if (req.getItemKey() == null || req.getItemKey().isBlank()) {
                throw new BizException(ErrorCode.PARAM_ERROR, "itemKey 不能为空");
            }
            SysDictItem existing = itemMapper.selectOne(new LambdaQueryWrapper<SysDictItem>()
                    .eq(SysDictItem::getDictCode, dictCode)
                    .eq(SysDictItem::getItemKey, req.getItemKey()));
            if (existing == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "字典项不存在: " + req.getItemKey());
            }
            // 铁律：只改 label/color/sort/status，item_key 永不回写
            SysDictItem patch = new SysDictItem();
            patch.setId(existing.getId());
            patch.setItemLabel(req.getItemLabel());
            patch.setItemColor(req.getItemColor());
            patch.setSort(req.getSort());
            patch.setStatus(req.getStatus());
            itemMapper.updateById(patch);
        }
        cacheService.incrementVersion();
    }

    @Override
    public DictBundleVO getBundle() {
        List<SysDict> dicts = dictMapper.selectList(new LambdaQueryWrapper<SysDict>()
                .eq(SysDict::getStatus, "ENABLED")
                .orderByAsc(SysDict::getId));
        Map<String, List<SysDictItemVO>> map = new LinkedHashMap<>();
        for (SysDict d : dicts) {
            map.put(d.getDictCode(), listItems(d.getDictCode()));
        }
        DictBundleVO bundle = new DictBundleVO();
        bundle.setVersion(cacheService.getVersion());
        bundle.setDicts(map);
        return bundle;
    }

    @Override
    public long getVersion() {
        return cacheService.getVersion();
    }

    private SysDictVO toDictVO(SysDict d) {
        SysDictVO vo = new SysDictVO();
        vo.setDictCode(d.getDictCode());
        vo.setDictName(d.getDictName());
        vo.setDescription(d.getDescription());
        vo.setStatus(d.getStatus());
        return vo;
    }

    private SysDictItemVO toItemVO(SysDictItem it) {
        SysDictItemVO vo = new SysDictItemVO();
        vo.setItemKey(it.getItemKey());
        vo.setItemLabel(it.getItemLabel());
        vo.setItemColor(it.getItemColor());
        vo.setSort(it.getSort());
        vo.setStatus(it.getStatus());
        vo.setExtraJson(it.getExtraJson());
        return vo;
    }
}
```

- [ ] **Step 4: 跑测试确认通过 + 覆盖率**

Run: `mvn test -Dtest=DictServiceImplTest`
Expected: 6 tests PASS。DictServiceImpl 行覆盖 ≥ 80%。

- [ ] **Step 5: Commit**

```bash
git add hey-pickler-server/src/main/java/com/heypickler/service/impl/DictServiceImpl.java hey-pickler-server/src/test/java/com/heypickler/service/DictServiceImplTest.java
git commit -m "feat(dict): DictServiceImpl CRUD + 缓存联动 + bundle 聚合（item_key 不可改）"
```

**Chunk 2 review gate** → 派 plan-document-reviewer 评审 Chunk 2。

---

## Chunk 3: Controller + AppAuthFilter bypass + 测试

### Task 8: AdminDictController + AppDictController

**Files:**
- Create: `controller/admin/AdminDictController.java`
- Create: `controller/app/AppDictController.java`

- [ ] **Step 1: 写 AdminDictController**（对齐 `AdminBannerController`；写操作 `@RequireRole`，自动被 `OperationLogAspect` 审计）

```java
package com.heypickler.controller.admin;

import com.heypickler.common.annotation.RequireRole;
import com.heypickler.common.enums.UserRole;
import com.heypickler.common.result.Result;
import com.heypickler.dto.admin.DictItemUpdateRequest;
import com.heypickler.service.DictService;
import com.heypickler.vo.DictBundleVO;
import com.heypickler.vo.SysDictItemVO;
import com.heypickler.vo.SysDictVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/dict")
@RequiredArgsConstructor
@Tag(name = "管理端-字典管理")
public class AdminDictController {

    private final DictService dictService;

    @GetMapping
    @Operation(summary = "字典目录列表")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN, UserRole.OPERATOR})
    public Result<List<SysDictVO>> listDicts() {
        return Result.ok(dictService.listDicts());
    }

    @GetMapping("/{dictCode}/items")
    @Operation(summary = "某字典的项列表")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN, UserRole.OPERATOR})
    public Result<List<SysDictItemVO>> listItems(@PathVariable String dictCode) {
        return Result.ok(dictService.listItems(dictCode));
    }

    @PutMapping("/{dictCode}/items")
    @Operation(summary = "批量更新字典项（label/color/sort/status）")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN})
    public Result<Void> updateItems(@PathVariable String dictCode,
                                    @RequestBody @Valid List<DictItemUpdateRequest> items) {
        dictService.updateItems(dictCode, items);
        return Result.ok();
    }

    @GetMapping("/bundle")
    @Operation(summary = "聚合 bundle（全部字典+items，带版本号）")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN, UserRole.OPERATOR})
    public Result<DictBundleVO> bundle() {
        return Result.ok(dictService.getBundle());
    }

    @GetMapping("/version")
    @Operation(summary = "全局字典版本号")
    @RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN, UserRole.OPERATOR})
    public Result<Map<String, Long>> version() {
        return Result.ok(Map.of("version", dictService.getVersion()));
    }
}
```

- [ ] **Step 2: 写 AppDictController**（匿名只读 bundle；bypass 在 Task 10 配）

```java
package com.heypickler.controller.app;

import com.heypickler.common.result.Result;
import com.heypickler.service.DictService;
import com.heypickler.vo.DictBundleVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/app/dict")
@RequiredArgsConstructor
@Tag(name = "小程序端-字典")
public class AppDictController {

    private final DictService dictService;

    @GetMapping("/bundle")
    @Operation(summary = "字典聚合 bundle（匿名，供小程序启动拉取）")
    public Result<DictBundleVO> bundle() {
        return Result.ok(dictService.getBundle());
    }
}
```

- [ ] **Step 3: 编译 + Commit**

```bash
mvn clean package -DskipTests
git add hey-pickler-server/src/main/java/com/heypickler/controller/admin/AdminDictController.java hey-pickler-server/src/main/java/com/heypickler/controller/app/AppDictController.java
git commit -m "feat(dict): AdminDictController + AppDictController（匿名 bundle）"
```

### Task 9: AppAuthFilter bypass——/api/app/dict 匿名

**Files:**
- Modify: `filter/AppAuthFilter.java:28-32`

- [ ] **Step 1: 把 `/api/app/dict` 加入 `PUBLIC_GET_PREFIXES`**

```java
    private static final Set<String> PUBLIC_GET_PREFIXES = Set.of(
            "/api/app/banners",
            "/api/app/events",
            "/api/app/rankings",
            "/api/app/dict"
    );
```

> 小程序登录页就要显示品牌/字典（无 token），故 bundle 必须匿名。`shouldNotFilter` 现有逻辑：GET + path startsWith prefix → bypass（`/my-team` 例外不适用 dict）。

- [ ] **Step 2: 写 AppAuthFilter 单测验证 dict GET 被跳过**

`shouldNotFilter` 是 `protected`，测试类必须放在同包 `com.heypickler.filter`。直接 new filter（构造注入 mock 依赖）+ `MockHttpServletRequest`（来自 spring-boot-starter-test 的 spring-test）。

```java
// src/test/java/com/heypickler/filter/AppAuthFilterBypassTest.java
package com.heypickler.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heypickler.common.util.JwtUtil;
import com.heypickler.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class AppAuthFilterBypassTest {

    private final AppAuthFilter filter =
            new AppAuthFilter(mock(JwtUtil.class), new ObjectMapper(), mock(UserMapper.class));

    @Test
    void dictBundleGetIsBypassed() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/app/dict/bundle");
        assertTrue(filter.shouldNotFilter(req));
    }

    @Test
    void dictPostIsNotBypassed() {
        // bypass 仅限 GET；POST 仍走鉴权
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/app/dict/bundle");
        assertFalse(filter.shouldNotFilter(req));
    }

    @Test
    void adminPathIsBypassed() {
        // 非 /api/app/ 前缀整体跳过（AppAuthFilter 只管 app 端）
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/admin/dict");
        assertTrue(filter.shouldNotFilter(req));
    }
}
```

- [ ] **Step 3: 跑测试 + Commit**

```bash
mvn test -Dtest=AppAuthFilterBypassTest
git add hey-pickler-server/src/main/java/com/heypickler/filter/AppAuthFilter.java hey-pickler-server/src/test/java/com/heypickler/filter/AppAuthFilterBypassTest.java
git commit -m "feat(dict): AppAuthFilter bypass /api/app/dict（bundle 匿名可访问）"
```

### Task 10: Controller 层测试 + 全量回归

- [ ] **Step 1: AdminDictControllerTest**（方法级，mock DictService，验证委托 + Result 包装）

```java
// src/test/java/com/heypickler/controller/admin/AdminDictControllerTest.java
package com.heypickler.controller.admin;

import com.heypickler.common.result.Result;
import com.heypickler.service.DictService;
import com.heypickler.vo.DictBundleVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminDictControllerTest {

    @InjectMocks AdminDictController controller;
    @Mock DictService dictService;

    @Test
    void listDicts_delegatesAndWraps() {
        when(dictService.listDicts()).thenReturn(List.of());
        assertEquals(0, controller.listDicts().getData().size());
    }

    @Test
    void listItems_delegatesByCode() {
        when(dictService.listItems("event_type")).thenReturn(List.of());
        controller.listItems("event_type");
        verify(dictService).listItems("event_type");
    }

    @Test
    void updateItems_delegates() {
        controller.updateItems("event_type", List.of());
        verify(dictService).updateItems(eq("event_type"), anyList());
    }

    @Test
    void bundle_delegates() {
        DictBundleVO vo = new DictBundleVO();
        when(dictService.getBundle()).thenReturn(vo);
        assertSame(vo, controller.bundle().getData());
    }

    @Test
    void version_returnsVersionMap() {
        when(dictService.getVersion()).thenReturn(5L);
        Result<Map<String, Long>> r = controller.version();
        assertEquals(0, r.getCode());
        assertEquals(5L, r.getData().get("version"));
    }
}
```

- [ ] **Step 2: AppDictControllerTest**（验证 `bundle()` 委托 + Result.ok）

```java
// src/test/java/com/heypickler/controller/app/AppDictControllerTest.java
package com.heypickler.controller.app;

import com.heypickler.service.DictService;
import com.heypickler.vo.DictBundleVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppDictControllerTest {

    @InjectMocks AppDictController controller;
    @Mock DictService dictService;

    @Test
    void bundle_delegatesAndReturnsData() {
        DictBundleVO vo = new DictBundleVO();
        when(dictService.getBundle()).thenReturn(vo);
        assertSame(vo, controller.bundle().getData());
        assertEquals(0, controller.bundle().getCode());
    }
}
```

- [ ] **Step 3: 全量单测回归**

Run: `mvn test -Dtest='!*IntegrationTest'`
Expected: 全绿，字典相关新类覆盖率 ≥ 80% instruction。

- [ ] **Step 4: Commit**

```bash
git add hey-pickler-server/src/test/java/com/heypickler/controller/admin/AdminDictControllerTest.java hey-pickler-server/src/test/java/com/heypickler/controller/app/AppDictControllerTest.java
git commit -m "test(dict): AdminDictController/AppDictController 端点测试"
```

**Chunk 3 review gate** → 派 plan-document-reviewer 评审 Chunk 3。

---

## Chunk 4: admin 字典管理页

### Task 11: api/dict.ts + types

**Files:**
- Create: `hey-pickler-admin/src/api/dict.ts`
- Modify: `hey-pickler-admin/src/types/index.ts`（追加 SysDictItem）

- [ ] **Step 1: 写 api 模块**（对齐 `api/banners.ts`，request 相对 `/api/admin`）

```typescript
import request from './request'
import type { ApiResponse } from '@/types'

export interface SysDictVO {
  dictCode: string
  dictName: string
  description: string
  status: string
}

export interface SysDictItemVO {
  itemKey: string
  itemLabel: string
  itemColor: string | null
  sort: number
  status: string
  extraJson: string | null
}

export interface DictBundleVO {
  version: number
  dicts: Record<string, SysDictItemVO[]>
}

export interface DictItemUpdateRequest {
  itemKey: string
  itemLabel: string
  itemColor: string | null
  sort: number
  status: string
}

export const getDictList = () =>
  request.get<unknown, ApiResponse<SysDictVO[]>>('/dict')

export const getDictItems = (dictCode: string) =>
  request.get<unknown, ApiResponse<SysDictItemVO[]>>(`/dict/${dictCode}/items`)

export const updateDictItems = (dictCode: string, items: DictItemUpdateRequest[]) =>
  request.put<unknown, ApiResponse<void>>(`/dict/${dictCode}/items`, items)

export const getDictBundle = () =>
  request.get<unknown, ApiResponse<DictBundleVO>>('/dict/bundle')

export const getDictVersion = () =>
  request.get<unknown, ApiResponse<{ version: number }>>('/dict/version')
```

> 类型同时导出供其它模块复用；`types/index.ts` 追加 `export interface SysDictItem` 别名（可选）。

- [ ] **Step 2: lint**

Run: `cd hey-pickler-admin && npm run lint`
Expected: 无 error。

- [ ] **Step 3: Commit**

```bash
git add hey-pickler-admin/src/api/dict.ts hey-pickler-admin/src/types/index.ts
git commit -m "feat(dict): admin api/dict.ts + 类型"
```

### Task 12: 字典管理页 DictListView.vue

**Files:**
- Create: `hey-pickler-admin/src/views/dict/DictListView.vue`

- [ ] **Step 1: 写页面**（左字典列表 + 右 item 编辑表；对齐 `BannerListView.vue` 的 page-header/card/table 模式；item_key 只读、label/color/sort/status 可编）

页面结构：
- 左栏：`el-menu` / `el-table` 列出 `getDictList()` 的字典目录，点击切换右栏。
- 右栏：当前字典的 items 表格，列：`item_key`（只读）、`item_label`（input）、`item_color`（`el-color-picker`）、`sort`（input-number）、`status`（switch ENABLED/DISABLED）。
- 底部「保存」按钮 → `updateDictItems(currentDictCode, rows)`，成功后 `ElMessage.success` + 重新拉取。
- 颜色列用 `el-color-picker`，预览列用 `:color`+`effect="dark"` 的 `el-tag`（避免重蹈「形式」列 plain 冲突覆辙）。

关键代码骨架（节选，完整实现按 `BannerListView.vue` 骨架补全 script setup + 样式）：

```vue
<template>
  <div>
    <div class="page-header"><h1>字典管理</h1></div>
    <div class="dict-layout" style="display:flex;gap:16px">
      <!-- 左：字典目录 -->
      <el-card class="dict-nav" style="width:240px">
        <el-table :data="dicts" highlight-current-row @current-change="onPickDict" size="small">
          <el-table-column prop="dictName" label="字典" />
        </el-table>
      </el-card>
      <!-- 右：item 编辑 -->
      <el-card class="dict-items" style="flex:1">
        <template v-if="currentDict">
          <div style="margin-bottom:12px;display:flex;justify-content:space-between;align-items:center">
            <span>{{ currentDict.dictName }} <el-tag size="small" type="info">item_key 不可改，仅可改展示名/颜色/排序/启停</el-tag></span>
            <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
          </div>
          <el-table :data="rows" size="small">
            <el-table-column prop="itemKey" label="key" width="160" />
            <el-table-column label="展示名" width="160">
              <template #default="{ row }"><el-input v-model="row.itemLabel" size="small" /></template>
            </el-table-column>
            <el-table-column label="颜色" width="120">
              <template #default="{ row }">
                <el-color-picker v-model="row.itemColor" size="small" />
                <el-tag :color="row.itemColor" effect="dark" size="small" style="margin-left:6px">{{ row.itemLabel }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="排序" width="100">
              <template #default="{ row }"><el-input-number v-model="row.sort" :min="0" controls-position="right" size="small" /></template>
            </el-table-column>
            <el-table-column label="启用" width="80">
              <template #default="{ row }">
                <el-switch v-model="row.status" active-value="ENABLED" inactive-value="DISABLED" />
              </template>
            </el-table-column>
          </el-table>
        </template>
        <el-empty v-else description="请选择左侧字典" />
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getDictList, getDictItems, updateDictItems, type SysDictVO, type DictItemUpdateRequest } from '@/api/dict'

const dicts = ref<SysDictVO[]>([])
const currentDict = ref<SysDictVO | null>(null)
const rows = ref<DictItemUpdateRequest[]>([])
const saving = ref(false)

onMounted(async () => {
  try {
    dicts.value = (await getDictList()).data || []
  } catch {
    ElMessage.error('字典目录加载失败')
  }
})

async function onPickDict(d: SysDictVO | null) {
  if (!d) return
  currentDict.value = d
  try {
    const items = (await getDictItems(d.dictCode)).data || []
    rows.value = items.map(it => ({
      itemKey: it.itemKey,
      itemLabel: it.itemLabel,
      itemColor: it.itemColor,
      sort: it.sort,
      status: it.status
    }))
  } catch {
    ElMessage.error('字典项加载失败')
  }
}

async function handleSave() {
  if (!currentDict.value) return
  saving.value = true
  try {
    await updateDictItems(currentDict.value.dictCode, rows.value)
    ElMessage.success('已保存')
  } catch (e) {
    ElMessage.error('保存失败')
  } finally { saving.value = false }
}
</script>

<style scoped>
.page-header { margin-bottom: 16px; }
.page-header h1 { margin: 0; font-size: 20px; }
.dict-layout { align-items: flex-start; }
.dict-items :deep(.el-table .cell) { display: flex; align-items: center; }
</style>
```

- [ ] **Step 2: lint**

Run: `cd hey-pickler-admin && npm run lint`
Expected: 无 error（`no-explicit-any` 可 warn）。

- [ ] **Step 3: Commit**

```bash
git add hey-pickler-admin/src/views/dict/DictListView.vue
git commit -m "feat(dict): admin 字典管理页（左目录右编辑，item_key 只读）"
```

### Task 13: 路由 + 侧栏注册

**Files:**
- Modify: `hey-pickler-admin/src/router/index.ts`

- [ ] **Step 1: 在 `children` 数组（系统分组）加路由**

```typescript
        {
          path: 'dict',
          name: 'Dict',
          component: () => import('@/views/dict/DictListView.vue'),
          meta: { title: '字典管理', icon: 'Collection', group: '系统' }
        },
```

> 插入位置：`admin-logs` 之后、`notifications` 之前（同属「系统」分组）。`AppSidebar` 若按 `meta.group` 自动聚合，则无需改侧栏组件；若硬编码菜单则同步追加一项。

- [ ] **Step 2: lint + 构建**

Run: `cd hey-pickler-admin && npm run lint && npm run build`
Expected: 构建成功。

- [ ] **Step 3: Commit**

```bash
git add hey-pickler-admin/src/router/index.ts
git commit -m "feat(dict): 注册 /dict 路由 + 系统分组侧栏"
```

### Task 14: 端到端验证

- [ ] **Step 1: 起后端 + admin**

后端 `mvn spring-boot:run`；admin `npm run dev`。
- [ ] **Step 2: 登录 admin（admin/admin123）→ 系统分组 → 字典管理**
- [ ] **Step 3: 选「赛事形式」字典，改「混打」颜色为粉色、展示名仍「混打」，保存 → 刷新页面颜色生效（`effect="dark"` 白字清晰可读）。
- [ ] **Step 4: 匿名拉 bundle**——浏览器或 curl `GET http://localhost:8080/api/app/dict/bundle`（不带 token）应返回 200 + 全字典 JSON。
- [ ] **Step 5: 验证 item_key 不可改**——尝试在保存请求里改 itemKey（前端已禁，后端 `updateItems` 也只 patch 非 key 字段，双重保险）。
- [ ] **Step 6: 全量回归**——`mvn test -Dtest='!*IntegrationTest'` + admin `npm run lint && npm run build`，记录结果。

**Chunk 4 review gate** → 派 plan-document-reviewer 评审 Chunk 4。

---

## 完成标准（Definition of Done）

- [ ] V18 迁移建表 + seed，本地 & CI MySQL 8 通过。
- [ ] `DictService`/`DictCacheService` 单测全绿，覆盖率 ≥ 80% instruction。
- [ ] `/api/admin/dict/**` 五端点 + `/api/app/dict/bundle` 匿名端点可用；`AppAuthFilter` bypass 生效。
- [ ] admin 字典管理页可改 label/color/sort/status 并持久化，颜色 `effect="dark"` 清晰可读。
- [ ] 铁律守住：`item_key` 在前端只读 + 后端 patch 不回写。
- [ ] backend & frontend CI job 全绿。
- [ ] 不越界——未触及 tier_config / 三端硬编码替换 / 徽章 / 品牌（留待阶段 2–5）。

---

## 阶段 1 完成后的衔接

阶段 1 只搭了字典读基础设施与 admin 配置入口。后续阶段（各自独立 spec→plan）：
- **阶段 2（B）**：`tier_config` + `TierResolver`，替换 `TierProperties`；seed PARTY 球友称号系。
- **阶段 3（D）**：admin/wxapp 启动拉 bundle，替换 `constants/terms.ts`、`utils/util.js` 硬编码；版本号增量刷新。
- **阶段 4（E）/ 5（品牌）**：徽章视觉 / 品牌层。

本计划不实现这些，但 `getBundle()` 已为它们准备好数据出口。
