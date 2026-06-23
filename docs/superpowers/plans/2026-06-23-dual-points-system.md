# 双积分体系（战力 / 活力）实施计划

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 STAR/PARTY 双积分体系重命名为战力/活力，段位扩到 6 档，新增积分来源分类、按类型独立的赛季机制（含归档可查）、发分解耦（PointService），并为积分商城预留 PointWallet 接口。

**Architecture:** 后端 `STAR/PARTY` 字段名不变，仅前端文案替换为常量映射；新增 `PointService` 承接发分（原耦合在 `RankingService`）；段位/来源/赛季通过枚举 + `@ConfigurationProperties` 配置 + 新表实现；Flyway `V11` 迁移历史数据（含 3 档→6 档 tier 重算）。

**Tech Stack:** Spring Boot 3.2 / MyBatis-Plus / MySQL 8 / Flyway / JUnit5 + Mockito（后端）；Vue3 + TS + Vite + Element Plus（admin）；原生 WXML/WXSS/JS（wxapp）。

**Spec:** `docs/superpowers/specs/2026-06-23-dual-points-system-design.md`（v2，Approved）

---

## File Structure

### 后端新增
| 文件 | 职责 |
|---|---|
| `common/enums/PointSource.java` | 积分来源枚举（6 值） |
| `config/TierProperties.java` | 段位配置（`@ConfigurationProperties`，6 档阈值/名） |
| `entity/Season.java` | 赛季实体 |
| `mapper/SeasonMapper.java` | 赛季 Mapper |
| `service/PointService.java` + `impl/PointServiceImpl.java` | 发分服务（承接原 `enterPoints`） |
| `service/PointWallet.java` | 商城预留窄接口（getBalance 实现 / deduct 仅签名） |
| `service/SeasonService.java` + `impl/SeasonServiceImpl.java` | 赛季管理（列表/新建/切换/归档查询） |
| `dto/admin/SeasonCreateRequest.java` | 赛季新建 DTO |
| `vo/SeasonVO.java` | 赛季 VO |
| `controller/admin/AdminSeasonController.java` | 赛季管理 API |
| `db/migration/V11__dual_points_system.sql` | 数据迁移 |

### 后端修改
| 文件 | 改动 |
|---|---|
| `service/RankingService.java` | **删 `enterPoints` 方法签名** |
| `service/impl/RankingServiceImpl.java` | 移 `enterPoints`→PointService；`calculateTier` 读 `TierProperties`；`refreshRankings(type, seasonCode)` delete 加 season 维度；清缓存遍历 `tier.keys+null` |
| `listener/PointChangeListener.java` | 嵌套 `PointChangeEvent` record 加 `String seasonCode` 字段 |
| `controller/admin/AdminEventController.java` | `enterPoints` 改调 `PointService` |
| `controller/admin/AdminRankingController.java` | `enterPoints` 改调 `PointService` |
| `controller/admin/AdminDashboardController.java` | tier 分组兜底 `"SHINING"` 改读 `TierProperties` 最低档 |
| `vo/RankingVO.java`、`vo/UserVO.java`（含 starTier/partyTier 的 VO） | +`tierName`（中文档名） |
| `resources/application.yml` | +`hey-pickler.tier` 配置块 |

### 前端 wxapp
| 文件 | 改动 |
|---|---|
| `utils/terms.js`（新） | 术语常量 `{STAR:{type,points,tier}, PARTY:{...}}` |
| `pages/profile/*`、`pages/ranking/*`、`pages/event-detail/*`、`pages/my-events/*`、`components/event-card/*`、`components/tier-badge/*` | 文案替换 + 段位 6 档展示 |

### 前端 admin
| 文件 | 改动 |
|---|---|
| `src/constants/terms.ts`（新） | 术语常量 |
| `src/api/seasons.ts`（新） | 赛季 API 客户端 |
| `src/views/seasons/*`（新） | 赛季管理页（列表/新建/切换/归档排名） |
| `src/router/index.ts` | +赛季路由 |
| `views/events/EventFormDialog.vue`、`views/users/UserDetailDrawer.vue`、`views/rankings/PointEntryDialog.vue`、`views/rankings/*`、`views/dashboard/*` | 文案替换 + 段位 6 档 |

---

## Chunk 1: 后端配置与数据层（spec step 1-2）

> 强依赖声明：Chunk 1 的 V11（tier 重算到 6 档）必须与 Chunk 2 Task 2.3（`calculateTier` 读配置 + 清缓存参数化）**同一 PR 合并**，否则 migration 执行后 tier 已是 6 档但代码仍按 3 档清缓存/计算，产生中间态混乱。

### Task 1.1: `PointSource` 枚举

**Files:**
- Create: `hey-pickler-server/src/main/java/com/heypickler/common/enums/PointSource.java`

- [ ] **Step 1: 创建枚举**

