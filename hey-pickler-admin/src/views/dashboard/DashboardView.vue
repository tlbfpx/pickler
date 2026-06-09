<template>
  <div>
    <div class="page-header">
      <h1>控制台</h1>
    </div>
    <div v-loading="loading" class="stats-grid">
      <div class="stat-card">
        <div class="stat-value">{{ stats.totalUsers }}</div>
        <div class="stat-label">总用户数</div>
      </div>
      <div class="stat-card">
        <div class="stat-value">{{ stats.activeEvents }}</div>
        <div class="stat-label">活跃赛事</div>
      </div>
      <div class="stat-card">
        <div class="stat-value">{{ stats.recentRegistrations }}</div>
        <div class="stat-label">近期报名</div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getDashboardStats } from '@/api/dashboard'

const loading = ref(true)

const stats = reactive({
  totalUsers: 0,
  activeEvents: 0,
  recentRegistrations: 0
})

const fetchStats = async () => {
  loading.value = true
  try {
    const res = await getDashboardStats()
    if (res.code === 0) {
      stats.totalUsers = res.data.totalUsers
      stats.activeEvents = res.data.activeEvents
      stats.recentRegistrations = res.data.recentRegistrations
    } else {
      ElMessage.error(res.message || '获取统计数据失败')
    }
  } catch (error) {
    // If dashboard API doesn't exist, use zero values
    console.warn('Dashboard stats API not available, using zero values')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchStats()
})
</script>

<style scoped>
/* Styles are in src/styles/index.scss */
</style>
