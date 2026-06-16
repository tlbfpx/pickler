# Design: 清理孤儿排名 / 报名引用

## 总体思路

**两步走**：
1. **数据修复**：一次性 SQL 清理历史孤儿
2. **查询防御**：在读取入口 INNER JOIN / batch-load-then-filter，防止未来再次产生孤儿时 UI 空白

## 数据修复

### V7 migration

`V7__cleanup_orphan_rankings_and_registrations.sql`：

```sql
-- 1. 删除 ranking 表里指向已删用户的孤儿行
DELETE r FROM ranking r
LEFT JOIN user u ON r.user_id = u.id AND u.deleted_at IS NULL
WHERE u.id IS NULL;

-- 2. 软删 registration 表里指向已删用户的孤儿行
UPDATE registration reg
LEFT JOIN user u ON reg.user_id = u.id AND u.deleted_at IS NULL
SET reg.deleted_at = NOW(), reg.status = 'CANCELLED'
WHERE u.id IS NULL AND reg.deleted_at IS NULL;
```

**决策**：
- ranking 硬删 — 排名是衍生数据，可由 `refreshRankings` 重建
- registration 软删 — 报名是原始业务数据，保留可审计
- registration 同时改 status 为 CANCELLED — 让 `AdminDashboardController` 的 `notIn(WITHDRAWN, CANCELLED)` 过滤生效，dashboard 自然不显示

**关于 Flyway 语法**：
- MySQL 8 支持 `DELETE ... LEFT JOIN` 和 `UPDATE ... LEFT JOIN ... SET`
- `NOW()` 是 MySQL 函数，与项目现有 migration 风格一致

## 查询防御

### RankingServiceImpl.getRankings

当前代码（line 189-200）：
```java
List<Ranking> rankings = rankingMapper.selectList(queryWrapper);
List<Long> userIds = rankings.stream().map(Ranking::getUserId).distinct().collect(Collectors.toList());
Map<Long, User> userMap = batchLoadUsers(userIds);

List<RankingVO> result = rankings.stream().map(ranking -> {
    RankingVO vo = new RankingVO();
    // ...
    User user = userMap.get(ranking.getUserId());
    if (user != null) {
        vo.setNickname(user.getNickname());
        // ...
    }
    return vo;
}).collect(Collectors.toList());
```

改为：
```java
List<Ranking> rankings = rankingMapper.selectList(queryWrapper);
List<Long> userIds = rankings.stream().map(Ranking::getUserId).distinct().collect(Collectors.toList());
Map<Long, User> userMap = batchLoadUsers(userIds);

// 过滤孤儿行（user 已被软删）
List<RankingVO> result = rankings.stream()
    .filter(r -> userMap.containsKey(r.getUserId()))
    .map(ranking -> {
        RankingVO vo = new RankingVO();
        // ...（不变）
        User user = userMap.get(ranking.getUserId());
        vo.setNickname(user.getNickname());  // 此时 user 必非 null，可去掉 if
        vo.setAvatarUrl(user.getAvatarUrl());
        vo.setCity(user.getCity());
        return vo;
    }).collect(Collectors.toList());
```

**同步改动 `getTop5`**：同样加 filter。

**缓存**：原代码用 Redis 缓存整个 result list，本次 filter 后 list 已不含孤儿，缓存行为不变。

### AdminDashboardController.getStats

当前代码（line 144-168）有显式 fallback "未知"。改为：在写入 recent list 时过滤孤儿：

```java
List<Map<String, Object>> recentRegList = recentRegs.stream()
    .filter(reg -> userMap.containsKey(reg.getUserId()))  // 新增
    .map(reg -> {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", reg.getId());
        User user = userMap.get(reg.getUserId());
        item.put("nickname", user.getNickname());  // user 必非 null
        // ...（不变）
    }).collect(Collectors.toList());
```

**决策**：filter 而非降级 fallback — 既然数据已清，"未知" 永远不应再出现。如果再出现，是 bug，应让前端显式报错而不是悄悄显示 "未知"。

## 数据流

```
V7 migration 启动时跑一次 → 清理历史孤儿
                                ↓
ranking 表 / registration 表 干净
                                ↓
ranking API 调用 → selectList → filter（防御未来孤儿）
                                ↓
返回 list 全部有 user 信息
```

## 关键决策

| 决策点 | 选择 | 拒绝的备选 | 理由 |
|--------|------|-----------|------|
| ranking 处理 | 硬 DELETE | 软删 + 标记 | 排名衍生数据，无审计价值 |
| registration 处理 | 软删 + CANCELLED | 硬删 | 业务原始数据，保留审计 |
| 查询过滤位置 | Service 层 | SQL JOIN | MyBatis-Plus 习惯 + 不破坏现有缓存 |
| Dashboard fallback | 移除 "未知" | 保留 | "未知" 是 silent failure，hide bug |
| FK 约束 | 不加 | 加 ON DELETE CASCADE | 项目无 FK 约定；FK 改 schema 影响大 |

## 风险

| 风险 | 缓解 |
|------|------|
| migration 在 prod 跑时锁表大 | ranking 表预计 < 1000 行，无影响 |
| 误删非孤儿数据 | LEFT JOIN + 双条件（u.id IS NULL）确保只删孤儿 |
| 未来又产生孤儿 | 查询防御 filter 已加；定期巡检可加 cron（v2） |

## 测试策略

- Migration 测试：项目无 Flyway test 框架，靠 IntegrationTest 间接覆盖
- Service 单测 `RankingServiceImplTest.getRankings_filtersOrphans`：mock `userMapper.selectBatchIds` 返回缺一个 user，断言结果 list 不含该 ranking
- Controller 单测 `AdminDashboardControllerTest.getStats_filtersOrphans`：类似
- 手测：部署后 `/rankings` 前 30 名全部有用户名
