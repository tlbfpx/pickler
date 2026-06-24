<template>
  <div>
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
    <div class="card">
      <div class="filter-bar">
        <el-input
          v-model="filterKeyword"
          placeholder="搜索赛事标题"
          clearable
          style="width: 220px"
          :prefix-icon="Search"
          @keyup.enter="handleFilter"
          @clear="handleFilter"
        />
        <el-select
          v-model="filterType"
          placeholder="按类型筛选"
          clearable
          style="width: 150px"
          @change="handleFilter"
        >
          <el-option
            :label="TERMS.STAR.type"
            value="STAR"
          />
          <el-option
            :label="TERMS.PARTY.type"
            value="PARTY"
          />
        </el-select>
        <el-select
          v-model="filterStatus"
          placeholder="按状态筛选"
          clearable
          style="width: 150px"
          @change="handleFilter"
        >
          <el-option
            label="草稿"
            value="DRAFT"
          />
          <el-option
            label="报名中"
            value="OPEN"
          />
          <el-option
            label="名额已满"
            value="FULL"
          />
          <el-option
            label="进行中"
            value="IN_PROGRESS"
          />
          <el-option
            label="已结束"
            value="COMPLETED"
          />
          <el-option
            label="已取消"
            value="CANCELLED"
          />
        </el-select>
        <el-button
          type="primary"
          @click="handleFilter"
        >
          查询
        </el-button>
        <el-button @click="handleReset">
          重置
        </el-button>
      </div>

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
          prop="title"
          label="标题"
          width="200"
        />
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
          prop="currentParticipants"
          label="已报名"
          width="80"
        />
        <el-table-column
          label="状态"
          width="150"
        >
          <template #default="{ row }">
            <el-popover
              placement="bottom"
              :width="160"
              trigger="click"
              :visible="row._statusPopoverVisible"
              @update:visible="(val: boolean) => row._statusPopoverVisible = val"
            >
              <template #reference>
                <span
                  class="status-badge clickable"
                  :style="{ backgroundColor: getEventStatusColor(row.status) }"
                >
                  {{ formatEventStatus(row.status) }} ▾
                </span>
              </template>
              <div class="status-options">
                <div
                  v-for="target in getAllowedStatusTransitions(row.status)"
                  :key="target.value"
                  class="status-option"
                  @click="handleChangeStatus(row, target.value)"
                >
                  <span
                    class="status-dot"
                    :style="{ backgroundColor: getEventStatusColor(target.value) }"
                  />
                  {{ target.label }}
                </div>
                <div
                  v-if="getAllowedStatusTransitions(row.status).length === 0"
                  class="status-option disabled"
                >
                  无可用转换
                </div>
              </div>
            </el-popover>
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
          width="290"
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
    </div>

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
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { getEventList, deleteEvent, changeEventStatus } from '@/api/events'
import { formatDate, formatEventType, formatEventStatus, getEventTypeColor, getEventStatusColor } from '@/utils'
import { TERMS } from '@/constants/terms'
import Pagination from '@/components/common/Pagination.vue'
import EventFormDialog from './EventFormDialog.vue'
import RegistrationDrawer from './RegistrationDrawer.vue'
import PointEntryDialog from '@/views/rankings/PointEntryDialog.vue'
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

const handleReset = () => {
  filterKeyword.value = ''
  filterType.value = ''
  filterStatus.value = ''
  pagination.page = 1
  fetchEvents()
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
  } catch (error: any) {
    if (error !== 'cancel') {
      // ignore
    }
  }
}

const STATUS_TRANSITIONS: Record<string, { value: string; label: string }[]> = {
  DRAFT: [{ value: 'OPEN', label: '报名中' }],
  OPEN: [{ value: 'CANCELLED', label: '已取消' }],
  FULL: [{ value: 'CANCELLED', label: '已取消' }],
  IN_PROGRESS: [
    { value: 'COMPLETED', label: '已结束' },
    { value: 'CANCELLED', label: '已取消' }
  ],
  COMPLETED: [],
  CANCELLED: []
}

const getAllowedStatusTransitions = (status: string) => {
  return STATUS_TRANSITIONS[status] || []
}

const handleChangeStatus = async (event: Event, targetStatus: string) => {
  try {
    const res = await changeEventStatus(event.id, targetStatus)
    if (res.code === 0) {
      ElMessage.success('状态变更成功')
      event._statusPopoverVisible = false
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
}

.filter-bar {
  display: flex;
  gap: 12px;
}

.status-badge.clickable {
  cursor: pointer;
}

.status-options {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.status-option {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 13px;
}

.status-option:hover {
  background-color: #f5f7fa;
}

.status-option.disabled {
  color: #c0c4cc;
  cursor: default;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}
</style>
