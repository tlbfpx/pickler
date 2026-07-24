<template>
  <div
    v-loading="loading"
    class="courts-editor"
  >
    <div class="courts-toolbar">
      <div class="courts-hint">
        场馆下的物理场地；每个场地可配置独立的时段定价带。
      </div>
      <el-button
        type="primary"
        plain
        @click="handleAdd"
      >
        <el-icon><Plus /></el-icon>
        新增场地
      </el-button>
    </div>

    <el-table
      :data="rows"
      style="width: 100%; margin-top: 12px"
    >
      <el-table-column
        label="名称"
        min-width="180"
      >
        <template #default="{ row }">
          <el-input
            v-model="row.name"
            placeholder="例如：1 号场"
            maxlength="64"
          />
        </template>
      </el-table-column>
      <el-table-column
        label="类型"
        width="140"
      >
        <template #default="{ row }">
          <el-select
            v-model="row.courtType"
            style="width: 100%"
          >
            <el-option
              label="室内"
              value="INDOOR"
            />
            <el-option
              label="室外"
              value="OUTDOOR"
            />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column
        label="单场时长(分钟)"
        width="160"
      >
        <template #default="{ row }">
          <el-input-number
            v-model="row.slotMinutes"
            :min="15"
            :max="240"
            :step="15"
            style="width: 100%"
          />
        </template>
      </el-table-column>
      <el-table-column
        label="状态"
        width="140"
      >
        <template #default="{ row }">
          <el-select
            v-model="row.status"
            style="width: 100%"
          >
            <el-option
              label="开放"
              value="OPEN"
            />
            <el-option
              label="关闭"
              value="CLOSED"
            />
            <el-option
              label="维护"
              value="MAINTENANCE"
            />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column
        label="排序"
        width="110"
      >
        <template #default="{ row }">
          <el-input-number
            v-model="row.sortOrder"
            :min="0"
            style="width: 100%"
          />
        </template>
      </el-table-column>
      <el-table-column
        label="操作"
        width="220"
        fixed="right"
      >
        <template #default="{ row, $index }">
          <el-button
            type="primary"
            size="small"
            link
            @click="handleSave(row)"
          >
            {{ row.id ? '保存' : '创建' }}
          </el-button>
          <el-button
            type="warning"
            size="small"
            link
            :disabled="!row.id"
            @click="openPricing(row)"
          >
            定价
          </el-button>
          <el-button
            type="danger"
            size="small"
            link
            :disabled="!row.id"
            @click="handleDelete(row, $index)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
      <template #empty>
        <div class="empty-hint">
          暂无场地，点击右上角新增
        </div>
      </template>
    </el-table>

    <PricingBandsEditor
      v-model="pricingOpen"
      :court="pricingCourt"
      :courts="rows"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createCourt,
  updateCourt,
  deleteCourt
} from '@/api/venues'
import type { Court } from '@/types'
import PricingBandsEditor from './PricingBandsEditor.vue'

const props = defineProps<{
  venueId: number | null
  courts: Court[]
}>()

const emit = defineEmits<{
  changed: []
}>()

const loading = ref(false)
const rows = ref<Court[]>([])
const pricingOpen = ref(false)
const pricingCourt = ref<Court | null>(null)

interface CourtRow {
  id?: number
  venueId?: number
  name: string
  courtType: 'INDOOR' | 'OUTDOOR'
  slotMinutes: number
  status: 'OPEN' | 'CLOSED' | 'MAINTENANCE'
  sortOrder: number
}

const toRow = (c: Court): Court => ({ ...c })

const buildRows = (source: Court[]) => {
  rows.value = (source || [])
    .slice()
    .sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0))
    .map(toRow)
}

const handleAdd = () => {
  rows.value.push({
    id: 0 as unknown as number,
    venueId: props.venueId ?? 0,
    name: '',
    courtType: 'INDOOR',
    slotMinutes: 60,
    status: 'OPEN',
    sortOrder: rows.value.length
  } as Court)
}

const validateRow = (row: CourtRow): string | null => {
  if (!row.name || !row.name.trim()) return '请输入场地名称'
  if (!row.courtType) return '请选择场地类型'
  if (!row.status) return '请选择场地状态'
  if (!row.slotMinutes || row.slotMinutes < 15 || row.slotMinutes > 240) {
    return '单场时长需在 15..240 分钟之间'
  }
  return null
}

const handleSave = async (row: Court & { id?: number }) => {
  if (props.venueId == null) {
    ElMessage.warning('请先保存场馆基础信息')
    return
  }
  const err = validateRow(row)
  if (err) {
    ElMessage.warning(err)
    return
  }
  loading.value = true
  try {
    const payload = {
      name: row.name.trim(),
      courtType: row.courtType,
      slotMinutes: row.slotMinutes,
      status: row.status,
      sortOrder: row.sortOrder ?? 0
    }
    if (row.id) {
      const res = await updateCourt(row.id, { ...payload, id: row.id, venueId: props.venueId })
      if (res.code === 0) {
        ElMessage.success('已更新')
        emit('changed')
      } else {
        ElMessage.error(res.message || '更新失败')
      }
    } else {
      const res = await createCourt({ ...payload, venueId: props.venueId })
      if (res.code === 0) {
        ElMessage.success('已创建')
        emit('changed')
      } else {
        ElMessage.error(res.message || '创建失败')
      }
    }
  } catch {

  } finally {
    loading.value = false
  }
}

const openPricing = (row: Court) => {
  pricingCourt.value = row
  pricingOpen.value = true
}

const handleDelete = async (row: Court & { id?: number }, index: number) => {
  try {
    await ElMessageBox.confirm(`确定删除场地「${row.name || '未命名'}」？关联的时段定价带会一并释放。`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
    if (!row.id) {
      rows.value.splice(index, 1)
      return
    }
    loading.value = true
    const res = await deleteCourt(row.id)
    if (res.code === 0) {
      ElMessage.success('已删除')
      emit('changed')
    } else {
      ElMessage.error(res.message || '删除失败')
    }
  } catch {

  } finally {
    loading.value = false
  }
}

watch(
  () => props.courts,
  (val) => {
    buildRows(val)
  },
  { immediate: true }
)
</script>

<style scoped>
.courts-editor {
  padding-top: 4px;
}

.courts-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.courts-hint {
  font-size: 13px;
  color: #6b7280;
}

.empty-hint {
  padding: 16px;
  color: #6b7280;
  font-size: 13px;
}
</style>
