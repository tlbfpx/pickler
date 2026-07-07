# Design: 赛事搜索增强

## 后端签名

`EventService.adminListEvents(type, status, keyword, location, startTime, endTime, sort, page, size)`

- `sort`: 新增参数。`"event_time"` (default) / `"created_at"` / `"current_participants"`
- 已有参数不变

## 算法变化

keyword 解析：
```java
String[] words = keyword.trim().split("\\s+");
if (words.length == 0 || words[0].isEmpty()) {
    // no filter
} else {
    wrapper.and(w -> {
        for (String word : words) {
            w.or().like(Event::getTitle, word)
             .or().like(Event::getDescription, word);
        }
    });
}
```

→ 行为：
- `keyword="周六"` → title LIKE '%周六%' OR description LIKE '%周六%'
- `keyword="周六 双打"` → (周六 in title/desc) AND (双打 in title/desc)
- `keyword=null` or empty → 无 keyword 过滤

sort 实现：
```java
boolean asc = sort != null && sort.endsWith("_asc");
String field = asc ? sort.substring(0, sort.length() - 4) : sort;
switch (field) {
    case "created_at": wrapper.orderBy(true, asc, Event::getCreatedAt); break;
    case "current_participants": wrapper.orderBy(true, asc, Event::getCurrentParticipants); break;
    default: wrapper.orderByDesc(Event::getEventTime);
}
```

## 测试计划

EventServiceTest 新增 5 cases:
1. keyword 单 token → title OR description 命中
2. keyword 多 token → 每个都 AND
3. keyword 命中 description（不在 title）
4. keyword 空 → 不过滤
5. sort 参数 3 种 → 验证 SQL order by 子句

AdminEventControllerTest 1 case:
- keyword 多 token 走 controller → service 透传
