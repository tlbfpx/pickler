<template>
  <el-drawer
    :model-value="modelValue"
    title="积分明细"
    size="880px"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <div
      v-if="userId"
      v-loading="loading"
    >
      <div class="ledger-user">
        {{ userName || ('用户 ' + userId) }}
        <span class="ledger-uid">ID: {{ userId }}</span>
      </div>

      <el-table
        :data="records"
        size="small"
        stripe
      >
        <el-table-column
          label="时间"
          width="120"
        >
          <template #default="{ row }">
            {{ fmtTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column
          label="来源"
          width="100"
        >
          <template #default="{ row }">
            <DictTag
              dict-code="point_source"
              :item-key="row.source"
              size="small"
            />
          </template>
        </el-table-column>
        <el-table-column
          label="关联赛事"
          min-width="140"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            {{ row.eventTitle || '—' }}
          </template>
        </el-table-column>
        <el-table-column
          label="积分"
          width="80"
          align="right"
        >
          <template #default="{ row }">
            <span :class="row.points >= 0 ? 'pts-plus' : 'pts-minus'">
              {{ row.points >= 0 ? '+' : '' }}{{ row.points }}
            </span>
          </template>
        </el-table-column>
        <el-table-column
          label="原因"
          min-width="150"
          show-overflow-tooltip
          prop="reason"
        />
        <el-table-column
          label="操作人"
          width="90"
        >
          <template #default="{ row }">
            {{ row.operatorName || '系统' }}
          </template>
        </el-table-column>
        <el-table-column
          label="操作"
          width="70"
          align="center"
        >
          <template #default="{ row }">
            <el-button
              v-if="canRevert(row.source)"
              link
              type="danger"
              size="small"
              @click="handleRevert(row)"
            >
              撤销
            </el-button>
            <span
              v-else
              class="muted"
            >—</span>
          </template>
        </el-table-column>
      </el-table>

      <Pagination
        v-model:page="page"
        v-model:size="size"
        :total="total"
        @update:page="fetch"
        @update:size="fetch"
      />
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getUserPoints, type PointRecord } from '@/api/users'
import { revertPointRecord } from '@/api/rankings'
import Pagination from '@/components/common/Pagination.vue'
import DictTag from '@/components/common/DictTag.vue'

const props = defineProps<{
  modelValue: boolean
  userId: number
  userName?: string | null
  type: 'STAR' | 'PARTY'
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  reverted: []
}>()

const loading = ref(false)
const records = ref<PointRecord[]>([])
const page = ref(1)
const size = ref(20)
const total = ref(0)

const fetch = async () => {
  if (!props.userId) return
  loading.value = true
  try {
    const res = await getUserPoints(props.userId, {
      type: props.type,
      page: page.value,
      size: size.value
    })
    if (res.code === 0) {
      records.value = res.data.list || []
      total.value = res.data.total || 0
    } else {
      ElMessage.error(res.message || '获取积分明细失败')
    }
  } catch { /* ignore */ } finally {
    loading.value = false
  }
}

// 仅 MANUAL/ADJUST 可撤销；PLACEMENT 等系统来源不可撤销
const canRevert = (source: string) => source === 'MANUAL' || source === 'ADJUST'

const handleRevert = async (row: PointRecord) => {
  try {
    await ElMessageBox.confirm(
      `确认撤销该积分记录？将写入一条等额负分的纠错记录（来源：系统纠错）。\n原因：${row.reason || '—'}`,
      '撤销确认',
      { type: 'warning', confirmButtonText: '撤销', cancelButtonText: '取消' }
    )
  } catch {
    return // 用户取消
  }
  try {
    const res = await revertPointRecord(row.id)
    if (res.code === 0) {
      ElMessage.success('已撤销')
      await fetch()
      emit('reverted')
    } else {
      ElMessage.error(res.message || '撤销失败')
    }
  } catch { /* ignore */ }
}

const fmtTime = (d: string | null) => {
  if (!d) return '-'
  const dt = new Date(d)
  if (isNaN(dt.getTime())) return d
  const mm = String(dt.getMonth() + 1).padStart(2, '0')
  const dd = String(dt.getDate()).padStart(2, '0')
  const hh = String(dt.getHours()).padStart(2, '0')
  const mi = String(dt.getMinutes()).padStart(2, '0')
  return `${mm}-${dd} ${hh}:${mi}`
}

watch(() => props.modelValue, (val) => {
  if (val) {
    page.value = 1
    fetch()
  }
})
</script>

<style scoped>
.ledger-user {
  font-size: 14px;
  font-weight: 600;
  color: #1f2937;
  margin-bottom: 12px;
}

.ledger-uid {
  font-size: 12px;
  color: #9ca3af;
  font-weight: 400;
  margin-left: 8px;
}

.pts-plus {
  color: #43e97b;
  font-weight: 600;
}

.pts-minus {
  color: #f5576c;
  font-weight: 600;
}

.muted {
  color: #d1d5db;
}
</style>