```java
package com.heypickler.common.enums;

import lombok.Getter;

@Getter
public enum PointSource {
    REGISTRATION("报名"),
    CHECK_IN("签到"),
    PLACEMENT("名次"),
    MANUAL("管理员手动"),
    REDEEM("商城兑换"),
    ADJUST("系统纠错");

    private final String label;

    PointSource(String label) {
        this.label = label;
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `mvn -f hey-pickler-server/pom.xml compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add hey-pickler-server/src/main/java/com/heypickler/common/enums/PointSource.java
git commit -m "feat(points): 新增 PointSource 积分来源枚举"
```

---

### Task 1.2: `TierProperties` 配置 + yml

**Files:**
- Create: `hey-pickler-server/src/main/java/com/heypickler/config/TierProperties.java`
- Create: `hey-pickler-server/src/test/java/com/heypickler/config/TierPropertiesTest.java`
- Modify: `hey-pickler-server/src/main/resources/application.yml`

- [ ] **Step 1: 写失败测试**

```java
package com.heypickler.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class TierPropertiesTest {

    @Test
    void bindsStarThresholds_andTierForPoints() {
        var source = new MapConfigurationPropertySource(Map.of(
            "hey-pickler.tier.keys[0]", "BRONZE",
            "hey-pickler.tier.keys[1]", "SILVER",
            "hey-pickler.tier.keys[2]", "GOLD",
            "hey-pickler.tier.keys[3]", "PLATINUM",
            "hey-pickler.tier.keys[4]", "DIAMOND",
            "hey-pickler.tier.keys[5]", "MASTER",
            "hey-pickler.tier.star.thresholds[0]", "0",
            "hey-pickler.tier.star.thresholds[1]", "500",
            "hey-pickler.tier.star.thresholds[2]", "1200",
            "hey-pickler.tier.star.thresholds[3]", "2500",
            "hey-pickler.tier.star.thresholds[4]", "5000",
            "hey-pickler.tier.star.thresholds[5]", "10000",
            "hey-pickler.tier.party.thresholds[0]", "0",
            "hey-pickler.tier.party.thresholds[1]", "200",
            "hey-pickler.tier.party.thresholds[2]", "500",
            "hey-pickler.tier.party.thresholds[3]", "1200",
            "hey-pickler.tier.party.thresholds[4]", "2500",
            "hey-pickler.tier.party.thresholds[5]", "5000"
        ));
        TierProperties props = Binder.get(source).bind("hey-pickler.tier", TierProperties.class).get();

        assertEquals("BRONZE", props.keyFor(0, "STAR"));
        assertEquals("SILVER", props.keyFor(499, "STAR"));
        assertEquals("GOLD", props.keyFor(1200, "STAR"));
        assertEquals("MASTER", props.keyFor(10000, "STAR"));
        assertEquals("BRONZE", props.keyFor(0, "PARTY"));
        assertEquals("GOLD", props.keyFor(500, "PARTY"));
        assertEquals(6, props.getKeys().size());
    }
}
```

- [ ] **Step 2: 跑测试，确认失败**

Run: `mvn -f hey-pickler-server/pom.xml test -Dtest=TierPropertiesTest`
Expected: FAIL（`TierProperties` 类不存在 + `keyFor` 方法未定义）

- [ ] **Step 3: 写最小实现**

```java
package com.heypickler.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "hey-pickler.tier")
public class TierProperties {
    private List<String> keys;          // [BRONZE, SILVER, GOLD, PLATINUM, DIAMOND, MASTER]
    private List<String> names;         // [青铜, 白银, 黄金, 铂金, 钻石, 王者]
    private StarTier star;
    private PartyTier party;

    @Data
    public static class StarTier { private List<Integer> thresholds; }
    @Data
    public static class PartyTier { private List<Integer> thresholds; }

    /** 返回某积分在某类型下对应的档位 key；type 非 PARTY 一律按 STAR 阈值 */
    public String keyFor(int points, String type) {
        List<Integer> th = ("PARTY".equals(type) && party != null) ? party.getThresholds() : star.getThresholds();
        String result = keys.get(0);
        for (int i = 0; i < keys.size(); i++) {
            if (points >= th.get(i)) result = keys.get(i);
        }
        return result;
    }

    /** 中文档名 */
    public String nameFor(String key) {
        int i = keys.indexOf(key);
        return i >= 0 ? names.get(i) : names.get(0);
    }

    /** 所有 key + null（用于清 Redis 缓存） */
    public List<String> cacheKeysWithNull() {
        var all = new java.util.ArrayList<>(keys);
        all.add(null);
        return all;
    }
}
```

> 注：`@ConfigurationProperties + @Component` 在 Spring Boot 3.2 可直接生效。项目此前**无** `@ConfigurationProperties` 先例（现有配置用 `@Value`，如 `AppConfig`/`RateLimitFilter`），本任务为新引入该模式；若 review 倾向标准写法，可改为在 `@SpringBootApplication` 加 `@ConfigurationPropertiesScan` 并去掉 `@Component`。

- [ ] **Step 4: 加 yml 配置**

在 `application.yml` 的 `hey-pickler:` 块下追加：

```yaml
hey-pickler:
  # ...existing config...
  tier:
    keys: [BRONZE, SILVER, GOLD, PLATINUM, DIAMOND, MASTER]
    names: [青铜, 白银, 黄金, 铂金, 钻石, 王者]
    star:
      thresholds: [0, 500, 1200, 2500, 5000, 10000]
    party:
      thresholds: [0, 200, 500, 1200, 2500, 5000]
```

- [ ] **Step 5: 跑测试，确认通过**

Run: `mvn -f hey-pickler-server/pom.xml test -Dtest=TierPropertiesTest`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add hey-pickler-server/src/main/java/com/heypickler/config/TierProperties.java \
        hey-pickler-server/src/test/java/com/heypickler/config/TierPropertiesTest.java \
        hey-pickler-server/src/main/resources/application.yml
git commit -m "feat(points): 新增 TierProperties 段位配置 (6 档, yml 可配)"
```

---

### Task 1.3: `Season` 实体 + Mapper

