# 管理后台运营体验优化 Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让运营在「赛事详情页」一个地方走完办赛全流程（建赛→报名→分组→对阵→发分），并完成菜单信息架构重构与一批运营提效改造。

**Architecture:** 以前端改造为主（`hey-pickler-admin` Vue 3 + Element Plus），后端仅 2 处只读小改（ranking `keyword`、admin `standings` endpoint，均复用已有 service）。核心是一个新的 `/events/:id` 详情页，把现有分散组件（`EventFormDialog`/`RegistrationDrawer`/`GroupingPanel`/`PlacementPointsDialog`）与新补齐的「对阵/比赛/完成发分」聚合为按生命周期 stepper 串联的指挥中心，并把状态转换显式化（严格对齐后端 7 条规则）。

**Tech Stack:** Vue 3 (composition API, `<script setup>`)、Element Plus、Pinia、Vue Router 4、TypeScript、Vite、axios；后端 Spring Boot 3 / MyBatis-Plus / JUnit 5 + Mockito。

**Spec:** `docs/superpowers/specs/2026-06-29-admin-optimization-design.md`（已 Approved）

---

## 测试策略（重要：前后端不同）

**后端（`hey-pickler-server`）—— 经典 TDD：** JUnit 5 + Mockito 单测（`src/test/java/com/heypickler/service/`）+ `*IntegrationTest`（`src/test/java/com/heypickler/integration/`，需 MySQL+Redis）。命令：
- 单测：`mvn test -Dtest='!*IntegrationTest'`
- 集成：`mvn test -Dtest='*IntegrationTest'`

**前端（`hey-pickler-admin`）—— 无单测框架，只有 Playwright e2e + lint + build。** 因此：
- **每个前端任务的硬性门禁**：`cd hey-pickler-admin && npm run build` 必须通过；`npm run lint:check`（不带 `--fix`）**不得在改动文件中新增 lint 错误**——CI 的 lint 步骤因历史存量（约 72 个）是 `continue-on-error`，"全量 PASS"不现实，纪律是**不增量**。
- **关键流程**写 Playwright e2e（先 failing → 实现 → passing）：详情页全流程、批量签到、排名搜索。**e2e 是本地门禁，CI 不跑 Playwright**——需本地起后端。
- UI 细节附**人工验证清单**（启动后端 + `npm run dev`，按清单点验）。

> 跑前端 e2e 需后端在 `localhost:8080` 且 `WX_DEV_MODE=true`。首次需 `npx playwright install`。

---

## File Structure（创建/修改一览）

**后端**
- Modify: `hey-pickler-server/src/main/java/com/heypickler/dto/app/RankingQuery.java` — 加 `keyword`
- Modify: `hey-pickler-server/src/main/java/com/heypickler/service/impl/RankingServiceImpl.java` — `getRankings` 内按 nickname 过滤
- Modify: `hey-pickler-server/src/main/java/com/heypickler/controller/admin/AdminRankingController.java` — 透传 `keyword`
- Modify: `hey-pickler-server/src/main/java/com/heypickler/controller/admin/AdminMatchController.java` — 加 `GET /events/{id}/standings`
- Test: `hey-pickler-server/src/test/java/com/heypickler/service/RankingServiceTest.java` — 加 keyword 用例
- Test: `hey-pickler-server/src/test/java/com/heypickler/integration/MatchPlayIntegrationTest.java` — 加 admin standings 用例

**前端**
- Create: `src/constants/eventStatus.ts` — 状态机 7 规则 + 文案 + 颜色（共享单一事实源）
- Create: `src/api/matches.ts` — matches/standings/score/reset/complete/generate 封装
- Create: `src/views/events/EventDetailView.vue` — 办赛指挥中心（核心）
- Create: `src/views/events/MatchesPanel.vue` — 对阵/比赛阶段（详情页子组件）
- Create: `src/views/events/IssuancePanel.vue` — 发分阶段（详情页子组件）
- Create: `src/components/common/EventStatusBadge.vue` — 状态徽章 + 合法转换（共享）
- Create: `src/components/common/EventFilterBar.vue` — 列表筛选栏（共享，含快捷 chip）
- Create: `tests/e2e/event-detail.spec.ts` — 详情页全流程 e2e
- Create: `tests/e2e/ranking-search.spec.ts` — 排名搜索 e2e
- Modify: `src/router/index.ts` — 加 `/events/:id` 路由 + 菜单 `meta`（title/icon/group）
- Modify: `src/components/layout/AppSidebar.vue` — 改为 meta 驱动分组渲染
- Modify: `src/views/events/EventListView.vue` — 用共享组件、跳详情、进度条、状态收敛
- Modify: `src/views/activities/ActivityListView.vue` — 命名统一、用共享组件
- Modify: `src/views/events/RegistrationDrawer.vue` — 批量签到 + 名单导出
- Modify: `src/views/rankings/RankingView.vue` — 分页 + 搜索（keyword）
- Modify: `src/views/dashboard/DashboardView.vue` — 待办区
- Modify: `src/api/rankings.ts` — 透传 keyword
- Modify: `src/types/index.ts` — 加 Match/Standing/Matches 类型

---

## Chunk 1: 后端 2 处只读小改（ranking keyword + admin standings）

### Task 1.1: RankingQuery 加 keyword 字段

**Files:**
- Modify: `hey-pickler-server/src/main/java/com/heypickler/dto/app/RankingQuery.java`
- Test: `hey-pickler-server/src/test/java/com/heypickler/service/RankingServiceTest.java`

- [ ] **Step 1: 写 failing tests（在 `RankingServiceTest` 末尾追加两个用例：未命中缓存 + 命中缓存）**

```java
@Test
void getRankings_ShouldFilterByKeyword() {
    Ranking r1 = new Ranking(); r1.setUserId(1L); r1.setType("STAR");
    r1.setTier("BRONZE"); r1.setRank(1); r1.setPoints(100); r1.setChange(0);
    Ranking r2 = new Ranking(); r2.setUserId(2L); r2.setType("STAR");
    r2.setTier("BRONZE"); r2.setRank(2); r2.setPoints(80); r2.setChange(0);

    User user2 = new User(); user2.setId(2L); user2.setNickname("Other User");

    RankingQuery query = new RankingQuery();
    query.setType("STAR"); query.setKeyword("Test"); query.setPage(1); query.setSize(20);

    org.springframework.data.redis.core.ValueOperations<String, Object> valueOps =
        mock(org.springframework.data.redis.core.ValueOperations.class);
    when(valueOps.get(any())).thenReturn(null);                 // 未命中缓存
    when(redisTemplate.opsForValue()).thenReturn(valueOps);
    when(rankingMapper.selectList(any())).thenReturn(Arrays.asList(r1, r2));
    when(userMapper.selectBatchIds(anyList())).thenReturn(Arrays.asList(testUser, user2));

    com.heypickler.common.result.PageResult<RankingVO> result =
        rankingService.getRankings(query);

    assertEquals(1, result.getList().size(), "keyword='Test' 应只保留 Test User");
    assertEquals(1L, result.getList().get(0).getUserId());
    assertEquals("Test User", result.getList().get(0).getNickname());
}

@Test
void getRankings_ShouldFilterByKeyword_OnCacheHit() {
    // 缓存命中分支也必须过滤——否则搜索在热缓存下失效
    RankingVO vo1 = new RankingVO(); vo1.setUserId(1L); vo1.setNickname("Test User"); vo1.setPoints(100);
    RankingVO vo2 = new RankingVO(); vo2.setUserId(2L); vo2.setNickname("Other User"); vo2.setPoints(80);

    RankingQuery query = new RankingQuery();
    query.setType("STAR"); query.setKeyword("Test"); query.setPage(1); query.setSize(20);

    org.springframework.data.redis.core.ValueOperations<String, Object> valueOps =
        mock(org.springframework.data.redis.core.ValueOperations.class);
    when(valueOps.get(any())).thenReturn(Arrays.asList(vo1, vo2));   // 命中缓存
    when(redisTemplate.opsForValue()).thenReturn(valueOps);

    com.heypickler.common.result.PageResult<RankingVO> result =
        rankingService.getRankings(query);

    assertEquals(1, result.getList().size(), "缓存命中时也必须按 keyword 过滤");
    assertEquals(1L, result.getList().get(0).getUserId());
    assertEquals("Test User", result.getList().get(0).getNickname());
}
```

