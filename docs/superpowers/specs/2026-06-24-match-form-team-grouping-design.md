# 比赛形式 + 队伍 + 自动分组设计（比赛闭环 Spec 1）

- **日期**：2026-06-24
- **状态**：Draft（待 review）
- **范围**：比赛闭环 Spec 1 = 赛事比赛形式 + 队伍化 + 自动分组（对阵签表/赛制/比分 = Spec 2，自动结算发分 = Spec 3）
- **前置**：双积分体系（PR #15 spec / #16 后端 / #17 前端）已完成

---

## 1. 背景

用户最初需求："赛事报名后，根据单打/双打/混打的参赛选手进行自动分组（分组策略：随机/按排名等），比赛完成后记录比分，按规则送积分；活动也一样。"

比赛闭环共 6 个子系统，按业务流程拆 3 个 spec。本 Spec 1 解决**报名后到分组**：让赛事有固定比赛形式、双打/混打能组队、管理员能按策略自动分组并锁定。

现状（PR-1/2 探索确认）：
- `Event` 无 `format`（比赛形式），`Registration.matchType` 由用户自选（一个赛事可混入单/双/混）
- 双打只有松散 `Registration.partnerId`，无队伍实体
- 完全无 team/group/match/score/bracket/round/seed/placement 任何实体

## 2. 范围

**In scope**
- `Event.format`（SINGLES/DOUBLES/MIXED）固定赛事形式 + 报名校验
- `Team` 实体（双打/混打 2 人一队，队长发起+队友确认）
- 自动分组：`GroupingStrategy`（RANDOM/SERPENTINE/MANUAL）+ 锁定
- admin 分组管理页 + wxapp 报名适配

**Out of scope**（后续 spec）
- 赛制（淘汰/循环/小组赛内对阵）+ 对阵签表 + 比分记录（Spec 2）
- 按比分自动结算发分规则、`source=PLACEMENT`（Spec 3）
- MIXED 男女配对约束（无 gender 字段，YAGNI；将来要再加 `user.gender`）

## 3. 命名映射

后端字段 `STAR`/`PARTY` 不变（沿用双积分体系）。本 spec 不涉及积分命名。

## 4. 数据模型

### 4.1 Event 加比赛形式 + 分组锁
```sql
ALTER TABLE event ADD COLUMN format VARCHAR(8) NOT NULL DEFAULT 'SINGLES';
ALTER TABLE event ADD COLUMN grouping_locked TINYINT(1) NOT NULL DEFAULT 0;
```
- `format`：`SINGLES` / `DOUBLES` / `MIXED`，创建赛事时必填
- `grouping_locked`：分组锁定标记，锁定后不可微调/重分（除非显式解锁）

### 4.2 Team 实体（双打/混打 2 人一队）
```sql
CREATE TABLE team (
  id              BIGINT NOT NULL AUTO_INCREMENT,
  event_id        BIGINT NOT NULL,
  member1_user_id BIGINT NOT NULL,        -- 队长
  member2_user_id BIGINT,                 -- 队友（PENDING 时 NULL）
  name            VARCHAR(64),
  status          VARCHAR(12) NOT NULL,   -- PENDING | CONFIRMED | DISSOLVED
  created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_event_member (event_id, member1_user_id),
  UNIQUE KEY uk_event_member2 (event_id, member2_user_id)  -- 同一赛事一人只能在一个队
);
```
- 双打/混打固定 2 人，用 `member1/member2` 两字段（比 team_member 关联表简单）
- 单打（SINGLES）不建 Team
- `status`：`PENDING`（队长已建、队友未确认）/ `CONFIRMED`（两人确认，可分组）/ `DISSOLVED`（退赛解散）

### 4.3 分组表（避 `group` 保留字，用 `match_group`）
```sql
CREATE TABLE match_group (
  id          BIGINT NOT NULL AUTO_INCREMENT,
  event_id    BIGINT NOT NULL,
  group_index INT NOT NULL,            -- 组序号 0..N-1
  name        VARCHAR(32),             -- A/B/C...
  PRIMARY KEY (id),
  UNIQUE KEY uk_event_index (event_id, group_index)
);

CREATE TABLE group_assignment (
  id        BIGINT NOT NULL AUTO_INCREMENT,
  group_id  BIGINT NOT NULL,
  user_id   BIGINT NULL,     -- 单打：参赛者
  team_id   BIGINT NULL,     -- 双打/混打：队伍
  seed      INT,             -- 蛇形/分配序号
  PRIMARY KEY (id),
  KEY idx_group (group_id)
);
```
- 单打：`group_assignment.user_id` 填参赛者
- 双打/混打：`group_assignment.team_id` 填队伍
- `seed`：排序/分配序号（RANDOM 时为分配序，SERPENTINE 时为蛇形序）

### 4.4 Registration 改造
```sql
ALTER TABLE registration ADD COLUMN team_id BIGINT NULL;
```
- 双打/混打关联 `team_id`；单打 NULL
- `matchType` / `partnerId` **保留**（兼容现有数据/前端；新建报名服务端强制 `matchType = event.format`，双打改走 Team，partnerId 不再用于新报名）

### 4.5 V12 migration
- `event` +`format`（回填现有：默认 `SINGLES`）+`grouping_locked`
- 建 `team`、`match_group`、`group_assignment` 表
- `registration` +`team_id`
- migration head 现 V11 → V12

## 5. 报名与队伍流程

### 5.1 单打报名
沿用 `POST /api/app/events/{id}/register`，服务端校验 `event.format=SINGLES`，拒绝带 partner；只记 `registration`。

