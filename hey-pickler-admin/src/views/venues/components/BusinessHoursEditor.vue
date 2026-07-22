<template>
  <div
    v-loading="loading"
    class="bh-editor"
  >
    <div class="bh-toolbar">
      <div class="bh-hint">
        每行代表一天；将「开」或「关」时间留空即视为当日休息。
      </div>
      <el-button
        type="primary"
        :loading="saving"
        @click="handleSave"
      >
        保存营业时间
      </el-button>
    </div>

    <el-table
      :data="rows"
      style="width: 100%; margin-top: 12px"
    >
      <el-table-column
        label="星期"
        width="120"
      >
        <template #default="{ row }">
          {{ dayLabel(row.dayOfWeek) }}
        </template>
      </el-table-column>
      <el-table-column
        label="开门时间"
        width="200"
      >
        <template #default="{ row }">
          <el-time-select
            v-model="row.openTime"
            :max-time="row.closeTime"
            placeholder="留空 = 休息"
            start="00:00"
            step="00:30"
            end="23:30"
            clearable
          />
        </template>
      </el-table-column>
      <el-table-column
        label="关门时间"
        width="200"
      >
        <template #default="{ row }">
          <el-time-select
            v-model="row.closeTime"
            :min-time="row.openTime"
            placeholder="留空 = 休息"
            start="00:00"
            step="00:30"
            end="23:59"
            clearable
          />
        </template>
      </el-table-column>
      <el-table-column label="状态">
        <template #default="{ row }">
          <el-tag
            v-if="isClosed(row)"
            type="info"
            effect="plain"
          >
            休息
          </el-tag>
          <el-tag
            v-else
            type="success"
            effect="plain"
          >
            营业
          </el-tag>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { replaceBusinessHours } from '@/api/venues'
import type { BusinessHour } from '@/types'

const props = defineProps<{
  venueId: number | null
  hours: BusinessHour[]
}>()

const emit = defineEmits<{
  saved: []
}>()

const loading = ref(false)
const saving = ref(false)
const rows = ref<BusinessHour[]>([])

const dayLabels = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
const dayLabel = (d: number) => dayLabels[d] ?? `星期${d}`

const isClosed = (row: BusinessHour) => !row.openTime || !row.closeTime

/** 始终展示 7 行：周一~周日（1..6, 0）— 后端用 dayOfWeek 0..6 (周日=0) */
const buildRows = (source: BusinessHour[]) => {
  const order = [1, 2, 3, 4, 5, 6, 0]
  const map = new Map<number, BusinessHour>()
  for (const h of source || []) {
    map.set(h.dayOfWeek, { ...h })
  }
  rows.value = order.map(dow => {
    const existing = map.get(dow)
    if (existing) return existing
    return { dayOfWeek: dow, openTime: undefined, closeTime: undefined }
  })
}

const handleSave = async () => {
  if (props.venueId == null) {
    ElMessage.warning('请先保存场馆基础信息')
    return
  }
  // 清洗：开/关只要其一为空，则视为当日休息（两个都剔除）
  const payload = rows.value.map(r => ({
    dayOfWeek: r.dayOfWeek,
    openTime: r.openTime || undefined,
    closeTime: r.closeTime || undefined
  }))

  // 客户端只校验"开 < 关"；时段全空 = 休息，由后端接受
  for (const r of payload) {
    if (r.openTime && r.closeTime && r.openTime >= r.closeTime) {
      ElMessage.error(`${dayLabel(r.dayOfWeek)}：开门时间必须早于关门时间`)
      return
    }
  }

  saving.value = true
  try {
    const res = await replaceBusinessHours(props.venueId, payload)
    if (res.code === 0) {
      ElMessage.success('营业时间已保存')
      emit('saved')
    } else {
      ElMessage.error(res.message || '保存失败')
    }
  } catch {

  } finally {
    saving.value = false
  }
}

watch(
  () => props.hours,
  (val) => {
    buildRows(val)
  },
  { immediate: true }
)

watch(
  () => props.venueId,
  () => {
    /* venueId 切换不需要重新拉取；父级会重置 hours prop */
    loading.value = false
  }
)
</script>

<style scoped>
.bh-editor {
  padding-top: 4px;
}

.bh-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.bh-hint {
  font-size: 13px;
  color: #6b7280;
}
</style>
