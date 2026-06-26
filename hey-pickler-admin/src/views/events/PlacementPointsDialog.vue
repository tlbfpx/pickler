<template>
  <el-dialog
    :model-value="modelValue"
    :title="`加分表配置 — ${event?.title ?? ''}`"
    width="600px"
    :close-on-click-modal="false"
    @update:model-value="$emit('update:modelValue', $event)"
    @open="handleOpen"
  >
    <div
      v-if="readonly"
      class="readonly-banner"
    >
      赛事已结束，加分表不可再修改。
    </div>

    <div
      v-else
      class="source-banner"
      :class="source"
    >
      当前来源：
      <strong>{{ source === 'default' ? '系统默认' : '赛事自定义' }}</strong>
      <span v-if="source === 'default'">
        （未配置自定义表，完赛时按 application.yml 默认加分）
      </span>
    </div>

    <el-table
      :data="rows"
      border
      style="width: 100%; margin-top: 12px"
    >
      <el-table-column
        label="名次"
        width="120"
      >
        <template #default="{ $index }">
          <el-input-number
            v-model="rows[$index].rank"
            :min="1"
            :max="999"
            :disabled="readonly"
            size="small"
            controls-position="right"
            style="width: 100%"
            @change="(val) => handleRankChange($index, val)"
          />
        </template>
      </el-table-column>
      <el-table-column
        label="积分"
      >
        <template #default="{ $index }">
          <el-input-number
            v-model="rows[$index].points"
            :min="0"
            :max="100000"
            :disabled="readonly"
            size="small"
            controls-position="right"
            style="width: 100%"
          />
        </template>
      </el-table-column>
      <el-table-column
        v-if="!readonly"
        label="操作"
        width="80"
        align="center"
      >
        <template #default="{ $index }">
          <el-button
            type="danger"
            size="small"
            link
            @click="removeRow($index)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <div
      v-if="!readonly"
      class="add-row"
    >
      <el-button
        type="primary"
        size="small"
        :icon="Plus"
        @click="addRow"
      >
        增加名次
      </el-button>
      <el-button
        v-if="source === 'custom'"
        type="warning"
        size="small"
        :icon="RefreshLeft"
        @click="resetToDefault"
      >
        重置为默认
      </el-button>
      <el-button
        size="small"
        :icon="InfoFilled"
        @click="previewTotal"
      >
        完赛发分预览
      </el-button>
    </div>

    <div
      v-if="previewText"
      class="preview-banner"
    >
      {{ previewText }}
    </div>

    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">
        取消
      </el-button>
      <el-button
        v-if="!readonly"
        type="primary"
        :loading="saving"
        @click="handleSave"
      >
        保存
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Plus, RefreshLeft, InfoFilled } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getPlacementPoints, setPlacementPoints, clearPlacementPoints } from '@/api/placement'
import type { Event } from '@/types'

interface Row {
  rank: number
  points: number
}

const props = defineProps<{
  modelValue: boolean
  event: Event | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', val: boolean): void
  (e: 'saved'): void
}>()

const rows = ref<Row[]>([])
const source = ref<'default' | 'custom'>('default')
const saving = ref(false)
const previewText = ref('')

/** "Default" fallback table — mirrors application.yml; we cache the last
 *  fetched default into here so resetToDefault can restore it. */
const lastSeenDefault = ref<Record<string, number> | null>(null)

const readonly = computed(() => props.event?.status === 'COMPLETED')

const handleOpen = async () => {
  if (!props.event) return
  try {
    const res = await getPlacementPoints(props.event.id)
    if (res.code === 0 && res.data) {
      source.value = res.data.source
      // 后端以 JSON 返回，key 是数字字符串如 '1'、'2'
      rows.value = Object.entries(res.data.points)
        .map(([k, v]) => ({ rank: Number(k), points: Number(v) }))
        .sort((a, b) => a.rank - b.rank)
      // Cache the defaults so resetToDefault can restore them.
      if (res.data.source === 'default') {
        lastSeenDefault.value = { ...res.data.points }
      }
    } else {
      ElMessage.error(res.message || '获取加分表失败')
    }
  } catch {
    ElMessage.error('获取加分表失败')
  }
}

