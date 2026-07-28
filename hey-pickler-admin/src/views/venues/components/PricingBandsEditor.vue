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

    <div
      v-if="businessHours.length"
      class="bh-banner"
    >
      📌 营业时间参考：{{ hoursLine }}
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
        label="营业匹配"
        width="110"
      >
        <template #default="{ row }">
          <el-tooltip
            v-if="businessHours.length && isBandDead(row, businessHours)"
            content="该时段不与任何营业时间相交，不会产生可订格子"
            placement="top"
          >
            <el-tag
              type="warning"
              size="small"
            >
              ⚠ 死带
            </el-tag>
          </el-tooltip>
          <span
            v-else
            class="match-dash"
          >—</span>
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

    <div class="preview-section">
      <div class="preview-head">
        <span class="preview-title">👀 价格预览</span>
        <el-radio-group
          v-model="previewDayType"
          size="small"
          @change="loadPreview"
        >
          <el-radio-button label="WEEKDAY">
            工作日
          </el-radio-button>
          <el-radio-button label="WEEKEND">
            周末
          </el-radio-button>
        </el-radio-group>
        <el-button
          size="small"
          plain
          :loading="previewLoading"
          @click="loadPreview"
        >
          刷新
        </el-button>
      </div>
      <div
        v-if="previewLoading"
        class="preview-hint"
      >
        加载中...
      </div>
      <div
        v-else-if="previewSlots.length === 0"
        class="preview-hint"
      >
        当日暂无可订时段（检查营业时间 / 定价带 / 可订窗口）
      </div>
      <div
        v-else
        class="preview-grid"
      >
        <div
          v-for="s in previewSlots"
          :key="s.start"
          class="preview-cell"
          :class="{ taken: !s.available }"
        >
          <span class="pc-time">{{ s.start.slice(11, 16) }}</span>
          <span
            v-if="s.available"
            class="pc-price"
          >¥{{ s.price }}</span>
          <span
            v-else
            class="pc-taken"
          >已占</span>
        </div>
      </div>
      <div
        v-if="previewDate"
        class="preview-meta"
      >
        取自 {{ previewDate }} 的时段 · 预览反映「已保存」的定价带（改完点上方「保存」再「刷新」）
      </div>
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
import { ref, watch, computed } from 'vue'
import { ElMessage } from 'element-plus'
import {
  replacePricingBands,
  getPricingBands,
  copyPricingBands,
  getCourtSlots
} from '@/api/venues'
import type { Court, CourtPricingBand, BusinessHour, Slot } from '@/types'
import {
  formatHoursLine,
  isBandDead,
  nextWeekdayDate,
  nextWeekendDate
} from '@/utils/pricingPreview'

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    /** 当前定价带所属场地 */
    court: Court | null
    /** 当前场馆下全部场地（用于"复制自"下拉） */
    courts: Court[]
    /** 本场馆营业时间(用于上下文展示 + 死带判定) */
    businessHours?: BusinessHour[]
  }>(),
  {
    businessHours: () => []
  }
)

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

// ===== 营业时间上下文 + 价格预览 =====
const hoursLine = computed(() => formatHoursLine(props.businessHours))

const previewDayType = ref<'WEEKDAY' | 'WEEKEND'>('WEEKDAY')
const previewDate = ref('')
const previewSlots = ref<Slot[]>([])
const previewLoading = ref(false)

const loadPreview = async () => {
  if (!props.court?.id) return
  const date =
    previewDayType.value === 'WEEKDAY' ? nextWeekdayDate(new Date()) : nextWeekendDate(new Date())
  previewDate.value = date
  previewLoading.value = true
  try {
    const res = await getCourtSlots(props.court.id, date)
    previewSlots.value = res.code === 0 ? res.data || [] : []
  } catch {
    previewSlots.value = []
  } finally {
    previewLoading.value = false
  }
}

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

const loadBands = async () => {
  if (!props.court) return
  const res = await getPricingBands(props.court.id)
  if (res.code !== 0) {
    ElMessage.error(res.message || '加载定价带失败')
    bands.value = [newBand()]
    return
  }
  // 后端 LocalTime 序列化为 "HH:mm" / "HH:mm:ss"；前端 time-select 步长 30min，统一截断到 HH:mm
  bands.value = (res.data || []).map(b => ({
    id: b.id,
    dayType: b.dayType,
    startTime: (b.startTime || '').slice(0, 5),
    endTime: (b.endTime || '').slice(0, 5),
    price: b.price
  }))
  if (bands.value.length === 0) {
    // 空表给一条默认行，避免空白让用户无起点
    bands.value = [newBand()]
  }
}

const handleOpen = async () => {
  if (!props.court) return
  courtName.value = props.court.name
  copyFrom.value = undefined
  refreshSiblingCourts()
  loading.value = true
  try {
    await loadBands()
  } finally {
    loading.value = false
  }
  // 定价带加载完后,异步拉价格预览(自带 loading 态,不阻塞弹窗展示)
  loadPreview()
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
      // 刷新预览以反映刚保存的定价带
      loadPreview()
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

// 切换编辑的场地时清空预览(下一场地的预览在 handleOpen 里重新拉)
watch(
  () => props.court?.id,
  () => {
    previewSlots.value = []
    previewDate.value = ''
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

/* 营业时间参考条 */
.bh-banner {
  margin-top: 12px;
  padding: 8px 12px;
  background: #f0f9eb;
  border: 1px solid #e1f3d8;
  border-radius: 4px;
  font-size: 12px;
  color: #529b2e;
  line-height: 1.6;
}

.match-dash {
  color: #c0c4cc;
}

/* 价格预览区 */
.preview-section {
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px dashed #e4e7ed;
}

.preview-head {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.preview-title {
  font-size: 13px;
  font-weight: 500;
  color: #1f2937;
}

.preview-hint {
  padding: 16px;
  text-align: center;
  font-size: 13px;
  color: #9ca3af;
}

.preview-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.preview-cell {
  width: 92px;
  padding: 8px 0;
  border-radius: 6px;
  background: #f0f9eb;
  border: 1px solid #e1f3d8;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.preview-cell.taken {
  background: #f4f4f5;
  border-color: #e4e7ed;
}

.pc-time {
  font-size: 12px;
  color: #333;
}

.pc-price {
  margin-top: 4px;
  font-size: 14px;
  font-weight: 600;
  color: #67c23a;
}

.pc-taken {
  margin-top: 4px;
  font-size: 11px;
  color: #c0c4cc;
}

.preview-meta {
  margin-top: 12px;
  font-size: 12px;
  color: #9ca3af;
}
</style>
