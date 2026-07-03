<template>
  <div class="issuance-panel">
    <div class="panel-header">
      <h3>发分</h3>
    </div>
    <el-alert
      v-if="event.status === 'COMPLETED'"
      type="success"
      :closable="false"
      title="赛事已结束，名次积分已发放"
    />
    <template v-if="event.status === 'COMPLETED'">
      <div
        v-loading="placementsLoading"
        class="placement-detail"
      >
        <el-table
          v-if="placements.length > 0"
          :data="placements"
          stripe
          size="small"
          class="placement-table"
        >
          <el-table-column
            label="#"
            prop="rank"
            width="64"
            align="center"
          />
          <el-table-column
            label="选手"
            min-width="160"
          >
            <template #default="{ row }">
              {{ row.nickname || `用户 ${row.userId}` }}
            </template>
          </el-table-column>
          <el-table-column
            label="积分"
            width="120"
            align="right"
          >
            <template #default="{ row }">
              <span class="points-bold">+{{ row.points }}</span>
            </template>
          </el-table-column>
          <el-table-column
            label="原因"
            min-width="240"
          >
            <template #default="{ row }">
              <el-tooltip
                :content="row.reason"
                placement="top"
                :show-after="200"
              >
                <span class="reason-text">{{ row.reason }}</span>
              </el-tooltip>
            </template>
          </el-table-column>
          <el-table-column
            label="时间"
            width="180"
          >
            <template #default="{ row }">
              {{ formatDate(row.createdAt) }}
            </template>
          </el-table-column>
        </el-table>
        <div
          v-else-if="!placementsLoading"
          class="empty-state"
        >
          暂无发分记录
        </div>
      </div>
    </template>
    <template v-else>
      <el-button
        type="primary"
        @click="openPlacement"
      >
        自动积分配置
      </el-button>
      <el-button
        type="success"
        :loading="completing"
        :disabled="event.status !== 'IN_PROGRESS'"
        @click="handleComplete"
      >
        完成赛事并发分
      </el-button>
      <div class="hint">
        完成后将按自动积分配置发放名次积分（source=PLACEMENT）。需所有比赛已完成，否则会提示未完成场次。
      </div>
    </template>
    <PlacementPointsDialog
      v-model="placementOpen"
      :event="event"
      @saved="emit('changed')"
    />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { completeEvent, getEventMatches } from '@/api/matches'
import { getEventPlacements, type PlacementDetail } from '@/api/events'
import PlacementPointsDialog from './PlacementPointsDialog.vue'
import type { Event } from '@/types'

const props = defineProps<{ event: Event }>()
const emit = defineEmits<{ changed: [] }>()

const placementOpen = ref(false)
const completing = ref(false)
const placementsLoading = ref(false)
const placements = ref<PlacementDetail[]>([])

const openPlacement = () => { placementOpen.value = true }

const formatDate = (iso: string) => {
  if (!iso) return ''
  const d = new Date(iso)
  if (isNaN(d.getTime())) return iso
  const pad = (n: number) => n.toString().padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

const loadPlacements = async () => {
  if (props.event.status !== 'COMPLETED') return
  placementsLoading.value = true
  try {
    const r = await getEventPlacements(props.event.id)
    if (r.code === 0) {
      placements.value = r.data || []
    } else {
      placements.value = []
      ElMessage.error(r.message || '加载发分记录失败')
    }
  } finally {
    placementsLoading.value = false
  }
}

onMounted(loadPlacements)
watch(() => props.event.id, loadPlacements)
watch(() => props.event.status, loadPlacements)

const handleComplete = async () => {
  try {
    const mr = await getEventMatches(props.event.id)
    if (mr.code !== 0) {
      ElMessage.error(mr.message || '获取比赛列表失败，无法结赛')
      return
    }
    const pending = (mr.data || []).flat().filter(m => m.status !== 'COMPLETED').length
    if (pending > 0) {
      ElMessage.warning(`还有 ${pending} 场比赛未完成，无法结赛`)
      return
    }
    await ElMessageBox.confirm('确认完成赛事并发分？此操作不可撤销。', '完成并发分', { type: 'warning' })
  } catch { return }
  completing.value = true
  try {
    const r = await completeEvent(props.event.id)
    if (r.code === 0) { ElMessage.success('已完成并发分'); emit('changed') }
    else ElMessage.error(r.message || '完成失败')
  } finally { completing.value = false }
}
</script>

<style scoped>
.panel-header { margin-bottom: 12px; }
.hint { color: #9ca3af; font-size: 12px; margin-top: 12px; }
.placement-detail { margin-top: 12px; }
.placement-table { margin-top: 4px; }
.points-bold { font-weight: 600; color: #16a34a; }
.reason-text {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: bottom;
}
.empty-state {
  margin-top: 8px;
  padding: 16px;
  text-align: center;
  color: #9ca3af;
  font-size: 13px;
  background: #f9fafb;
  border-radius: 4px;
}
</style>