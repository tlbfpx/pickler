<template>
  <div>
    <div class="page-header">
      <h1>通知中心</h1>
      <div class="header-actions">
        <el-tag
          v-if="unreadCount > 0"
          type="danger"
          size="small"
        >
          未读 {{ unreadCount }} 条
        </el-tag>
        <el-button
          type="primary"
          size="small"
          :disabled="unreadCount === 0 || bulkLoading"
          :loading="bulkLoading"
          @click="handleMarkAllRead"
        >
          全部标记已读
        </el-button>
      </div>
    </div>

    <div class="card">
      <el-table
        v-loading="loading"
        :data="notificationList"
        style="width: 100%"
        empty-text="暂无通知"
        :row-class-name="(args: { row: NotificationItem }) => args.row.readFlag === 0 ? 'row-unread' : ''"
      >
        <el-table-column
          label="状态"
          width="80"
          align="center"
        >
          <template #default="{ row }">
            <el-tag
              v-if="row.readFlag === 0"
              type="danger"
              size="small"
            >
              未读
            </el-tag>
            <el-tag
              v-else
              type="info"
              size="small"
            >
              已读
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          label="时间"
          width="180"
        >
          <template #default="{ row }">
            {{ formatDate(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column
          label="类型"
          width="140"
        >
          <template #default="{ row }">
            <el-tag
              size="small"
              :type="typeTagType(row.type)"
            >
              {{ formatType(row.type) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          label="标题"
          prop="title"
          min-width="180"
          show-overflow-tooltip
        />
        <el-table-column
          label="内容"
          min-width="280"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            <span v-if="row.content">{{ row.content }}</span>
            <span
              v-else
              class="muted"
            >-</span>
          </template>
        </el-table-column>
        <el-table-column
          label="操作"
          width="200"
          fixed="right"
        >
          <template #default="{ row }">
            <el-button
              v-if="row.linkUrl"
              link
              type="primary"
              size="small"
              @click="openLink(row)"
            >
              详情
            </el-button>
            <el-button
              v-if="row.readFlag === 0"
              link
              type="success"
              size="small"
              :loading="rowReadLoading[row.id]"
              @click="handleMarkRead(row)"
            >
              标记已读
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <Pagination
        v-model:page="pagination.page"
        v-model:size="pagination.size"
        :total="pagination.total"
        @update:page="fetchNotifications"
        @update:size="fetchNotifications"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import Pagination from '@/components/common/Pagination.vue'
import {
  getNotifications,
  markNotificationRead,
  markAllNotificationsRead,
  type NotificationItem
} from '@/api/notifications'
import { formatDate } from '@/utils'

const loading = ref(false)
const notificationList = ref<NotificationItem[]>([])
const pagination = reactive({ page: 1, size: 20, total: 0 })
const unreadCount = ref(0)
const bulkLoading = ref(false)
const rowReadLoading = reactive<Record<number, boolean>>({})

const fetchNotifications = async () => {
  loading.value = true
  try {
    const res = await getNotifications({ page: pagination.page, size: pagination.size })
    if (res.code === 0) {
      notificationList.value = res.data.list || []
      pagination.total = res.data.total || 0
    } else {
      ElMessage.error(res.message || '获取通知失败')
    }
  } catch {
    // interceptor surfaces error
  } finally {
    loading.value = false
  }
}

const refreshUnreadCount = async () => {
  // Local recompute from current page view (cheap, no extra endpoint hit).
  unreadCount.value = notificationList.value.filter(n => n.readFlag === 0).length
}

const handleMarkRead = async (row: NotificationItem) => {
  rowReadLoading[row.id] = true
  try {
    const res = await markNotificationRead(row.id)
    if (res.code === 0) {
      row.readFlag = 1
      await refreshUnreadCount()
    } else {
      ElMessage.error(res.message || '标记失败')
    }
  } catch {
    // swallowed
  } finally {
    rowReadLoading[row.id] = false
  }
}

const handleMarkAllRead = async () => {
  bulkLoading.value = true
  try {
    const res = await markAllNotificationsRead()
    if (res.code === 0) {
      ElMessage.success('全部通知已标记为已读')
      await fetchNotifications()
      unreadCount.value = 0
    } else {
      ElMessage.error(res.message || '操作失败')
    }
  } catch {
    // swallowed
  } finally {
    bulkLoading.value = false
  }
}

const openLink = (row: NotificationItem) => {
  if (!row.linkUrl) return
  // Treat the link as an in-app route. Open in a new tab when it looks like
  // an absolute URL (e.g. external banner link), otherwise route inside the SPA.
  if (/^https?:\/\//i.test(row.linkUrl)) {
    window.open(row.linkUrl, '_blank', 'noopener,noreferrer')
  } else {
    // Use Vue Router via dynamic import to avoid a hard dependency at module top.
    import('@/router').then(({ default: router }) => {
      router.push(row.linkUrl as string)
    })
  }
}

const TYPE_LABELS: Record<string, string> = {
  EVENT_IN_PROGRESS: '赛事开赛',
  EVENT_COMPLETED: '赛事结束',
  TEAM_INVITED: '组队邀请',
  BANNER_PUBLISHED: 'Banner 发布',
  SYSTEM: '系统通知'
}

const formatType = (type: string) => TYPE_LABELS[type] || type

const typeTagType = (type: string) => {
  switch (type) {
    case 'EVENT_IN_PROGRESS': return 'warning'
    case 'EVENT_COMPLETED': return 'success'
    case 'TEAM_INVITED': return 'primary'
    case 'BANNER_PUBLISHED': return 'info'
    default: return 'info'
  }
}

onMounted(async () => {
  await fetchNotifications()
  await refreshUnreadCount()
})
</script>

<style scoped>
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
.page-header h1 {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: #1f2937;
}
.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}
.muted { color: #9ca3af; }

/* Subtle highlight for unread rows. */
:deep(.row-unread) td {
  background-color: #fefce8 !important;
  font-weight: 500;
}
</style>
