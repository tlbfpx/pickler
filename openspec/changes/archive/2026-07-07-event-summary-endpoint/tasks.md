# Tasks: 赛事汇总 endpoint

## 1. Data layer

- [ ] 1.1 Add `RegistrationMapper.selectCountByEventGroupedByStatus(eventId)` returning `List<Map<String, Long>>` (or a typed projection)
- [ ] 1.2 Add `TeamMapper.selectCountByEventGroupedByStatus(eventId)` returning status-keyed counts
- [ ] 1.3 Add `MatchMapper.selectCountByEventGroupedByStatus(eventId)` returning status-keyed counts
- [ ] 1.4 Verify all 3 mapper methods with `selectList(groupBy)` pattern

## 2. DTO

- [ ] 2.1 Create `vo/EventSummaryVO.java` with all 12+ fields
- [ ] 2.2 Create 4 nested static classes: `RegistrationCountVO`, `TeamCountVO`, `MatchCountVO`, `FeeSummaryVO`
- [ ] 2.3 Add Lombok `@Data` annotation for all

## 3. Service layer

- [ ] 3.1 Add `getEventSummary(Long eventId)` to `EventService` interface
- [ ] 3.2 Implement in `EventServiceImpl` with the 9-step algorithm in spec
- [ ] 3.3 Use existing `StatusTransitionValidator.getAllowedTargets()` for transitionableStatuses

## 4. Controller layer

- [ ] 4.1 Add `eventSummary(Long id)` method to `AdminEventController`
- [ ] 4.2 Map `BizException(NOT_FOUND)` to `Result.fail(NOT_FOUND)`

## 5. Tests (TDD)

- [ ] 5.1 EventServiceTest: 6 new cases (singles happy, doubles happy, no registrations, all withdrawn, maxParticipants null, event not found)
- [ ] 5.2 AdminEventControllerTest: 3 new cases (happy, role check by class annotation, eventId 404)
- [ ] 5.3 LambdaCache `@BeforeAll` warm-up for the 3 mappers

## 6. Verification

- [ ] 6.1 `mvn test -Dtest='EventServiceTest'` — all green
- [ ] 6.2 `mvn test -Dtest='AdminEventControllerTest'` — all green
- [ ] 6.3 `mvn verify` — full suite + jacoco:check passes
- [ ] 6.4 Coverage delta: should not drop below 87% (Loop-v12 baseline)

## 7. Documentation

- [ ] 7.1 Update `CLAUDE.md` package structure with the new DTO + endpoint (optional, only if existing AdminEventController description needs update)
- [ ] 7.2 No memory updates — this is a new feature, not a loop fix

## 8. Rollout

- [ ] 8.1 Commit atomic per task category (data / dto / service / controller / tests / verify)
- [ ] 8.2 PR via `feature/event-summary-endpoint` branch (per AGENTS.md)
- [ ] 8.3 CI must pass before merge
- [ ] 8.4 After merge: do NOT archive OpenSpec yet (waiting for actual client sign-off like Phase 1)

## Order of execution (recommended)

1. DTO first (no dependencies)
2. Mapper methods (3 small additions)
3. Service (depends on DTO + mappers)
4. Tests for service
5. Controller
6. Tests for controller
7. Verify full suite
8. Commit per category

This is 6-8 atomic commits, similar to Loop-v1 fix-loop.