- [ ] **Step 2: 跑测试，确认失败**

Run: `cd hey-pickler-server && mvn test -Dtest=RankingServiceTest#getRankings_ShouldFilterByKeyword`
Expected: 编译失败 — `RankingQuery` 无 `setKeyword`。

- [ ] **Step 3: 加字段**

```java
// RankingQuery.java
@Data
public class RankingQuery {
    private String type;
    private String tier;
    private String keyword;   // 新增：按昵称模糊搜索
    private int page = 1;
    private int size = 20;
}
```

- [ ] **Step 4: 抽取「过滤 + 分页」helper，两个分支都用（修复缓存命中分支不过滤的 bug）**

⚠ `getRankings` 有两条返回路径。Redis **缓存命中**分支（`if (cached != null)`）在 `result` 组装之前就 `subList` 早返回——若 keyword 过滤只加在 `result` 之后，缓存命中时会返回**未过滤的全量分页**，搜索在热缓存下静默失效。必须两分支都过滤。

新增私有 helper（类内）：

```java
/** 按 keyword 在已含 nickname 的列表上内存过滤，再分页。keyword 为空时原样分页。 */
private PageResult<RankingVO> filterAndPaginate(List<RankingVO> source, String keyword, int page, int size) {
    List<RankingVO> filtered = source;
    if (keyword != null && !keyword.trim().isEmpty()) {
        String lower = keyword.trim().toLowerCase();
        filtered = source.stream()
            .filter(vo -> vo.getNickname() != null && vo.getNickname().toLowerCase().contains(lower))
            .collect(java.util.stream.Collectors.toList());
    }
    int start = (page - 1) * size;
    int end = Math.min(start + size, filtered.size());
    List<RankingVO> pageList = start >= filtered.size()
        ? java.util.Collections.emptyList() : filtered.subList(start, end);
    return PageResult.of(filtered.size(), page, size, pageList);
}
```

两分支改用 helper（替换原 `subList` + `PageResult.of` 返回处）：
- 缓存命中分支：`if (cached != null) return filterAndPaginate(cached, query.getKeyword(), page, size);`（**不回写**——缓存始终存全量）
- 未命中分支：写**全量** `result` 到缓存后，`return filterAndPaginate(result, query.getKeyword(), page, size);`

> 缓存写入保持全量 `result`（未过滤），保证不同 keyword 查询命中同一份缓存。`PageResult` 已在该文件 import。

- [ ] **Step 5: 跑测试，确认通过**

Run: `mvn test -Dtest=RankingServiceTest`
Expected: PASS（含新用例与原有用例，注意原 `getRankings_*` 用例未设 keyword，走 null 分支，行为不变）。

- [ ] **Step 6: Controller 透传（`AdminRankingController`，约 line 42-50 的 `@GetMapping("/{type}")`）**

```java
query.setKeyword(keyword); // 新增：从 @RequestParam(required=false) String keyword 读取
```
在方法签名加参数 `@RequestParam(required = false) String keyword`。

- [ ] **Step 7: 跑全部单测**

Run: `mvn test -Dtest='!*IntegrationTest'`
Expected: PASS。

- [ ] **Step 8: Commit**

```bash
git add hey-pickler-server/src/main/java/com/heypickler/dto/app/RankingQuery.java \
        hey-pickler-server/src/main/java/com/heypickler/service/impl/RankingServiceImpl.java \
        hey-pickler-server/src/main/java/com/heypickler/controller/admin/AdminRankingController.java \
        hey-pickler-server/src/test/java/com/heypickler/service/RankingServiceTest.java
git commit -m "feat(ranking): 排名支持按昵称 keyword 搜索（getRankings 内存过滤）"
```

### Task 1.2: AdminMatchController 加 standings endpoint

**Files:**
- Modify: `hey-pickler-server/src/main/java/com/heypickler/controller/admin/AdminMatchController.java`
- Test: `hey-pickler-server/src/test/java/com/heypickler/integration/MatchPlayIntegrationTest.java`

> `MatchService.standings(Long)` 已存在并已被 app 端 `AppMatchController` 使用且测试覆盖，故此处只加薄 controller 包装 + 一个集成断言。

- [ ] **Step 1: 加 endpoint**

```java
// AdminMatchController.java，在 listEventMatches 之后追加
@GetMapping("/events/{eventId}/standings")
@RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN, UserRole.OPERATOR})
public Result<List<List<StandingVO>>> standings(@PathVariable Long eventId) {
    return Result.ok(matchService.standings(eventId));
}
```
（`StandingVO` 已在该文件 import，无需新增 import。）

- [ ] **Step 2: 加集成断言（`MatchPlayIntegrationTest`，用该类现有 harness：`restTemplate` + `adminAuthHeaders()` + `resultCode/resultData` + `createEvent/seedUser/register/generateMatches/cleanup`）**

```java
@Test
void adminStandings_returnsGroupedStandings() {
    seedUser(8101L, 0);
    seedUser(8102L, 0);
    Long eventId = createEvent("Admin Standings IT", "SINGLES", "OPEN");
    try {
        assertEquals(0, register(eventId, 8101L));
        assertEquals(0, register(eventId, 8102L));
        jdbcTemplate.update("UPDATE event SET grouping_locked = 1 WHERE id = ?", eventId);
        jdbcTemplate.update("INSERT INTO match_group (event_id, group_index, name) VALUES (?, 0, 'A')", eventId);
        Long groupId = jdbcTemplate.queryForObject(
            "SELECT id FROM match_group WHERE event_id = ? AND group_index = 0", Long.class, eventId);
        jdbcTemplate.update("INSERT INTO group_assignment (group_id, event_id, user_id, seed) VALUES (?, ?, ?, ?)", groupId, eventId, 8101L, 1);
        jdbcTemplate.update("INSERT INTO group_assignment (group_id, event_id, user_id, seed) VALUES (?, ?, ?, ?)", groupId, eventId, 8102L, 2);
        assertEquals(0, generateMatches(eventId));

        // 断言 admin 端 standings 接口
        HttpEntity<Void> req = new HttpEntity<>(adminAuthHeaders());
        ResponseEntity<Map> resp = restTemplate.exchange(
            "/api/admin/events/" + eventId + "/standings", HttpMethod.GET, req, Map.class);
        assertEquals(0, resultCode(resp));
        @SuppressWarnings("unchecked")
        List<List<Map<String, Object>>> byGroup = (List<List<Map<String, Object>>>) resultData(resp);
        assertEquals(1, byGroup.size(), "应有 1 个组");
        assertEquals(2, byGroup.get(0).size(), "该组应有 2 名参与者");
    } finally {
        cleanup(eventId);
    }
}
```
> 所有辅助方法（`createEvent`/`seedUser`/`register`/`generateMatches`/`cleanup`/`adminAuthHeaders`/`resultCode`/`resultData`）均已存在于本类或 `IntegrationTestConfig`，直接复用，无需新增。

- [ ] **Step 3: 跑集成测试**

Run: `mvn test -Dtest=MatchPlayIntegrationTest`
Expected: PASS（需本地 MySQL 8 + Redis）。

- [ ] **Step 4: Commit**

```bash
git add hey-pickler-server/src/main/java/com/heypickler/controller/admin/AdminMatchController.java \
        hey-pickler-server/src/test/java/com/heypickler/integration/MatchPlayIntegrationTest.java
git commit -m "feat(match): admin 端新增 standings 接口（复用 matchService.standings）"
```

