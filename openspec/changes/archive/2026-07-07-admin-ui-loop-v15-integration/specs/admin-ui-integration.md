# Spec: admin UI 集成

## getEventSummary(eventId)

API client wrapper for `GET /api/admin/events/{id}/summary` (Loop-v13 backend).

### Signature
```ts
getEventSummary(eventId: number): Promise<ApiResponse<EventSummaryVO>>
```

### Behavior
- Throws axios error on network failure / 4xx / 5xx
- Returns `ApiResponse<EventSummaryVO>` with `code: 0` and `data: EventSummaryVO` on success

## bulkCheckIn(eventId, registrationIds)

API client wrapper for `POST /api/admin/events/{eventId}/registrations/bulk-check-in` (Loop-v14 backend).

### Signature
```ts
bulkCheckIn(eventId: number, registrationIds: number[]): Promise<ApiResponse<BulkCheckInResult>>
```

### Behavior
- `registrationIds` 1-200 items, each > 0 (validated server-side; client should pre-validate too)
- Throws on invalid input (server returns 400 PARAM_ERROR)
- Returns `ApiResponse<BulkCheckInResult>` with `code: 0` and bucketed counts

## RegistrationDrawer.vue 改造

Add a multi-select checkbox column to the registration list and a "批量签到" action in the footer.

### UI changes
- `<el-table-column type="selection" width="55" />` as the first column
- `:row-key="(row) => row.id"` so el-table tracks selected rows by id
- Footer adds "批量签到" button:
  ```vue
  <el-button type="success" :disabled="selected.length === 0" @click="onBulkCheckIn">
    批量签到 ({{ selected.length }})
  </el-button>
  ```
- `onBulkCheckIn()`:
  1. Disable button
  2. Call `bulkCheckIn(props.event.id, selected.map(r => r.id))`
  3. Show ElMessage based on result
  4. Re-fetch the list to update the status column
  5. Clear selection

## EventDetailView.vue 改造

Add a 4-column summary card at the top of the event detail page.

### UI changes
- New `<el-card>` with `<el-row :gutter="20">` containing 4 `<MetricCard>`-style sub-cards
- Metrics shown: `currentParticipants/maxParticipants` (报名数), `checkInRate%`, `teams.confirmed`, `matches.completed`
- `onMounted` calls `getEventSummary(props.id)`, stores result in `summary` ref
- Watch on `props.id` refetches when switching events

## 兼容性

- Both new endpoints use existing axios `request` instance with bearer token
- No new dependencies needed
- Element Plus 2.5 components used (already in project)
