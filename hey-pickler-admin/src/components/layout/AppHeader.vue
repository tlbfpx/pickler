<template>
  <div class="header">
    <div class="header-left">
      <img
        v-if="brandStore.logoUrl"
        :src="brandStore.logoUrl"
        class="header-logo"
        alt="logo"
      >
      <h2>{{ brandStore.appName }} 管理后台</h2>
    </div>
    <div class="header-right">
      <!-- Notification bell with unread badge + dropdown of latest items. -->
      <el-popover
        placement="bottom-end"
        :width="360"
        trigger="click"
        :hide-after="0"
        @show="onPopoverShow"
      >
        <template #reference>
          <el-badge
            :value="unreadCount"
            :hidden="unreadCount === 0"
            :max="99"
          >
            <el-icon class="bell-icon">
              <Bell />
            </el-icon>
          </el-badge>
        </template>
        <div class="notif-popover">
          <div class="notif-header">
            <span>站内通知</span>
            <el-button
              link
              type="primary"
              size="small"
              :disabled="unreadCount === 0 || markingAll"
              @click="handleMarkAllRead"
            >
              全部已读
            </el-button>
          </div>
          <div class="notif-body">
            <el-empty
              v-if="!notifications.length"
              description="暂无通知"
              :image-size="60"
            />
            <ul
              v-else
              class="notif-list"
            >
              <li
                v-for="n in notifications"
                :key="n.id"
                :class="['notif-item', n.readFlag === 0 ? 'is-unread' : '']"
                @click="handleItemClick(n)"
              >
                <div class="notif-title">
                  {{ n.title }}
                </div>
                <div
                  v-if="n.content"
                  class="notif-content"
                >
                  {{ n.content }}
                </div>
                <div class="notif-time">
                  {{ formatDate(n.createdAt) }}
                </div>
              </li>
            </ul>
          </div>
          <div class="notif-footer">
            <router-link
              to="/notifications"
              class="more-link"
            >
              查看更多
            </router-link>
          </div>
        </div>
      </el-popover>

      <el-dropdown @command="handleCommand">
        <span class="user-info">
          <el-icon class="user-icon">
            <User />
          </el-icon>
          <span class="username">{{ authStore.admin?.username }}</span>
          <el-icon class="dropdown-icon">
            <ArrowDown />
          </el-icon>
        </span>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="logout">
              <el-icon>
                <SwitchButton />
              </el-icon>
              退出登录
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Bell } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { useBrandStore } from '@/stores/brand'
import {
  getNotifications,
  getUnreadCount,
  markNotificationRead,
  markAllNotificationsRead,
  type NotificationItem
} from '@/api/notifications'
import { formatDate } from '@/utils'

const router = useRouter()
const authStore = useAuthStore()
const brandStore = useBrandStore()

const unreadCount = ref(0)
const notifications = ref<NotificationItem[]>([])
const markingAll = ref(false)
let pollTimer: ReturnType<typeof setInterval> | null = null

const fetchUnreadCount = async () => {
  try {
    const res = await getUnreadCount()
    if (res.code === 0 && typeof res.data?.count === 'number') {
      unreadCount.value = res.data.count
    }
  } catch {
    // ignore — interceptor already toasts a meaningful error
  }
}

const fetchLatest = async () => {
  try {
    const res = await getNotifications({ page: 1, size: 10 })
    if (res.code === 0) {
      notifications.value = res.data.list || []
      // The dropdown shows latest 10; recompute unread locally to avoid a second hop.
      unreadCount.value = notifications.value.filter(n => n.readFlag === 0).length
    }
  } catch {
    // ignore
  }
}

const onPopoverShow = async () => {
  await fetchLatest()
}

const handleItemClick = async (n: NotificationItem) => {
  if (n.readFlag === 0) {
    try {
      const res = await markNotificationRead(n.id)
      if (res.code === 0) {
        n.readFlag = 1
        unreadCount.value = Math.max(0, unreadCount.value - 1)
      }
    } catch {
      // ignore
    }
  }
  if (n.linkUrl) {
    if (/^https?:\/\//i.test(n.linkUrl)) {
      window.open(n.linkUrl, '_blank', 'noopener,noreferrer')
    } else {
      router.push(n.linkUrl)
    }
  }
}

const handleMarkAllRead = async () => {
  markingAll.value = true
  try {
    const res = await markAllNotificationsRead()
    if (res.code === 0) {
      ElMessage.success('已全部标记已读')
      await fetchLatest()
      unreadCount.value = 0
    } else {
      ElMessage.error(res.message || '操作失败')
    }
  } catch {
    // ignore
  } finally {
    markingAll.value = false
  }
}

const handleCommand = async (command: string) => {
  if (command === 'logout') {
    try {
      await ElMessageBox.confirm('确定要退出登录吗？', '确认', {
        type: 'warning'
      })
      authStore.logout()
      router.push('/login')
    } catch {
      // User cancelled
    }
  }
}

onMounted(async () => {
  // Initial fetch + 30s polling. Keeping it cheap; the endpoint is a SELECT COUNT.
  await fetchUnreadCount()
  pollTimer = setInterval(fetchUnreadCount, 30000)
})

onBeforeUnmount(() => {
  if (pollTimer) clearInterval(pollTimer)
})
</script>

<style scoped>
.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.header-logo {
  width: 28px;
  height: 28px;
  border-radius: 6px;
  object-fit: cover;
}

.header-left h2 {
  font-size: 20px;
  font-weight: 600;
  color: #1f2937;
  margin: 0;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.bell-icon {
  font-size: 20px;
  color: #4b5563;
  cursor: pointer;
  padding: 6px;
  border-radius: 6px;
  transition: background-color 0.2s;
}
.bell-icon:hover {
  background-color: #f3f4f6;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 8px 12px;
  border-radius: 6px;
  transition: background-color 0.2s;
}

.user-info:hover {
  background-color: #f5f5f5;
}

.user-icon {
  font-size: 20px;
  color: #6b7280;
}

.username {
  font-size: 14px;
  font-weight: 500;
  color: #1f2937;
}

.dropdown-icon {
  font-size: 12px;
  color: #6b7280;
}

/* Notification popover — bypass scoped styles for inner classes. */
.notif-popover {
  display: flex;
  flex-direction: column;
  max-height: 480px;
}
.notif-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 4px 12px;
  border-bottom: 1px solid #f3f4f6;
  font-size: 14px;
  font-weight: 600;
  color: #1f2937;
}
.notif-body {
  flex: 1;
  overflow-y: auto;
  padding: 4px 0;
  max-height: 360px;
}
.notif-list {
  list-style: none;
  padding: 0;
  margin: 0;
}
.notif-item {
  padding: 10px 8px;
  border-bottom: 1px solid #f3f4f6;
  cursor: pointer;
  transition: background-color 0.15s;
}
.notif-item:hover {
  background-color: #f9fafb;
}
.notif-item.is-unread {
  background-color: #fefce8;
}
.notif-title {
  font-size: 13px;
  font-weight: 600;
  color: #1f2937;
  margin-bottom: 2px;
}
.notif-content {
  font-size: 12px;
  color: #4b5563;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  margin-bottom: 4px;
}
.notif-time {
  font-size: 11px;
  color: #9ca3af;
}
.notif-footer {
  padding-top: 8px;
  border-top: 1px solid #f3f4f6;
  text-align: center;
}
.more-link {
  color: #409eff;
  font-size: 13px;
  text-decoration: none;
}
.more-link:hover {
  text-decoration: underline;
}
</style>