### 5.2 双打/混打报名（队长发起 + 队友确认）
1. 队长报名时填 `partnerUserId` → 系统建 `team`（`status=PENDING`，member1=队长，member2=NULL）+ 队长的 registration（`team_id` 关联）
2. 队友收到邀请，`POST .../register` 带 `teamId` 确认 → `team.status=CONFIRMED`、补 `member2_user_id` + 队友 registration（同 `team_id`）
3. 两人都 CONFIRMED 才算成队，可参与分组

### 5.3 退赛与队伍
- 双打/混打：任一成员退赛 → 整队 `DISSOLVED`（不补人，YAGNI），分组时排除
- 单打：个人退赛，分组排除

### 5.4 报名校验（服务端，格式匹配）
- `event.format=SINGLES`：单人报名，拒绝带 partner/team
- `event.format=DOUBLES|MIXED`：必须两人成队（team CONFIRMED），单人报名拒绝
- `RegisterRequest.matchType` 正则保留，服务端强制 `matchType = event.format`
- MIXED 因无 gender 字段，不约束性别，2 人即可成队

## 6. 分组服务

### 6.1 策略模式
```java
interface GroupingStrategy {
    List<GroupAssignment> assign(List<Participant> ranked, int groupCount);
}
```
- `Participant`：单打包 `userId` / 双打混打包 `teamId`，都带 `rankScore`
- `RandomStrategy`：打乱后轮流分到 N 组
- `SerpentineStrategy`：按 `rankScore` 降序**蛇形**分配（1→组0, 2→组1 … N→组N-1, N+1→组N-1 …），强手分散、最均衡
- `MANUAL`：不自动，管理员在分组管理页手动分配/换组，仅未锁定时

### 6.2 排序依据（rankScore）
- 单打：`user.starPoints`（STAR 赛事）/ `partyPoints`（PARTY 赛事）
- 双打/混打 team：**成员积分和**（member1 + member2 对应积分）
- 积分 0 照常参与排序

### 6.3 分组流程（管理员手动触发）
1. 后台「开始分组」→ 选策略 + 组数 → `GroupingService.group(eventId, strategy, groupCount)`
2. 服务端取参赛者（SINGLES→CONFIRMED registrations 的 user；DOUBLES/MIXED→CONFIRMED teams），算 rankScore 排序
3. 策略分配 → 建 `match_group`(N 个) + `group_assignment`
4. 返回分组预览（**未锁定**，可微调）
5. 管理员微调换组 → 满意后「确认分组」→ `event.grouping_locked=true`

### 6.4 锁定/解锁
- 锁定后：微调/重分被拒（Spec 2 比赛依赖固化分组）
- 「解锁」= `grouping_locked=false` + 清 `group_assignment`/`match_group`（允许重分）；提示"解锁会清空当前分组"

### 6.5 admin API（`@RequireRole ADMIN+`）
| 方法 | 路径 | 作用 |
|------|------|------|
| POST | `/api/admin/events/{id}/grouping` | 执行分组（body: strategy/groupCount），返回预览，未锁定 |
| GET | `/api/admin/events/{id}/grouping` | 查分组结果 |
| PUT | `/api/admin/events/{id}/grouping/assignments/{aid}` | 微调换组（body: targetGroupId，仅未锁定） |
| POST | `/api/admin/events/{id}/grouping/lock` | 锁定 |
| POST | `/api/admin/events/{id}/grouping/unlock` | 解锁（清结果） |

## 7. 前端 + 改动面 + 兼容性

### 7.1 admin 前端
- 赛事创建/编辑表单：加「比赛形式」select + 「分组组数」（分组时配）
- 赛事详情 → **分组管理**：开始分组（选策略 + 组数）→ 预览 → 微调（拖拽换组）→ 锁定/解锁
- 队伍列表：双打/混打赛事看队伍（成员 + status）

### 7.2 wxapp 前端
- 赛事卡/详情：显示 format 标签（单打/双打/混打）
- 报名流程：单打直接报；双打/混打选队友（发起队伍→队友确认）

### 7.3 改动面
| 层 | 新增 | 修改 |
|----|------|------|
| entity | `Team`, `MatchGroup`, `GroupAssignment` | `Event +format +grouping_locked`、`Registration +team_id` |
| service | `GroupingStrategy`(接口)+`RandomStrategy/SerpentineStrategy`、`TeamService`、`GroupingService` | `EventServiceImpl.register`（format 校验 + Team 流程） |
| controller | admin grouping 5 API | `AppEventController.register`（双打建队/确认） |
| migration | `V12` | — |
| admin | 分组管理页 + format 配置 | EventFormDialog |
| wxapp | 双打报名选队友 UI | event-card/event-detail format 标签 |

### 7.4 兼容性
- 现有 event 无 format → V12 回填 `SINGLES`
- `registration.matchType/partnerId` 保留（不删）
- 双积分 ranking/积分体系不动

## 8. 分阶段落地（供 writing-plans）
1. `Event.format/grouping_locked` + `Team/MatchGroup/GroupAssignment` entity + `V12` migration
2. 报名改造：format 校验 + Team 建/确认/退赛解散
3. `GroupingStrategy` 接口 + `Random/SerpentineStrategy` + `GroupingService`（含锁定）+ 单测
4. admin grouping 5 API + 单测
5. MANUAL 微调 + 集成测试
6. admin：format 配置 + 分组管理页
7. wxapp：format 标签 + 双打报名选队友
8. 文档（README/CLAUDE.md）

## 9. 后续 spec（不在本范围）
- **Spec 2**：赛制（淘汰/循环/小组赛内对阵）+ 对阵签表 + 比分记录
- **Spec 3**：按比分自动结算发分规则（`source=PLACEMENT`）+ 名次→积分引擎
