# Tasks: admin UI 集成

## 1. Type definitions
- [ ] 1.1 Create `src/types/event-summary.ts` with EventSummaryVO + 4 nested interfaces
- [ ] 1.2 Create `src/types/bulk-check-in.ts` with BulkCheckInResult + Skipped

## 2. API client
- [ ] 2.1 Add `getEventSummary(eventId)` to `src/api/events.ts`
- [ ] 2.2 Add `bulkCheckIn(eventId, ids)` to `src/api/events.ts`
- [ ] 2.3 Add type imports

## 3. RegistrationDrawer.vue (bulk check-in UI)
- [ ] 3.1 Add selection column to `<el-table>`
- [ ] 3.2 Add "批量签到" button in drawer footer
- [ ] 3.3 Wire `onBulkCheckIn()` handler with ElMessage feedback
- [ ] 3.4 Refresh list after success

## 4. EventDetailView.vue (summary card)
- [ ] 4.1 Add summary state ref
- [ ] 4.2 onMounted / watch calls `getEventSummary`
- [ ] 4.3 Render 4-metric card at top of page

## 5. Verification
- [ ] 5.1 `npm run build` — passes
- [ ] 5.2 `npm run lint:check` — clean
- [ ] 5.3 (optional) `npm run test:e2e` with backend up

## 6. Rollout
- [ ] 6.1 Branch: `feature/admin-ui-loop-v15-integration`
- [ ] 6.2 Atomic commit per task
- [ ] 6.3 PR to master
- [ ] 6.4 CI must pass

## Order
1. Types (no deps)
2. API client
3. RegistrationDrawer
4. EventDetailView
5. Verify + commit + PR