### Task 1.3: AdminEventController 加 `GET /{id}` 详情接口（详情页依赖）

**Files:**
- Modify: `hey-pickler-server/src/main/java/com/heypickler/controller/admin/AdminEventController.java`
- Modify: `hey-pickler-server/src/main/java/com/heypickler/service/impl/EventServiceImpl.java`（若无单条 VO 查询）
- Modify: `hey-pickler-admin/src/api/events.ts`
- Test: `hey-pickler-server/src/test/java/com/heypickler/integration/MatchPlayIntegrationTest.java`

> 详情页（Chunk 3 Task 3.6）强依赖按 id 取单个赛事。现 `AdminEventController` 无 `GET /{id}`（仅有 `/{id}/participants`、`/{id}/placement-points`、`/{eventId}/registrations`）。本任务补这个只读接口。

- [ ] **Step 1: 后端加 endpoint（复用现有 VO 装配）**

```java
// AdminMatchController.java —— 注意是 AdminEventController（赛事控制器）
@GetMapping("/{id}")
@RequireRole({UserRole.SUPER_ADMIN, UserRole.ADMIN, UserRole.OPERATOR})
public Result<EventVO> detail(@PathVariable Long id) {
    return Result.ok(eventService.getEventDetail(id));
}
```
> 若 `EventService` 无单条 `EventVO getEventDetail(Long id)`，在接口 + `EventServiceImpl` 新增：`Event e = eventMapper.selectById(id);`（null/已删抛 `NOT_FOUND`）+ 复用 `adminListEvents` 中已有的 `Event → EventVO` 装配逻辑抽成 `toVO(Event)`。

- [ ] **Step 2: 加集成断言（`MatchPlayIntegrationTest` 末尾，复用 `createEvent`/`cleanup`）**

```java
@Test
void adminEventDetail_returnsEvent() {
    Long eventId = createEvent("Detail IT", "SINGLES", "DRAFT");
    try {
        HttpEntity<Void> req = new HttpEntity<>(adminAuthHeaders());
        ResponseEntity<Map> resp = restTemplate.exchange(
            "/api/admin/events/" + eventId, HttpMethod.GET, req, Map.class);
        assertEquals(0, resultCode(resp));
        assertEquals(eventId, ((Number) ((Map<String, Object>) resultData(resp)).get("id")).longValue());
    } finally {
        cleanup(eventId);
    }
}
```

- [ ] **Step 3: 跑集成**

Run: `cd hey-pickler-server && mvn test -Dtest=MatchPlayIntegrationTest`
Expected: PASS。

- [ ] **Step 4: 前端 `api/events.ts` 加 `getEventDetail`**

```ts
export const getEventDetail = (id: number) =>
  request.get<any, ApiResponse<Event>>(`/events/${id}`)
```

- [ ] **Step 5: Commit**

```bash
git add hey-pickler-server/src/main/java/com/heypickler/controller/admin/AdminEventController.java \
        hey-pickler-server/src/main/java/com/heypickler/service/EventService.java \
        hey-pickler-server/src/main/java/com/heypickler/service/impl/EventServiceImpl.java \
        hey-pickler-server/src/test/java/com/heypickler/integration/MatchPlayIntegrationTest.java \
        hey-pickler-admin/src/api/events.ts
git commit -m "feat(event): admin 新增 GET /events/{id} 详情接口（详情页依赖）"
```

---

## Chunk 2: P0 菜单信息架构

### Task 2.1: 路由 meta 驱动（加 group/title/icon + 详情页路由占位）

**Files:**
- Modify: `src/router/index.ts`

- [ ] **Step 1: 给每个 child 路由加 `meta: { title, icon, group }`，并加 `/events/:id`**

```ts
// router/index.ts children 内
{ path: '', name: 'Dashboard',
  component: () => import('@/views/dashboard/DashboardView.vue'),
  meta: { title: '工作台', icon: 'DataBoard', group: '运营管理' } },
{ path: 'events', name: 'Events',
  component: () => import('@/views/events/EventListView.vue'),
  meta: { title: '竞技赛事', icon: 'Calendar', group: '运营管理' } },
{ path: 'events/:id', name: 'EventDetail',
  component: () => import('@/views/events/EventDetailView.vue'),
  meta: { title: '赛事详情', hidden: true } },            // 不进菜单
{ path: 'activities', name: 'Activities',
  component: () => import('@/views/activities/ActivityListView.vue'),
  meta: { title: '社交活动', icon: 'Football', group: '运营管理' } },
{ path: 'users', name: 'Users',
  component: () => import('@/views/users/UserListView.vue'),
  meta: { title: '用户管理', icon: 'User', group: '运营管理' } },
{ path: 'rankings', name: 'Rankings',
  component: () => import('@/views/rankings/RankingView.vue'),
  meta: { title: '排名管理', icon: 'Trophy', group: '积分与赛季' } },
{ path: 'seasons', name: 'Seasons',
  component: () => import('@/views/seasons/SeasonView.vue'),
  meta: { title: '赛季管理', icon: 'Timer', group: '积分与赛季' } },
{ path: 'banners', name: 'Banners',
  component: () => import('@/views/banners/BannerListView.vue'),
  meta: { title: 'Banner 管理', icon: 'Picture', group: '内容运营' } },
{ path: 'admins', name: 'Admins',
  component: () => import('@/views/admins/AdminListView.vue'),
  meta: { title: '管理员管理', icon: 'UserFilled', group: '系统' } },
{ path: 'ban-records', name: 'BanRecords',
  component: () => import('@/views/ban-records/BanRecordListView.vue'),
  meta: { title: '封禁记录', icon: 'Document', group: '系统' } },
{ path: 'admin-logs', name: 'AdminLogs',
  component: () => import('@/views/admin-logs/AdminLogListView.vue'),
  meta: { title: '操作日志', icon: 'List', group: '系统' } },
```

> 命名决议（spec §10）：「赛事管理」→「**竞技赛事**」、「活动管理」→「**社交活动**」、「用户日志」→「**封禁记录**」、「首页」→「**工作台**」。

- [ ] **Step 2: 占位 `EventDetailView.vue`（Chunk 3 实现，先建桩避免路由报错）**

```vue
<template><div class="page-header"><h1>赛事详情</h1><p>（Chunk 3 实现）</p></div></template>
<script setup lang="ts"></script>
```

- [ ] **Step 3: 门禁**

Run: `cd hey-pickler-admin && npm run build`
Expected: PASS。

- [ ] **Step 4: Commit**

```bash
git add hey-pickler-admin/src/router/index.ts hey-pickler-admin/src/views/events/EventDetailView.vue
git commit -m "feat(admin): 路由 meta 驱动 + 命名统一 + 详情页路由占位"
```

### Task 2.2: AppSidebar 改为 meta 驱动分组渲染

**Files:**
- Modify: `src/components/layout/AppSidebar.vue`

- [ ] **Step 1: 重写为按 `group` 分组的 `el-sub-menu` + 图标动态组件**

```vue
<template>
  <div class="sidebar">
    <div class="sidebar-logo">Hey Pickler 管理后台</div>
    <el-menu :default-active="activeMenu" class="sidebar-menu"
      background-color="#001529" text-color="#fff" active-text-color="#409EFF" :router="true">
      <el-sub-menu v-for="g in groups" :key="g.name" :index="g.name">
        <template #title><span>{{ g.name }}</span></template>
        <el-menu-item v-for="r in g.items" :key="r.path" :index="r.path">
          <el-icon><component :is="iconMap[r.icon]" /></el-icon>
          <span>{{ r.title }}</span>
        </el-menu-item>
      </el-sub-menu>
    </el-menu>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import * as ElIcons from '@element-plus/icons-vue'
import router from '@/router'

const route = useRoute()
const activeMenu = computed(() => route.path)
const iconMap: Record<string, any> = ElIcons as any

// 直接读静态路由表，避免 getRoutes() 返回 /login、/events/:id 等噪音
// 布局路由（path:'/'）的 children 才是菜单项；meta.hidden 不进菜单
const menuRoutes = (router.options.routes.find((r: any) => r.path === '/')?.children || [])
  .filter((r: any) => r.meta?.group && !r.meta?.hidden)
  .map((r: any) => ({ path: '/' + r.path, title: r.meta.title, icon: r.meta.icon, group: r.meta.group }))

const GROUP_ORDER = ['运营管理', '积分与赛季', '内容运营', '系统']
const groups = computed(() => GROUP_ORDER
  .map(name => ({ name, items: menuRoutes.filter(r => r.group === name) }))
  .filter(g => g.items.length))
</script>
```
> Dashboard child 的 `path: ''` → 拼成 `/`（与路由根一致）；其余 `'users'` → `/users`，与 `el-menu :router="true"` 的 index 语义一致。`/events/:id` 有 `meta.hidden` 被滤除，不进菜单。静态读取，无 `getRoutes()` 噪音风险。

