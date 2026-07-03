<template>
  <div>
    <div class="page-header">
      <h1>活动管理</h1>
      <el-button
        type="primary"
        @click="handleCreate"
      >
        <el-icon><Plus /></el-icon>
        新建活动
      </el-button>
    </div>
    <div class="card">
      <div class="filter-bar">
        <EventFilterBar
          :keyword="filterKeyword"
          :type="filterType"
          :status="filterStatus"
          @filter="onFilter"
          @reset="onReset"
        />
        <el-input
          v-model="filterLocation"
          placeholder="搜索地点"
          clearable
          style="width: 160px"
          @keyup.enter="handleFilter"
          @clear="handleFilter"
        />
        <el-date-picker
          v-model="filterDateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          value-format="YYYY-MM-DD"
          style="width: 260px"
          @change="handleFilter"
        />
      </div>

      <el-table
        v-loading="loading"
        :data="activityList"
        style="width: 100%; margin-top: 16px"
      >
        <el-table-column
          prop="id"
          label="ID"
          width="80"
        />
        <el-table-column
          prop="title"
          label="标题"
          min-width="200"
          show-overflow-tooltip
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
          show-overflow-tooltip
        />
        <el-table-column
          label="活动时间"
          width="170"
        >
          <template #default="{ row }">
            {{ formatDate(row.eventTime) }}
          </template>
        </el-table-column>
        <el-table-column
          label="报名截止"
          width="170"
        >
          <template #default="{ row }">
            {{ formatDate(row.registrationDeadline) }}
          </template>
        </el-table-column>
        <el-table-column
          prop="maxParticipants"
          label="上限"
          width="70"
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
          width="130"
        >
          <template #default="{ row }">
            <EventStatusBadge
              :status="row.status"
              @change="(t) => handleChangeStatus(row, t)"
            />
          </template>
        </el-table-column>
        <el-table-column
          label="费用"
          width="80"
        >
          <template #default="{ row }">
            {{ row.fee ? '¥' + row.fee : '免费' }}
          </template>
        </el-table-column>
        <el-table-column
          label="操作"
          width="210"
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
        @update:page="fetchActivities"
        @update:size="fetchActivities"
      />
    </div>

    <ActivityFormDialog
      v-model="formDialogVisible"
      :event="selectedActivity"
      @success="fetchActivities"
    />

    <RegistrationDrawer
      v-model="regDrawerVisible"
      :event="selectedActivityForReg"
      @changed="fetchActivities"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getEventList, deleteEvent, changeEventStatus } from '@/api/events'
import { formatDate } from '@/utils'
import { type EventStatus } from '@/constants/eventStatus'
import Pagination from '@/components/common/Pagination.vue'
import EventStatusBadge from '@/components/common/EventStatusBadge.vue'
import EventFilterBar from '@/components/common/EventFilterBar.vue'
import ActivityFormDialog from './ActivityFormDialog.vue'
import RegistrationDrawer from '../events/RegistrationDrawer.vue'
import type { Event } from '@/types'

const loading = ref(false)
const filterKeyword = ref('')
const filterType = ref('PARTY')
const filterStatus = ref('')
const filterLocation = ref('')
const filterDateRange = ref<string[]>([])
const activityList = ref<Event[]>([])

const pagination = reactive({
  page: 1,
  size: 10,
  total: 0
})

const formDialogVisible = ref(false)
const selectedActivity = ref<Event | null>(null)

const regDrawerVisible = ref(false)
const selectedActivityForReg = ref<Event | null>(null)

const fetchActivities = async () => {
  loading.value = true
  try {
    const res = await getEventList({
      page: pagination.page,
      size: pagination.size,
      type: 'PARTY',
      keyword: filterKeyword.value || undefined,
      location: filterLocation.value || undefined,
      startTime: filterDateRange.value?.[0] || undefined,
      endTime: filterDateRange.value?.[1] || undefined,
      status: filterStatus.value
    })
    if (res.code === 0) {
      activityList.value = res.data.list || []
      pagination.total = res.data.total || 0
    } else {
      ElMessage.error(res.message || '获取活动列表失败')
    }
  } catch {
    
  } finally {
    loading.value = false
  }
}

const handleFilter = () => {
  pagination.page = 1
  fetchActivities()
}

const onFilter = (payload: { keyword: string; type: string; status: string }) => {
  filterKeyword.value = payload.keyword
  filterType.value = payload.type || 'PARTY'
  filterStatus.value = payload.status
  handleFilter()
}

const onReset = () => {
  filterKeyword.value = ''
  filterType.value = 'PARTY'
  filterStatus.value = ''
  filterLocation.value = ''
  filterDateRange.value = []
  pagination.page = 1
  handleFilter()
}

const handleCreate = () => {
  selectedActivity.value = null
  formDialogVisible.value = true
}

const handleEdit = (activity: Event) => {
  selectedActivity.value = activity
  formDialogVisible.value = true
}

const handleViewRegistrations = (activity: Event) => {
  selectedActivityForReg.value = activity
  regDrawerVisible.value = true
}

const handleDelete = async (activity: Event) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除活动 "${activity.title}" 吗？`,
      '确认删除',
      { type: 'warning' }
    )
    const res = await deleteEvent(activity.id)
    if (res.code === 0) {
      ElMessage.success('活动删除成功')
      fetchActivities()
    } else {
      ElMessage.error(res.message || '删除活动失败')
    }
  } catch (error: unknown) {
    if (error !== 'cancel') {
      // ignore
    }
  }
}

const handleChangeStatus = async (activity: Event, targetStatus: EventStatus) => {
  try {
    const res = await changeEventStatus(activity.id, targetStatus)
    if (res.code === 0) {
      ElMessage.success('状态变更成功')
      fetchActivities()
    } else {
      ElMessage.error(res.message || '状态变更失败')
    }
  } catch {

  }
}

onMounted(() => {
  fetchActivities()
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
  align-items: center;
  flex-wrap: wrap;
}

.title-link {
  color: #409eff;
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
