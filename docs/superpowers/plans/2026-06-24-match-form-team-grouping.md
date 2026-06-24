# 比赛形式 + 队伍 + 自动分组实施计划（比赛闭环 Spec 1）

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让赛事有固定比赛形式（单/双/混打）、双打/混打能组队（队长发起+队友确认）、管理员能按策略（随机/蛇形/手动）自动分组并锁定。

**Architecture:** Event 加 `format`/`grouping_locked`；新建 `Team`/`MatchGroup`/`GroupAssignment` 实体 + V12 migration；报名走 format 校验 + Team 状态机（PENDING→CONFIRMED，解散物理删行）；分组用策略模式（GroupingStrategy: RANDOM/SERPENTINE，MANUAL 跳过接口）+ 锁定冻结名单。

**Tech Stack:** Spring Boot 3.2 / MyBatis-Plus / MySQL 8（≥8.0.16）/ Flyway / JUnit5+Mockito；Vue3+TS+Element Plus（admin）；原生 WXML（wxapp）。

**Spec:** `docs/superpowers/specs/2026-06-24-match-form-team-grouping-design.md`（v3 定稿，Approved）

---

## File Structure

### 后端新增
| 文件 | 职责 |
|---|---|
| `common/enums/EventFormat.java` | SINGLES/DOUBLES/MIXED |
| `common/enums/TeamStatus.java` | PENDING/CONFIRMED |
| `common/enums/GroupingStrategyType.java` | RANDOM/SERPENTINE/MANUAL |
| `entity/Team.java`、`entity/MatchGroup.java`、`entity/GroupAssignment.java` | 队伍/分组表/分组槽位 |
| `service/GroupingStrategy.java`(接口) + `service/impl/RandomStrategy.java`、`SerpentineStrategy.java` | 分组策略 |
| `service/TeamService.java` + `impl` | 队伍建/确认/解散/拒邀 |
| `service/GroupingService.java` + `impl` | 分组执行/微调/锁定/解锁 |
| `dto/Participant.java` | 分组参与者抽象（userId/teamId + rankScore） |
| `dto/admin/GroupingRequest.java` | 策略+组数 |
| `vo/TeamVO.java`、`vo/GroupVO.java` | 队伍/分组视图 |
| `controller/admin/AdminGroupingController.java` | 5 个分组 API |
| `db/migration/V12__match_form_team_grouping.sql` | 数据迁移 |

### 后端修改
| 文件 | 改动 |
|---|---|
| `entity/Event.java` | +`format` +`groupingLocked` |
| `entity/Registration.java` | +`teamId` |
| `service/impl/EventServiceImpl.java` | `register`/`cancel`：format 校验 + Team 流程 |
| `controller/admin/AdminEventController.java` | 创建/编辑赛事加 format 字段 |
| `controller/app/AppEventController.java` | register/cancel 改造、+decline/my-team |

### 前端 admin
| 文件 | 改动 |
|---|---|
| `views/events/EventFormDialog.vue` | +比赛形式 select |
| `views/events/GroupingPanel.vue`（新） | 分组管理（开始/预览/微调/锁定/解锁） |
| `api/grouping.ts`（新）、`api/teams.ts`（新） | API 客户端 |

### 前端 wxapp
| 文件 | 改动 |
|---|---|
| `pages/event-detail/event-detail.*` | format 标签 + 双打报名选队友 |
| `components/event-card/*` | format 标签 |

---

## Chunk 1: 后端数据层（spec step 1）

### Task 1.1: `EventFormat` / `TeamStatus` / `GroupingStrategyType` 枚举

**Files:**
- Create: `common/enums/EventFormat.java`、`common/enums/TeamStatus.java`、`common/enums/GroupingStrategyType.java`

- [ ] **Step 1: 创建三个枚举**

```java
// EventFormat.java
package com.heypickler.common.enums;
import lombok.Getter;
@Getter
public enum EventFormat { SINGLES("单打"), DOUBLES("双打"), MIXED("混打");
    private final String label; EventFormat(String l){this.label=l;} }

// TeamStatus.java
package com.heypickler.common.enums;
import lombok.Getter;
@Getter
public enum TeamStatus { PENDING("待确认"), CONFIRMED("已确认");
    private final String label; TeamStatus(String l){this.label=l;} }

// GroupingStrategyType.java
package com.heypickler.common.enums;
import lombok.Getter;
@Getter
public enum GroupingStrategyType { RANDOM("随机"), SERPENTINE("蛇形按排名"), MANUAL("手动");
    private final String label; GroupingStrategyType(String l){this.label=l;} }
```