- [ ] **Step 2: 门禁 + 人工验证**

Run: `npm run build`
人工：`npm run dev`，确认 4 组折叠、命名正确、点击跳转、`/events/:id` 不出现在菜单。

- [ ] **Step 3: Commit**

```bash
git add hey-pickler-admin/src/components/layout/AppSidebar.vue
git commit -m "feat(admin): 侧边栏改为 meta 驱动的 4 组分组渲染"
```

---

## Chunk 3: P1 赛事详情页（办赛指挥中心）—— 核心

### Task 3.1: 共享状态机常量 `eventStatus.ts`

**Files:**
- Create: `src/constants/eventStatus.ts`

> 这是状态显式化的单一事实源，对齐后端 `StatusTransitionValidator` 7 条规则，并修复前端漏写的 `OPEN→IN_PROGRESS` 等。

- [ ] **Step 1: 实现**

```ts
// 严格对齐 hey-pickler-server StatusTransitionValidator
export type EventStatus = 'DRAFT' | 'OPEN' | 'FULL' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED'

export const STATUS_LABEL: Record<EventStatus, string> = {
  DRAFT: '草稿', OPEN: '报名中', FULL: '名额已满',
  IN_PROGRESS: '进行中', COMPLETED: '已结束', CANCELLED: '已取消'
}

export const STATUS_COLOR: Record<EventStatus, string> = {
  DRAFT: '#909399', OPEN: '#10B981', FULL: '#F59E0B',
  IN_PROGRESS: '#3B82F6', COMPLETED: '#6B7280', CANCELLED: '#EF4444'
}

export const ALLOWED_TRANSITIONS: Record<EventStatus, EventStatus[]> = {
  DRAFT: ['OPEN', 'CANCELLED'],
  OPEN: ['FULL', 'IN_PROGRESS', 'CANCELLED'],
  FULL: ['OPEN', 'IN_PROGRESS', 'CANCELLED'],
  IN_PROGRESS: ['COMPLETED', 'CANCELLED'],
  COMPLETED: [],
  CANCELLED: []
}

export const getAllowedTargets = (s: EventStatus): EventStatus[] => ALLOWED_TRANSITIONS[s] || []

export const formatStatus = (s: string): string => STATUS_LABEL[s as EventStatus] || s
export const statusColor = (s: string): string => STATUS_COLOR[s as EventStatus] || '#6B7280'
```

- [ ] **Step 2: 门禁 + Commit**

```bash
npm run build
git add hey-pickler-admin/src/constants/eventStatus.ts
git commit -m "feat(admin): 抽取状态机共享常量（7 规则，修复漏写的转换）"
```

### Task 3.2: `api/matches.ts` + 类型

**Files:**
- Create: `src/api/matches.ts`；Modify: `src/types/index.ts`

- [ ] **Step 1: 在 `types/index.ts` 追加**

```ts
export interface GameScore { game: number; a: number; b: number }
export interface MatchItem {
  id: number; eventId: number; groupId: number
  slotAUserId: number | null; slotATeamId: number | null
  slotBUserId: number | null; slotBTeamId: number | null
  slotADisplayName: string | null; slotBDisplayName: string | null
  status: 'SCHEDULED' | 'IN_PROGRESS' | 'COMPLETED'
  games: GameScore[]; gamesWonA: number | null; gamesWonB: number | null
  submittedAt: string | null; completedAt: string | null
}
export interface StandingRow {
  participantKey: number; rank: number; wins: number; losses: number
  gamesFor: number; gamesAgainst: number; displayName: string | null
}
```

- [ ] **Step 2: 实现 `api/matches.ts`（镜像 `api/grouping.ts` 风格；baseURL 已是 `/api/admin`）**

```ts
import request from './request'
import type { ApiResponse, MatchItem, StandingRow, GameScore } from '@/types'

export const generateMatches = (eventId: number) =>
  request.post<any, ApiResponse<MatchItem[]>>(`/events/${eventId}/matches/generate`)

export const getEventMatches = (eventId: number) =>
  request.get<any, ApiResponse<MatchItem[][]>>(`/events/${eventId}/matches`)

export const getEventStandings = (eventId: number) =>
  request.get<any, ApiResponse<StandingRow[][]>>(`/events/${eventId}/standings`)

export const submitMatchScore = (matchId: number, games: GameScore[]) =>
  request.post<any, ApiResponse<void>>(`/matches/${matchId}/score`, { games })

export const resetMatch = (matchId: number) =>
  request.post<any, ApiResponse<void>>(`/matches/${matchId}/reset`)

export const completeEvent = (eventId: number) =>
  request.post<any, ApiResponse<void>>(`/events/${eventId}/complete`)
```

- [ ] **Step 3: 门禁 + Commit**

```bash
npm run build
git add hey-pickler-admin/src/api/matches.ts hey-pickler-admin/src/types/index.ts
git commit -m "feat(admin): 新增 api/matches.ts 与 Match/Standing 类型"
```

### Task 3.3: `MatchesPanel.vue`（对阵/比赛阶段）

**Files:**
- Create: `src/views/events/MatchesPanel.vue`

- [ ] **Step 1: 实现（按组展示对阵 + 代录/重置 + 实时 standings）**

