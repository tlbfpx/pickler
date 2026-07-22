<template>
  <el-dialog
    :model-value="modelValue"
    title="定价带管理"
    width="820px"
    @update:model-value="$emit('update:modelValue', $event)"
    @open="handleOpen"
  >
    <div
      v-loading="loading"
      class="bands-toolbar"
    >
      <span class="bands-title">场地：{{ courtName }}</span>
      <div class="copy-bar">
        <el-select
          v-model="copyFrom"
          placeholder="选择源场地（复制定价带）"
          filterable
          clearable
          style="width: 280px"
        >
          <el-option
            v-for="c in siblingCourts"
            :key="c.id"
            :label="c.name"
            :value="c.id"
          />
        </el-select>
        <el-button
          type="warning"
          plain
          :disabled="!copyFrom"
          @click="handleCopy"
        >
          复制
        </el-button>
        <el-button
          type="primary"
          :loading="saving"
          @click="handleSave"
        >
          保存
        </el-button>
      </div>
    </div>

    <el-table
      :data="bands"
      style="width: 100%; margin-top: 12px"
    >
      <el-table-column
        label="时段类型"
        width="140"
      >
        <template #default="{ row }">
          <el-select
            v-model="row.dayType"
            style="width: 100%"
          >
            <el-option
              label="工作日"
              value="WEEKDAY"
            />
            <el-option
              label="周末"
              value="WEEKEND"
            />
            <el-option
              label="全部"
              value="ALL"
            />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column
        label="开始"
        width="140"
      >
        <template #default="{ row }">
          <el-time-select
            v-model="row.startTime"
            :max-time="row.endTime"
            placeholder="开始"
            start="00:00"
            step="00:30"
            end="23:30"
          />
        </template>
      </el-table-column>
      <el-table-column
        label="结束"
        width="140"
      >
        <template #default="{ row }">
          <el-time-select
            v-model="row.endTime"
            :min-time="row.startTime"
            placeholder="结束"
            start="00:00"
            step="00:30"
            end="23:59"
          />
        </template>
      </el-table-column>
      <el-table-column
        label="价格(元)"
        width="140"
      >
        <template #default="{ row }">
          <el-input-number
            v-model="row.price"
            :min="0"
            :precision="2"
            :step="10"
            style="width: 100%"
          />
        </template>
      </el-table-column>
      <el-table-column
        label="操作"
        width="100"
      >
        <template #default="{ $index }">
          <el-button
            type="danger"
            size="small"
            link
            @click="removeBand($index)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
      <template #empty>
        <div class="empty-hint">
          暂无定价带，点击下方按钮新增
        </div>
      </template>
    </el-table>

    <div class="add-row">
      <el-button
        type="primary"
        plain
        @click="addBand"
      >
        <el-icon><Plus /></el-icon>
        新增定价带
      </el-button>
    </div>

    <div class="hint">
      后端会校验同一 dayType 下的时段是否重叠；保存失败会以提示形式给出原因。
    </div>

    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">
        关闭
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  replacePricingBands,
  copyPricingBands
} from '@/api/venues'
import type { Court, CourtPricingBand } from '@/types'

const props = defineProps<{
  modelValue: boolean
  /** 当前定价带所属场地 */
  court: Court | null
  /** 当前场馆下全部场地（用于"复制自"下拉） */
  courts: Court[]
}>()

defineEmits<{
  'update:modelValue': [value: boolean]
}>()

const loading = ref(false)
const saving = ref(false)
const bands = ref<CourtPricingBand[]>([])
const copyFrom = ref<number | undefined>(undefined)

const courtName = ref('')

/** 同场馆下的其它场地（不含自己）作为复制源 */
const siblingCourts = ref<Court[]>([])

const newBand = (): CourtPricingBand => ({
  dayType: 'WEEKDAY',
  startTime: '09:00',
  endTime: '12:00',
  price: 0
})

const addBand = () => {
  bands.value.push(newBand())
}

const removeBand = (index: number) => {
  bands.value.splice(index, 1)
}

const refreshSiblingCourts = () => {
  if (!props.court) {
    siblingCourts.value = []
    return
  }
  // props.courts 已是同场馆列表；排除当前 court
  siblingCourts.value = props.courts.filter(c => c.id !== props.court?.id)
}

const initBands = () => {
  // 后端暂无 admin 侧 GET pricing-bands 端点（仅 PUT/POST copy）。
  // 打开编辑器即按空表 + 一条默认行初始化，由用户录入或从其它场地复制。
  bands.value = [newBand()]
}

const handleOpen = async () => {
  if (!props.court) return
  courtName.value = props.court.name
  copyFrom.value = undefined
  refreshSiblingCourts()
  loading.value = true
  try {
    initBands()
  } finally {
    loading.value = false
  }
}

const handleCopy = async () => {
  if (!props.court || !copyFrom.value) return
  loading.value = true
  try {
    const res = await copyPricingBands(props.court.id, copyFrom.value)
    if (res.code === 0) {
      ElMessage.success('复制成功，关闭并重新打开编辑器可查看结果')
    } else {
      ElMessage.error(res.message || '复制失败')
    }
  } catch {

  } finally {
    loading.value = false
  }
}

const handleSave = async () => {
  if (!props.court) return
  if (bands.value.length === 0) {
    ElMessage.warning('至少保留一条定价带')
    return
  }
  // 客户端只做空值/顺序校验；时段重叠校验在后端 PARAM_ERROR
  for (const b of bands.value) {
    if (!b.startTime || !b.endTime) {
      ElMessage.warning('请完整填写开始/结束时间')
      return
    }
    if (b.startTime >= b.endTime) {
      ElMessage.warning('开始时间必须早于结束时间')
      return
    }
  }
  saving.value = true
  try {
    const res = await replacePricingBands(props.court.id, bands.value)
    if (res.code === 0) {
      ElMessage.success('保存成功')
    } else {
      ElMessage.error(res.message || '保存失败')
    }
  } catch {

  } finally {
    saving.value = false
  }
}

watch(
  () => props.courts,
  () => {
    if (props.modelValue) refreshSiblingCourts()
  }
)
</script>

<style scoped>
.bands-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}

.bands-title {
  font-weight: 500;
  color: #1f2937;
}

.copy-bar {
  display: flex;
  gap: 8px;
  align-items: center;
}

.add-row {
  margin-top: 12px;
}

.empty-hint {
  padding: 16px;
  color: #6b7280;
  font-size: 13px;
}

.hint {
  margin-top: 12px;
  font-size: 12px;
  color: #9ca3af;
}
</style>