- [ ] **Step 2: 编译验证** — `mvn -f hey-pickler-server/pom.xml compile` → BUILD SUCCESS
- [ ] **Step 3: Commit** — `git add common/enums/{EventFormat,TeamStatus,GroupingStrategyType}.java && git commit -m "feat(match): 新增 EventFormat/TeamStatus/GroupingStrategyType 枚举"`

---

### Task 1.2: `Event` 加 format/grouping_locked + `Registration` 加 team_id

**Files:**
- Modify: `entity/Event.java`（+`format`+`groupingLocked`）
- Modify: `entity/Registration.java`（+`teamId`）

- [ ] **Step 1: Event 加字段**

```java
// entity/Event.java 末尾追加字段（@Data 自动 getter/setter）
private String format;            // SINGLES|DOUBLES|MIXED
private Boolean groupingLocked;   // 分组锁定
```

- [ ] **Step 2: Registration 加字段**

```java
// entity/Registration.java 末尾追加
private Long teamId;              // 双打/混打关联 Team；单打 NULL
```

- [ ] **Step 3: 编译验证** → BUILD SUCCESS
- [ ] **Step 4: Commit** — `git commit -am "feat(match): Event +format/grouping_locked; Registration +team_id"`

---

### Task 1.3: `Team` / `MatchGroup` / `GroupAssignment` 实体

**Files:**
- Create: `entity/Team.java`、`entity/MatchGroup.java`、`entity/GroupAssignment.java`、对应 `mapper/*.java`

- [ ] **Step 1: 创建 Team 实体**

```java
package com.heypickler.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("team")
public class Team {
    @TableId(type = IdType.AUTO) private Long id;
    private Long eventId;
    private Long member1UserId;   // 队长
    private Long member2UserId;   // 队友（建队即填 partnerUserId）
    private String name;
    private String status;        // PENDING | CONFIRMED
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createdAt;
}
```

- [ ] **Step 2: 创建 MatchGroup 实体**

```java
package com.heypickler.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("match_group")
public class MatchGroup {
    @TableId(type = IdType.AUTO) private Long id;
    private Long eventId;
    private Integer groupIndex;   // 0..N-1
    private String name;          // A/B/C... 后端按 index 生成
}
```

- [ ] **Step 3: 创建 GroupAssignment 实体**

```java
package com.heypickler.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("group_assignment")
public class GroupAssignment {
    @TableId(type = IdType.AUTO) private Long id;
    private Long groupId;
    private Long eventId;         // 冗余，便于按 event 查/校验锁
    private Long userId;          // 单打：参赛者（与 teamId 互斥）
    private Long teamId;          // 双打/混打：队伍
    private Integer seed;         // 蛇形/分配序号
}
```

- [ ] **Step 4: 创建三个 Mapper**（`@Mapper public interface XxxMapper extends BaseMapper<Xxx> {}`）
- [ ] **Step 5: 编译验证** → BUILD SUCCESS
- [ ] **Step 6: Commit** — `git add entity/{Team,MatchGroup,GroupAssignment}.java mapper/{Team,MatchGroup,GroupAssignment}Mapper.java && git commit -m "feat(match): 新增 Team/MatchGroup/GroupAssignment 实体与 Mapper"`

---

### Task 1.4: V12 migration

**Files:**
- Create: `db/migration/V12__match_form_team_grouping.sql`

> 实施前 `ls db/migration/` 确认 head=V11（双积分占用 V9-V11）。MySQL ≥8.0.16（CHECK 强制）。

- [ ] **Step 1: 写 migration**