```vue
<template>
  <div v-loading="loading" class="matches-panel">
    <div class="panel-header">
      <h3>对阵 / 比赛</h3>
      <div>
        <el-button v-if="!hasMatches" type="primary" :loading="genLoading"
          :disabled="!event.groupingLocked" @click="handleGenerate">生成对阵</el-button>
        <el-button v-if="hasMatches" type="success" :loading="standingLoading" @click="fetchStandings">刷新排名</el-button>
      </div>
    </div>
    <el-alert v-if="!event.groupingLocked" type="info" :closable="false" title="需先锁定分组后才能生成对阵" />
    <el-empty v-else-if="!hasMatches && !genLoading" description="尚未生成对阵" />

    <div v-for="(group, gi) in matches" :key="gi" class="group-block">
      <div class="group-title">第 {{ gi + 1 }} 组</div>
      <el-table :data="group" size="small">
        <el-table-column label="A" min-width="120"><template #default="{ row }">{{ row.slotADisplayName || '-' }}</template></el-table-column>
        <el-table-column label="比分" width="120"><template #default="{ row }">{{ scoreText(row) }}</template></el-table-column>
        <el-table-column label="B" min-width="120"><template #default="{ row }">{{ row.slotBDisplayName || '-' }}</template></el-table-column>
        <el-table-column label="状态" width="100"><template #default="{ row }">
          <el-tag size="small" :type="row.status === 'COMPLETED' ? 'success' : row.status === 'IN_PROGRESS' ? 'warning' : 'info'">{{ statusLabel(row.status) }}</el-tag>
        </template></el-table-column>
        <el-table-column label="操作" width="160" fixed="right"><template #default="{ row }">
          <el-button link type="primary" size="small" @click="openScore(row)">代录</el-button>
          <el-button v-if="row.status !== 'SCHEDULED'" link type="danger" size="small" @click="handleReset(row)">重置</el-button>
        </template></el-table-column>
      </el-table>
      <div v-if="standings[gi]?.length" class="standings">
        <span class="standings-title">本组排名：</span>
        <span v-for="s in standings[gi]" :key="s.participantKey" class="standing-item">
          #{{ s.rank }} {{ s.displayName }} ({{ s.wins }}胜{{ s.losses }}负)
        </span>
      </div>
    </div>

    <el-dialog v-model="scoreOpen" title="代录比分（三局两胜）" width="520px">
      <div v-for="(g, idx) in scoreForm" :key="idx" class="score-row">
        <span>第{{ idx + 1 }}局</span>
        <el-input-number v-model="g.a" :min="0" :max="30" />
        <el-input-number v-model="g.b" :min="0" :max="30" />
      </div>
      <div class="hint">规则：21 分起，净胜 2 分，单局 ≤30</div>
      <template #footer>
        <el-button @click="scoreOpen = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmitScore">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { generateMatches, getEventMatches, getEventStandings, submitMatchScore, resetMatch } from '@/api/matches'
import type { Event, MatchItem, StandingRow, GameScore } from '@/types'

const props = defineProps<{ event: Event }>()
const emit = defineEmits<{ completed: [] }>()

const loading = ref(false); const genLoading = ref(false)
const matches = ref<MatchItem[][]>([])
const standings = ref<StandingRow[][]>([])
const standingLoading = ref(false)
const hasMatches = computed(() => matches.value.some(g => g.length))

const fetchMatches = async () => {
  loading.value = true
  try { const r = await getEventMatches(props.event.id); if (r.code === 0) matches.value = r.data || [] } finally { loading.value = false }
}
const fetchStandings = async () => {
  standingLoading.value = true
  try { const r = await getEventStandings(props.event.id); if (r.code === 0) standings.value = r.data || [] } finally { standingLoading.value = false }
}
const handleGenerate = async () => {
  genLoading.value = true
  try {
    const r = await generateMatches(props.event.id)
    if (r.code === 0) { ElMessage.success('对阵已生成'); await fetchMatches(); await fetchStandings() }
    else ElMessage.error(r.message || '生成失败')
  } finally { genLoading.value = false }
}

const scoreOpen = ref(false); const submitting = ref(false)
const scoreForm = ref<GameScore[]>([{ game: 1, a: 0, b: 0 }, { game: 2, a: 0, b: 0 }, { game: 3, a: 0, b: 0 }])
const currentMatch = ref<MatchItem | null>(null)
const openScore = (m: MatchItem) => {
  currentMatch.value = m
  scoreForm.value = (m.games?.length ? m.games : [{ game: 1, a: 0, b: 0 }, { game: 2, a: 0, b: 0 }, { game: 3, a: 0, b: 0 }])
    .map(g => ({ game: g.game, a: g.a, b: g.b }))
  scoreOpen.value = true
}
const handleSubmitScore = async () => {
  if (!currentMatch.value) return
  submitting.value = true
  try {
    const r = await submitMatchScore(currentMatch.value.id, scoreForm.value)
    if (r.code === 0) { ElMessage.success('已录入'); scoreOpen.value = false; await fetchMatches(); await fetchStandings() }
    else ElMessage.error(r.message || '录入失败')
  } finally { submitting.value = false }
}
const handleReset = async (m: MatchItem) => {
  try { await ElMessageBox.confirm('确定重置该场比分？', '重置', { type: 'warning' }) } catch { return }
  const r = await resetMatch(m.id)
  if (r.code === 0) { ElMessage.success('已重置'); await fetchMatches(); await fetchStandings() } else ElMessage.error(r.message)
}

const scoreText = (m: MatchItem) => m.games?.length ? m.games.map(g => `${g.a}:${g.b}`).join(' ') : (m.gamesWonA != null ? `${m.gamesWonA}:${m.gamesWonB}` : '-')
const statusLabel = (s: string) => ({ SCHEDULED: '待打', IN_PROGRESS: '进行中', COMPLETED: '已完成' } as any)[s] || s

onMounted(fetchMatches)
</script>

<style scoped>
.panel-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.group-block { margin-bottom: 20px; }
.group-title { font-weight: 600; margin-bottom: 8px; color: #374151; }
.standings { margin-top: 8px; font-size: 13px; color: #6b7280; }
.standings-title { font-weight: 600; color: #374151; }
.standing-item { margin-right: 12px; }
.score-row { display: flex; gap: 12px; align-items: center; margin-bottom: 12px; }
.hint { color: #9ca3af; font-size: 12px; }
</style>
```

- [ ] **Step 2: 门禁 + Commit**

```bash
npm run build
git add hey-pickler-admin/src/views/events/MatchesPanel.vue
git commit -m "feat(admin): 新增对阵/比赛面板（生成对阵/代录/重置/排名）"
```

### Task 3.4: `IssuancePanel.vue`（发分阶段）

**Files:**
- Create: `src/views/events/IssuancePanel.vue`

- [ ] **Step 1: 实现（placement 配置 + 完成赛事并发分，前置校验未完成场次）**

```vue
<template>
  <div class="issuance-panel">
    <div class="panel-header"><h3>发分</h3></div>
    <el-alert v-if="event.status === 'COMPLETED'" type="success" :closable="false" title="赛事已结束，名次积分已发放" />
    <template v-else>
      <el-button type="primary" @click="openPlacement">配置加分表</el-button>
      <el-button type="success" :loading="completing" :disabled="event.status !== 'IN_PROGRESS'" @click="handleComplete">
        完成赛事并发分
      </el-button>
      <div class="hint">完成后将按加分表自动发放名次积分（source=PLACEMENT）。需所有比赛已完成，否则会提示未完成场次。</div>
    </template>
    <PlacementPointsDialog v-model="placementOpen" :event="event" @saved="emit('changed')" />
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { completeEvent, getEventMatches } from '@/api/matches'
import PlacementPointsDialog from './PlacementPointsDialog.vue'
import type { Event } from '@/types'

const props = defineProps<{ event: Event }>()
const emit = defineEmits<{ changed: [] }>()

const placementOpen = ref(false)
const completing = ref(false)
const openPlacement = () => { placementOpen.value = true }

const handleComplete = async () => {
  try {
    // 前置：检查是否有未完成比赛，给出更友好提示
    const mr = await getEventMatches(props.event.id)
    if (mr.code === 0) {
      const pending = (mr.data || []).flat().filter(m => m.status !== 'COMPLETED').length
      if (pending > 0) {
        ElMessage.warning(`还有 ${pending} 场比赛未完成，无法结赛`)
        return
      }
    }
    await ElMessageBox.confirm('确认完成赛事并发分？此操作不可撤销。', '完成并发分', { type: 'warning' })
  } catch { return }
  completing.value = true
  try {
    const r = await completeEvent(props.event.id)
    if (r.code === 0) { ElMessage.success('已完成并发分'); emit('changed') }
    else ElMessage.error(r.message || '完成失败')
  } finally { completing.value = false }
}
</script>

<style scoped>
.panel-header { margin-bottom: 12px; }
.hint { color: #9ca3af; font-size: 12px; margin-top: 12px; }
</style>
```

- [ ] **Step 2: 门禁 + Commit**

```bash
npm run build
git add hey-pickler-admin/src/views/events/IssuancePanel.vue
git commit -m "feat(admin): 新增发分面板（加分表配置 + 完成并发分前置校验）"
```

### Task 3.5: `RegistrationDrawerEmbed.vue`（报名阶段内嵌版）

**Files:**
- Create: `src/views/events/RegistrationDrawerEmbed.vue`

> `RegistrationDrawer.vue` 是 `el-drawer`（`modelValue` 受控），无法直接塞进详情页 tab。本任务抽出其报名表/签到/取消逻辑为内嵌组件（无 drawer 外壳），供详情页「报名」阶段使用。`RegistrationDrawer.vue` 本身保留（列表页「报名」按钮仍弹抽屉）。

