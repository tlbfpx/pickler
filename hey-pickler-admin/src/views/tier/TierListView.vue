<template>
  <div>
    <div class="page-header">
      <h1>段位管理</h1>
    </div>
    <el-tabs
      v-model="activeTrack"
      @tab-change="handleTabChange"
    >
      <el-tab-pane
        label="STAR 竞技"
        name="STAR"
      />
      <el-tab-pane
        label="PARTY 社交"
        name="PARTY"
      />
    </el-tabs>

    <el-card>
      <div class="tier-toolbar">
        <span class="tier-title">
          {{ activeTrack === 'STAR' ? 'STAR 战力段位' : 'PARTY 活力段位' }}
          <el-tag
            size="small"
            type="info"
          >tier_code 系统绑定不可改，仅可改展示名/颜色/阈值/图标</el-tag>
        </span>
        <el-button
          type="primary"
          :loading="saving"
          @click="handleSave"
        >
          保存
        </el-button>
      </div>
      <el-table
        v-loading="loading"
        :data="rows"
        size="small"
      >
        <el-table-column
          prop="tierCode"
          label="tier_code"
          width="120"
        />
        <el-table-column
          label="展示名"
          width="160"
        >
          <template #default="{ row }">
            <el-input
              v-model="row.tierName"
              size="small"
            />
          </template>
        </el-table-column>
        <el-table-column
          label="颜色"
          width="200"
        >
          <template #default="{ row }">
            <el-color-picker
              v-model="row.tierColor"
              size="small"
            />
            <el-tag
              :color="row.tierColor || undefined"
              effect="dark"
              size="small"
              class="color-preview"
            >
              {{ row.tierName }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          label="阈值"
          width="140"
        >
          <template #default="{ row }">
            <el-input-number
              v-model="row.threshold"
              :min="0"
              controls-position="right"
              size="small"
            />
          </template>
        </el-table-column>
        <el-table-column
          label="图标"
          min-width="120"
        >
          <template #default="{ row }">
            <el-input
              v-model="row.icon"
              size="small"
              placeholder="emoji"
            />
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, nextTick, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getTierConfig,
  updateTierConfig,
  type TierConfigVO,
  type TierItemUpdateRequest
} from '@/api/tier'

// 后端按 BRONZE..MASTER 顺序返回并强校验；前端预校验需保持同一序。
const TIER_ORDER = ['BRONZE', 'SILVER', 'GOLD', 'PLATINUM', 'DIAMOND', 'MASTER'] as const

const activeTrack = ref<'STAR' | 'PARTY'>('STAR')
const rows = ref<TierItemUpdateRequest[]>([])

const loading = ref(false)
const saving = ref(false)

// dirty guard：仅追踪用户真实编辑，程序化赋值（加载档位）不触发
const dirty = ref(false)
const suppressDirty = ref(false)

watch(
  rows,
  () => {
    if (suppressDirty.value) return
    dirty.value = true
  },
  { deep: true }
)

const loadTrack = async (track: 'STAR' | 'PARTY') => {
  loading.value = true
  // 程序化赋值（加载）不应触发 dirty：在赋值前抑制、在 watch 触发后的下一 tick 解除
  suppressDirty.value = true
  try {
    const res = await getTierConfig(track)
    if (res.code === 0) {
      const items: TierConfigVO[] = res.data || []
      // 后端返回顺序即 BRONZE..MASTER，直接映射；不回写 tierCode（仅定位）
      rows.value = items.map((it) => ({
        tierCode: it.tierCode,
        tierName: it.tierName,
        tierColor: it.tierColor,
        threshold: it.threshold,
        icon: it.icon
      }))
    } else {
      ElMessage.error(res.message || '段位配置加载失败')
    }
  } catch {
    ElMessage.error('段位配置加载失败')
  } finally {
    loading.value = false
    await nextTick()
    suppressDirty.value = false
    dirty.value = false
  }
}

const handleTabChange = async (tabName: string | number) => {
  const next = String(tabName) as 'STAR' | 'PARTY'
  if (next === activeTrack.value) return
  // 切换前若有未保存修改，提示确认
  if (dirty.value) {
    try {
      await ElMessageBox.confirm(
        '当前段位配置有未保存的修改，确认切换？',
        '提示',
        {
          confirmButtonText: '切换',
          cancelButtonText: '取消',
          type: 'warning'
        }
      )
    } catch {
      // 用户取消：恢复 activeTrack 显示，不切换数据
      // el-tabs 已改变 v-model，需回滚到加载的 track
      // rows 仍是旧 track 数据，根据当前 rows 反推不可靠，故维护一个 loadedTrack
      activeTrack.value = loadedTrack.value
      return
    }
  }
  loadedTrack.value = next
  await loadTrack(next)
}

// 真正已加载数据的 track（用于 dirty 取消时回滚 el-tabs 的 v-model）
const loadedTrack = ref<'STAR' | 'PARTY'>('STAR')

// 前端预校验（后端兜底）：BRONZE==0、按 BRONZE..MASTER 严格递增、全 >=0
const validateRows = (): string | null => {
  const byCode = new Map<string, TierItemUpdateRequest>()
  for (const r of rows.value) {
    byCode.set(r.tierCode, r)
  }
  // tierCode 齐全
  for (const code of TIER_ORDER) {
    if (!byCode.has(code)) {
      return `缺少档位 ${code}`
    }
  }
  // 全 >= 0
  for (const r of rows.value) {
    if (r.threshold == null || Number.isNaN(r.threshold) || r.threshold < 0) {
      return `${r.tierCode} 阈值必须为非负整数`
    }
  }
  // BRONZE == 0
  if (byCode.get('BRONZE')!.threshold !== 0) {
    return 'BRONZE（青铜）阈值必须为 0'
  }
  // 按 BRONZE..MASTER 严格递增
  for (let i = 1; i < TIER_ORDER.length; i++) {
    const prev = byCode.get(TIER_ORDER[i - 1])!
    const curr = byCode.get(TIER_ORDER[i])!
    if (curr.threshold <= prev.threshold) {
      return `${TIER_ORDER[i]} 阈值需严格大于 ${TIER_ORDER[i - 1]}（${curr.threshold} ≤ ${prev.threshold}）`
    }
  }
  return null
}

const handleSave = async () => {
  const err = validateRows()
  if (err) {
    ElMessage.error(err)
    return
  }
  saving.value = true
  try {
    const res = await updateTierConfig(activeTrack.value, rows.value)
    if (res.code === 0) {
      ElMessage.success('已保存')
      dirty.value = false
    } else {
      ElMessage.error(res.message || '保存失败')
    }
  } catch {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  loadedTrack.value = activeTrack.value
  loadTrack(activeTrack.value)
})
</script>

<style scoped>
.page-header {
  margin-bottom: 16px;
}

.page-header h1 {
  margin: 0;
  font-size: 20px;
}

.tier-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.tier-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.color-preview {
  margin-left: 6px;
  color: #fff;
  border: none;
}

.el-card :deep(.el-table .cell) {
  display: flex;
  align-items: center;
}
</style>
