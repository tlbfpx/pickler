<template>
  <div class="reg-embed">
    <!-- 筛选栏 -->
    <div class="filter-bar">
      <el-select
        v-model="filterStatus"
        placeholder="报名状态"
        clearable
        style="width: 130px"
        @change="handleFilter"
      >
        <el-option
          label="已报名"
          value="REGISTERED"
        />
        <el-option
          label="已签到"
          value="CHECKED_IN"
        />
      </el-select>
      <el-select
        v-model="filterMatchType"
        placeholder="比赛类型"
        clearable
        style="width: 130px"
        @change="handleFilter"
      >
        <el-option
          label="单打"
          value="SINGLES"
        />
        <el-option
          label="双打"
          value="DOUBLES"
        />
        <el-option
          label="混双"
          value="MIXED"
        />
      </el-select>
      <el-button @click="handleFilter">
        查询
      </el-button>
      <el-button @click="handleReset">
        重置
      </el-button>
    </div>

    <!-- 报名列表 -->
    <el-table
      v-loading="loading"
      :data="registrationList"
      size="small"
      style="width: 100%"
      @selection-change="onSelectionChange"
    >
      <el-table-column
        type="selection"
        width="42"
      />
      <el-table-column
        prop="id"
        label="ID"
        width="60"
      />
      <el-table-column
        label="用户"
        min-width="160"
      >
        <template #default="{ row }">
          <div class="user-cell">
            <el-avatar
              :src="row.avatarUrl || undefined"
              :size="28"
            />
            <span class="user-name">{{ row.nickname || '-' }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column
        prop="city"
        label="城市"
        width="80"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          {{ row.city || '-' }}
        </template>
      </el-table-column>
      <el-table-column
        label="比赛类型"
        width="80"
      >
        <template #default="{ row }">
          <el-tag
            size="small"
            :type="matchTypeTagType(row.matchType)"
          >
            {{ formatMatchType(row.matchType) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column
        label="搭档"
        width="90"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          {{ row.partnerNickname || (row.partnerId ? 'ID:' + row.partnerId : '-') }}
        </template>
      </el-table-column>
      <el-table-column
        label="状态"
        width="80"
      >
        <template #default="{ row }">
          <el-tag
            size="small"
            :type="regStatusTagType(row.status)"
          >
            {{ formatRegStatus(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column
        label="报名时间"
        width="150"
      >
        <template #default="{ row }">
          {{ formatDate(row.createdAt) }}
        </template>
      </el-table-column>
      <el-table-column
        label="操作"
        width="140"
        fixed="right"
      >
        <template #default="{ row }">
          <el-button
            v-if="row.status === 'REGISTERED'"
            link
            type="success"
            size="small"
            @click="handleCheckIn(row)"
          >
            签到
          </el-button>
          <el-button
            v-if="row.status !== 'WITHDRAWN'"
            link
            type="danger"
            size="small"
            @click="handleWithdraw(row)"
          >
            取消报名
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="bulk-bar">
      <span class="muted">已选 {{ selected.length }} 项</span>
      <el-button
        type="success"
        size="small"
        :disabled="!selected.length"
        :loading="bulkLoading"
        @click="handleBulkCheckIn"
      >
        批量签到
      </el-button>
      <el-button
        size="small"
        @click="handleExport"
      >
        导出名单(CSV)
      </el-button>
    </div>

    <Pagination
      v-model:page="pagination.page"
      v-model:size="pagination.size"
      :total="pagination.total"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import Pagination from '@/components/common/Pagination.vue'
import { getEventRegistrations, updateRegistrationStatus } from '@/api/events'
import { formatDate } from '@/utils'
import type { Event, Registration } from '@/types'

const props = defineProps<{ event: Event }>()
const emit = defineEmits<{ changed: [] }>()

const loading = ref(false)
const registrationList = ref<Registration[]>([])
const filterStatus = ref('')
const filterMatchType = ref('')
const pagination = reactive({ page: 1, size: 10, total: 0 })
const selected = ref<Registration[]>([])
const bulkLoading = ref(false)

const onSelectionChange = (rows: Registration[]) => { selected.value = rows }

const handleBulkCheckIn = async () => {
  if (!props.event) return
  const targets = selected.value.filter(r => r.status === 'REGISTERED')
  if (!targets.length) { ElMessage.info('无可签到的已报名项'); return }
  bulkLoading.value = true
  let ok = 0; const failed: string[] = []
  for (const r of targets) {                       // 串行，规避 per-IP 限流
    try {
      const res = await updateRegistrationStatus(props.event!.id, r.id, 'CHECKED_IN')
      if (res.code === 0) ok++; else failed.push(r.nickname || `#${r.id}`)
    } catch { failed.push(r.nickname || `#${r.id}`) }
  }
  bulkLoading.value = false
  ElMessage.success(`签到成功 ${ok} / ${targets.length}` + (failed.length ? `；失败 ${failed.length}` : ''))
  await fetchRegistrations(); emit('changed')
}

const handleExport = async () => {
  if (!props.event) return
  // 拉全量
  const all: Registration[] = []; let page = 1; let total = Infinity
  while (all.length < total) {
    const res = await getEventRegistrations(props.event.id, { page, size: 100 })
    if (res.code !== 0) break
    all.push(...(res.data.list || [])); total = res.data.total || 0; page++
  }
  const header = ['ID', '昵称', '城市', '比赛类型', '搭档', '状态', '报名时间']
  const rows = all.map(r => [r.id, r.nickname || '', r.city || '', r.matchType, r.partnerNickname || (r.partnerId ? 'ID:' + r.partnerId : ''), r.status, r.createdAt])
  const csv = [header, ...rows].map(cols => cols.map(c => `"${String(c).replace(/"/g, '""')}"`).join(',')).join('\n')
  const blob = new Blob(['﻿' + csv], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a'); a.href = url; a.download = `event-${props.event.id}-registrations.csv`; a.click()
  URL.revokeObjectURL(url)
}

async function fetchRegistrations() {
  if (!props.event) return
  loading.value = true
  try {
    const res = await getEventRegistrations(props.event.id, {
      page: pagination.page,
      size: pagination.size,
      status: filterStatus.value || undefined,
      matchType: filterMatchType.value || undefined,
    })
    if (res.code === 0) {
      registrationList.value = res.data.list
      pagination.total = res.data.total
    }
  } catch {

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
      await fetchRegistrations()
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
      await fetchRegistrations()
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

onMounted(fetchRegistrations)
</script>

<style scoped>
.reg-embed {
  padding: 0 4px;
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

.bulk-bar { display: flex; align-items: center; gap: 12px; padding: 8px 0; }
</style>