- [ ] **Step 1: 创建内嵌组件（把 `RegistrationDrawer.vue` 的 `<el-table>` 段 + `<script setup>` 整体搬入，去掉 `el-drawer`/`modelValue`/`visible` 逻辑，改为 `onMounted(fetchRegistrations)`）**

```vue
<template>
  <div class="reg-embed">
    <div class="filter-bar">
      <el-select v-model="filterStatus" placeholder="报名状态" clearable style="width:130px" @change="handleFilter">
        <el-option label="已报名" value="REGISTERED" />
        <el-option label="已签到" value="CHECKED_IN" />
      </el-select>
      <el-button @click="handleFilter">查询</el-button>
      <el-button @click="handleReset">重置</el-button>
    </div>
    <!-- 此处 <el-table> 及各列 = RegistrationDrawer.vue 的报名表段原样搬入 -->
    <el-table :data="registrationList" size="small">
      <!-- 用户/城市/比赛类型/搭档/状态/报名时间/操作(签到·取消) -->
    </el-table>
    <Pagination v-model:page="pagination.page" v-model:size="pagination.size"
      :total="pagination.total" @update:page="fetchRegistrations" @update:size="fetchRegistrations" />
  </div>
</template>
<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import Pagination from '@/components/common/Pagination.vue'
import { getEventRegistrations, updateRegistrationStatus } from '@/api/events'
import type { Event, Registration } from '@/types'

const props = defineProps<{ event: Event }>()
const emit = defineEmits<{ changed: [] }>()
// 与 RegistrationDrawer.vue 同构：registrationList / pagination / fetchRegistrations /
// handleFilter / handleReset / handleCheckIn / handleWithdraw（去掉 visible/modelValue watch）
const registrationList = ref<Registration[]>([])
const pagination = reactive({ page: 1, size: 10, total: 0 })
const filterStatus = ref('')
const filterMatchType = ref('')
async function fetchRegistrations() { /* 同 RegistrationDrawer */ }
function handleFilter() { pagination.page = 1; fetchRegistrations() }
function handleReset() { filterStatus.value = ''; filterMatchType.value = ''; pagination.page = 1; fetchRegistrations() }
async function handleCheckIn(row: Registration) { /* 同 RegistrationDrawer，成功后 emit('changed') */ }
async function handleWithdraw(row: Registration) { /* 同 */ }
onMounted(fetchRegistrations)
</script>
```
> 实现纪律：`fetchRegistrations`/`handleCheckIn`/`handleWithdraw` 的完整实现 = `RegistrationDrawer.vue` 中同名函数原样复制（emit `changed` 代替原抽屉的 `emit('changed')`）。批量签到/导出（Task 4.2）会再增强抽屉与内嵌两处。

- [ ] **Step 2: 门禁 + Commit**

```bash
cd hey-pickler-admin && npm run build
git add hey-pickler-admin/src/views/events/RegistrationDrawerEmbed.vue
git commit -m "feat(admin): 抽出报名内嵌组件 RegistrationDrawerEmbed（详情页报名阶段用）"
```

### Task 3.6: `EventDetailView.vue`（指挥中心，串联 stepper + 状态显式化）

**Files:**
- Modify (替换桩): `src/views/events/EventDetailView.vue`

- [ ] **Step 1: 实现**

```vue
<template>
  <div v-loading="loading" class="event-detail">
    <div class="page-header">
      <div class="title-area">
        <el-button link @click="$router.back()"><el-icon><ArrowLeft /></el-icon>返回</el-button>
        <h1>{{ event?.title || '…' }}</h1>
        <el-tag v-if="event" :color="statusColor(event.status)" effect="dark">{{ formatStatus(event.status) }}</el-tag>
        <el-tag v-if="event?.format" size="small" effect="plain">{{ formatEventFormat(event.format) }}</el-tag>
        <el-tag size="small" :color="getEventTypeColor(event?.type)" effect="dark">{{ formatEventType(event?.type) }}</el-tag>
      </div>
      <div v-if="event">
        <el-button size="small" @click="editOpen = true">编辑</el-button>
      </div>
    </div>

    <div v-if="event" class="summary">
      <span>{{ event.location || '-' }}</span> · <span>{{ formatDate(event.eventTime) }}</span> ·
      <span>报名 {{ event.currentParticipants }}/{{ event.maxParticipants ?? '∞' }}</span>
    </div>

    <!-- Stepper -->
    <el-steps v-if="event" :active="activeStepIndex" finish-status="success" align-center class="stepper">
      <el-step v-for="s in steps" :key="s.key" :title="s.title" :status="s.status" />
    </el-steps>

    <!-- 阶段内容 -->
    <el-tabs v-if="event" v-model="activeTab" class="stage-tabs">
      <el-tab-pane label="基本信息" name="info">
        <p class="muted">基本信息通过右上「编辑」修改。</p>
      </el-tab-pane>
      <el-tab-pane label="报名" name="reg">
        <RegistrationDrawerEmbed :event="event" @changed="reload" />
      </el-tab-pane>
      <el-tab-pane label="分组" name="group">
        <GroupingPanel :key="`g-${event.id}-${event.groupingLocked}`" :event="event" />
      </el-tab-pane>
      <el-tab-pane label="对阵/比赛" name="match">
        <MatchesPanel :event="event" />
      </el-tab-pane>
      <el-tab-pane label="发分" name="issue">
        <IssuancePanel :event="event" @changed="reload" />
      </el-tab-pane>
    </el-tabs>

    <!-- 状态显式化：当前合法的下一阶段按钮 -->
    <div v-if="event" class="status-actions">
      <span class="muted">状态推进：</span>
      <el-button v-for="t in getAllowedTargets(event.status)" :key="t"
        :type="t === 'CANCELLED' ? 'danger' : 'primary'" plain size="small"
        @click="changeStatus(t)">
        → {{ formatStatus(t) }}
      </el-button>
      <span v-if="!getAllowedTargets(event.status).length" class="muted">（终态，无可用转换）</span>
    </div>

    <EventFormDialog v-model="editOpen" :event="event" @success="reload" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import { getEventDetail, changeEventStatus } from '@/api/events'
import { formatStatus, statusColor, getAllowedTargets, type EventStatus } from '@/constants/eventStatus'
import { formatDate, formatEventType, formatEventFormat, getEventTypeColor } from '@/utils'
import EventFormDialog from './EventFormDialog.vue'
import GroupingPanel from './GroupingPanel.vue'
import MatchesPanel from './MatchesPanel.vue'
import IssuancePanel from './IssuancePanel.vue'
import RegistrationDrawerEmbed from './RegistrationDrawerEmbed.vue'
import type { Event } from '@/types'

const route = useRoute()
const id = Number(route.params.id)
const loading = ref(false)
const event = ref<Event | null>(null)
const editOpen = ref(false)
const activeTab = ref('info')

const reload = async () => {
  loading.value = true
  try {
    const r = await getEventDetail(id)
    if (r.code === 0) event.value = r.data
    else ElMessage.error(r.message || '加载失败')
  } finally { loading.value = false }
}
onMounted(reload)

const STAGE_ORDER: Record<string, number> = { DRAFT: 0, OPEN: 1, FULL: 1, IN_PROGRESS: 2, COMPLETED: 3, CANCELLED: 0 }
const activeStepIndex = computed(() => {
  if (!event.value) return 0
  if (event.value.status === 'CANCELLED') return 0
  if (event.value.groupingLocked) return Math.max(STAGE_ORDER[event.value.status], 2)
  return STAGE_ORDER[event.value.status] ?? 0
})
const steps = computed(() => {
  const base = [
    { key: 'draft', title: '草稿', status: 'finish' as const },
    { key: 'reg', title: '报名', status: 'finish' as const },
    { key: 'group', title: '分组', status: 'finish' as const },
    { key: 'match', title: '对阵', status: 'finish' as const },
    { key: 'issue', title: '发分', status: 'finish' as const }
  ]
  const i = activeStepIndex.value
  base.forEach((s, idx) => {
    if (idx < i) s.status = 'finish'
    else if (idx === i) s.status = 'process'
    else s.status = 'wait'
  })
  return base
})

const changeStatus = async (t: EventStatus) => {
  if (!event.value) return
  const r = await changeEventStatus(event.value.id, t)
  if (r.code === 0) { ElMessage.success('状态已更新'); reload() }
  else ElMessage.error(r.message || '更新失败')
}
</script>

<style scoped>
.title-area { display: flex; align-items: center; gap: 12px; }
.summary { color: #6b7280; font-size: 13px; margin: 8px 0 16px; }
.stepper { margin-bottom: 16px; }
.stage-tabs { margin-bottom: 16px; }
.status-actions { display: flex; gap: 8px; align-items: center; padding: 12px; background: #f9fafb; border-radius: 8px; }
.muted { color: #9ca3af; font-size: 13px; }
</style>
```

