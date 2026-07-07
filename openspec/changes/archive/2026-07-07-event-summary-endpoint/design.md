# Design: 赛事汇总 endpoint

## 接口设计

### `GET /api/admin/events/{id}/summary`

**权限**：`@RequireRole({SUPER_ADMIN, ADMIN, OPERATOR})`

**Response** (`200 OK`):
```json
{
  "code": 0,
  "data": {
    "eventId": 7,
    "title": "周六下午双打",
    "type": "DOUBLES",
    "status": "OPEN",
    "eventTime": "2026-07-12T14:00:00",
    "maxParticipants": 32,
    "currentParticipants": 18,
    "fillRate": 0.5625,

    "registration": {
      "registered": 18,
      "checkedIn": 0,
      "withdrawn": 2,
      "checkInRate": 0.0
    },

    "teams": {
      "pending": 4,
      "confirmed": 5
    },

    "matches": {
      "scheduled": 0,
      "inProgress": 0,
      "completed": 0
    },

    "fees": {
      "totalCollected": 0,
      "currency": "CNY"
    },

    "transitionableStatuses": ["FULL", "IN_PROGRESS", "CANCELLED"]
  }
}
```

**错误**：`404 Not Found`（eventId 不存在 / soft-deleted）/ `401 Unauthorized`（无 token）/ `403 Forbidden`（无角色）

## 数据流

```
GET /api/admin/events/{id}/summary
  ↓ AdminEventController.eventSummary(id)
    ↓ EventService.getEventSummary(id)
      1. eventMapper.selectById(id) → Event entity
      2. registrationMapper.selectCount(group by status) → 3-row result
      3. teamMapper.selectCount(group by status) → 2-row result
      4. matchMapper.selectCount(group by status) → 3-row result
      5. 计算派生：fillRate, checkInRate
    ↓ EventSummaryVO 构造
  ↓ Result<EventSummaryVO>
```

**SQL 优化**：4 个 `SELECT COUNT(*) GROUP BY status` 比循环 `SELECT COUNT(*) WHERE status=?` 略差（一次查询返回多行）。但 4 个独立查询仍比 N+1 强。

## 数据 schema

`EventSummaryVO` (新 DTO, 12 fields):
```java
public class EventSummaryVO {
    private Long eventId;
    private String title;
    private String type;
    private String status;
    private LocalDateTime eventTime;
    private Integer maxParticipants;
    private Integer currentParticipants;
    private Double fillRate;        // current / max, 0.0 if max is null

    private RegistrationCountVO registration;
    private TeamCountVO teams;
    private MatchCountVO matches;
    private FeeSummaryVO fees;
    private List<String> transitionableStatuses;
}

class RegistrationCountVO {
    Integer registered, checkedIn, withdrawn;
    Double checkInRate;  // checkedIn / registered, 0.0 if 0
}
class TeamCountVO { Integer pending, confirmed; }
class MatchCountVO  { Integer scheduled, inProgress, completed; }
class FeeSummaryVO   { Long totalCollected; String currency; }
```

## 实现位置
- `controller/admin/AdminEventController.java` 加 `eventSummary` method
- `service/EventService.java` 加 `getEventSummary` method
- `service/impl/EventServiceImpl.java` 实现
- `vo/EventSummaryVO.java` 新 DTO + 4 个 nested static
- `mapper/RegistrationMapper.java`、`TeamMapper.java`、`MatchMapper.java` 加 `selectCountByEventGroupedByStatus` 三个新方法

## 测试计划
- `EventServiceTest` 加 6 个用例：singles/doubles、empty registration、has withdrawn、maxParticipants null、event not found
- `AdminEventControllerTest` 加 3 个用例：happy path、role check、eventId 不存在 404
- 覆盖率门槛 80%/60%（Loop-v12 baseline 87.80%，不该掉太多）
