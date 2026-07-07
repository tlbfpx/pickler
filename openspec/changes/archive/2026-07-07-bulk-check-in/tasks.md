# Tasks: 批量签到 API

## 1. Data layer

- [ ] 1.1 Add `RegistrationMapper.findByEventAndIds(@Param("eventId") Long eventId, @Param("ids") List<Long> ids)` returning `List<Registration>` (id+status fields only used)
- [ ] 1.2 Add `RegistrationMapper.updateStatusToCheckedIn(@Param("ids") List<Long> ids)` returning `int` (rows affected)
- [ ] 1.3 Verify both with single-SQL test cases

## 2. DTOs

- [ ] 2.1 Create `dto/admin/BulkCheckInRequest.java` with @NotNull + @Size validation
- [ ] 2.2 Create `vo/BulkCheckInResult.java` with nested Skipped class

## 3. Service layer

- [ ] 3.1 Add `bulkCheckIn(Long eventId, List<Long> ids)` to `EventService` interface
- [ ] 3.2 Implement in `EventServiceImpl` with 7-step algorithm in spec
- [ ] 3.3 Add `@Transactional(rollbackFor = Exception.class)` for atomicity

## 4. Controller layer

- [ ] 4.1 Add `bulkCheckIn(Long eventId, @Valid @RequestBody BulkCheckInRequest body)` to `AdminEventController`
- [ ] 4.2 Map `BizException(PARAM_ERROR)` and `BizException(NOT_FOUND)` to standard error responses

## 5. Tests (TDD)

- [ ] 5.1 EventServiceTest: 6 new cases (happy mixed, withdrawn, not-found, empty, size>200, event not found)
- [ ] 5.2 AdminEventControllerTest: 3 new cases (happy, 400 size>200, 404 event not found)
- [ ] 5.3 LambdaCache warm-up not needed (no entity lambda queries)

## 6. Verification

- [ ] 6.1 `mvn test -Dtest='EventServiceTest'` — all green
- [ ] 6.2 `mvn test -Dtest='AdminEventControllerTest'` — all green
- [ ] 6.3 `mvn verify` — full suite + jacoco:check passes
- [ ] 6.4 Coverage delta: should not drop below 87% (Loop-v13 baseline)

## 7. Documentation

- [ ] 7.1 No CLAUDE.md updates needed (controller description in package structure is high-level)

## 8. Rollout

- [ ] 8.1 Atomic commits per task category (data / dto / service / controller / tests)
- [ ] 8.2 PR via `feature/bulk-check-in` branch
- [ ] 8.3 CI must pass before merge
- [ ] 8.4 After merge: do NOT archive OpenSpec yet

## Order of execution

1. DTOs first (no deps)
2. Mapper methods (3 small additions)
3. Service (depends on DTOs + mappers)
4. Tests for service
5. Controller
6. Tests for controller
7. Verify
8. Commit per category

5-6 atomic commits total.
