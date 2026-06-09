<template>
  <div>
    <div class="page-header">
      <h1>排名管理</h1>
      <div class="header-actions">
        <el-button type="primary" @click="handleEnterPoints">
          <el-icon><Plus /></el-icon>
          录入积分
        </el-button>
        <el-button type="success" @click="handleRefresh('STAR')">
          刷新明星排名
        </el-button>
        <el-button type="success" @click="handleRefresh('PARTY')">
          刷新派对排名
        </el-button>
      </div>
    </div>

    <el-tabs v-model="activeTab" @tab-change="handleTabChange">
      <el-tab-pane label="明星排名" name="STAR">
        <div class="card">
          <el-table v-loading="loading" :data="starRankings" style="width: 100%">
            <el-table-column prop="rank" label="排名" width="80" />
            <el-table-column label="用户" width="250">
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
            <el-table-column label="等级" width="120">
              <template #default="{ row }">
                <span
                  class="tier-badge"
                  :style="{ backgroundColor: getTierColor(row.tier) }"
                >
                  {{ formatTier(row.tier) }}
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="totalPoints" label="总积分" width="150" />
          </el-table>
        </div>
      </el-tab-pane>

      <el-tab-pane label="派对排名" name="PARTY">
        <div class="card">
          <el-table v-loading="loading" :data="partyRankings" style="width: 100%">
            <el-table-column prop="rank" label="排名" width="80" />
            <el-table-column label="用户" width="250">
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
            <el-table-column label="等级" width="120">
              <template #default="{ row }">
                <span
                  class="tier-badge"
                  :style="{ backgroundColor: getTierColor(row.tier) }"
                >
                  {{ formatTier(row.tier) }}
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="totalPoints" label="总积分" width="150" />
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
      ElMessage.error(starRes.message || '获取明星排名失败')
    }

    if (partyRes.code === 0) {
      partyRankings.value = partyRes.data.rankings
    } else {
      ElMessage.error(partyRes.message || '获取派对排名失败')
    }
  } catch (error) {
    ElMessage.error('获取排名失败')
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
      ElMessage.success(`${type}排名刷新成功`)
      fetchRankings()
    } else {
      ElMessage.error(res.message || '刷新排名失败')
    }
  } catch (error) {
    ElMessage.error('刷新排名失败')
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