```sql
-- V12: 比赛形式 + 队伍 + 分组表

-- 1) event 加形式 + 分组锁
ALTER TABLE event ADD COLUMN format VARCHAR(8) NOT NULL DEFAULT 'SINGLES';
ALTER TABLE event ADD COLUMN grouping_locked TINYINT(1) NOT NULL DEFAULT 0;

-- 现有 event 按 registration 多数 matchType 回填 format（仅更新有报名的）
UPDATE event e SET format = COALESCE(
  (SELECT matchType FROM registration WHERE event_id = e.id
   GROUP BY matchType ORDER BY COUNT(*) DESC, matchType ASC LIMIT 1), 'SINGLES')
WHERE EXISTS (SELECT 1 FROM registration WHERE event_id = e.id);

-- 2) team（双打/混打 2 人一队）
CREATE TABLE team (
  id BIGINT NOT NULL AUTO_INCREMENT,
  event_id BIGINT NOT NULL,
  member1_user_id BIGINT NOT NULL,
  member2_user_id BIGINT NOT NULL,
  name VARCHAR(64),
  status VARCHAR(12) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_event_member1 (event_id, member1_user_id),
  UNIQUE KEY uk_event_member2 (event_id, member2_user_id)
);

-- 3) match_group + group_assignment
CREATE TABLE match_group (
  id BIGINT NOT NULL AUTO_INCREMENT,
  event_id BIGINT NOT NULL,
  group_index INT NOT NULL,
  name VARCHAR(32),
  PRIMARY KEY (id),
  UNIQUE KEY uk_event_index (event_id, group_index)
);

CREATE TABLE group_assignment (
  id BIGINT NOT NULL AUTO_INCREMENT,
  group_id BIGINT NOT NULL,
  event_id BIGINT NOT NULL,
  user_id BIGINT NULL,
  team_id BIGINT NULL,
  seed INT NOT NULL,
  PRIMARY KEY (id),
  KEY idx_group (group_id),
  KEY idx_event (event_id),
  FOREIGN KEY (group_id) REFERENCES match_group(id) ON DELETE CASCADE,
  CHECK ((user_id IS NOT NULL AND team_id IS NULL)
      OR (user_id IS NULL AND team_id IS NOT NULL))
);

-- 4) registration 加 team_id
ALTER TABLE registration ADD COLUMN team_id BIGINT NULL;
```

- [ ] **Step 2: 启动应用验证 Flyway 执行 V12**（本地 MySQL+Redis 在跑）
  Run（后台）: `mvn -f hey-pickler-server/pom.xml spring-boot:run`
  Expected 日志：`Migrating schema "hey_pickler" to version "12 - match form team grouping"` + `Successfully applied ... now at version v12` + `Started`
  失败则 STOP 报 BLOCKED（附错误）
- [ ] **Step 3: 查库验证**
  ```bash
  mysql -uroot -proot hey_pickler -e "
  SELECT version,success FROM flyway_schema_history WHERE version='12';
  SELECT format, COUNT(*) FROM event GROUP BY format;
  SHOW CREATE TABLE team; SHOW CREATE TABLE group_assignment;"
  ```
  Expected：V12 success=1；event.format 有 SINGLES/DOUBLES/MIXED 分布；team/group_assignment 表存在（group_assignment 含 CHECK + FK）
- [ ] **Step 4: Commit** — `git add db/migration/V12__match_form_team_grouping.sql && git commit -m "feat(match): V12 migration - format/grouping_locked + team/match_group/group_assignment + registration.team_id"`

---

## Chunk 2: 报名 + Team（spec step 2）

### Task 2.1: `TeamService` + 报名改造（TDD）

**Files:**
- Create: `service/TeamService.java` + `impl/TeamServiceImpl.java`、`vo/TeamVO.java`
- Modify: `service/impl/EventServiceImpl.java`（`register`/`cancel`）、`controller/app/AppEventController.java`（+decline/my-team）

- [ ] **Step 1: 写失败测试** `TeamServiceImplTest`

  关键用例（Mockito）：
  - `createTeam_队长发起_建PENDING队+队长registration`（校验两人未在别队 FOR UPDATE、member1=队长 member2=partner、status=PENDING）
  - `confirmTeam_队友确认_PENDING→CONFIRMED+队友registration`（校验 status=PENDING、member2==当前用户、重复确认抛 BizException）
  - `cancel_PENDING队长退赛_删team+队长reg`
  - `cancel_CONFIRMED成员退赛_删team+双方reg`
  - `decline_队友拒邀_删team+撤队长reg`

- [ ] **Step 2: 跑测试确认失败**

