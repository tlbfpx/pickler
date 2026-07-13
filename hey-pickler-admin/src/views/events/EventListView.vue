<template>
  <div>
    <el-card
      shadow="never"
      class="page-card"
      :body-style="{ padding: '20px 24px' }"
    >
      <div class="page-header">
        <h1>赛事管理</h1>
        <el-button
          type="primary"
          @click="handleCreate"
        >
          <el-icon><Plus /></el-icon>
          新建赛事
        </el-button>
      </div>
      <EventFilterBar
        :keyword="filterKeyword"
        :type="filterType"
        :status="filterStatus"
        :sort="filterSort"
        @filter="onFilter"
        @reset="onReset"
      />

      <el-table
        v-loading="loading"
        :data="eventList"
        style="width: 100%; margin-top: 16px"
      >
        <el-table-column
          prop="id"
          label="ID"
          width="80"
        />
        <el-table-column
          label="类型"
          width="120"
        >
          <template #default="{ row }">
            <el-tag
              :color="getEventTypeColor(row.type)"
              effect="dark"
            >
              {{ formatEventType(row.type) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          label="形式"
          width="90"
        >
          <template #default="{ row }">
            <el-tag
              v-if="row.format"
              :color="getEventFormatColor(row.format)"
              effect="dark"
              size="small"
            >
              {{ formatEventFormat(row.format) }}
            </el-tag>
            <span
              v-else
              style="color: #9ca3af"
            >-</span>
          </template>
        </el-table-column>
        <el-table-column
          prop="title"
          label="标题"
          width="200"
        >
          <template #default="{ row }">
            <router-link
              :to="`/events/${row.id}`"
              class="title-link"
            >
              {{ row.title }}
            </router-link>
          </template>
        </el-table-column>
        <el-table-column
          prop="location"
          label="地点"
          width="150"
        />
        <el-table-column
          label="比赛时间"
          width="180"
        >
          <template #default="{ row }">
            {{ formatDate(row.eventTime) }}
          </template>
        </el-table-column>
        <el-table-column
          prop="maxParticipants"
          label="上限"
          width="80"
        />
        <el-table-column
          label="已报名"
          width="140"
        >
          <template #default="{ row }">
            <div class="registration-progress">
              <span class="progress-text">
                {{ row.currentParticipants ?? 0 }} / {{ row.maxParticipants ?? '∞' }}
              </span>
              <el-progress
                :percentage="row.maxParticipants ? Math.round((row.currentParticipants ?? 0) / row.maxParticipants * 100) : 0"
                :stroke-width="6"
                :show-text="false"
                :color="(row.currentParticipants ?? 0) >= (row.maxParticipants ?? Infinity) ? '#F59E0B' : '#10B981'"
              />
            </div>
          </template>
        </el-table-column>
        <el-table-column
          label="状态"
          width="150"
        >
          <template #default="{ row }">
            <el-tooltip
              :content="statusTooltip(row.status)"
              placement="top"
            >
              <EventStatusBadge
                :status="row.status"
                @change="(t) => handleChangeStatus(row, t)"
              />
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column
          prop="fee"
          label="费用"
          width="100"
        >
          <template #default="{ row }">
            ¥{{ row.fee }}
          </template>
        </el-table-column>
        <el-table-column
          label="操作"
          width="350"
          fixed="right"
        >
          <template #default="{ row }">
            <el-tooltip
              :content="canViewRegistrations(row) ? '' : '仅 OPEN/FULL 阶段可查看报名'"
              :disabled="canViewRegistrations(row)"
              placement="top"
            >
              <el-button
                type="success"
                size="small"
                :disabled="!canViewRegistrations(row)"
                @click="handleViewRegistrations(row)"
              >
                报名
              </el-button>
            </el-tooltip>
            <el-tooltip
              :content="canEditPoints(row) ? '' : '已结束/已取消的赛事不可编辑积分规则'"
              :disabled="canEditPoints(row)"
              placement="top"
            >
              <el-button
                type="info"
                size="small"
                :disabled="!canEditPoints(row)"
                @click="handleOpenPlacement(row)"
              >
                积分规则
              </el-button>
            </el-tooltip>
            <el-button
              v-if="row.status === 'COMPLETED'"
              type="warning"
              size="small"
              @click="handleEnterResults(row)"
            >
              录入成绩
            </el-button>
            <el-tooltip
              :content="canEditEvent(row) ? '' : '已取消的赛事不可编辑'"
              :disabled="canEditEvent(row)"
              placement="top"
            >
              <el-button
                type="primary"
                size="small"
                :disabled="!canEditEvent(row)"
                @click="handleEdit(row)"
              >
                编辑
              </el-button>
            </el-tooltip>
            <el-tooltip
              :content="canDeleteEvent(row) ? '' : '已开启/进行中/已结束/已取消的赛事不可删除'"
              :disabled="canDeleteEvent(row)"
              placement="top"
            >
              <el-button
                type="danger"
                size="small"
                :disabled="!canDeleteEvent(row)"
                @click="handleDelete(row)"
              >
                删除
              </el-button>
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>

      <Pagination
        v-model:page="pagination.page"
        v-model:size="pagination.size"
        :total="pagination.total"
        @update:page="fetchEvents"
        @update:size="fetchEvents"
      />
    </el-card>

    <EventFormDialog
      v-model="formDialogVisible"
      :event="selectedEvent"
      @success="fetchEvents"
    />

    <RegistrationDrawer
      v-model="regDrawerVisible"
      :event="selectedEventForReg"
      @changed="fetchEvents"
    />

    <PointEntryDialog
      v-model="pointDialogVisible"
      :preset-event="selectedEventForPoints"
      @success="fetchEvents"
    />

    <PlacementPointsDialog
      v-model="placementDialogVisible"
      :event="selectedEventForPlacement"
      @saved="fetchEvents"
    />

    <UndoBar
      :visible="undoState.visible"
      :message="undoState.message"
      :seconds-left="undoState.secondsLeft"
      @undo="handleUndo"
      @dismiss="handleUndoDismiss"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { getEventList, deleteEvent, changeEventStatus } from '@/api/events'
import { formatDate, formatEventType, formatEventFormat, getEventTypeColor, getEventFormatColor } from '@/utils'
import { type EventStatus, statusTooltip } from '@/constants/eventStatus'
import {
  canEditEvent,
  canDeleteEvent,
  canEditPoints,
  canViewRegistrations
} from '@/constants/eventGuards'
import Pagination from '@/components/common/Pagination.vue'
import EventStatusBadge from '@/components/common/EventStatusBadge.vue'
import EventFilterBar from '@/components/common/EventFilterBar.vue'
import UndoBar from '@/components/common/UndoBar.vue'
import EventFormDialog from './EventFormDialog.vue'
import RegistrationDrawer from './RegistrationDrawer.vue'
import PointEntryDialog from '@/views/rankings/PointEntryDialog.vue'
import PlacementPointsDialog from './PlacementPointsDialog.vue'
import type { Event } from '@/types'

const loading = ref(false)
const filterKeyword = ref('')
const filterType = ref('')
const filterStatus = ref('')
// Loop-v18 — sort selector. Default 'event_time_desc'. Server supports
// 6 values per the loop-v16A OpenSpec; expose the 3 most useful here.
const filterSort = ref('event_time_desc')
const eventList = ref<Event[]>([])

const pagination = reactive({
  page: 1,
  size: 10,
  total: 0
})

const formDialogVisible = ref(false)
const selectedEvent = ref<Event | null>(null)

const regDrawerVisible = ref(false)
const selectedEventForReg = ref<Event | null>(null)

const pointDialogVisible = ref(false)
const selectedEventForPoints = ref<Event | null>(null)

const placementDialogVisible = ref(false)
const selectedEventForPlacement = ref<Event | null>(null)

const fetchEvents = async () => {
  loading.value = true
  try {
    const res = await getEventList({
      page: pagination.page,
      size: pagination.size,
      keyword: filterKeyword.value || undefined,
      type: filterType.value,
      status: filterStatus.value,
      sort: filterSort.value
    })
    if (res.code === 0) {
      eventList.value = res.data.list || []
      pagination.total = res.data.total || 0
    } else {
      ElMessage.error(res.message || '获取赛事列表失败')
    }
  } catch {
    
  } finally {
    loading.value = false
  }
}

const handleFilter = () => {
  pagination.page = 1
  fetchEvents()
}

const onFilter = (payload: { keyword: string; type: string; status: string; sort: string }) => {
  filterKeyword.value = payload.keyword
  filterType.value = payload.type
  filterStatus.value = payload.status
  filterSort.value = payload.sort
  handleFilter()
}

const onReset = () => {
  filterKeyword.value = ''
  filterType.value = ''
  filterStatus.value = ''
  handleFilter()
}

const handleCreate = () => {
  selectedEvent.value = null
  formDialogVisible.value = true
}

const handleEdit = (event: Event) => {
  selectedEvent.value = event
  formDialogVisible.value = true
}

const handleViewRegistrations = (event: Event) => {
  selectedEventForReg.value = event
  regDrawerVisible.value = true
}

const handleEnterResults = (event: Event) => {
  selectedEventForPoints.value = event
  pointDialogVisible.value = true
}

const handleOpenPlacement = (event: Event) => {
  selectedEventForPlacement.value = event
  placementDialogVisible.value = true
}

const handleDelete = async (event: Event) => {
  // 乐观删除：立刻从列表移除 + 弹出 5s 撤销条；倒计时结束后才真正调用后端 DELETE。
  startOptimisticDelete(event)
}

// ---- 乐观删除 + 撤销窗口 ----
const UNDO_DURATION_MS = 5000
const undoState = reactive({
  visible: false,
  message: '',
  secondsLeft: 0
})
// 待提交的删除队列：每项 = 赛事快照 + 真正调用 DELETE 的回调
const pendingDeletes = ref<Array<{ snapshot: Event; commit: () => Promise<void> }>>([])
let commitTimer: number | null = null
let countdownTimer: number | null = null
let startedAt = 0

function clearTimers() {
  if (commitTimer !== null) { window.clearTimeout(commitTimer); commitTimer = null }
  if (countdownTimer !== null) { window.clearInterval(countdownTimer); countdownTimer = null }
}

function insertEventBack(snapshot: Event) {
  if (eventList.value.some(e => e.id === snapshot.id)) return
  eventList.value.unshift(snapshot)
}

function refreshUndoBar() {
  if (pendingDeletes.value.length === 0) {
    undoState.visible = false
    undoState.message = ''
    undoState.secondsLeft = 0
    return
  }
  const n = pendingDeletes.value.length
  const first = pendingDeletes.value[0].snapshot
  undoState.message = n === 1
    ? `已删除 "${first.title}"`
    : `已删除 ${n} 项赛事（含 "${first.title}" 等）`
  const elapsed = Date.now() - startedAt
  undoState.secondsLeft = Math.max(0, Math.ceil((UNDO_DURATION_MS - elapsed) / 1000))
  undoState.visible = true
}

function scheduleCommit() {
  clearTimers()
  startedAt = Date.now()
  commitTimer = window.setTimeout(() => {
    const queue = pendingDeletes.value.slice()
    pendingDeletes.value = []
    refreshUndoBar()
    void (async () => {
      for (const item of queue) {
        await item.commit()
      }
    })()
  }, UNDO_DURATION_MS)
  countdownTimer = window.setInterval(() => {
    if (pendingDeletes.value.length === 0) {
      if (countdownTimer !== null) {
        window.clearInterval(countdownTimer)
        countdownTimer = null
      }
      return
    }
    const elapsed = Date.now() - startedAt
    undoState.secondsLeft = Math.max(0, Math.ceil((UNDO_DURATION_MS - elapsed) / 1000))
  }, 250)
}

function startOptimisticDelete(event: Event) {
  const idx = eventList.value.findIndex(e => e.id === event.id)
  if (idx === -1) return
  const snapshot = eventList.value[idx]
  eventList.value.splice(idx, 1)

  const commit = async () => {
    try {
      const res = await deleteEvent(snapshot.id)
      if (res.code === 0) {
        ElMessage.success(`"${snapshot.title}" 已删除`)
      } else {
        insertEventBack(snapshot)
        ElMessage.error(res.message || '删除失败')
      }
    } catch {
      insertEventBack(snapshot)
      ElMessage.error('删除失败')
    }
  }

  pendingDeletes.value.push({ snapshot, commit })
  refreshUndoBar()
  scheduleCommit()
}

function handleUndo() {
  if (pendingDeletes.value.length === 0) return
  for (const item of pendingDeletes.value) {
    insertEventBack(item.snapshot)
  }
  pendingDeletes.value = []
  clearTimers()
  refreshUndoBar()
  ElMessage.info('已撤销删除')
}

function handleUndoDismiss() {
  // 用户主动关闭撤销条：等同撤销（不立刻提交，让用户重做决定）
  handleUndo()
}

onBeforeUnmount(() => {
  clearTimers()
})

const handleChangeStatus = async (event: Event, targetStatus: EventStatus) => {
  try {
    const res = await changeEventStatus(event.id, targetStatus)
    if (res.code === 0) {
      ElMessage.success('状态变更成功')
      fetchEvents()
    } else {
      ElMessage.error(res.message || '状态变更失败')
    }
  } catch {

  }
}

onMounted(() => {
  fetchEvents()
})
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding-bottom: 16px;
  border-bottom: 1px solid #f3f4f6;
}

.page-header h1 {
  font-size: 22px;
  font-weight: 600;
  color: #111827;
}

.title-link {
  color: #6366f1;
  text-decoration: none;
}

.title-link:hover {
  text-decoration: underline;
}

.registration-progress {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.progress-text {
  font-size: 12px;
  color: #606266;
}
</style>