**Files:**
- Create: `hey-pickler-server/src/main/java/com/heypickler/entity/Season.java`
- Create: `hey-pickler-server/src/main/java/com/heypickler/mapper/SeasonMapper.java`

- [ ] **Step 1: 创建实体**

```java
package com.heypickler.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("season")
public class Season {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String type;          // STAR | PARTY
    private String code;          // 2026-Q2
    private String name;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;        // CURRENT | ARCHIVED
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
```

- [ ] **Step 2: 创建 Mapper**

```java
package com.heypickler.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.heypickler.entity.Season;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SeasonMapper extends BaseMapper<Season> {
}
```

- [ ] **Step 3: 编译验证**

Run: `mvn -f hey-pickler-server/pom.xml compile`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add hey-pickler-server/src/main/java/com/heypickler/entity/Season.java \
        hey-pickler-server/src/main/java/com/heypickler/mapper/SeasonMapper.java
git commit -m "feat(season): 新增 Season 实体与 Mapper"
```

---

### Task 1.4: V11 migration

**Files:**
- Create: `hey-pickler-server/src/main/resources/db/migration/V11__dual_points_system.sql`

> 执行前确认 migration head 仍为 V10。tier 重算用 SQL `CASE WHEN`（阈值快照，与本 PR 内 yml 阈值保持一致；后续阈值调整走 yml + 重启，migration 不再重跑，无漂移风险）。

- [ ] **Step 1: 写 migration**

```sql
-- 1) season 表（按类型独立结算周期）
CREATE TABLE season (
  id         BIGINT NOT NULL AUTO_INCREMENT,
  type       VARCHAR(8)  NOT NULL,
  code       VARCHAR(16) NOT NULL,
  name       VARCHAR(32),
  start_date DATE,
  end_date   DATE,
  status     VARCHAR(8)  NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_type_code (type, code)
);

-- 2) 初始化两条 CURRENT（沿用现有 CURRENT_SEASON='2026-Q2'）
INSERT INTO season (type, code, name, status) VALUES
  ('STAR',  '2026-Q2', '2026 第二季度·战力', 'CURRENT'),
  ('PARTY', '2026-Q2', '2026 第二季度·活力', 'CURRENT');

-- 3) point_record 加来源 + 赛季
ALTER TABLE point_record ADD COLUMN source     VARCHAR(16) NOT NULL DEFAULT 'MANUAL';
ALTER TABLE point_record ADD COLUMN season_code VARCHAR(16);
UPDATE point_record SET season_code = '2026-Q2' WHERE season_code IS NULL;
CREATE INDEX idx_type_season ON point_record (type, season_code);
-- 不再单建 idx_user(user_id)：V1 已有 idx_user_created(user_id, created_at DESC)，
-- 最左前缀已覆盖 user_id 等值查询（spec §5.1 的 idx_user 经核冗余，勘误不建）。

-- 4) ranking.tier 重算（覆盖旧值 SHINING/SUPER/LEGEND 三档 → 6 档）—— STAR 按 star_points
UPDATE ranking r JOIN user u ON r.user_id = u.id
SET r.tier = CASE
  WHEN u.star_points >= 10000 THEN 'MASTER'
  WHEN u.star_points >= 5000  THEN 'DIAMOND'
  WHEN u.star_points >= 2500  THEN 'PLATINUM'
  WHEN u.star_points >= 1200  THEN 'GOLD'
  WHEN u.star_points >= 500   THEN 'SILVER'
  ELSE 'BRONZE' END
WHERE r.type = 'STAR';

UPDATE ranking r JOIN user u ON r.user_id = u.id
SET r.tier = CASE
  WHEN u.party_points >= 5000 THEN 'MASTER'
  WHEN u.party_points >= 2500 THEN 'DIAMOND'
  WHEN u.party_points >= 1200 THEN 'PLATINUM'
  WHEN u.party_points >= 500  THEN 'GOLD'
  WHEN u.party_points >= 200  THEN 'SILVER'
  ELSE 'BRONZE' END
WHERE r.type = 'PARTY';

-- 5) user.star_tier / party_tier 同步重算
UPDATE user SET star_tier = CASE
  WHEN star_points >= 10000 THEN 'MASTER'
  WHEN star_points >= 5000  THEN 'DIAMOND'
  WHEN star_points >= 2500  THEN 'PLATINUM'
  WHEN star_points >= 1200  THEN 'GOLD'
  WHEN star_points >= 500   THEN 'SILVER'
  ELSE 'BRONZE' END;

UPDATE user SET party_tier = CASE
  WHEN party_points >= 5000 THEN 'MASTER'
  WHEN party_points >= 2500 THEN 'DIAMOND'
  WHEN party_points >= 1200 THEN 'PLATINUM'
  WHEN party_points >= 500  THEN 'GOLD'
  WHEN party_points >= 200  THEN 'SILVER'
  ELSE 'BRONZE' END;