- [ ] **Step 3: 写实现**
  - `TeamService`：`createTeam(eventId, captainId, partnerId)` / `confirmTeam(teamId, userId)` / `dissolve(teamId)`（物理删行 + 撤相关 registration）/ `decline(teamId, userId)`
  - `EventServiceImpl.register`：按 `event.format` 分支
    - `SINGLES`：校验无 partner，建个人 registration（teamId=null）
    - `DOUBLES/MIXED`：若带 partnerUserId → 走 `createTeam`（队长发起）；若带 teamId → 走 `confirmTeam`（队友确认）
    - 服务端强制 `matchType = event.format`
  - `EventServiceImpl.cancel`：单打撤 reg；双打按 team 状态走 dissolve（PENDING 队长退赛 / CONFIRMED 任一退赛解散）
  - 全程 `grouping_locked=true` 时拒绝（抛 BizException "已分组锁定"）
  - `AppEventController`：`register`/`cancel` 改造 + `POST /api/app/teams/{teamId}/decline` + `GET /api/app/events/{id}/my-team`

- [ ] **Step 4: 跑测试确认通过** + `mvn test -Dtest='!*IntegrationTest'` 全量 PASS
- [ ] **Step 5: Commit** — `git commit -m "feat(match): TeamService 队伍状态机 + 报名 format 校验 (建队/确认/解散/拒邀)"`

---

## Chunk 3: 分组服务 + API（spec step 3-5）

### Task 3.1: `GroupingStrategy` 接口 + Random/Serpentine（TDD）

**Files:**
- Create: `service/GroupingStrategy.java`(接口)、`service/impl/RandomStrategy.java`、`SerpentineStrategy.java`、`dto/Participant.java`

- [ ] **Step 1: 写失败测试** `SerpentineStrategyTest` / `RandomStrategyTest`

  ```java
  // Serpentine：8 队分 4 组，rankScore 降序蛇形 → 每组 2 队，强手分散
  @Test void serpentine_distributesStrongAcrossGroups() {
      var ps = ranked(8); // rankScore 100,90,...,30
      var result = strategy.assign(ps, 4);
      // 组0: rank1(100)+rank8(30), 组1: rank2(90)+rank7(40), ... 强弱配对
      assertEquals(4, result.stream().map(GroupAssignment::getGroupId).distinct().count());
  }
  // Random：分配后每组成员数差≤1，seed 连续
  ```

- [ ] **Step 2: 跑测试确认失败**
- [ ] **Step 3: 写实现**
  - `Participant`：`Long userId/Long teamId`（互斥）+ `int rankScore`（构造时校验非都空）
  - `GroupingStrategy.assign(List<Participant> ranked, int groupCount) → List<GroupAssignment>`（assignment 此时只填 user/team/seed，groupId 由调用方按 index 映射）
  - `SerpentineStrategy`：蛇形 `for i: groupId = i%N if evenRound else N-1-(i%N)`
  - `RandomStrategy`：`Collections.shuffle` 后轮流分（seed=分配序）

- [ ] **Step 4: 跑测试确认通过**
- [ ] **Step 5: Commit** — `git commit -m "feat(match): GroupingStrategy + Random/Serpentine 策略"`

### Task 3.2: `GroupingService` + admin API + MANUAL + 集成测试

**Files:**
- Create: `service/GroupingService.java` + `impl/GroupingServiceImpl.java`、`controller/admin/AdminGroupingController.java`、`dto/admin/GroupingRequest.java`、`vo/GroupVO.java`
- Create: `test/.../integration/GroupingIntegrationTest.java`

- [ ] **Step 1: 写失败测试** `GroupingServiceImplTest`
  - `group_SERPENTINE_建N组+槽位+rankScore按event.type`
  - `group_LOCKED抛异常` / `unlock_清空group_assignment+match_group`
  - `reassign_未锁定可换组_锁定拒绝`
- [ ] **Step 2: 跑测试确认失败**
- [ ] **Step 3: 写实现**
  - `GroupingService.group(eventId, strategyType, groupCount)`：
    1. 校验 `!grouping_locked`、清旧分组（事务内）
    2. 取参赛者：SINGLES→CONFIRMED reg 的 user_id（忽略 partnerId）；DOUBLES/MIXED→CONFIRMED teams
    3. 算 rankScore：`event.type=STAR` 用 starPoints（team=成员和），PARTY 用 partyPoints
    4. 选策略：MANUAL→只建空 match_group 返回；否则 `strategy.assign` → 建 match_group(N) + group_assignment（name 按 index→字母）
  - `reassign(assignmentId, targetGroupId)`：未锁定校验 → 改 groupId
  - `lock(eventId)` / `unlock(eventId)`：unlock 事务内删 match_group（CASCADE 清 assignment）+ grouping_locked=false
