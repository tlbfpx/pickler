<template>
  <div>
    <div class="page-header">
      <h1>赛季管理</h1>
      <div class="header-actions">
        <el-button
          type="primary"
          @click="openCreate"
        >
          <el-icon><Plus /></el-icon>
          新建赛季
        </el-button>
      </div>
    </div>

    <el-tabs
      v-model="activeType"
      @tab-change="handleTypeChange"
    >
      <el-tab-pane
        :label="TERMS.STAR.type + '赛季'"
        name="STAR"
      />
      <el-tab-pane
        :label="TERMS.PARTY.type + '赛季'"
        name="PARTY"
      />
    </el-tabs>

    <div class="card">
      <el-table
        v-loading="loading"
        :data="seasons"
        style="width: 100%"
      >
        <el-table-column
          prop="code"
          label="赛季编码"
          width="160"
        />
        <el-table-column
          prop="name"
          label="赛季名称"
          min-width="180"
        />
        <el-table-column
          label="起止日期"
          width="240"
        >
          <template #default="{ row }">
            {{ row.startDate }} ~ {{ row.endDate }}
          </template>
        </el-table-column>
        <el-table-column
          label="状态"
          width="120"
        >
          <template #default="{ row }">
            <el-tag
              v-if="row.status === 'CURRENT'"
              type="success"
            >
              当前赛季
            </el-tag>
            <el-tag
              v-else
              type="info"
            >
              已归档
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          label="操作"
          width="240"
          fixed="right"
        >
          <template #default="{ row }">
            <el-button
              v-if="row.status !== 'CURRENT'"
              type="warning"
              size="small"
              @click="handleActivate(row)"
            >
              设为当前
            </el-button>
            <el-button
              size="small"
              @click="openRankings(row)"
            >
              查看排名
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 新建赛季对话框 -->
    <el-dialog
      v-model="createVisible"
      title="新建赛季"
      width="520px"
    >
      <el-form
        ref="createFormRef"
        :model="createForm"
        :rules="createRules"
        label-width="100px"
      >
        <el-form-item
          label="赛季类型"
          prop="type"
        >
          <el-select
            v-model="createForm.type"
            style="width: 100%"
          >
            <el-option
              :label="TERMS.STAR.type + '（' + TERMS.STAR.points + '）'"
              value="STAR"
            />
            <el-option
              :label="TERMS.PARTY.type + '（' + TERMS.PARTY.points + '）'"
              value="PARTY"
            />
          </el-select>
        </el-form-item>
        <el-form-item
          label="赛季编码"
          prop="code"
        >
          <el-input
            v-model="createForm.code"
            placeholder="如 2026-Q3"
          />
        </el-form-item>
        <el-form-item
          label="赛季名称"
          prop="name"
        >
          <el-input
            v-model="createForm.name"
            placeholder="如 2026 第三赛季"
          />
        </el-form-item>
        <el-form-item
          label="开始日期"
          prop="startDate"
        >
          <el-date-picker
            v-model="createForm.startDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择开始日期"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item
          label="结束日期"
          prop="endDate"
        >
          <el-date-picker
            v-model="createForm.endDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择结束日期"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="createLoading"
          @click="handleCreate"
        >
          创建
        </el-button>
      </template>
    </el-dialog>

    <!-- 归档排名抽屉 -->
    <el-drawer
      v-model="rankingsVisible"
      :title="`${currentSeason?.name || '赛季'} - 归档排名`"
      size="60%"
      direction="rtl"
    >
      <el-table
        v-loading="rankingsLoading"
        :data="rankings"
        style="width: 100%"
      >
        <el-table-column
          prop="rank"
          label="排名"
          width="80"
        />
        <el-table-column
          label="用户"
          min-width="200"
        >
          <template #default="{ row }">
            <div class="user-cell">
              <el-avatar
                :src="row.avatarUrl || undefined"
                :size="32"
              />
              <div class="user-info">
                <div class="user-name">
                  {{ row.nickname || ('用户 ' + row.userId) }}
                </div>
                <div class="user-id">
                  ID: {{ row.userId }}
                </div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column
          label="段位"
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
          :label="activeType === 'STAR' ? TERMS.STAR.points : TERMS.PARTY.points"
          width="100"
        />
      </el-table>
      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="rankingsPage"
          v-model:page-size="rankingsSize"
          :total="rankingsTotal"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @current-change="fetchRankings"
          @size-change="fetchRankings"
        />
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { listSeasons, createSeason, activateSeason, getSeasonRankings } from '@/api/seasons'
import { TERMS, formatTierName } from '@/constants/terms'
import { getTierColor } from '@/utils'
import type { Season, SeasonRankingEntry, PointsType } from '@/types'

