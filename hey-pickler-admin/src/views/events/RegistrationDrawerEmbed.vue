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
        v-if="isTeamEvent(event)"
        label="队伍"
        width="240"
      >
        <template #default="{ row }">
          <div class="team-cell">
            <el-tag
              v-if="teamContextByUserId[row.userId]"
              size="small"
              :type="teamContextByUserId[row.userId]!.status === 'CONFIRMED' ? 'success' : 'warning'"
            >
              {{ teamBadgeLabel(row.userId) }}
            </el-tag>
            <span
              v-else
              class="muted"
            >未组队</span>
            <el-button
              v-if="canCreateTeam(event, teamContextByUserId[row.userId])"
              link
              type="primary"
              size="small"
              @click="openCreateDialog(row)"
            >
              建队
            </el-button>
            <template v-else-if="teamContextByUserId[row.userId]">
              <el-button
                v-if="isInvitedPartner(row.userId) && canConfirmTeam(event, teamContextByUserId[row.userId])"
                link
                type="success"
                size="small"
                @click="handleConfirmTeam(row)"
              >
                接受
              </el-button>
              <el-button
                v-if="isInvitedPartner(row.userId) && canDeclineTeam(event, teamContextByUserId[row.userId])"
                link
                type="danger"
                size="small"
                @click="handleDeclineTeam(row)"
              >
                拒绝
              </el-button>
              <el-button
                v-if="canDissolveTeam(event, teamContextByUserId[row.userId])"
                link
                type="danger"
                size="small"
                @click="handleDissolveTeam(row)"
              >
                解散
              </el-button>
            </template>
          </div>
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
          <el-tooltip
            :content="canCheckIn(row) ? '' : '该报名已签到/已退赛，无法重复签到'"
            :disabled="canCheckIn(row)"
            placement="top"
          >
            <el-button
              v-if="row.status === 'REGISTERED'"
              link
              type="success"
              size="small"
              :disabled="!canCheckIn(row) || !canBulkCheckInByEvent(event)"
              @click="handleCheckIn(row)"
            >
              签到
            </el-button>
          </el-tooltip>
          <el-tooltip
            :content="canWithdraw(row) ? '' : '该报名已退赛'"
            :disabled="canWithdraw(row)"
            placement="top"
          >
            <el-button
              v-if="row.status !== 'WITHDRAWN'"
              link
              type="danger"
              size="small"
              :disabled="!canWithdraw(row)"
              @click="handleWithdraw(row)"
            >
              取消报名
            </el-button>
          </el-tooltip>
        </template>
      </el-table-column>
    </el-table>

    <div class="bulk-bar">
      <span class="muted">已选 {{ selected.length }} 项</span>
      <el-tooltip
        :content="bulkCheckInDisabledReason"
        :disabled="!bulkCheckInDisabledReason"
        placement="top"
      >
        <el-button
          type="success"
          size="small"
          :disabled="!selected.length || !canBulkCheckInByEvent(event) || !canBulkCheckIn(selected)"
          :loading="bulkLoading"
          @click="handleBulkCheckIn"
        >
          批量签到
        </el-button>
      </el-tooltip>
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

    <!-- 建队对话框 -->
    <el-dialog
      v-model="createDialogVisible"
      title="代用户建队"
      width="520px"
      append-to-body
      @open="loadPartnerCandidates"
    >
      <el-form
        :model="createForm"
        label-width="100px"
        size="small"
      >
        <el-form-item label="队长">
          <div class="captain-row">
            <el-avatar
              v-if="createFormCaptain"
              :src="createFormCaptain.avatarUrl || undefined"
              :size="24"
            />
            <span v-if="createFormCaptain">
              {{ createFormCaptain.nickname || '用户' }}
              <span class="muted-inline">(ID: {{ createForm.captainUserId }})</span>
            </span>
            <span
              v-else
              class="muted"
            >-</span>
          </div>
        </el-form-item>
        <el-form-item label="搭档">
          <div class="partner-picker">
            <el-input
              v-model="partnerSearch"
              placeholder="搜索昵称筛选下方候选"
              clearable
              size="small"
              class="partner-search"
            />
            <el-table
              v-loading="partnerLoading"
              :data="filteredPartnerCandidates"
              size="small"
              height="220"
              highlight-current-row
              :row-class-name="(args: { row: PartnerCandidate }) => isSelectedPartner(args.row) ? 'partner-row-selected' : ''"
              @row-click="(row: PartnerCandidate) => selectPartnerCandidate(row)"
            >
              <el-table-column
                width="56"
                align="center"
              >
                <template #default="{ row }">
                  <el-avatar
                    :src="row.avatarUrl || undefined"
                    :size="24"
                  />
                </template>
              </el-table-column>
              <el-table-column
                label="昵称"
                min-width="120"
                show-overflow-tooltip
              >
                <template #default="{ row }">
                  {{ row.nickname || '-' }}
                </template>
              </el-table-column>
              <el-table-column
                label="用户ID"
                width="90"
                align="center"
                prop="userId"
              />
              <el-table-column
                label="报名状态"
                width="90"
                align="center"
              >
                <template #default="{ row }">
                  <el-tag
                    size="small"
                    :type="row.status === 'REGISTERED' ? 'primary' : row.status === 'CHECKED_IN' ? 'success' : 'info'"
                  >
                    {{ formatRegStatus(row.status) }}
                  </el-tag>
                </template>
              </el-table-column>
            </el-table>
            <div
              v-if="!partnerLoading && !filteredPartnerCandidates.length"
              class="partner-empty muted"
            >
              该赛事暂无其他报名用户
            </div>
          </div>
        </el-form-item>
        <el-form-item label="或手动输入">
          <el-input
            v-model.number="createForm.partnerUserId"
            placeholder="候选名单外的用户可手动输入 ID"
            clearable
          />
        </el-form-item>
        <el-form-item label="队伍名 (可选)">
          <el-input
            v-model="createForm.name"
            placeholder="可空"
            maxlength="32"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="createSubmitting"
          @click="handleCreateTeam"
        >
          创建
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import Pagination from '@/components/common/Pagination.vue'
import { getEventRegistrations, updateRegistrationStatus } from '@/api/events'
import { listEventTeams, createTeam, confirmTeam, declineTeam, dissolveTeam, type TeamVO } from '@/api/teams'
import { formatDate } from '@/utils'
import type { Event, Registration } from '@/types'
import {
  canCheckIn,
  canWithdraw,
  canBulkCheckIn,
  canBulkCheckInByEvent,
  canCreateTeam,
  canConfirmTeam,
  canDeclineTeam,
  canDissolveTeam,
  isTeamEvent
} from '@/constants/eventGuards'

