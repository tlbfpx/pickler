# Design: 批量签到 API

## 接口设计

### `POST /api/admin/events/{eventId}/registrations/bulk-check-in`

**权限**: `@RequireRole({SUPER_ADMIN, ADMIN})`

**Request Body**:
```json
{
  "registrationIds": [101, 102, 103, ..., 200]
}
```

**Validation**:
- `registrationIds` 不可为 null/empty
- max 200 项（防止 OOM / SQL 过长）
- 每个 id 必须 > 0

**Response 200** (`BulkCheckInResult`):
```json
{
  "code": 0,
  "data": {
    "eventId": 7,
    "requested": 100,
    "updated": 95,
    "skipped": {
      "notFound": [201, 999],
      "withdrawn": [301, 302]
    },
    "updatedRegistrationIds": [101, 102, 103]
  }
}
```

**Errors**:
- 400 PARAM_ERROR: list empty / size > 200
- 404: eventId 不存在
- 401/403: 认证/角色

## 数据流

```
POST /events/{eventId}/registrations/bulk-check-in
  ↓ AdminEventController.bulkCheckIn(eventId, body)
    ↓ EventService.bulkCheckIn(eventId, ids)
      1. requireEvent(eventId)
      2. SELECT id, status FROM registration WHERE id IN (ids) AND event_id = eventId
         → classify into found_not_checked_in / found_withdrawn / not_found
      3. UPDATE registration SET status='CHECKED_IN' WHERE id IN (to_update)
      4. Return BulkCheckInResult
    ↓ Result<BulkCheckInResult>
```

**SQL 优化**：1 个 SELECT + 1 个 UPDATE，2 次 round-trip。Idempotent：重复调用幂等（已 CHECKED_IN 的会 skip）。

## 数据 schema

```java
@Data
class BulkCheckInRequest {
    @NotNull @Size(min=1, max=200) List<Long> registrationIds;
}

@Data
class BulkCheckInResult {
    Long eventId;
    Integer requested;
    Integer updated;
    Skipped skipped;
    List<Long> updatedRegistrationIds;
}

@Data
class Skipped {
    List<Long> notFound;        // ids not in registration table for this event
    List<Long> withdrawn;       // ids already WITHDRAWN
}
```

## 实现位置
- `controller/admin/AdminEventController.java` 加 `bulkCheckIn` method
- `service/EventService.java` 加 `bulkCheckIn(eventId, ids)` method
- `service/impl/EventServiceImpl.java` 实现
- `dto/admin/BulkCheckInRequest.java` 新 DTO
- `vo/BulkCheckInResult.java` 新 VO
- `mapper/RegistrationMapper.java` 加 `selectByEventAndIds` + `updateStatusToCheckedIn`

## 测试计划
- `EventServiceTest`: 6 个新用例（happy mixed、with withdrawn、with not-found、empty list throws、size>200 throws、event not found throws）
- `AdminEventControllerTest`: 3 个新用例（happy、size>200 400、event not found 404）
