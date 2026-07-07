# Tasks: event search multi-keyword

## 1. Service

- [ ] 1.1 Modify `EventServiceImpl.adminListEvents` signature to add `sort` parameter
- [ ] 1.2 Refactor keyword handling to multi-token + (title OR description)
- [ ] 1.3 Add sort parsing logic (default + valid values)

## 2. Controller

- [ ] 2.1 Update `AdminEventController.listEvents` to accept `sort` query param
- [ ] 2.2 Pass through to service

## 3. Tests (TDD)

- [ ] 3.1 EventServiceTest: 5 new cases (multi-token AND, single OR title, single OR desc, empty ignored, sort 3 values)
- [ ] 3.2 AdminEventControllerTest: 1 case (passes sort param)

## 4. Verification

- [ ] 4.1 `mvn test -Dtest=EventServiceTest` — green
- [ ] 4.2 `mvn verify` — full suite green
- [ ] 4.3 Coverage ≥ 88% (Loop-v15 baseline)

## 5. Rollout

- [ ] 5.1 Branch: `feature/event-search-multi-keyword`
- [ ] 5.2 Atomic commits: service / controller / tests
- [ ] 5.3 PR + CI