const resetToDefault = async () => {
  if (readonly.value || !props.event) return
  try {
    await ElMessageBox.confirm(
      '确定要清除自定义加分表吗？完赛时将按 application.yml 默认值发分。',
      '重置为默认',
      { type: 'warning' }
    )
  } catch {
    return  // user cancelled
  }
  try {
    const res = await clearPlacementPoints(props.event.id)
    if (res.code === 0) {
      ElMessage.success('已恢复为系统默认')
      source.value = 'default'
      // Re-fetch the default table to repopulate rows for display.
      const fresh = await getPlacementPoints(props.event.id)
      if (fresh.code === 0 && fresh.data) {
        rows.value = Object.entries(fresh.data.points)
          .map(([k, v]) => ({ rank: Number(k), points: Number(v) }))
          .sort((a, b) => a.rank - b.rank)
        lastSeenDefault.value = { ...fresh.data.points }
      }
      previewText.value = ''
      emit('saved')
    } else {
      ElMessage.error(res.message || '重置失败')
    }
  } catch {
    ElMessage.error('重置失败')
  }
}

const previewTotal = () => {
  // Estimate: ∑ table[rank] for r in 1..participantCount.
  // currentParticipants is a count of registered people. Doubles teams count
  // as 1 row in match_group; use participants as proxy for placements.
  if (!props.event) return
  const participantCount = props.event.currentParticipants || 0
  if (participantCount === 0) {
    previewText.value = '当前赛事暂无参赛者，完赛发分为 0。'
    return
  }
  const table = new Map<number, number>()
  for (const r of rows.value) table.set(r.rank, r.points)
  let total = 0
  for (let rank = 1; rank <= participantCount; rank++) {
    total += table.get(rank) ?? 0
  }
  previewText.value = `按当前 ${participantCount} 位参赛者 + ${rows.value.length} 名次规则，完赛预计共发 ${total} 积分。`
}

const addRow = () => {
  // 默认新行 = (最大 rank + 1) / 0
  const maxRank = rows.value.reduce((acc, r) => Math.max(acc, r.rank), 0)
  rows.value.push({ rank: maxRank + 1, points: 0 })
}

const removeRow = (idx: number) => {
  rows.value.splice(idx, 1)
}

const handleRankChange = (idx: number, val: number | undefined) => {
  if (val === undefined) return
  // 检测重名
  const dup = rows.value.findIndex((r, i) => i !== idx && r.rank === val)
  if (dup !== -1) {
    ElMessage.warning(`名次 ${val} 已存在，将被合并为最新值`)
    rows.value.splice(dup, 1)
  }
}

const handleSave = async () => {
  if (!props.event) return
  // 校验：名次必须 ≥ 1，积分必须 ≥ 0
  for (const r of rows.value) {
    if (!Number.isInteger(r.rank) || r.rank < 1) {
      ElMessage.error(`名次必须为正整数（当前：${r.rank}）`)
      return
    }
    if (r.points < 0) {
      ElMessage.error(`名次 ${r.rank} 的积分不能为负`)
      return
    }
  }
  // 名次去重（理论上 UI 已合并，再防一手）
  const ranks = new Set<number>()
  for (const r of rows.value) {
    if (ranks.has(r.rank)) {
      ElMessage.error(`名次重复：${r.rank}`)
      return
    }
    ranks.add(r.rank)
  }
  if (rows.value.length === 0) {
    ElMessage.error('加分表不能为空')
    return
  }

  const body = {
    points: rows.value.reduce<Record<string, number>>((acc, r) => {
      acc[String(r.rank)] = r.points
      return acc
    }, {})
  }

  saving.value = true
  try {
    const res = await setPlacementPoints(props.event.id, body)
    if (res.code === 0) {
      ElMessage.success('加分表已保存')
      source.value = 'custom'
      emit('saved')
      emit('update:modelValue', false)
    } else {
      ElMessage.error(res.message || '保存失败')
    }
  } catch {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

// 每次重新打开都重置（dialog 的 @open 已 fetch，但防御性清空）
watch(() => props.modelValue, (v) => {
  if (!v) rows.value = []
})
</script>

<style scoped>
.source-banner,
.readonly-banner {
  padding: 8px 12px;
  border-radius: 4px;
  font-size: 13px;
  line-height: 1.6;
}

.source-banner.default {
  background: #f5f7fa;
  color: #606266;
}

.source-banner.custom {
  background: #ecf5ff;
  color: #409eff;
}

.readonly-banner {
  background: #fef0f0;
  color: #f56c6c;
}

.add-row {
  margin-top: 12px;
  display: flex;
  justify-content: center;
  gap: 8px;
  flex-wrap: wrap;
}

.preview-banner {
  margin-top: 12px;
  padding: 10px 14px;
  background: #f0f9eb;
  color: #67c23a;
  border-radius: 4px;
  font-size: 13px;
  line-height: 1.6;
  text-align: center;
}
</style>