```

- [ ] **Step 2: 启动应用，验证 Flyway 执行 V11**（本地手动验证，需本地 MySQL+Redis 在跑；CI 环境无 DB，由 Task 4.1 集成测试覆盖）

Run: `mvn -f hey-pickler-server/pom.xml spring-boot:run`（后台），等启动。
Expected 日志含：`Migrating schema "hey_pickler" to version "11 - dual points system"` + `Successfully applied 1 migration ... now at version v11` + `Started HeyPicklerApplication`

- [ ] **Step 3: 查库验证**

```bash
mysql -uroot -proot hey_pickler -e "
SELECT version, success FROM flyway_schema_history WHERE version='11';
SELECT type, code, status FROM season;
SELECT source, season_code, COUNT(*) FROM point_record GROUP BY source, season_code;
SELECT tier, COUNT(*) FROM ranking GROUP BY tier;
SELECT star_tier, COUNT(*) FROM user GROUP BY star_tier;"
```
Expected：V11 success=1；season 两条 CURRENT；point_record 全 source=MANUAL/season_code=2026-Q2；ranking.tier 只剩 6 档 key（无 LEGEND/SUPER/SHINING 残留）。

- [ ] **Step 4: Commit**

```bash
git add hey-pickler-server/src/main/resources/db/migration/V11__dual_points_system.sql
git commit -m "feat(points): V11 migration - season 表 + point_record 来源/赛季 + tier 6 档重算"
```

> ⚠️ 本 commit 暂**不要单独合并**——必须与 Chunk 2 Task 2.3（calculateTier 读配置 + 清缓存参数化）同 PR，见 Chunk 2 开头说明。

---

## Chunk 2: 后端服务层（spec step 3-6）

> 实施前先读 `service/impl/RankingServiceImpl.java:47-102`（`enterPoints` 现有实现）与 `:104-169`（`calculateTier` / `refreshRankings`），本 chunk 的迁移与改造均以此为基准。

### Task 2.1: `PointService` 接口 + Impl（承接 `enterPoints`，加 source/赛季）

**Files:**
- Create: `service/PointService.java`
- Create: `service/impl/PointServiceImpl.java`
- Create: `service/dto/PointEntry.java`（或在 `dto/admin` 内复用结构）
- Create: `test/.../service/PointServiceImplTest.java`
- Modify: `listener/PointChangeListener.java`（嵌套 `PointChangeEvent` record 加 `String seasonCode`）

- [ ] **Step 1: 写失败测试**

```java
@ExtendWith(MockitoExtension.class)
class PointServiceImplTest {
    @InjectMocks PointServiceImpl service;
    @Mock SeasonMapper seasonMapper;
    @Mock PointRecordMapper pointRecordMapper;
    @Mock UserMapper userMapper;
    @Mock TierProperties tierProperties;
    @Mock ApplicationEventPublisher eventPublisher;

