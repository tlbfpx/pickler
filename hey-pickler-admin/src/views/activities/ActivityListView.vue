<template>
  <div>
    <div class="page-header">
      <h1>活动管理</h1>
      <el-button type="primary" @click="handleCreate">
        <el-icon><Plus /></el-icon>
        新建活动
      </el-button>
    </div>
    <div class="card">
      <div class="filter-bar">
        <el-input
          v-model="filterKeyword"
          placeholder="搜索活动标题"
          clearable
          style="width: 200px"
          :prefix-icon="Search"
          @keyup.enter="handleFilter"
          @clear="handleFilter"
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
        <el-select
          v-model="filterStatus"
          placeholder="按状态筛选"
          clearable
          style="width: 150px"
          @change="handleFilter"
        >
          <el-option label="草稿" value="DRAFT" />
          <el-option label="报名中" value="OPEN" />
          <el-option label="名额已满" value="FULL" />
          <el-option label="进行中" value="IN_PROGRESS" />
          <el-option label="已结束" value="COMPLETED" />
          <el-option label="已取消" value="CANCELLED" />
        </el-select>
        <el-button type="primary" @click="handleFilter">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="activityList" style="width: 100%; margin-top: 16px">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
        <el-table-column prop="location" label="地点" width="150" show-overflow-tooltip />
        <el-table-column label="活动时间" width="170">
          <template #default="{ row }">
            {{ formatDate(row.eventTime) }}
          </template>
        </el-table-column>
        <el-table-column label="报名截止" width="170">
          <template #default="{ row }">
            {{ formatDate(row.registrationDeadline) }}
          </template>
        </el-table-column>
        <el-table-column prop="maxParticipants" label="上限" width="70" />
        <el-table-column prop="currentParticipants" label="已报名" width="70" />
        <el-table-column label="状态" width="130">
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
                  <span class="status-dot" :style="{ backgroundColor: getEventStatusColor(target.value) }"></span>
                  {{ target.label }}
                </div>
                <div v-if="getAllowedStatusTransitions(row.status).length === 0" class="status-option disabled">
                  无可用转换
                </div>
              </div>
            </el-popover>
          </template>
        </el-table-column>
        <el-table-column label="费用" width="80">
          <template #default="{ row }">
            {{ row.fee ? '¥' + row.fee : '免费' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="handleEdit(row)">
              编辑
            </el-button>
            <el-button type="danger" size="small" @click="handleDelete(row)">
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
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { getEventList, deleteEvent, changeEventStatus } from '@/api/events'
import { formatDate, formatEventStatus, getEventStatusColor } from '@/utils'
import Pagination from '@/components/common/Pagination.vue'
import ActivityFormDialog from './ActivityFormDialog.vue'
import type { Event } from '@/types'

const loading = ref(false)
const filterKeyword = ref('')
const filterLocation = ref('')
const filterDateRange = ref<string[]>([])
const filterStatus = ref('')
const activityList = ref<Event[]>([])

const pagination = reactive({
  page: 1,
  size: 10,
  total: 0
})

const formDialogVisible = ref(false)
const selectedActivity = ref<Event | null>(null)

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
  } catch (error) {
    ElMessage.error('获取活动列表失败')
  } finally {
    loading.value = false
  }
}

const handleFilter = () => {
  pagination.page = 1
  fetchActivities()
}

const handleReset = () => {
  filterKeyword.value = ''
  filterLocation.value = ''
  filterDateRange.value = []
  filterStatus.value = ''
  pagination.page = 1
  fetchActivities()
}

const handleCreate = () => {
  selectedActivity.value = null
  formDialogVisible.value = true
}

const handleEdit = (activity: Event) => {
  selectedActivity.value = activity
  formDialogVisible.value = true
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
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error('删除活动失败')
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

const handleChangeStatus = async (activity: Event, targetStatus: string) => {
  try {
    const res = await changeEventStatus(activity.id, targetStatus)
    if (res.code === 0) {
      ElMessage.success('状态变更成功')
      activity._statusPopoverVisible = false
      fetchActivities()
    } else {
      ElMessage.error(res.message || '状态变更失败')
    }
  } catch {
    ElMessage.error('状态变更失败')
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
