<template>
  <div>
    <div class="page-header">
      <h1>Ranking Management</h1>
      <div class="header-actions">
        <el-button type="primary" @click="handleEnterPoints">
          <el-icon><Plus /></el-icon>
          Enter Points
        </el-button>
        <el-button type="success" @click="handleRefresh('STAR')">
          Refresh Star Rankings
        </el-button>
        <el-button type="success" @click="handleRefresh('PARTY')">
          Refresh Party Rankings
        </el-button>
      </div>
    </div>

    <el-tabs v-model="activeTab" @tab-change="handleTabChange">
      <el-tab-pane label="Star Rankings" name="STAR">
        <div class="card">
          <el-table v-loading="loading" :data="starRankings" style="width: 100%">
            <el-table-column prop="rank" label="Rank" width="80" />
            <el-table-column label="User" width="250">
              <template #default="{ row }">
                <div class="user-cell">
                  <el-avatar :src="row.avatar" :size="40" />
                  <div class="user-info">
                    <div class="user-name">{{ row.nickname }}</div>
                    <div class="user-id">ID: {{ row.userId }}</div>
                  </div>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="Tier" width="120">
              <template #default="{ row }">
                <span
                  class="tier-badge"
                  :style="{ backgroundColor: getTierColor(row.tier) }"
                >
                  {{ formatTier(row.tier) }}
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="totalPoints" label="Total Points" width="150" />
          </el-table>
        </div>
      </el-tab-pane>

      <el-tab-pane label="Party Rankings" name="PARTY">
        <div class="card">
          <el-table v-loading="loading" :data="partyRankings" style="width: 100%">
            <el-table-column prop="rank" label="Rank" width="80" />
            <el-table-column label="User" width="250">
              <template #default="{ row }">
                <div class="user-cell">
                  <el-avatar :src="row.avatar" :size="40" />
                  <div class="user-info">
                    <div class="user-name">{{ row.nickname }}</div>
                    <div class="user-id">ID: {{ row.userId }}</div>
                  </div>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="Tier" width="120">
              <template #default="{ row }">
                <span
                  class="tier-badge"
                  :style="{ backgroundColor: getTierColor(row.tier) }"
                >
                  {{ formatTier(row.tier) }}
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="totalPoints" label="Total Points" width="150" />
          </el-table>
        </div>
      </el-tab-pane>
    </el-tabs>

    <PointEntryDialog v-model="pointDialogVisible" @success="fetchRankings" />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getRankings, refreshRankings } from '@/api/rankings'
import { formatTier, getTierColor } from '@/utils'
import PointEntryDialog from './PointEntryDialog.vue'
import type { RankingEntry } from '@/types'

const loading = ref(false)
const activeTab = ref('STAR')
const starRankings = ref<RankingEntry[]>([])
const partyRankings = ref<RankingEntry[]>([])
const pointDialogVisible = ref(false)

const fetchRankings = async () => {
  loading.value = true
  try {
    const [starRes, partyRes] = await Promise.all([
      getRankings('STAR'),
      getRankings('PARTY')
    ])

    if (starRes.code === 0) {
      starRankings.value = starRes.data.rankings
    } else {
      ElMessage.error(starRes.message || 'Failed to fetch star rankings')
    }

    if (partyRes.code === 0) {
      partyRankings.value = partyRes.data.rankings
    } else {
      ElMessage.error(partyRes.message || 'Failed to fetch party rankings')
    }
  } catch (error) {
    ElMessage.error('Failed to fetch rankings')
  } finally {
    loading.value = false
  }
}

const handleTabChange = (tabName: string) => {
  activeTab.value = tabName
}

const handleEnterPoints = () => {
  pointDialogVisible.value = true
}

const handleRefresh = async (type: 'STAR' | 'PARTY') => {
  try {
    const res = await refreshRankings(type)
    if (res.code === 0) {
      ElMessage.success(`${type} rankings refreshed successfully`)
      fetchRankings()
    } else {
      ElMessage.error(res.message || 'Failed to refresh rankings')
    }
  } catch (error) {
    ElMessage.error('Failed to refresh rankings')
  }
}

onMounted(() => {
  fetchRankings()
})
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-actions {
  display: flex;
  gap: 12px;
}

.user-cell {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.user-name {
  font-weight: 500;
  color: #1f2937;
}

.user-id {
  font-size: 12px;
  color: #6b7280;
}
</style>
