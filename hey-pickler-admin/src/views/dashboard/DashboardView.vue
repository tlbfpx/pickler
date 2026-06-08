<template>
  <div>
    <div class="page-header">
      <h1>Dashboard</h1>
    </div>
    <div v-loading="loading" class="stats-grid">
      <div class="stat-card">
        <div class="stat-value">{{ stats.totalUsers }}</div>
        <div class="stat-label">Total Users</div>
      </div>
      <div class="stat-card">
        <div class="stat-value">{{ stats.activeEvents }}</div>
        <div class="stat-label">Active Events</div>
      </div>
      <div class="stat-card">
        <div class="stat-value">{{ stats.recentRegistrations }}</div>
        <div class="stat-label">Recent Registrations</div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'

const loading = ref(true)

const stats = reactive({
  totalUsers: 0,
  activeEvents: 0,
  recentRegistrations: 0
})

// Mock data for now - should be fetched from dashboard stats API
onMounted(() => {
  setTimeout(() => {
    stats.totalUsers = 1234
    stats.activeEvents = 8
    stats.recentRegistrations = 56
    loading.value = false
  }, 1000)
})
</script>

<style scoped>
/* Styles are in src/styles/index.scss */
</style>