const props = defineProps<{ event: Event }>()
const emit = defineEmits<{ changed: [] }>()

const loading = ref(false)
const registrationList = ref<Registration[]>([])
const filterStatus = ref('')
const filterMatchType = ref('')
const pagination = reactive({ page: 1, size: 10, total: 0 })
const selected = ref<Registration[]>([])
const bulkLoading = ref(false)

// ---- teams state ----
const teams = ref<TeamVO[]>([])
const teamContextByUserId = ref<Record<number, TeamVO | null>>({})
const createDialogVisible = ref(false)
const createSubmitting = ref(false)
const createForm = reactive({ captainUserId: 0, partnerUserId: 0, name: '' })

// Partner candidate picker state
type PartnerCandidate = Pick<Registration, 'userId' | 'nickname' | 'avatarUrl' | 'status'>
const partnerCandidates = ref<PartnerCandidate[]>([])
const partnerLoading = ref(false)
const partnerSearch = ref('')

const createFormCaptain = computed<Registration | undefined>(() =>
  registrationList.value.find(r => r.userId === createForm.captainUserId)
)

const filteredPartnerCandidates = computed<PartnerCandidate[]>(() => {
  const q = partnerSearch.value.trim().toLowerCase()
  return partnerCandidates.value
    .filter(c => c.userId !== createForm.captainUserId)
    .filter(c => {
      if (!q) return true
      const nick = (c.nickname || '').toLowerCase()
      return nick.includes(q) || String(c.userId).includes(q)
    })
})

const isSelectedPartner = (row: PartnerCandidate) =>
  createForm.partnerUserId === row.userId

const selectPartnerCandidate = (row: PartnerCandidate) => {
  createForm.partnerUserId = row.userId
}

async function loadPartnerCandidates() {
  if (!props.event) return
  partnerLoading.value = true
  try {
    const res = await getEventRegistrations(props.event.id, { page: 1, size: 100 })
    if (res.code === 0) {
      partnerCandidates.value = (res.data.list || []).map(r => ({
        userId: r.userId,
        nickname: r.nickname,
        avatarUrl: r.avatarUrl,
        status: r.status
      }))
    } else {
      partnerCandidates.value = []
    }
  } catch {
    partnerCandidates.value = []
  } finally {
    partnerLoading.value = false
  }
}

const onSelectionChange = (rows: Registration[]) => { selected.value = rows }

const bulkCheckInDisabledReason = computed(() => {
  if (selected.value.length === 0) return '请先选择要签到的报名项'
  if (!canBulkCheckInByEvent(props.event)) return '仅 OPEN/FULL 阶段支持签到'
  if (!canBulkCheckIn(selected.value)) return '所选项中存在非「已报名」状态，无法签到'
  return ''
})

function rebuildTeamContext() {
  const map: Record<number, TeamVO | null> = {}
  for (const r of registrationList.value) map[r.userId] = null
  for (const t of teams.value) {
    if (map[t.member1UserId] !== undefined) map[t.member1UserId] = t
    if (map[t.member2UserId] !== undefined) map[t.member2UserId] = t
  }
  teamContextByUserId.value = map
}

function isInvitedPartner(userId: number) {
  const t = teamContextByUserId.value[userId]
  return !!t && t.member2UserId === userId && t.status === 'PENDING'
}

