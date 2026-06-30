<template>
  <div class="issuance-panel">
    <div class="panel-header"><h3>发分</h3></div>
    <el-alert v-if="event.status === 'COMPLETED'" type="success" :closable="false" title="赛事已结束，名次积分已发放" />
    <template v-else>
      <el-button type="primary" @click="openPlacement">配置加分表</el-button>
      <el-button type="success" :loading="completing" :disabled="event.status !== 'IN_PROGRESS'" @click="handleComplete">
        完成赛事并发分
      </el-button>
      <div class="hint">完成后将按加分表自动发放名次积分（source=PLACEMENT）。需所有比赛已完成，否则会提示未完成场次。</div>
    </template>
    <PlacementPointsDialog v-model="placementOpen" :event="event" @saved="emit('changed')" />
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { completeEvent, getEventMatches } from '@/api/matches'
import PlacementPointsDialog from './PlacementPointsDialog.vue'
import type { Event } from '@/types'

const props = defineProps<{ event: Event }>()
const emit = defineEmits<{ changed: [] }>()

const placementOpen = ref(false)
const completing = ref(false)
const openPlacement = () => { placementOpen.value = true }

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
</style>