const loading = ref(false)
const activeType = ref<PointsType>('STAR')
const seasons = ref<Season[]>([])

const fetchSeasons = async () => {
  loading.value = true
  try {
    const res = await listSeasons(activeType.value)
    if (res.code === 0) {
      seasons.value = res.data || []
    } else {
      ElMessage.error(res.message || '获取赛季列表失败')
    }
  } catch {
    /* intercepted */
  } finally {
    loading.value = false
  }
}

const handleTypeChange = (tab: string | number) => {
  activeType.value = (tab === 'PARTY' ? 'PARTY' : 'STAR') as PointsType
  fetchSeasons()
}

// ===== 新建 =====
const createVisible = ref(false)
const createLoading = ref(false)
const createFormRef = ref<FormInstance>()
const createForm = reactive({
  type: 'STAR' as PointsType,
  code: '',
  name: '',
  startDate: '',
  endDate: ''
})
const createRules: FormRules = {
  type: [{ required: true, message: '请选择赛季类型', trigger: 'change' }],
  code: [{ required: true, message: '请输入赛季编码', trigger: 'blur' }],
  name: [{ required: true, message: '请输入赛季名称', trigger: 'blur' }],
  startDate: [{ required: true, message: '请选择开始日期', trigger: 'change' }],
  endDate: [{ required: true, message: '请选择结束日期', trigger: 'change' }]
}

const openCreate = () => {
  createForm.type = activeType.value
  createForm.code = ''
  createForm.name = ''
  createForm.startDate = ''
  createForm.endDate = ''
  createVisible.value = true
}

const handleCreate = async () => {
  if (!createFormRef.value) return
  await createFormRef.value.validate(async (valid) => {
    if (!valid) return
    createLoading.value = true
    try {
      const res = await createSeason({
        type: createForm.type,
        code: createForm.code.trim(),
        name: createForm.name.trim(),
        startDate: createForm.startDate,
        endDate: createForm.endDate
      })
      if (res.code === 0) {
        ElMessage.success('赛季创建成功（默认归档）')
        createVisible.value = false
        activeType.value = createForm.type
        fetchSeasons()
      } else {
        ElMessage.error(res.message || '创建失败')
      }
    } catch {
      /* intercepted */
    } finally {
      createLoading.value = false
    }
  })
}

// ===== 切换为当前 =====
const handleActivate = async (row: Season) => {
  try {
    await ElMessageBox.confirm(
      `确认将赛季「${row.name}」设为当前赛季？同类型原当前赛季将被归档，且影响后续发分。`,
      '切换当前赛季',
      {
        confirmButtonText: '确认切换',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
  } catch {
    return
  }
  try {
    const res = await activateSeason(row.id)
    if (res.code === 0) {
      ElMessage.success('切换成功')
      fetchSeasons()
    } else {
      ElMessage.error(res.message || '切换失败')
    }
  } catch {
    /* intercepted */
  }
}

// ===== 归档排名 =====
const rankingsVisible = ref(false)
const rankingsLoading = ref(false)
const rankings = ref<SeasonRankingEntry[]>([])
const rankingsPage = ref(1)
const rankingsSize = ref(20)
const rankingsTotal = ref(0)
const currentSeason = ref<Season | null>(null)

const openRankings = (row: Season) => {
  currentSeason.value = row
  rankingsPage.value = 1
  rankingsSize.value = 20
  rankingsVisible.value = true
  fetchRankings()
}

const fetchRankings = async () => {
  if (!currentSeason.value) return
  rankingsLoading.value = true
  try {
    const res = await getSeasonRankings(currentSeason.value.id, {
      page: rankingsPage.value,
      size: rankingsSize.value
    })
    if (res.code === 0) {
      rankings.value = res.data.list || []
      rankingsTotal.value = res.data.total || 0
    } else {
      ElMessage.error(res.message || '获取排名失败')
    }
  } catch {
    /* intercepted */
  } finally {
    rankingsLoading.value = false
  }
}

// 初始化
fetchSeasons()
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

.card {
  margin-top: 12px;
  background: #fff;
  border-radius: 8px;
  padding: 16px;
}

.user-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.user-info {
  display: flex;
  flex-direction: column;
  line-height: 1.2;
}

.user-name {
  font-size: 13px;
  font-weight: 500;
  color: #1f2937;
}

.user-id {
  font-size: 11px;
  color: #9ca3af;
}

.tier-badge {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 12px;
  color: #fff;
  font-size: 12px;
}

.pagination-wrap {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