function teamBadgeLabel(userId: number) {
  const t = teamContextByUserId.value[userId]
  if (!t) return ''
  if (t.status === 'CONFIRMED') {
    const partnerName = t.member1UserId === userId ? t.member2Name : t.member1Name
    return `已组队 (与 ${partnerName || '队友'})`
  }
  // PENDING
  if (t.member1UserId === userId) return 'PENDING'
  return 'PENDING (你被邀请)'
}

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
  await fetchRegistrations(); await fetchTeams(); emit('changed')
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
      rebuildTeamContext()
    }
  } catch {

  }
  loading.value = false
}

async function fetchTeams() {
  if (!props.event) return
  if (!isTeamEvent(props.event)) {
    teams.value = []
    rebuildTeamContext()
    return
  }
  try {
    const res = await listEventTeams(props.event.id)
    if (res.code === 0) {
      teams.value = res.data || []
      rebuildTeamContext()
    }
  } catch {
    // request interceptor already surfaces errors
  }
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
      await fetchTeams()
      emit('changed')
    } else {
      ElMessage.error(res.message)
    }
  } catch { /* cancelled */ }
}

// ---- team actions ----

function openCreateDialog(row: Registration) {
  createForm.captainUserId = row.userId
  createForm.partnerUserId = 0
  createForm.name = ''
  partnerSearch.value = ''
  createDialogVisible.value = true
}

async function handleCreateTeam() {
  if (!props.event) return
  if (!createForm.captainUserId || !createForm.partnerUserId) {
    ElMessage.warning('队长与搭档不能为空')
    return
  }
  if (createForm.captainUserId === createForm.partnerUserId) {
    ElMessage.warning('不能与自己组队')
    return
  }
  createSubmitting.value = true
  try {
    const res = await createTeam(props.event.id, {
      captainUserId: createForm.captainUserId,
      partnerUserId: createForm.partnerUserId,
      name: createForm.name || undefined
    })
    if (res.code === 0) {
      ElMessage.success('建队成功')
      createDialogVisible.value = false
      await fetchTeams()
      await fetchRegistrations()
      emit('changed')
    } else {
      ElMessage.error(res.message || '建队失败')
    }
  } finally {
    createSubmitting.value = false
  }
}

async function handleConfirmTeam(row: Registration) {
  if (!props.event) return
  const t = teamContextByUserId.value[row.userId]
  if (!t) return
  try {
    const res = await confirmTeam(t.id, { userId: row.userId })
    if (res.code === 0) {
      ElMessage.success('已接受邀请')
      await fetchTeams()
      await fetchRegistrations()
      emit('changed')
    } else {
      ElMessage.error(res.message || '确认失败')
    }
  } catch { /* swallowed */ }
}

async function handleDeclineTeam(row: Registration) {
  if (!props.event) return
  const t = teamContextByUserId.value[row.userId]
  if (!t) return
  try {
    await ElMessageBox.confirm(`确认拒绝该队伍的邀请？`, '拒绝邀请', { type: 'warning' })
    const res = await declineTeam(t.id, { userId: row.userId })
    if (res.code === 0) {
      ElMessage.success('已拒绝邀请')
      await fetchTeams()
      await fetchRegistrations()
      emit('changed')
    } else {
      ElMessage.error(res.message || '操作失败')
    }
  } catch { /* cancelled */ }
}

async function handleDissolveTeam(row: Registration) {
  if (!props.event) return
  const t = teamContextByUserId.value[row.userId]
  if (!t) return
  try {
    await ElMessageBox.confirm(
      `确认解散该队伍？解散后该队伍两名成员的报名都将被撤回。`,
      '解散队伍',
      { type: 'warning' }
    )
    const res = await dissolveTeam(t.id)
    if (res.code === 0) {
      ElMessage.success('队伍已解散')
      await fetchTeams()
      await fetchRegistrations()
      emit('changed')
    } else {
      ElMessage.error(res.message || '解散失败')
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

onMounted(async () => {
  await fetchRegistrations()
  await fetchTeams()
})

// Refetch teams when the parent swaps in a different event.
watch(() => props.event?.id, async (id, prev) => {
  if (id === prev) return
  pagination.page = 1
  teams.value = []
  teamContextByUserId.value = {}
  await fetchRegistrations()
  await fetchTeams()
})
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

.team-cell {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.bulk-bar { display: flex; align-items: center; gap: 12px; padding: 8px 0; }

/* Partner picker */
.captain-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #374151;
}
.muted-inline { color: #9ca3af; font-size: 12px; margin-left: 4px; }
.partner-picker {
  width: 100%;
}
.partner-search { margin-bottom: 8px; }
.partner-empty {
  padding: 12px;
  text-align: center;
  font-size: 12px;
  border: 1px dashed #e5e7eb;
  border-radius: 4px;
}
:deep(.partner-row-selected) {
  background-color: #ecf5ff !important;
}
</style>
