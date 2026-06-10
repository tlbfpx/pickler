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
        <el-select
          v-model="filterStatus"
          placeholder="按状态筛选"
          clearable
          style="width: 150px"
          @change="handleFilter"
        >
          <el-option label="草稿" value="DRAFT" />
          <el-option label="即将开始" value="UPCOMING" />
          <el-option label="报名中" value="OPEN" />
          <el-option label="进行中" value="ONGOING" />
          <el-option label="已结束" value="COMPLETED" />
          <el-option label="已取消" value="CANCELLED" />
        </el-select>
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
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <span
              class="status-badge"
              :style="{ backgroundColor: getEventStatusColor(row.status) }"
            >
              {{ formatEventStatus(row.status) }}
            </span>
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
import { getEventList, deleteEvent } from '@/api/events'
import { formatDate, formatEventStatus, getEventStatusColor } from '@/utils'
import Pagination from '@/components/common/Pagination.vue'
import ActivityFormDialog from './ActivityFormDialog.vue'
import type { Event } from '@/types'

const loading = ref(false)
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
</style>