    @Test
    void enterPoints_writesSourceAndSeasonCode_andAccumulates_andPublishesSeasonEvent() {
        // 当前 STAR 赛季
        Season s = new Season(); s.setType("STAR"); s.setCode("2026-Q2"); s.setStatus("CURRENT");
        when(seasonMapper.selectOne(any())).thenReturn(s);
        User u = new User(); u.setId(1L); u.setStarPoints(400); u.setStarTier("BRONZE");
        when(userMapper.selectById(1L)).thenReturn(u);
        when(tierProperties.keyFor(500, "STAR")).thenReturn("SILVER");

        service.enterPoints(null, "STAR", List.of(new PointEntry(1L, 100, "手动")), PointSource.MANUAL, 9L);

        ArgumentCaptor<PointRecord> cap = ArgumentCaptor.forClass(PointRecord.class);
        verify(pointRecordMapper).insert(cap.capture());
        assertEquals("MANUAL", cap.getValue().getSource());        // 新增 source
        assertEquals("2026-Q2", cap.getValue().getSeasonCode());   // 新增 season_code
        assertEquals(500, u.getStarPoints());                       // 累加
        assertEquals("SILVER", u.getStarTier());                    // 重算 tier
        var evt = ArgumentCaptor.forClass(PointChangeListener.PointChangeEvent.class);
        verify(eventPublisher).publishEvent(evt.capture());
        assertEquals("2026-Q2", evt.getValue().seasonCode());       // 事件携带赛季
    }
}
```

- [ ] **Step 2: 跑测试，确认失败**

Run: `mvn -f hey-pickler-server/pom.xml test -Dtest=PointServiceImplTest`
Expected: FAIL（`PointService` / `PointEntry` 不存在）

- [ ] **Step 3: 写实现**

`PointEntry`（record 或 POJO）：`userId / points / reason`。

```java
public interface PointService {
    /** source 由调用方 service 决定，不信任前端 */
    void enterPoints(Long eventId, String type, List<PointEntry> records,
                     PointSource source, Long operatorId);
}
```

`PointServiceImpl.enterPoints`：迁移 `RankingServiceImpl.enterPoints` 主体逻辑（遍历 records 插 `point_record`、累加 `user.starPoints/partyPoints`），改造点：
- 注入 `SeasonMapper` + `TierProperties`
- 开头取当前赛季：`seasonMapper.selectOne(type + status='CURRENT')` → `seasonCode`（找不到抛 `BizException`）
- 插入 `point_record` 时 set `source` 与 `seasonCode`
- 重算 tier 改为 `tierProperties.keyFor(points, type)`（替代原 `calculateTier`）
- `publishEvent(new PointChangeEvent(type, seasonCode))`

`PointChangeListener.PointChangeEvent`：`record PointChangeEvent(String type, String seasonCode) {}`。改造后 grep `new PointChangeListener.PointChangeEvent(` 确认所有构造点（原 `RankingServiceImpl:101` 已随 `enterPoints` 迁移到 `PointServiceImpl`）均改为 `(type, seasonCode)`；已核仅 `PointChangeListener` 一个监听器，无其他消费方。

- [ ] **Step 4: 跑测试，确认通过**

Run: `mvn -f hey-pickler-server/pom.xml test -Dtest=PointServiceImplTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add hey-pickler-server/src/main/java/com/heypickler/service/PointService.java \
        hey-pickler-server/src/main/java/com/heypickler/service/impl/PointServiceImpl.java \
        hey-pickler-server/src/main/java/com/heypickler/service/dto/PointEntry.java \
        hey-pickler-server/src/test/java/com/heypickler/service/PointServiceImplTest.java \
        hey-pickler-server/src/main/java/com/heypickler/listener/PointChangeListener.java
git commit -m "feat(points): 抽 PointService 承接发分，加 source/赛季; PointChangeEvent 携 seasonCode"
```

---

### Task 2.2: `RankingService` 删 `enterPoints` + 调用点改调 `PointService`

**Files:**
- Modify: `service/RankingService.java`（删 `enterPoints` 方法签名）
- Modify: `service/impl/RankingServiceImpl.java`（删 `enterPoints` 实现 + 其私有依赖若仅此处用）
- Modify: `controller/admin/AdminEventController.java`（`POST /{id}/points` 改调 `pointService.enterPoints(eventId, type, records, MANUAL, operatorId)`）
- Modify: `controller/admin/AdminRankingController.java`（`POST /rankings/points` 同理，eventId 传 null）

- [ ] **Step 1: 改两个 controller 注入 `PointService` 并改调**

admin 手填入口**强制 `PointSource.MANUAL`**（不读请求体的 source；`PointEntryRequest` 不新增 source 字段）。

- [ ] **Step 2: 删 `RankingService` 接口与 Impl 的 `enterPoints`**

- [ ] **Step 3: 处理 `RankingServiceTest` 回归**（现有 ~12 个测试会因删 `enterPoints`/改 `calculateTier` 而编译或断言失败，必须同步改）

  1. 删除 6 个 `testEnterPoints_*`（:133/:153/:189/:207/:231/:249）——逻辑已迁 `PointService`，迁到 `PointServiceImplTest` 或删
  2. 删除 6 个 `testCalculateTier_*`（:89-123，反射调私有 `calculateTier` 断言 SHINING/SUPER/LEGEND）——该方法已删，测试随之删
  3. `testRefreshRankings_GroupByTier`（:320-348）：mock `tierProperties.keyFor` 返回新 6 档 key，改断言
  4. `testRefreshRankings_ClearsRedisCache`（:350-360）：`times(5)` → `times(7)`（6 档 key + null + top5），或参数化为 `tierProperties.cacheKeysWithNull().size()+1`
  5. `RankingServiceImpl` 测试类加 `@Mock TierProperties tierProperties` + `@BeforeEach` stub（`keyFor`/`cacheKeysWithNull`），避免 NPE

- [ ] **Step 4: 编译 + 回归测试**

Run: `mvn -f hey-pickler-server/pom.xml test -Dtest='!*IntegrationTest'`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add hey-pickler-server/src/main/java/com/heypickler/service/RankingService.java \
        hey-pickler-server/src/main/java/com/heypickler/service/impl/RankingServiceImpl.java \
        hey-pickler-server/src/main/java/com/heypickler/controller/admin/AdminEventController.java \
        hey-pickler-server/src/main/java/com/heypickler/controller/admin/AdminRankingController.java
git commit -m "refactor(points): RankingService 删 enterPoints; 调用点改调 PointService (强制 MANUAL)"
```

---

### Task 2.3: `calculateTier` 读配置 + `refreshRankings` season 维度 + 清缓存参数化 ⚡（与 Task 1.4 V11 同 PR）

**Files:**
- Modify: `service/RankingService.java`（`refreshRankings(String type)` → `refreshRankings(String type, String seasonCode)`；保留无 season 重载默认取当前赛季，便于兼容）
- Modify: `service/impl/RankingServiceImpl.java`
- Modify: `listener/PointChangeListener.java`（取 `seasonCode` 调 `refreshRankings(type, seasonCode)`）
- Modify: `controller/admin/AdminRankingController.java`（手动 refresh 取当前赛季 code）
- Create/Modify: `test/.../service/RankingServiceImplTest.java`

- [ ] **Step 1: 写失败测试**（黑盒验证"防归档删除"——不内省 wrapper）

```java
@Test
void refreshRankings_keepsArchivedSeason_rowsSurvive() {
    Ranking archived = ranking(1L, "STAR", "2025-Q1");   // 旧赛季归档行，不应被删
    when(rankingMapper.selectList(any())).thenReturn(List.of());
    when(tierProperties.keyFor(anyInt(), eq("STAR"))).thenReturn("SILVER");
    when(tierProperties.cacheKeysWithNull()).thenReturn(List.of("BRONZE","SILVER","GOLD","PLATINUM","DIAMOND","MASTER",null));

    service.refreshRankings("STAR", "2026-Q2");

    // 实现须把 delete 抽成 protected deleteRankingsByTypeAndSeason(type, seasonCode)；
    // 这里用 spy 捕获实际被 delete 命中的 id 集合，断言不含 archived.id(=1L)
    verify(rankingMapper).delete(any());
}
```
> 说明：MyBatis-Plus `LambdaQueryWrapper` 内部条件无法可靠内省（`ArgumentCaptor<wrapper>` 断言 `.eq(season,...)` 不可行）。实现把 delete 抽成 `protected deleteRankingsByTypeAndSeason(type, seasonCode)`，对其单独写小单测：构造 wrapper 后断言 `getTargetSql()` 含 `season_code`；上层 `refreshRankings` 用黑盒（spy rankingMapper 捕获被删 id，断言旧赛季 id 不在其中）验证归档保留。

- [ ] **Step 2: 跑测试，确认失败**

Run: `mvn -f hey-pickler-server/pom.xml test -Dtest=RankingServiceImplTest`
Expected: FAIL

- [ ] **Step 3: 改实现**

- `calculateTier` 删除写死阈值，改为读 `tierProperties.keyFor(points, type)`（注意：`enterPoints` 已在 Task 2.1 改用 `tierProperties`；此步清掉 `RankingServiceImpl` 残留的私有 `calculateTier`，统一入口）
- `refreshRankings(type, seasonCode)`：delete 加 `.eq(Ranking::getSeason, seasonCode)`；插入的 ranking `setSeason(seasonCode)`
- 清缓存：`for (String tier : tierProperties.cacheKeysWithNull()) redisTemplate.delete(RedisKey.ranking(type, tier));`（替换硬编码 `LEGEND/SUPER/SHINING/null`）
- `PointChangeListener`：`refreshRankings(evt.type(), evt.seasonCode())`

- [ ] **Step 4: 跑测试，确认通过**

Run: `mvn -f hey-pickler-server/pom.xml test -Dtest=RankingServiceImplTest`
Expected: PASS

- [ ] **Step 5: Commit（与 Task 1.4 V11 同 PR 合并）**

```bash
git add hey-pickler-server/src/main/java/com/heypickler/service/RankingService.java \
        hey-pickler-server/src/main/java/com/heypickler/service/impl/RankingServiceImpl.java \
        hey-pickler-server/src/main/java/com/heypickler/listener/PointChangeListener.java \
        hey-pickler-server/src/main/java/com/heypickler/controller/admin/AdminRankingController.java \
        hey-pickler-server/src/test/java/com/heypickler/service/RankingServiceImplTest.java
git commit -m "feat(points): calculateTier 读 TierProperties; refreshRankings 加 season 维度防归档删除; 清缓存参数化"
```

---

### Task 2.4: `SeasonService` + `AdminSeasonController`

**Files:**
- Create: `service/SeasonService.java` + `impl/SeasonServiceImpl.java`
- Create: `controller/admin/AdminSeasonController.java`
- Create: `dto/admin/SeasonCreateRequest.java`
- Create: `vo/SeasonVO.java`
- Create: `test/.../service/SeasonServiceImplTest.java`

- [ ] **Step 1: 写失败测试**

```java
@Test
void activate_archivesOldCurrent_andSetsNew_inTransaction() {
    Season old = season("STAR","2026-Q2","CURRENT"); old.setId(1L);
    Season next = season("STAR","2026-Q3","ARCHIVED"); next.setId(2L);
    when(seasonMapper.selectById(2L)).thenReturn(next);
    when(seasonMapper.selectList(any())).thenReturn(List.of(old)); // 同 type 当前 CURRENT

    service.activate(2L);

    // 旧 CURRENT → ARCHIVED
    ArgumentCaptor<Season> cap = ArgumentCaptor.forClass(Season.class);
    verify(seasonMapper, atLeast(1)).updateById(cap.capture());
    // 断言存在 status=ARCHIVED(type=STAR) 与 status=CURRENT(id=2) 两次更新
    verify(next).setStatus("CURRENT");
}
```

- [ ] **Step 2: 跑测试，确认失败**

- [ ] **Step 3: 写实现**

`SeasonService`：`list(type)` / `create(req)`（默认 `ARCHIVED`）/ `@Transactional activate(id)`（先 `UPDATE season SET status='ARCHIVED' WHERE type=? AND status='CURRENT'`，再置目标行 `CURRENT`）/ `getRankings(seasonId)`（按 season.code + type 直查 `ranking` 表，走 DB 不走缓存）。

`AdminSeasonController`（`@RequireRole` ADMIN+，参考现有 `@RequireRole` + `RoleCheckAspect` 用法）：
- `GET /api/admin/seasons?type=STAR`
- `POST /api/admin/seasons`
- `POST /api/admin/seasons/{id}/activate`
- `GET /api/admin/seasons/{id}/rankings`（归档排名）

- [ ] **Step 4: 跑测试，确认通过**

- [ ] **Step 5: Commit**

```bash
git add hey-pickler-server/src/main/java/com/heypickler/service/SeasonService.java \
        hey-pickler-server/src/main/java/com/heypickler/service/impl/SeasonServiceImpl.java \
        hey-pickler-server/src/main/java/com/heypickler/controller/admin/AdminSeasonController.java \
        hey-pickler-server/src/main/java/com/heypickler/dto/admin/SeasonCreateRequest.java \
        hey-pickler-server/src/main/java/com/heypickler/vo/SeasonVO.java \
        hey-pickler-server/src/test/java/com/heypickler/service/SeasonServiceImplTest.java
git commit -m "feat(season): SeasonService + AdminSeasonController (列表/新建/切换事务/归档排名)"
```

---

### Task 2.5: `PointWallet` 接口（getBalance 实现 / deduct 仅签名）

**Files:**
- Create: `service/PointWallet.java`
- Modify: `service/impl/PointServiceImpl.java`（`implements PointWallet`）

- [ ] **Step 1: 写失败测试**

```java
@Test
void getBalance_returnsStarOrPartyPoints() {
    User u = new User(); u.setStarPoints(1230); u.setPartyPoints(450);
    when(userMapper.selectById(1L)).thenReturn(u);
    assertEquals(1230, wallet.getBalance(1L, "STAR"));
    assertEquals(450, wallet.getBalance(1L, "PARTY"));
}
```

- [ ] **Step 2: 跑测试，确认失败**

- [ ] **Step 3: 写实现**

```java
public interface PointWallet {
    int getBalance(Long userId, String type);
    /** 商城兑换扣减 —— 本次仅签名，实现留积分商城 spec */
    default void deduct(Long userId, String type, int amount, String itemRef) {
        throw new UnsupportedOperationException("PointWallet.deduct 未实现，见积分商城 spec");
    }
}
```

`PointServiceImpl` 加 `getBalance` 实现（读 `user.starPoints/partyPoints`），`implements PointWallet`。

- [ ] **Step 4: 跑测试，确认通过**

- [ ] **Step 5: Commit**

```bash
git add hey-pickler-server/src/main/java/com/heypickler/service/PointWallet.java \
        hey-pickler-server/src/main/java/com/heypickler/service/impl/PointServiceImpl.java \
        hey-pickler-server/src/test/java/com/heypickler/service/PointServiceImplTest.java
git commit -m "feat(points): PointWallet 接口预留 (getBalance 实现; deduct 签名留商城 spec)"
```

---

### Task 2.6: VO 增 `tierName`

**Files:**
- Modify: `vo/RankingVO.java`（+`tierName`）
- Modify: `vo/UserDetailVO.java`、`vo/UserAdminVO.java`、`vo/UserProfileVO.java`（三个均含 `starTier/partyTier`，各加 `starTierName`/`partyTierName`；实施时 grep `starTier` 确认全集——注意 **`UserVO.java` 不存在**，别写错）
- Modify: 对应 service 装配处用 `tierProperties.nameFor(key)` 填充

- [ ] **Step 1: 写失败测试**（`RankingVO` 装配时 `tierName` = 中文名）

- [ ] **Step 2: 跑测试，确认失败**

- [ ] **Step 3: 改 VO 字段 + service 装配 `tierProperties.nameFor(...)`**

- [ ] **Step 4: 跑测试，确认通过**

- [ ] **Step 5: Commit**

```bash
git add hey-pickler-server/src/main/java/com/heypickler/vo/RankingVO.java \
        hey-pickler-server/src/main/java/com/heypickler/vo/UserVO.java \
        hey-pickler-server/src/main/java/com/heypickler/service/impl/RankingServiceImpl.java
git commit -m "feat(points): VO 增 tierName 中文档名 (tierProperties.nameFor)"
```

---

## Chunk 3: 后端 dashboard 收尾 + 前端（spec step 7-8）

### Task 3.1: `AdminDashboardController` tier 兜底改读配置

**Files:**
- Modify: `controller/admin/AdminDashboardController.java:90-97`（`groupingBy(... : "SHINING")` 兜底 → `tierProperties.getKeys().get(0)`）

- [ ] **Step 1: 改两处兜底**（`:94` starTierDist、`:97` partyTierDist）注入 `TierProperties`，兜底用 `tierProperties.getKeys().get(0)`（即 BRONZE）
- [ ] **Step 2: 编译 + 启动后 `GET /api/admin/dashboard` 手动验证 tier 分布 key 为新 6 档**
- [ ] **Step 3: Commit**

```bash
git add hey-pickler-server/src/main/java/com/heypickler/controller/admin/AdminDashboardController.java
git commit -m "fix(dashboard): tier 分组兜底改读 TierProperties 最低档 (原硬编码 SHINING)"
```

---

### Task 3.2: wxapp 术语常量 + 全量文案替换 + 段位 6 档

**Files:**
- Create: `hey-pickler-wxapp/utils/terms.js`
- Modify: `pages/profile/profile.wxml`、`pages/ranking/ranking.{wxml,js}`、`pages/event-detail/event-detail.wxml`、`pages/my-events/*`、`components/event-card/*`、`components/tier-badge/*`

- [ ] **Step 1: 建术语常量**

```js
// utils/terms.js
module.exports = {
  STAR:  { type: '竞技赛事', points: '战力', tier: '战力段位' },
  PARTY: { type: '社交活动', points: '活力', tier: '活力段位' },
  // tier 英文 key → 中文（与后端 TierProperties.names 对齐）
  tierName: { BRONZE:'青铜', SILVER:'白银', GOLD:'黄金', PLATINUM:'铂金', DIAMOND:'钻石', MASTER:'王者' }
};
```

- [ ] **Step 2: 全量替换"明星/派对"文案**（各 wxml/js 引用 terms 常量；段位展示用 `tierName` 映射或后端返回的 `tierName`）
- [ ] **Step 3: grep 验收**

Run: `grep -rE "明星|派对" hey-pickler-wxapp/`
Expected: 无输出（无残留）

- [ ] **Step 4: 微信开发者工具编译，人工核对个人中心/排行榜/赛事详情文案与段位**
- [ ] **Step 5: Commit**

```bash
git add hey-pickler-wxapp/utils/terms.js hey-pickler-wxapp/pages/ hey-pickler-wxapp/components/
git commit -m "feat(wxapp): 积分体系文案替换为战力/活力 + 段位 6 档展示"
```

---

### Task 3.3: admin 术语常量 + 全量文案 + 段位 6 档

**Files:**
- Create: `hey-pickler-admin/src/constants/terms.ts`
- Modify: `views/events/EventFormDialog.vue`、`views/users/UserDetailDrawer.vue`、`views/rankings/PointEntryDialog.vue`、`views/rankings/*`、`views/dashboard/*`

- [ ] **Step 1: 建术语常量**（TS 版，结构同 wxapp）
- [ ] **Step 2: 全量替换文案；段位展示用 `tierName`（VO 已返回）或本地映射**
- [ ] **Step 3: grep 验收**

Run: `grep -rE "明星|派对" hey-pickler-admin/src/`
Expected: 无输出

- [ ] **Step 4: `npm --prefix hey-pickler-admin run lint` + `npm run build` 验证**
- [ ] **Step 5: Commit**

```bash
git add hey-pickler-admin/src/constants/terms.ts hey-pickler-admin/src/views/ hey-pickler-admin/src/components/
git commit -m "feat(admin): 积分体系文案替换为战力/活力 + 段位 6 档展示"
```

---

### Task 3.4: admin 赛季管理页

**Files:**
- Create: `hey-pickler-admin/src/api/seasons.ts`
- Create: `hey-pickler-admin/src/views/seasons/index.vue`
- Modify: `hey-pickler-admin/src/router/index.ts`、`src/components/layout/AppSidebar.vue`（加菜单项）

- [ ] **Step 1: 建 API 客户端**（参考现有 `api/*.ts` 写法，`request.get/post`）

```ts
// api/seasons.ts
import request from '@/utils/request';
export const listSeasons = (type: string) => request.get('/admin/seasons', { params: { type } });
export const createSeason = (data) => request.post('/admin/seasons', data);
export const activateSeason = (id: number) => request.post(`/admin/seasons/${id}/activate`);
export const getSeasonRankings = (id: number) => request.get(`/admin/seasons/${id}/rankings`);
```

- [ ] **Step 2: 建赛事管理页**（Element Plus 表格：列表[type/code/status/起止]、新建对话框、切换按钮、归档排名抽屉）
- [ ] **Step 3: 注册路由 + 侧边栏菜单"赛季管理"**
- [ ] **Step 4: `npm run dev`，人工验证：列表两条 CURRENT、新建 2026-Q3、切换 STAR 赛季、查看归档排名**
- [ ] **Step 5: Commit**

```bash
git add hey-pickler-admin/src/api/seasons.ts hey-pickler-admin/src/views/seasons/ \
        hey-pickler-admin/src/router/index.ts hey-pickler-admin/src/components/layout/AppSidebar.vue
git commit -m "feat(admin): 赛季管理页 (列表/新建/切换/归档排名)"
```

---

## Chunk 4: 集成测试 + 文档（spec step 9）

### Task 4.1: 集成测试

**Files:**
- Create: `test/.../integration/PointsSeasonIntegrationTest.java`（参考现有 `*IntegrationTest` + `application-integration.yml`）

- [ ] **Step 1: 写集成测试覆盖关键链路**

  - 发分：`POST /api/admin/events/{id}/points` → `point_record.source=MANUAL`、`season_code=当前`、`user` 余额增加、榜单刷新
  - 段位 6 档：不同积分命中不同 tier（BRONZE…MASTER）
  - 赛季切换：新建 2026-Q3 → activate → 旧 2026-Q2 ARCHIVED 且其 ranking 行保留；`GET /api/admin/seasons/{oldId}/rankings` 返回旧排名
  - 并发安全：activate 同类型不会产生两个 CURRENT（可选，用事务隔离验证）

- [ ] **Step 2: 跑集成测试**

Run: `mvn -f hey-pickler-server/pom.xml test -Dtest=PointsSeasonIntegrationTest`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add hey-pickler-server/src/test/java/com/heypickler/integration/PointsSeasonIntegrationTest.java
git commit -m "test(points): 集成测试覆盖发分/段位/赛季切换归档"
```

---

### Task 4.2: 文档更新

**Files:**
- Modify: `CLAUDE.md`（积分体系说明：战力/活力、6 档、source、赛季；**顺带修正 "Current head: V8" → V11**）
- Modify: `README.md`（积分体系面向用户文案 + 段位说明）

- [ ] **Step 1: 更新 CLAUDE.md**（Entity 列表加 Season；积分体系段位/来源/赛季说明；migration head V8→V11）
- [ ] **Step 2: 更新 README.md**（积分命名/段位/赛季运营说明）
- [ ] **Step 3: Commit**

```bash
git add CLAUDE.md README.md
git commit -m "docs: 双积分体系(战力/活力)说明 + migration head 更正"
```

---

## 执行交接（Execution Handoff）

**计划完整，保存于 `docs/superpowers/plans/2026-06-23-dual-points-system.md`。**

**关键约束（执行时务必遵守）：**
1. **Task 1.4（V11）与 Task 2.3（calculateTier 读配置 + refreshRankings season 维度 + 清缓存参数化）必须同一 PR 合并**——否则 migration 后 tier 已 6 档但代码仍 3 档逻辑，产生中间态混乱。
2. `STAR/PARTY` 后端字段名全程不变，仅前端文案替换。
3. `source` 由服务端按入口强制（admin 手填=MANUAL），`PointEntryRequest` 不加 source 字段。
4. `exchange_record` 表与 `PointWallet.deduct` 实现推迟到积分商城 spec，本计划不涉及。

**执行路径：** 本 harness 支持 subagent，按 superpowers:subagent-driven-development 执行——每个 task 派 fresh subagent + 两阶段 review。建议 PR 切分：
- PR-1：Chunk 1 + Chunk 2（后端，含 Task 1.4 + 2.3 强制同 PR）+ Chunk 4.1 集成测试
- PR-2：Chunk 3（前端 wxapp + admin，含赛季管理页）+ Chunk 4.2 文档

或按 chunk 顺序串行 4 个 PR。
