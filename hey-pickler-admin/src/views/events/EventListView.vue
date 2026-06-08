<template>
  <div>
    <div class="page-header">
      <h1>Event Management</h1>
      <el-button type="primary" @click="handleCreate">
        <el-icon><Plus /></el-icon>
        Create Event
      </el-button>
    </div>
    <div class="card">
      <div class="filter-bar">
        <el-select
          v-model="filterType"
          placeholder="Filter by type"
          clearable
          style="width: 150px"
          @change="handleFilter"
        >
          <el-option label="Star Event" value="STAR" />
          <el-option label="Party Event" value="PARTY" />
        </el-select>
        <el-select
          v-model="filterStatus"
          placeholder="Filter by status"
          clearable
          style="width: 150px"
          @change="handleFilter"
        >
          <el-option label="Upcoming" value="UPCOMING" />
          <el-option label="Ongoing" value="ONGOING" />
          <el-option label="Completed" value="COMPLETED" />
          <el-option label="Cancelled" value="CANCELLED" />
        </el-select>
      </div>

      <el-table v-loading="loading" :data="eventList" style="width: 100%; margin-top: 16px">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column label="Type" width="120">
          <template #default="{ row }">
            <el-tag :color="getEventTypeColor(row.type)" effect="dark">
              {{ formatEventType(row.type) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="Title" width="200" />
        <el-table-column prop="location" label="Location" width="150" />
        <el-table-column label="Event Date" width="180">
          <template #default="{ row }">
            {{ formatDate(row.eventDate) }}
          </template>
        </el-table-column>
        <el-table-column prop="maxParticipants" label="Max" width="80" />
        <el-table-column prop="currentParticipants" label="Current" width="80" />
        <el-table-column label="Status" width="120">
          <template #default="{ row }">
            <span
              class="status-badge"
              :style="{ backgroundColor: getEventStatusColor(row.status) }"
            >
              {{ formatEventStatus(row.status) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="fee" label="Fee" width="100">
          <template #default="{ row }">
            ¥{{ row.fee }}
          </template>
        </el-table-column>
        <el-table-column label="Actions" width="150" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="handleEdit(row)">
              Edit
            </el-button>
            <el-button type="danger" size="small" @click="handleDelete(row)">
              Delete
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
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getEventList, deleteEvent } from '@/api/events'
import { formatDate, formatEventType, formatEventStatus, getEventTypeColor, getEventStatusColor } from '@/utils'
import Pagination from '@/components/common/Pagination.vue'
import EventFormDialog from './EventFormDialog.vue'
import type { Event } from '@/types'

const loading = ref(false)
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

const fetchEvents = async () => {
  loading.value = true
  try {
    const res = await getEventList({
      page: pagination.page,
      size: pagination.size,
      type: filterType.value,
      status: filterStatus.value
    })
    if (res.code === 0) {
      eventList.value = res.data.events
      pagination.total = res.data.total
    } else {
      ElMessage.error(res.message || 'Failed to fetch events')
    }
  } catch (error) {
    ElMessage.error('Failed to fetch events')
  } finally {
    loading.value = false
  }
}

const handleFilter = () => {
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

const handleDelete = async (event: Event) => {
  try {
    await ElMessageBox.confirm(
      `Are you sure you want to delete event "${event.title}"?`,
      'Confirm Delete',
      { type: 'warning' }
    )
    const res = await deleteEvent(event.id)
    if (res.code === 0) {
      ElMessage.success('Event deleted successfully')
      fetchEvents()
    } else {
      ElMessage.error(res.message || 'Failed to delete event')
    }
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error('Failed to delete event')
    }
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
</style>
