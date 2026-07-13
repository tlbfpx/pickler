<template>
  <div>
    <div class="page-header">
      <h1>积分与排名</h1>
      <div class="header-actions">
        <el-button @click="seasonDialogVisible = true">
          赛季管理
        </el-button>
        <el-button
          type="success"
          :disabled="isArchived"
          :loading="refreshing"
          @click="handleRefresh"
        >
          刷新{{ TERMS[activeTab].ranking }}
        </el-button>
        <el-button
          type="primary"
          :disabled="isArchived"
          @click="openEntryDialog()"
        >
          录入积分
        </el-button>
      </div>
    </div>

    <el-tabs
      v-model="activeTab"
      @tab-change="handleTabChange"
    >
      <el-tab-pane
        :label="TERMS.STAR.ranking"
        name="STAR"
      />
      <el-tab-pane
        :label="TERMS.PARTY.ranking"
        name="PARTY"
      />
    </el-tabs>

    <!-- 工具条：赛季选择 + 段位筛选 + 昵称搜索 + 段位分布 -->
    <div class="toolbar">
      <el-select
        v-model="selectedSeasonId"
        placeholder="选择赛季"
        style="width: 260px"
        @change="onSeasonChange"
      >
        <el-option
          v-for="s in seasons"
          :key="s.id"
          :label="`${s.name || s.code}（${s.code}）${s.status === 'CURRENT' ? '· 当前' : '· 已归档'}`"
          :value="s.id"
        />
      </el-select>

      <el-radio-group
        v-model="tierFilter"
        @change="onFilterChange"
      >
        <el-radio-button value="">
          全部
        </el-radio-button>
        <el-radio-button
          v-for="key in TIER_KEYS"
          :key="key"
          :value="key"
        >
          {{ TIER_NAME[key] }}
        </el-radio-button>
      </el-radio-group>

      <el-input
        v-model="keyword"
        placeholder="搜索昵称"
        clearable
        style="width: 180px"
        @keyup.enter="onSearch"
        @clear="onSearch"
      />
    </div>

    <div class="tier-dist">
      <span
        v-for="key in TIER_KEYS"
        :key="key"
        class="dist-item"
      >
        <span
          class="dist-dot"
          :style="{ background: TIER_COLOR[key] }"
        />
        {{ TIER_NAME[key] }}
        <b>{{ tierDistribution[key] || 0 }}</b>
      </span>
      <span
        v-if="seasonCode"
        class="season-meta"
      >
        <el-tag
          size="small"
          :type="isArchived ? 'info' : 'success'"
        >
          {{ isArchived ? '已归档' : '当前赛季' }}
        </el-tag>
        <span class="meta-text">{{ seasonName || seasonCode }}（{{ seasonCode }}）</span>
        <span
          v-if="isArchived"
          class="meta-warn"
        >归档为快照，只读</span>
      </span>
    </div>

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
          label="变化"
          width="80"
          align="center"
        >
          <template #default="{ row }">
            <span :class="changeClass(row.change)">{{ changeText(row.change) }}</span>
          </template>
        </el-table-column>
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
        <el-table-column
          label="操作"
          width="140"
          align="center"
        >
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              size="small"
              @click="openLedger(row)"
            >
              明细
            </el-button>
            <el-button
              link
              type="primary"
              size="small"
              :disabled="isArchived"
              @click="openEntryDialog(row)"
            >
              调整
            </el-button>
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

    <PointLedgerDrawer
      v-model="ledgerVisible"
      :user-id="ledgerUserId"
      :user-name="ledgerUserName"
      :type="activeTab"
      @reverted="fetchOne"
    />
    <SeasonManageDialog
      v-model="seasonDialogVisible"
      :type="activeTab"
      @changed="loadSeasons"
    />
    <PointEntryDialog
      v-model="entryDialogVisible"
      :preset-user="presetUser"
      @success="onEntrySuccess"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getRankings, refreshRankings } from '@/api/rankings'
import { getSeasonRankings, listSeasons } from '@/api/seasons'
import { getTierColor } from '@/utils'
import { TERMS, formatTierName, TIER_NAME, TIER_COLOR } from '@/constants/terms'
import Pagination from '@/components/common/Pagination.vue'
import PointLedgerDrawer from './PointLedgerDrawer.vue'
import SeasonManageDialog from './SeasonManageDialog.vue'
import PointEntryDialog from './PointEntryDialog.vue'
import type { RankingEntry, Season } from '@/types'

const TIER_KEYS = Object.keys(TIER_NAME)

const loading = ref(false)
const refreshing = ref(false)
const activeTab = ref<'STAR' | 'PARTY'>('STAR')
const list = ref<RankingEntry[]>([])
const keyword = ref('')
const page = ref(1)
const size = ref(20)
const total = ref(0)

