# Design: admin UI 集成

## API 客户端（`src/api/events.ts`）

```ts
// Loop-v13 — 赛事运营汇总
export const getEventSummary = (eventId: number) =>
  request.get<EventSummaryVO, ApiResponse<EventSummaryVO>>(`/events/${eventId}/summary`)

// Loop-v14 — 批量签到
export const bulkCheckIn = (eventId: number, registrationIds: number[]) =>
  request.post<BulkCheckInRequest, ApiResponse<BulkCheckInResult>>(
    `/events/${eventId}/registrations/bulk-check-in`,
    { registrationIds }
  )
```

## 类型

```ts
// types/event-summary.ts
export interface RegistrationCountVO {
  registered: number
  checkedIn: number
  withdrawn: number
  checkInRate: number
}
export interface TeamCountVO { pending: number; confirmed: number }
export interface MatchCountVO { scheduled: number; inProgress: number; completed: number }
export interface FeeSummaryVO { totalCollected: number; currency: string }
export interface EventSummaryVO {
  eventId: number
  title: string
  type: string
  status: string
  eventTime: string
  maxParticipants: number | null
  currentParticipants: number
  fillRate: number
  registration: RegistrationCountVO
  teams: TeamCountVO
  matches: MatchCountVO
  fees: FeeSummaryVO
  transitionableStatuses: string[]
}

// types/bulk-check-in.ts
export interface BulkCheckInResult {
  eventId: number
  requested: number
  updated: number
  updatedRegistrationIds: number[]
  skipped: { notFound: number[]; withdrawn: number[] }
}
```

## UI 改造

### `RegistrationDrawer.vue`

在 actions 栏（drawer footer）加：
- **批量签到**：调用 `bulkCheckIn(eventId, selectedIds)`
  - 成功：ElMessage.success(`已签到 ${result.updated} 人`)
  - 部分失败：ElMessage.warning(`已签到 ${updated}, 跳过 ${notFound.length} (未找到), ${withdrawn.length} (已撤回)`)
  - 全部失败：ElMessage.error
- **多选 checkbox**：registration list 每行前加 checkbox，drawer header 加 "全选"

### `EventDetailView.vue`

在 page header 加 `<el-card>` 块（3-4 metric 指标）：

```vue
<el-row>
  <el-col :span="6"><metric :value="summary.currentParticipants" :total="summary.maxParticipants" label="已报名"/></el-col>
  <el-col :span="6"><metric :value="(summary.registration.checkInRate * 100).toFixed(0) + '%'" label="签到率"/></el-col>
  <el-col :span="6"><metric :value="summary.teams.confirmed" label="已确认队伍"/></el-col>
  <el-col :span="6"><metric :value="summary.matches.completed" label="已完赛"/></el-col>
</el-row>
```

- `onMounted` 时调 `getEventSummary`，存 `summary` ref
- watch `event.id` 重新拉

## 测试

`hey-pickler-admin` 没单测基础设施（package.json 里有 `test:e2e:playwright` 但要 backend + browser）。Loop-v15 不加单测，靠 e2e 验证（如果 backend 服务运行）。

## 提交计划
1. types/event-summary.ts + types/bulk-check-in.ts
2. api/events.ts (2 new functions + 1 import update)
3. RegistrationDrawer.vue (加按钮 + 批量处理逻辑)
4. EventDetailView.vue (summary 卡片)
5. verify: `npm run build` 通过 + lint 干净
6. 1 commit per file
