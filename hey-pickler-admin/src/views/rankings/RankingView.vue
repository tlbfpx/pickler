<template>
  <div>
    <div class="page-header">
      <h1>排名管理</h1>
      <div class="header-actions">
        <el-button
          type="primary"
          @click="handleEnterPoints"
        >
          <el-icon><Plus /></el-icon>
          录入积分
        </el-button>
        <el-button
          type="success"
          @click="handleRefresh('STAR')"
        >
          刷新{{ TERMS.STAR.ranking }}
        </el-button>
        <el-button
          type="success"
          @click="handleRefresh('PARTY')"
        >
          刷新{{ TERMS.PARTY.ranking }}
        </el-button>
      </div>
    </div>

    <el-input
      v-model="keyword"
      placeholder="搜索昵称"
      clearable
      style="width: 220px; margin-bottom: 12px"
      @keyup.enter="onSearch"
      @clear="onSearch"
    />

    <el-tabs
      v-model="activeTab"
      @tab-change="handleTabChange"
    >
      <el-tab-pane
        :label="TERMS.STAR.ranking"
        name="STAR"
      >
        <div class="card">
          <el-table
            v-loading="loading"
            :data="list"
            style="width: 100%"
          >
            <el-table-column
              prop="rank"
              label="排名"
              width="80"
            />
            <el-table-column
              label="用户"
              width="250"
            >
              <template #default="{ row }">
                <div class="user-cell">
                  <el-avatar
                    :src="row.avatarUrl || undefined"
                    :size="40"
                  />
                  <div class="user-info">
                    <div class="user-name">
                      {{ row.nickname || '-' }}
                    </div>
                    <div class="user-id">
                      ID: {{ row.userId }}
                    </div>
                  </div>
                </div>
              </template>
            </el-table-column>
            <el-table-column
              label="级别"
              width="120"
            >
              <template #default="{ row }">
                <span
                  class="tier-badge"
                  :style="{ backgroundColor: getTierColor(row.tier) }"
                >
                  {{ row.tierName || formatTierName(row.tier) }}
                </span>
              </template>
            </el-table-column>
            <el-table-column
              prop="points"
              label="积分"
              width="120"
            />
            <el-table-column
              label="城市"
              width="100"
            >
              <template #default="{ row }">
                {{ row.city || '-' }}
              </template>
            </el-table-column>
          </el-table>
          <Pagination
            v-model:page="page"
            v-model:size="size"
            :total="total"
            @update:page="fetchOne"
            @update:size="fetchOne"
          />
        </div>
      </el-tab-pane>

      <el-tab-pane
        :label="TERMS.PARTY.ranking"
        name="PARTY"
      >
        <div class="card">
          <el-table
            v-loading="loading"
            :data="list"
            style="width: 100%"
          >
            <el-table-column
              prop="rank"
              label="排名"
              width="80"
            />
            <el-table-column
              label="用户"
              width="250"
            >
              <template #default="{ row }">
                <div class="user-cell">
                  <el-avatar
                    :src="row.avatarUrl || undefined"
                    :size="40"
                  />
                  <div class="user-info">
                    <div class="user-name">
                      {{ row.nickname || '-' }}
                    </div>
                    <div class="user-id">
                      ID: {{ row.userId }}
                    </div>
                  </div>
                </div>
              </template>
            </el-table-column>
            <el-table-column
              label="级别"
              width="120"
            >
              <template #default="{ row }">
                <span
                  class="tier-badge"
                  :style="{ backgroundColor: getTierColor(row.tier) }"
                >
                  {{ row.tierName || formatTierName(row.tier) }}
                </span>
              </template>
            </el-table-column>
            <el-table-column
              prop="points"
              label="积分"
              width="120"
            />
            <el-table-column
              label="城市"
              width="100"
            >
              <template #default="{ row }">
                {{ row.city || '-' }}
              </template>
            </el-table-column>
          </el-table>
          <Pagination
            v-model:page="page"
            v-model:size="size"
            :total="total"
            @update:page="fetchOne"
            @update:size="fetchOne"
          />
        </div>
      </el-tab-pane>
    </el-tabs>

    <PointEntryDialog
      v-model="pointDialogVisible"
      @success="fetchOne"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getRankings, refreshRankings } from '@/api/rankings'
import { getTierColor } from '@/utils'
import { TERMS, formatTierName } from '@/constants/terms'
import Pagination from '@/components/common/Pagination.vue'
import PointEntryDialog from './PointEntryDialog.vue'
import type { RankingEntry } from '@/types'

const loading = ref(false)
const activeTab = ref<'STAR' | 'PARTY'>('STAR')
const list = ref<RankingEntry[]>([])
const keyword = ref('')
const page = ref(1)
const size = ref(20)
const total = ref(0)
const pointDialogVisible = ref(false)

const fetchOne = async () => {
  loading.value = true
  try {
    const res = await getRankings({
      type: activeTab.value,
      page: page.value,
      size: size.value,
      keyword: keyword.value || undefined
    })
    if (res.code === 0) {
      list.value = res.data.list || []
      total.value = res.data.total || 0
    } else {
      ElMessage.error(res.message || `获取${TERMS[activeTab.value].ranking}失败`)
    }
  } catch {

  } finally {
    loading.value = false
  }
}

const onSearch = () => {
  page.value = 1
  fetchOne()
}

watch(activeTab, onSearch)

const handleTabChange = (tabName: string | number) => {
  activeTab.value = tabName as 'STAR' | 'PARTY'
}

const handleEnterPoints = () => {
  pointDialogVisible.value = true
}

const handleRefresh = async (type: 'STAR' | 'PARTY') => {
  try {
    const res = await refreshRankings(type)
    if (res.code === 0) {
      ElMessage.success(`${TERMS[type].ranking}刷新成功`)
      fetchOne()
    } else {
      ElMessage.error(res.message || '刷新排名失败')
    }
  } catch {

  }
}

onMounted(() => {
  fetchOne()
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
