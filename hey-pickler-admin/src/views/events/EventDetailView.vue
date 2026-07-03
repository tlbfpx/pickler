<template>
  <div
    v-loading="loading"
    class="event-detail"
  >
    <div class="page-header">
      <div class="title-area">
        <el-button
          link
          @click="$router.back()"
        >
          <el-icon><ArrowLeft /></el-icon>返回
        </el-button>
        <h1>{{ event?.title || '…' }}</h1>
        <el-tag
          v-if="event"
          :color="statusColor(event.status)"
          effect="dark"
        >
          {{ formatStatus(event.status) }}
        </el-tag>
        <el-tag
          v-if="event?.format"
          size="small"
          effect="plain"
        >
          {{ formatEventFormat(event.format) }}
        </el-tag>
        <el-tag
          size="small"
          :color="getEventTypeColor(event?.type)"
          effect="dark"
        >
          {{ formatEventType(event?.type) }}
        </el-tag>
      </div>
      <div v-if="event">
        <el-button
          size="small"
          @click="editOpen = true"
        >
          编辑
        </el-button>
      </div>
    </div>

    <div
      v-if="event"
      class="summary"
    >
      <span>{{ event.location || '-' }}</span> · <span>{{ formatDate(event.eventTime) }}</span> ·
      <span>报名 {{ event.currentParticipants }}/{{ event.maxParticipants ?? '∞' }}</span>
    </div>

    <!-- Stepper -->
    <el-steps
      v-if="event"
      :active="activeStepIndex"
      finish-status="success"
      align-center
      class="stepper"
    >
      <el-step
        v-for="s in steps"
        :key="s.key"
        :title="s.title"
        :status="s.status"
      />
    </el-steps>

    <!-- 阶段内容 -->
    <el-tabs
      v-if="event"
      v-model="activeTab"
      class="stage-tabs"
    >
      <el-tab-pane label="基本信息" name="info">
        <el-descriptions
          v-if="event"
          :column="2"
          border
          size="default"
        >
          <el-descriptions-item label="赛事标题">
            {{ event.title }}
          </el-descriptions-item>
          <el-descriptions-item label="类型">
            <el-tag :color="getEventTypeColor(event.type)" effect="dark" size="small">
              {{ formatEventType(event.type) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item v-if="event.format" label="比赛形式">
            <el-tag size="small" effect="plain">{{ formatEventFormat(event.format) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :color="statusColor(event.status)" effect="dark" size="small">
              {{ formatStatus(event.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="比赛时间">
            {{ event.eventTime ? formatDate(event.eventTime) : '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="报名截止">
            {{ event.registrationDeadline ? formatDate(event.registrationDeadline) : '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="地点">
            {{ event.location || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="报名人数">
            {{ event.currentParticipants }} / {{ event.maxParticipants ?? '∞' }}
            <el-progress
              v-if="event.maxParticipants"
              :percentage="Math.round(event.currentParticipants / event.maxParticipants * 100)"
              :stroke-width="4"
              :show-text="false"
              style="display: inline-block; width: 80px; margin-left: 8px; vertical-align: middle;"
            />
          </el-descriptions-item>
          <el-descriptions-item label="报名费">
            <span v-if="event.fee > 0" style="color: #d97706; font-weight: 600">¥{{ event.fee }}</span>
            <span v-else style="color: #16a34a">免费</span>
          </el-descriptions-item>
          <el-descriptions-item v-if="event.minPoints" label="积分门槛">
            <span style="color: #d97706">{{ event.minPoints }}+ 战力</span>
          </el-descriptions-item>
          <el-descriptions-item v-if="event.bannerUrl" label="封面图" :span="2">
            <el-image
              :src="event.bannerUrl"
              fit="cover"
              style="width: 100%; max-width: 400px; border-radius: 4px"
              :preview-src-list="[event.bannerUrl]"
            />
          </el-descriptions-item>
        </el-descriptions>
        <p class="muted" style="margin-top: 12px">
          提示：基本信息通过右上「编辑」修改。
        </p>
      </el-tab-pane>
      <el-tab-pane
        label="报名"
        name="reg"
      >
        <RegistrationDrawerEmbed
          :event="event"
          @changed="reload"
        />
      </el-tab-pane>
      <el-tab-pane
        label="分组"
        name="group"
      >
        <GroupingPanel
          :key="`g-${event.id}-${event.groupingLocked}`"
          :event="event"
          @changed="reload"
        />
      </el-tab-pane>
      <el-tab-pane
        label="对阵/比赛"
        name="match"
      >
        <MatchesPanel
          :event="event"
          @changed="reload"
        />
      </el-tab-pane>
      <el-tab-pane
        label="发分"
        name="issue"
      >
        <IssuancePanel
          :event="event"
          @changed="reload"
        />
      </el-tab-pane>
    </el-tabs>

    <!-- 状态显式化：当前合法的下一阶段按钮 -->
    <div
      v-if="event"
      class="status-actions"
    >
      <span class="muted">状态推进：</span>
      <el-button
        v-for="t in getAllowedTargets(event.status)"
        :key="t"
        :type="t === 'CANCELLED' ? 'danger' : 'primary'"
        plain
        size="small"
        @click="changeStatus(t)"
      >
        → {{ formatStatus(t) }}
      </el-button>
      <span
        v-if="!getAllowedTargets(event.status).length"
        class="muted"
      >（终态，无可用转换）</span>
    </div>

    <EventFormDialog
      v-model="editOpen"
      :event="event"
      @success="reload"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import { getEventDetail, changeEventStatus } from '@/api/events'
import { formatStatus, statusColor, getAllowedTargets, type EventStatus } from '@/constants/eventStatus'
import { formatDate, formatEventType, formatEventFormat, getEventTypeColor } from '@/utils'
import EventFormDialog from './EventFormDialog.vue'
import GroupingPanel from './GroupingPanel.vue'
import MatchesPanel from './MatchesPanel.vue'
import IssuancePanel from './IssuancePanel.vue'
import RegistrationDrawerEmbed from './RegistrationDrawerEmbed.vue'
import type { Event } from '@/types'

const route = useRoute()
const raw = route.params.id
const id = Number(Array.isArray(raw) ? raw[0] : raw)
if (!Number.isFinite(id)) {
  ElMessage.error('无效的赛事 ID')
}
const validId = Number.isFinite(id)
const loading = ref(false)
const event = ref<Event | null>(null)
const editOpen = ref(false)
const activeTab = ref('info')

const reload = async () => {
  if (!validId) return
  loading.value = true
  try {
    const r = await getEventDetail(id)
    if (r.code === 0) event.value = r.data
    else ElMessage.error(r.message || '加载失败')
  } finally { loading.value = false }
}
if (validId) onMounted(reload)

const STAGE_ORDER: Record<string, number> = { DRAFT: 0, OPEN: 1, FULL: 1, IN_PROGRESS: 2, COMPLETED: 3, CANCELLED: 0 }
const activeStepIndex = computed(() => {
  if (!event.value) return 0
  if (event.value.status === 'CANCELLED') return 0
  if (event.value.groupingLocked) return Math.max(STAGE_ORDER[event.value.status], 2)
  return STAGE_ORDER[event.value.status] ?? 0
})
const steps = computed(() => {
  const base = [
    { key: 'draft', title: '草稿', status: 'finish' as const },
    { key: 'reg', title: '报名', status: 'finish' as const },
    { key: 'group', title: '分组', status: 'finish' as const },
    { key: 'match', title: '对阵', status: 'finish' as const },
    { key: 'issue', title: '发分', status: 'finish' as const }
  ]
  const i = activeStepIndex.value
  base.forEach((s, idx) => {
    if (idx < i) s.status = 'finish'
    else if (idx === i) s.status = 'process'
    else s.status = 'wait'
  })
  return base
})

const changeStatus = async (t: EventStatus) => {
  if (!event.value) return
  const r = await changeEventStatus(event.value.id, t)
  if (r.code === 0) { ElMessage.success('状态已更新'); reload() }
  else ElMessage.error(r.message || '更新失败')
}
</script>

<style scoped>
.title-area { display: flex; align-items: center; gap: 12px; }
.summary { color: #6b7280; font-size: 13px; margin: 8px 0 16px; }
.stepper { margin-bottom: 16px; }
.stage-tabs { margin-bottom: 16px; }
.status-actions { display: flex; gap: 8px; align-items: center; padding: 12px; background: #f9fafb; border-radius: 8px; flex-wrap: wrap; }
.muted { color: #9ca3af; font-size: 13px; }
</style>