> **依赖前置（前序任务已完成）：**
> - `getEventDetail`（`api/events.ts`）+ 后端 `GET /{id}`：由 Task 1.3 提供。
> - `RegistrationDrawerEmbed.vue`：由 Task 3.5 提供。

- [ ] **Step 2: 确认前序依赖就绪**

确认 Task 1.3 的 `getEventDetail` 与 Task 3.5 的 `RegistrationDrawerEmbed.vue` 已存在（`ls` 两个文件即可）。

- [ ] **Step 3: 门禁 + Commit**

```bash
cd hey-pickler-admin && npm run build
git add hey-pickler-admin/src/views/events/EventDetailView.vue
git commit -m "feat(admin): 赛事详情页指挥中心（stepper + 阶段聚合 + 状态显式化）"
```

### Task 3.7: 详情页全流程 e2e

**Files:**
- Create: `tests/e2e/event-detail.spec.ts`

- [ ] **Step 1: 先写 failing e2e（登录→进详情→推进状态→可见 stepper/按钮）**

```ts
import { test, expect } from '@playwright/test'

test.beforeEach(async ({ page }) => {
  await page.goto('/login')
  await page.fill('input[placeholder*="用户名"]', 'admin')
  await page.fill('input[placeholder*="密码"]', 'admin123')
  await page.click('button:has-text("登录")')
  await page.waitForURL('/')
})

test('event detail shows stepper and explicit status actions', async ({ page }) => {
  // 前置：需存在一个 DRAFT 事件（用 API 或已有种子）
  await page.goto('/events')
  await page.click('.el-table__row:first-child td:nth-child(3)') // 点标题进详情（按实际列调整）
  await expect(page.locator('.el-steps')).toBeVisible()
  // 状态推进按钮应出现且与后端规则一致
  await expect(page.locator('.status-actions')).toBeVisible()
})
```

- [ ] **Step 2: 跑 e2e（失败→实现细节修正→通过）**

Run: `cd hey-pickler-admin && npx playwright test event-detail`
Expected: 先 FAIL（选择器/路由未对齐）→ 调整至 PASS。

- [ ] **Step 3: Commit**

```bash
git add hey-pickler-admin/tests/e2e/event-detail.spec.ts
git commit -m "test(admin): 赛事详情页全流程 e2e"
```

---

## Chunk 4: P2 工作台待办 + 批量签到 + 名单导出

### Task 4.1: 工作台待办区

**Files:**
- Modify: `src/views/dashboard/DashboardView.vue`；可能 Modify: `src/api/dashboard.ts`（若无按 status 拉赛事的封装，复用 `getEventList`）

- [ ] **Step 1: 在 DashboardView KPI 行上方加「待办」区，前端聚合 `getEventList({status})`**

```vue
<!-- 插入到 .kpi-row 之前 -->
<div class="todo-panel">
  <div class="panel-head"><span>待办</span></div>
  <div v-if="!todos.length" class="muted">暂无待办</div>
  <div v-for="t in todos" :key="t.id" class="todo-row" @click="goDetail(t.id)">
    <el-tag size="small" :type="t.tagType">{{ t.label }}</el-tag>
    <span class="todo-title">{{ t.title }}</span>
    <span class="muted">{{ formatDate(t.eventTime) }}</span>
    <el-button link type="primary" size="small">{{ t.action }}</el-button>
  </div>
</div>
```
```ts
// script 内
import { getEventList } from '@/api/events'
import { useRouter } from 'vue-router'
const router = useRouter()
const todos = ref<Array<any>>([])
const buildTodos = async () => {
  const fetchBy = (status: string) => getEventList({ page: 1, size: 50, status }).then(r => r.code === 0 ? (r.data.list || []) : [])
  const [draft, open, prog] = await Promise.all([fetchBy('DRAFT'), fetchBy('IN_PROGRESS'), fetchBy('COMPLETED')])
  const now = Date.now()
  const upcoming = open  // OPEN/FULL 近 3 天
  const list: any[] = []
  draft.forEach(e => list.push({ id: e.id, title: e.title, eventTime: e.eventTime, label: '待发布', action: '去发布', tagType: 'info' }))
  upcoming.forEach(e => { const t = e.eventTime ? new Date(e.eventTime).getTime() - now : Infinity; if (t < 3 * 86400000) list.push({ id: e.id, title: e.title, eventTime: e.eventTime, label: '即将开赛', action: '去管理', tagType: 'warning' }) })
  prog.forEach(e => list.push({ id: e.id, title: e.title, eventTime: e.eventTime, label: '进行中', action: '去完成', tagType: 'danger' }))
  todos.value = list.sort((a, b) => new Date(a.eventTime || 0).getTime() - new Date(b.eventTime || 0).getTime()).slice(0, 12)
}
const goDetail = (id: number) => router.push(`/events/${id}`)
// onMounted 里追加 buildTodos()
```

> 注：`getEventList` 的 `type` 不传则两类都拉；按 status 过滤足够。STAR/PARTY 都进待办。

- [ ] **Step 2: 门禁 + Commit**

```bash
npm run build
git add hey-pickler-admin/src/views/dashboard/DashboardView.vue
git commit -m "feat(admin): 工作台新增待办区（前端聚合按状态）"
```

### Task 4.2: 批量签到 + 名单导出（RegistrationDrawer / Embed）

**Files:**
- Modify: `src/views/events/RegistrationDrawer.vue`（及其内嵌版）

- [ ] **Step 1: 报名表加多选 + 批量签到 + 导出按钮**

```vue
<!-- el-table 加 selection 列与工具栏 -->
<el-table :data="registrationList" size="small" @selection-change="onSelectionChange">
  <el-table-column type="selection" width="42" />
  <!-- 原有列... -->
</el-table>
<div class="bulk-bar">
  <span class="muted">已选 {{ selected.length }} 项</span>
  <el-button type="success" size="small" :disabled="!selected.length" :loading="bulkLoading" @click="handleBulkCheckIn">批量签到</el-button>
  <el-button size="small" @click="handleExport">导出名单(CSV)</el-button>
</div>
```
```ts
// script 内
const selected = ref<Registration[]>([])
const bulkLoading = ref(false)
const onSelectionChange = (rows: Registration[]) => { selected.value = rows }

const handleBulkCheckIn = async () => {
  if (!props.event) return
  const targets = selected.value.filter(r => r.status === 'REGISTERED')
  if (!targets.length) { ElMessage.info('无可签到的已报名项'); return }
  bulkLoading.value = true
  let ok = 0; const failed: string[] = []
  for (const r of targets) {                       // 串行，规避 per-IP 限流
    try {
      const res = await updateRegistrationStatus(props.event!.id, r.id, 'CHECKED_IN')
      if (res.code === 0) ok++; else failed.push(r.nickname || `#${r.id}`)
    } catch { failed.push(r.nickname || `#${r.id}`) }
  }
  bulkLoading.value = false
  ElMessage.success(`签到成功 ${ok} / ${targets.length}` + (failed.length ? `；失败 ${failed.length}` : ''))
  await fetchRegistrations(); emit('changed')
}

