<template>
  <div class="header">
    <div class="header-left">
      <h2>Hey Pickler Admin Panel</h2>
    </div>
    <div class="header-right">
      <el-dropdown @command="handleCommand">
        <span class="user-info">
          <el-icon class="user-icon"><User /></el-icon>
          <span class="username">{{ authStore.admin?.username }}</span>
          <el-icon class="dropdown-icon"><ArrowDown /></el-icon>
        </span>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="logout">
              <el-icon><SwitchButton /></el-icon>
              Logout
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { ElMessageBox } from 'element-plus'

const router = useRouter()
const authStore = useAuthStore()

const handleCommand = async (command: string) => {
  if (command === 'logout') {
    try {
      await ElMessageBox.confirm('Are you sure you want to logout?', 'Confirm', {
        type: 'warning'
      })
      authStore.logout()
      router.push('/login')
    } catch {
      // User cancelled
    }
  }
}
</script>

<style scoped>
.header-left {
  display: flex;
  align-items: center;
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
</style>
