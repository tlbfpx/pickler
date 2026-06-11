<template>
  <el-drawer
    v-model="visible"
    :title="`报名管理 - ${event?.title || ''}`"
    size="780px"
    destroy-on-close
  >
    <div v-loading="loading" class="reg-content">
      <!-- 赛事摘要 -->
      <div class="event-summary" v-if="event">
        <div class="summary-row">
          <el-tag :color="getEventTypeColor(event.type)" effect="dark" size="small" style="border: none">
            {{ formatEventType(event.type) }}
          </el-tag>
          <el-tag :color="getEventStatusColor(event.status)" effect="dark" size="small" style="border: none; margin-left: 8px">
            {{ formatEventStatus(event.status) }}
          </el-tag>
          <span class="summary-info">{{ event.location || '-' }} · {{ formatDate(event.eventTime!) }}</span>
        </div>
        <div class="capacity-bar">
          <span class="capacity-text">
            报名人数：{{ event.currentParticipants }} / {{ event.maxParticipants ?? '∞' }}
          </span>
          <el-progress
            :percentage="event.maxParticipants ? Math.round(event.currentParticipants / event.maxParticipants * 100) : 0"
            :stroke-width="10"
            :color="event.currentParticipants >= (event.maxParticipants ?? Infinity) ? '#F59E0B' : '#10B981'"
            style="flex: 1; margin-left: 12px"
          />
        </div>
      </div>

      <!-- 筛选栏 -->
      <div class="filter-bar">
        <el-select v-model="filterStatus" placeholder="报名状态" clearable style="width: 130px" @change="handleFilter">
          <el-option label="已报名" value="REGISTERED" />
          <el-option label="已签到" value="CHECKED_IN" />
        </el-select>
        <el-select v-model="filterMatchType" placeholder="比赛类型" clearable style="width: 130px" @change="handleFilter">
          <el-option label="单打" value="SINGLES" />
          <el-option label="双打" value="DOUBLES" />
          <el-option label="混双" value="MIXED" />
        </el-select>
        <el-button @click="handleFilter">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
      </div>

      <!-- 报名列表 -->
      <el-table :data="registrationList" size="small" style="width: 100%">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column label="用户" min-width="160">
          <template #default="{ row }">
            <div class="user-cell">
              <el-avatar :src="row.avatarUrl || undefined" :size="28" />
              <span class="user-name">{{ row.nickname || '-' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="city" label="城市" width="80" show-overflow-tooltip>
          <template #default="{ row }">{{ row.city || '-' }}</template>
        </el-table-column>
        <el-table-column label="比赛类型" width="80">
          <template #default="{ row }">
            <el-tag size="small" :type="matchTypeTagType(row.matchType)">
              {{ formatMatchType(row.matchType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="搭档" width="90" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.partnerNickname || (row.partnerId ? 'ID:' + row.partnerId : '-') }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag size="small" :type="regStatusTagType(row.status)">
              {{ formatRegStatus(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="报名时间" width="150">
          <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'REGISTERED'"
              link type="success" size="small"
              @click="handleCheckIn(row)"
            >签到</el-button>
            <el-button
              v-if="row.status !== 'WITHDRAWN'"
              link type="danger" size="small"
              @click="handleWithdraw(row)"
            >取消报名</el-button>
          </template>
        </el-table-column>
      </el-table>

      <Pagination
        v-model:page="pagination.page"
        v-model:size="pagination.size"
        :total="pagination.total"
      />
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { ref, reactive, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import Pagination from '@/components/common/Pagination.vue'
import { getEventRegistrations, updateRegistrationStatus } from '@/api/events'
import { formatDate, formatEventType, formatEventStatus, getEventTypeColor, getEventStatusColor } from '@/utils'
import type { Event, Registration } from '@/types'

const props = defineProps<{
  modelValue: boolean
  event: Event | null
}>()

const emit = defineEmits<{
  'update:modelValue': [val: boolean]
  'changed': []
}>()

const visible = ref(false)
const loading = ref(false)
const registrationList = ref<Registration[]>([])
const filterStatus = ref('')
const filterMatchType = ref('')
const pagination = reactive({ page: 1, size: 10, total: 0 })

watch(() => props.modelValue, (val) => { visible.value = val })
watch(visible, (val) => { emit('update:modelValue', val) })
watch(visible, (val) => {
  if (val && props.event) {
    filterStatus.value = ''
    filterMatchType.value = ''
    pagination.page = 1
    fetchRegistrations()
  }
})

async function fetchRegistrations() {
  if (!props.event) return
  loading.value = true
  try {
    const res = await getEventRegistrations(props.event.id, {
      page: pagination.page,
      size: pagination.size,
      status: filterStatus.value || undefined,
      matchType: filterMatchType.value || undefined,
    } as any)
    if (res.code === 0) {
      registrationList.value = res.data.list
      pagination.total = res.data.total
    }
  } catch {
    ElMessage.error('获取报名列表失败')
  }
  loading.value = false
}

function handleFilter() {
  pagination.page = 1
  fetchRegistrations()
}

function handleReset() {
  filterStatus.value = ''
  filterMatchType.value = ''
  pagination.page = 1
  fetchRegistrations()
}

async function handleCheckIn(row: Registration) {
  if (!props.event) return
  try {
    await ElMessageBox.confirm(`确认为 ${row.nickname || '用户'} 签到？`, '签到确认', { type: 'info' })
    const res = await updateRegistrationStatus(props.event.id, row.id, 'CHECKED_IN')
    if (res.code === 0) {
      ElMessage.success('签到成功')
      fetchRegistrations()
      emit('changed')
    } else {
      ElMessage.error(res.message)
    }
  } catch { /* cancelled */ }
}

async function handleWithdraw(row: Registration) {
  if (!props.event) return
  try {
    await ElMessageBox.confirm(`确认取消 ${row.nickname || '用户'} 的报名？`, '取消报名', { type: 'warning' })
    const res = await updateRegistrationStatus(props.event.id, row.id, 'WITHDRAWN')
    if (res.code === 0) {
      ElMessage.success('已取消报名')
      fetchRegistrations()
      emit('changed')
    } else {
      ElMessage.error(res.message)
    }
  } catch { /* cancelled */ }
}

function matchTypeTagType(type: string) {
  const map: Record<string, string> = { SINGLES: '', DOUBLES: 'warning', MIXED: 'danger' }
  return map[type] || 'info'
}

function formatMatchType(type: string) {
  const map: Record<string, string> = { SINGLES: '单打', DOUBLES: '双打', MIXED: '混双' }
  return map[type] || type
}

function regStatusTagType(status: string) {
  const map: Record<string, string> = { REGISTERED: 'primary', CHECKED_IN: 'success', WITHDRAWN: 'info' }
  return map[status] || 'info'
}

function formatRegStatus(status: string) {
  const map: Record<string, string> = { REGISTERED: '已报名', CHECKED_IN: '已签到', WITHDRAWN: '已退赛' }
  return map[status] || status
}
</script>

<style scoped>
.reg-content {
  padding: 0 4px;
}

.event-summary {
  background: #f9fafb;
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 16px;
}

.summary-row {
  display: flex;
  align-items: center;
  margin-bottom: 12px;
}

.summary-info {
  margin-left: 12px;
  font-size: 13px;
  color: #6b7280;
}

.capacity-bar {
  display: flex;
  align-items: center;
}

.capacity-text {
  font-size: 13px;
  color: #374151;
  white-space: nowrap;
}

.filter-bar {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}

.user-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.user-name {
  font-size: 13px;
  color: #374151;
}
</style>