const handleExport = async () => {
  if (!props.event) return
  // 拉全量
  const all: Registration[] = []; let page = 1; let total = Infinity
  while (all.length < total) {
    const res = await getEventRegistrations(props.event.id, { page, size: 100 } as any)
    if (res.code !== 0) break
    all.push(...(res.data.list || [])); total = res.data.total || 0; page++
  }
  const header = ['ID', '昵称', '城市', '比赛类型', '搭档', '状态', '报名时间']
  const rows = all.map(r => [r.id, r.nickname || '', r.city || '', r.matchType, r.partnerNickname || (r.partnerId ? 'ID:' + r.partnerId : ''), r.status, r.createdAt])
  const csv = [header, ...rows].map(cols => cols.map(c => `"${String(c).replace(/"/g, '""')}"`).join(',')).join('\n')
  const blob = new Blob(['﻿' + csv], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a'); a.href = url; a.download = `event-${props.event.id}-registrations.csv`; a.click()
  URL.revokeObjectURL(url)
}
```

- [ ] **Step 2: 门禁 + 人工验证（空选/全选/部分失败/导出打开正常）+ Commit**

```bash
npm run build
git add hey-pickler-admin/src/views/events/RegistrationDrawer.vue
git commit -m "feat(admin): 报名支持批量签到与名单导出(CSV)"
```

---

## Chunk 5: P3 排名分页/搜索 + 列表信息密度 + 共享组件

### Task 5.1: 排名分页 + 搜索（接后端 keyword）

**Files:**
- Modify: `src/views/rankings/RankingView.vue`；Modify: `src/api/rankings.ts`

- [ ] **Step 1: `api/rankings.ts` 的 `getRankings` 透传 keyword + 分页**

```ts
export interface RankingQuery { type: string; page?: number; size?: number; keyword?: string; tier?: string }
export const getRankings = (q: RankingQuery) =>
  request.get<any, ApiResponse<PageResult<RankingEntry>>>(`/rankings/${q.type}`, { params: { page: q.page, size: q.size, keyword: q.keyword, tier: q.tier } })
```

- [ ] **Step 2: RankingView 改为单 tab 数据 + 分页 + 搜索框（替换写死 size:100 的 Promise.all 双拉）**

```ts
const activeTab = ref<'STAR' | 'PARTY'>('STAR')
const list = ref<RankingEntry[]>([])
const keyword = ref('')
const page = ref(1); const size = ref(20); const total = ref(0)
const fetchOne = async () => {
  const r = await getRankings({ type: activeTab.value, page: page.value, size: size.value, keyword: keyword.value || undefined })
  if (r.code === 0) { list.value = r.data.list; total.value = r.data.total }
}
const onSearch = () => { page.value = 1; fetchOne() }
watch(activeTab, onSearch)
onMounted(fetchOne)
```
模板加 `<el-input v-model="keyword" @keyup.enter="onSearch" placeholder="搜索昵称" clearable @clear="onSearch" />` + `<Pagination v-model:page="page" v-model:size="size" :total="total" @update:page="fetchOne" @update:size="fetchOne" />`，表格 `:data="list"`。

- [ ] **Step 3: 门禁 + e2e + Commit**

```bash
npm run build
git add hey-pickler-admin/src/views/rankings/RankingView.vue hey-pickler-admin/src/api/rankings.ts
git commit -m "feat(admin): 排名分页 + 昵称搜索（接后端 keyword）"
```

- [ ] **Step 4: 排名搜索 e2e（可选，`tests/e2e/ranking-search.spec.ts`）**

```ts
test('ranking keyword search filters list', async ({ page }) => {
  await page.goto('/rankings')
  await page.fill('input[placeholder="搜索昵称"]', '张')
  await page.press('input[placeholder="搜索昵称"]', 'Enter')
  // 断言表格行数 ≤ total 或含高亮
})
```

### Task 5.2: 共享组件 `EventStatusBadge` + `EventFilterBar`，列表接入

**Files:**
- Create: `src/components/common/EventStatusBadge.vue`, `src/components/common/EventFilterBar.vue`
- Modify: `src/views/events/EventListView.vue`, `src/views/activities/ActivityListView.vue`

- [ ] **Step 1: `EventStatusBadge.vue`（用 `eventStatus.ts` 渲染徽章 + 合法转换 popover）**

```vue
<template>
  <el-popover placement="bottom" :width="160" trigger="click" :visible="visible" @update:visible="visible = $event">
    <template #reference>
      <span class="status-badge clickable" :style="{ backgroundColor: statusColor(status) }" @click="!readonly && (visible = true)">
        {{ formatStatus(status) }} <span v-if="!readonly">▾</span>
      </span>
    </template>
    <div class="status-options">
      <div v-for="t in getAllowedTargets(status)" :key="t" class="status-option" @click="pick(t)">
        <span class="status-dot" :style="{ backgroundColor: statusColor(t) }" /> {{ formatStatus(t) }}
      </div>
      <div v-if="!getAllowedTargets(status).length" class="status-option disabled">无可用转换</div>
    </div>
  </el-popover>
</template>
<script setup lang="ts">
import { ref } from 'vue'
import { formatStatus, statusColor, getAllowedTargets, type EventStatus } from '@/constants/eventStatus'
const props = defineProps<{ status: EventStatus; readonly?: boolean }>()
const emit = defineEmits<{ change: [t: EventStatus] }>()
const visible = ref(false)
const pick = (t: EventStatus) => { visible.value = false; emit('change', t) }
</script>
```

- [ ] **Step 2: `EventFilterBar.vue`（关键字 + 类型 + 状态 + 快捷 chip）**

抽取 EventListView/ActivityListView 共有的筛选栏，emit `filter`/`reset`。含快捷 chip：草稿/报名中/进行中/已结束（点击设 status 并 emit）。

- [ ] **Step 3: 两份列表接入共享组件 + 报名进度条**

`EventListView`/`ActivityListView`：状态列改用 `<EventStatusBadge :status @change="handleChangeStatus" />`（合法转换来自共享常量，自动补齐 `OPEN→进行中` 等）；筛选栏换 `<EventFilterBar @filter @reset />`；报名列加 `<el-progress :percentage="..." />`（复用 RegistrationDrawer 的进度算法）；标题列点击 `router.push('/events/'+row.id)`。

- [ ] **Step 4: 门禁 + 回归（两页筛选/状态/分页/跳详情）+ Commit**

```bash
npm run build
git add hey-pickler-admin/src/components/common/ hey-pickler-admin/src/views/events/EventListView.vue hey-pickler-admin/src/views/activities/ActivityListView.vue
git commit -m "refactor(admin): 抽取状态徽章/筛选栏共享组件，列表接入+进度条"
```

---

## 完成验收（对照 spec §9）

- [ ] 详情页全流程 E2E（建赛→发布→报名→分组→对阵→录分→完成发分）STAR 单/双/混各跑一遍
- [ ] 7 条状态转换在详情页均可点、非法转换不出现
- [ ] 赛事/活动两份列表回归：筛选、状态变更、分页、跳详情
- [ ] 批量签到 / 名单导出边界：空选、全选、部分失败、限流
- [ ] 排名分页/搜索：翻页、keyword 命中/未命中
- [ ] 菜单 4 组渲染、命名正确、路由守卫不受影响
- [ ] CI 绿：后端 `mvn test`（单测+集成）、前端 `lint:check`+`build`+e2e

## Roadmap（不在本计划，对应 spec §8 / 方案 B·C）

- 工作台待办后端聚合 API；批量签到后端接口（规避 per-admin 限流）
- 群发通知推送；用户双积分明细页/趋势；对阵表增强可视化（需新表/新服务/DB 迁移 V16+）
