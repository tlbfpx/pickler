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
              effect="plain"
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
            <EventStatusBadge
              :status="row.status"
              @change="(t) => handleChangeStatus(row, t)"
            />
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
            <el-button
              type="success"
              size="small"
              @click="handleViewRegistrations(row)"
            >
              报名
            </el-button>
            <el-button
              type="info"
              size="small"
              @click="handleOpenPlacement(row)"
            >
              积分规则
            </el-button>
            <el-button
              v-if="row.status === 'COMPLETED'"
              type="warning"
              size="small"
              @click="handleEnterResults(row)"
            >
              录入成绩
            </el-button>
            <el-button
              type="primary"
              size="small"
              @click="handleEdit(row)"
            >
              编辑
            </el-button>
            <el-button
              type="danger"
              size="small"
              @click="handleDelete(row)"
            >
              删除
            </el-button>
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
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getEventList, deleteEvent, changeEventStatus } from '@/api/events'
import { formatDate, formatEventType, formatEventFormat, getEventTypeColor, getEventFormatColor } from '@/utils'
import { type EventStatus } from '@/constants/eventStatus'
import Pagination from '@/components/common/Pagination.vue'
import EventStatusBadge from '@/components/common/EventStatusBadge.vue'
import EventFilterBar from '@/components/common/EventFilterBar.vue'
import EventFormDialog from './EventFormDialog.vue'
import RegistrationDrawer from './RegistrationDrawer.vue'
import PointEntryDialog from '@/views/rankings/PointEntryDialog.vue'
import PlacementPointsDialog from './PlacementPointsDialog.vue'
import type { Event } from '@/types'

const loading = ref(false)
const filterKeyword = ref('')
const filterType = ref('')
const filterStatus = ref('')
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
      status: filterStatus.value
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

const onFilter = (payload: { keyword: string; type: string; status: string }) => {
  filterKeyword.value = payload.keyword
  filterType.value = payload.type
  filterStatus.value = payload.status
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
  try {
    await ElMessageBox.confirm(
      `确定要删除赛事 "${event.title}" 吗？`,
      '确认删除',
      { type: 'warning' }
    )
    const res = await deleteEvent(event.id)
    if (res.code === 0) {
      ElMessage.success('赛事删除成功')
      fetchEvents()
    } else {
      ElMessage.error(res.message || '删除赛事失败')
    }
  } catch (error: unknown) {
    if (error !== 'cancel') {
      // ignore
    }
  }
}

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