const tierFilter = ref('')
const seasons = ref<Season[]>([])
const selectedSeasonId = ref<number | null>(null)
const tierDistribution = ref<Record<string, number>>({})
const seasonCode = ref('')
const seasonName = ref<string | null>(null)
const seasonStatus = ref<'CURRENT' | 'ARCHIVED'>('CURRENT')

const ledgerVisible = ref(false)
const ledgerUserId = ref(0)
const ledgerUserName = ref<string | null>(null)
const seasonDialogVisible = ref(false)
const entryDialogVisible = ref(false)
const presetUser = ref<{
  userId: number
  nickname: string | null
  avatarUrl?: string | null
  type: 'STAR' | 'PARTY'
} | null>(null)

const selectedSeason = computed(() => seasons.value.find(s => s.id === selectedSeasonId.value) || null)
const isArchived = computed(() => selectedSeason.value?.status === 'ARCHIVED')

const loadSeasons = async () => {
  try {
    const res = await listSeasons(activeTab.value)
    if (res.code === 0) {
      seasons.value = res.data || []
      // 默认选当前赛季；无当前则选第一条
      const current = seasons.value.find(s => s.status === 'CURRENT')
      selectedSeasonId.value = current?.id ?? seasons.value[0]?.id ?? null
      page.value = 1
      tierFilter.value = ''
      await fetchOne()
    }
  } catch { /* ignore */ }
}

const fetchOne = async () => {
  if (!selectedSeason.value) return
  loading.value = true
  try {
    const res = isArchived.value
      ? await getSeasonRankings(selectedSeasonId.value!, {
          tier: tierFilter.value || undefined,
          page: page.value,
          size: size.value
        })
      : await getRankings({
          type: activeTab.value,
          page: page.value,
          size: size.value,
          keyword: keyword.value || undefined,
          tier: tierFilter.value || undefined
        })
    if (res.code === 0) {
      const d = res.data
      list.value = d.page.list || []
      total.value = d.page.total || 0
      tierDistribution.value = d.tierDistribution || {}
      seasonCode.value = d.seasonCode
      seasonName.value = d.seasonName
      seasonStatus.value = d.seasonStatus
    } else {
      ElMessage.error(res.message || `获取${TERMS[activeTab.value].ranking}失败`)
    }
  } catch { /* ignore */ } finally {
    loading.value = false
  }
}

const onSeasonChange = () => {
  page.value = 1
  tierFilter.value = ''
  fetchOne()
}

const onFilterChange = () => {
  page.value = 1
  fetchOne()
}

const onSearch = () => {
  page.value = 1
  fetchOne()
}

const handleTabChange = (tabName: string | number) => {
  activeTab.value = tabName as 'STAR' | 'PARTY'
  keyword.value = ''
  loadSeasons()
}

const handleRefresh = async () => {
  refreshing.value = true
  try {
    const res = await refreshRankings(activeTab.value)
    if (res.code === 0) {
      ElMessage.success(`${TERMS[activeTab.value].ranking}刷新成功`)
      // 排名刷新异步（rankingExecutor），稍候再拉一次确保看到最新
      setTimeout(fetchOne, 800)
    } else {
      ElMessage.error(res.message || '刷新排名失败')
    }
  } catch { /* ignore */ } finally {
    refreshing.value = false
  }
}

const openLedger = (row: RankingEntry) => {
  ledgerUserId.value = row.userId
  ledgerUserName.value = row.nickname
  ledgerVisible.value = true
}

const openEntryDialog = (row?: RankingEntry) => {
  presetUser.value = row
    ? { userId: row.userId, nickname: row.nickname, avatarUrl: row.avatarUrl, type: activeTab.value }
    : null
  entryDialogVisible.value = true
}

const onEntrySuccess = () => {
  // 手动发分触发异步排名刷新，稍候拉一次
  setTimeout(fetchOne, 800)
}

const changeText = (c: number) => {
  if (c > 0) return `↑${c}`
  if (c < 0) return `↓${-c}`
  return '—'
}
const changeClass = (c: number) => {
  if (c > 0) return 'change-up'
  if (c < 0) return 'change-down'
  return 'change-same'
}

onMounted(() => {
  loadSeasons()
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

.toolbar {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
  margin: 12px 0;
}

.tier-dist {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
  margin-bottom: 12px;
  font-size: 13px;
  color: #6b7280;
}

.dist-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.dist-item b {
  color: #111827;
  margin-left: 2px;
}

.dist-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.season-meta {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin-left: auto;
}

.meta-text {
  color: #374151;
  font-size: 13px;
}

.meta-warn {
  color: #9ca3af;
  font-size: 12px;
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

.tier-badge {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 4px;
  color: #fff;
  font-size: 12px;
}

.change-up {
  color: #43e97b;
  font-weight: 600;
}

.change-down {
  color: #f5576c;
  font-weight: 600;
}

.change-same {
  color: #d1d5db;
}
</style>