- [ ] **Step 4: `AdminGroupingController`** 5 API（§6.5）：grouping(POST)/grouping(GET)/assignments/{aid}(PUT)/lock/unlock，`@RequireRole ADMIN+`
- [ ] **Step 5: 集成测试** `GroupingIntegrationTest`（参考 BannerIntegrationTest）：
  - 双打赛事建队+确认 → SERPENTINE 分组 → 锁定 → 解锁清空 → 单打分组（忽略 partnerId）
- [ ] **Step 6: 跑测试确认通过**（单测 + 集成）
- [ ] **Step 7: Commit** — `git commit -m "feat(match): GroupingService + admin 5 API + MANUAL + 集成测试"`

---

## Chunk 4: 前端 + 文档（spec step 6-8）

### Task 4.1: admin format 配置 + 分组管理页

**Files:**
- Modify: `views/events/EventFormDialog.vue`（+比赛形式 select）
- Create: `views/events/GroupingPanel.vue`、`api/grouping.ts`、`api/teams.ts`
- Modify: 赛事详情页接入 GroupingPanel

- [ ] **Step 1: EventFormDialog 加「比赛形式」select**（SINGLES/DOUBLES/MIXED）
- [ ] **Step 2: `api/grouping.ts`** 5 API 客户端 + `api/teams.ts`（队伍查询）
- [ ] **Step 3: `GroupingPanel.vue`**：选策略+组数→开始分组→预览表格（组×槽位）→拖拽/选择换组→锁定/解锁；双打赛事显示队伍列表
- [ ] **Step 4: 赛事详情页接入 GroupingPanel**
- [ ] **Step 5: `npm run lint` + `npm run build` 通过**
- [ ] **Step 6: Commit** — `git commit -m "feat(admin): 赛事 format 配置 + 分组管理页"`

### Task 4.2: wxapp format 标签 + 双打报名选队友

**Files:**
- Modify: `pages/event-detail/event-detail.{wxml,js}`、`components/event-card/*`、`utils/terms.js`（+format 文案）

- [ ] **Step 1: terms.js 加 FORMAT 映射**（SINGLES→单打/DOUBLES→双打/MIXED→混打）
- [ ] **Step 2: event-card / event-detail 显示 format 标签**
- [ ] **Step 3: event-detail 报名流程**：SINGLES 直接报；DOUBLES/MIXED 选队友（输入/选择 partnerUserId 发起，或带 teamId 确认）
- [ ] **Step 4: 微信开发者工具编译人工核对**
- [ ] **Step 5: Commit** — `git commit -m "feat(wxapp): format 标签 + 双打报名选队友"`

### Task 4.3: 文档

**Files:**
- Modify: `CLAUDE.md`（entity 加 Team/MatchGroup/GroupAssignment、migration head V12、比赛形式/分组 key pattern）、`README.md`

- [ ] **Step 1: CLAUDE.md** 更新（entity 列表、head V12、GroupingStrategy/TeamService 说明）
- [ ] **Step 2: README** 加比赛形式/分组说明
- [ ] **Step 3: Commit** — `git commit -m "docs: 比赛形式+队伍+分组说明 + migration head V12"`

---

## 执行交接（Execution Handoff）

**计划保存于 `docs/superpowers/plans/2026-06-24-match-form-team-grouping.md`。**

**关键约束：**
1. MySQL ≥8.0.16（group_assignment CHECK 强制）；低版本改 GroupingService 应用层校验
2. team 解散/拒邀/取消 = **物理删行**（释放 member1/member2 唯一约束，允许重组队），status 仅 PENDING/CONFIRMED
3. 锁定（`grouping_locked=true`）后冻结名单：拒报名/退赛/分组改动
4. 单打取数忽略 partnerId；双打 team rankScore = 成员积分和（按 `event.type` 选 star/party）

**执行路径：** subagent-driven-development，每 task 派 fresh subagent + 两阶段 review。PR 切分建议：
- **PR-1（后端）**：Chunk 1 + Chunk 2 + Chunk 3 + 集成测试
- **PR-2（前端+文档）**：Chunk 4

**实施前**：`ls db/migration/` 确认 head=V11（本 spec 用 V12）；本地 MySQL+Redis 在跑。
