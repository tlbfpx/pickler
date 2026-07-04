<template>
  <div
    v-loading="loading"
    class="event-detail"
  >
    <!-- Hero: 标题 + 状态/类型/形式 tags + 编辑按钮 -->
    <el-card
      v-if="event"
      shadow="never"
      class="hero-card"
      :body-style="{ padding: '20px 24px' }"
    >
      <div class="hero-top">
        <div class="hero-title-block">
          <div class="hero-back-row">
            <el-button link size="small" @click="$router.back()">
              <el-icon><ArrowLeft /></el-icon>返回赛事列表
            </el-button>
          </div>
          <h1 class="hero-title">{{ event.title }}</h1>
          <div class="hero-tags">
            <el-tooltip
              :content="statusTooltip(event.status)"
              placement="top"
            >
              <el-tag :color="statusColor(event.status)" effect="dark" size="large" round>
                {{ formatStatus(event.status) }}
              </el-tag>
            </el-tooltip>
            <el-tag :color="getEventTypeColor(event.type)" effect="dark" size="default" round>
              {{ formatEventType(event.type) }}
            </el-tag>
            <el-tag v-if="event.format" effect="plain" size="default">
              {{ formatEventFormat(event.format) }}
            </el-tag>
            <el-tag v-if="event.fee > 0" type="warning" size="default" effect="plain">
              ¥{{ event.fee }}
            </el-tag>
            <el-tag v-else type="success" size="default" effect="plain">
              免费
            </el-tag>
          </div>
        </div>
        <el-button type="primary" :icon="Edit" plain @click="editOpen = true">
          编辑信息
        </el-button>
      </div>
      <el-divider class="hero-divider" />
      <div class="hero-stats">
        <div class="stat-cell">
          <el-icon class="stat-icon"><Location /></el-icon>
          <div class="stat-content">
            <div class="stat-label">比赛地点</div>
            <div class="stat-value">{{ event.location || '未设置' }}</div>
          </div>
        </div>
        <div class="stat-cell">
          <el-icon class="stat-icon"><Clock /></el-icon>
          <div class="stat-content">
            <div class="stat-label">比赛时间</div>
            <div class="stat-value">{{ event.eventTime ? formatDate(event.eventTime) : '未设置' }}</div>
          </div>
        </div>
        <div class="stat-cell">
          <el-icon class="stat-icon"><Calendar /></el-icon>
          <div class="stat-content">
            <div class="stat-label">报名截止</div>
            <div class="stat-value">{{ event.registrationDeadline ? formatDate(event.registrationDeadline) : '未设置' }}</div>
          </div>
        </div>
        <div class="stat-cell stat-cell-registration">
          <el-icon class="stat-icon"><User /></el-icon>
          <div class="stat-content">
            <div class="stat-label">报名人数</div>
            <div class="stat-value">
              <span class="big-num">{{ event.currentParticipants }}</span>
              <span class="small-num"> / {{ event.maxParticipants ?? '∞' }}</span>
              <el-progress
                v-if="event.maxParticipants"
                :percentage="Math.round(event.currentParticipants / event.maxParticipants * 100)"
                :stroke-width="6"
                :show-text="false"
                class="stat-progress"
                :color="event.currentParticipants >= event.maxParticipants ? '#f59e0b' : '#10b981'"
              />
            </div>
          </div>
        </div>
      </div>
    </el-card>

    <!-- Stepper -->
    <el-card
      v-if="event"
      shadow="never"
      class="stage-card"
      :body-style="{ padding: '24px 24px' }"
    >
      <el-steps
        :active="activeStepIndex"
        finish-status="success"
        align-center
      >
        <el-step
          v-for="s in steps"
          :key="s.key"
          :title="s.title"
          :description="stepDescription(s.key)"
          :status="s.status"
        />
      </el-steps>
    </el-card>

    <!-- 状态显式化：当前合法的下一阶段按钮（粘底） -->
    <transition name="slide-up">
      <div
        v-if="event && getAllowedTargets(event.status).length"
        class="status-action-floating"
      >
        <div class="status-action-inner">
          <div class="status-action-label">
            <el-icon><Promotion /></el-icon>
            <span>推进到下一阶段</span>
          </div>
          <div class="status-action-buttons">
            <el-button
              v-for="t in getAllowedTargets(event.status)"
              :key="t"
              :type="t === 'CANCELLED' ? 'danger' : 'primary'"
              :icon="t === 'CANCELLED' ? CircleClose : Right"
              @click="changeStatus(t)"
            >
              {{ formatStatus(t) }}
            </el-button>
          </div>
        </div>
      </div>
    </transition>

    <!-- 阶段内容 -->
    <el-card
      v-if="event"
      shadow="never"
      class="stage-card"
      :body-style="{ padding: '12px 24px 24px' }"
    >
      <el-tabs
        v-model="activeTab"
        class="stage-tabs"
      >
        <el-tab-pane label="基本信息" name="info">
          <el-descriptions
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
              <el-tooltip
                :content="statusTooltip(event.status)"
                placement="top"
              >
                <el-tag :color="statusColor(event.status)" effect="dark" size="small">
                  {{ formatStatus(event.status) }}
                </el-tag>
              </el-tooltip>
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
    </el-card>

    <div class="bottom-spacer" />

    <EventFormDialog
      v-model="editOpen"
      :event="event"
      @success="reload"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  ArrowLeft, Edit, Location, Clock, Calendar, User, Promotion, Right, CircleClose
} from '@element-plus/icons-vue'
import { getEventDetail, changeEventStatus } from '@/api/events'
import { formatStatus, statusColor, statusTooltip, getAllowedTargets, type EventStatus } from '@/constants/eventStatus'
import { formatDate, formatEventType, formatEventFormat, getEventTypeColor } from '@/utils'
import EventFormDialog from './EventFormDialog.vue'
import GroupingPanel from './GroupingPanel.vue'
import MatchesPanel from './MatchesPanel.vue'
import IssuancePanel from './IssuancePanel.vue'
import RegistrationDrawerEmbed from './RegistrationDrawerEmbed.vue'
import type { Event } from '@/types'

const route = useRoute()
const router = useRouter()
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

// 允许的 Tab 名称（防止外部 query 传入非法值）
const VALID_TABS = new Set(['info', 'reg', 'group', 'match', 'issue'])
const initialTab = typeof route.query.tab === 'string' && VALID_TABS.has(route.query.tab)
  ? route.query.tab
  : 'info'
activeTab.value = initialTab

// 手动切换 Tab 时回写 URL（replace，不污染历史栈）
watch(activeTab, (tab) => {
  if (tab === route.query.tab) return
  router.replace({ query: { ...route.query, tab } })
})

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

const STEP_DESCRIPTIONS: Record<string, string> = {
  draft: '创建并完善赛事信息',
  reg: '选手报名与签到',
  group: '按策略分组（随机/蛇形/手动）',
  match: '生成对阵 + 录入比分',
  issue: '配置加分表 + 自动发分'
}
const stepDescription = (k: string) => STEP_DESCRIPTIONS[k] || ''

const changeStatus = async (t: EventStatus) => {
  if (!event.value) return
  const r = await changeEventStatus(event.value.id, t)
  if (r.code === 0) { ElMessage.success('状态已更新'); reload() }
  else ElMessage.error(r.message || '更新失败')
}
</script>

<style scoped>
.event-detail {
  max-width: 1280px;
  margin: 0 auto;
  padding-bottom: 24px;
}

/* Hero card */
.hero-card {
  margin-bottom: 16px;
  border: 1px solid #e5e7eb;
  background: linear-gradient(135deg, #ffffff 0%, #f8fafc 100%);
}
.hero-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
}
.hero-title-block { flex: 1; min-width: 0; }
.hero-back-row { margin-bottom: 4px; }
.hero-back-row :deep(.el-button) { font-size: 13px; color: #6b7280; padding: 0; }
.hero-title {
  font-size: 24px;
  font-weight: 600;
  color: #111827;
  margin: 4px 0 12px;
  line-height: 1.3;
  word-break: break-word;
}
.hero-tags {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  align-items: center;
}
.hero-divider { margin: 16px 0 !important; }
.hero-stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 24px;
}
.stat-cell { display: flex; align-items: flex-start; gap: 10px; min-width: 0; }
.stat-icon {
  font-size: 18px;
  color: #6366f1;
  margin-top: 2px;
  flex-shrink: 0;
}
.stat-content { flex: 1; min-width: 0; }
.stat-label {
  font-size: 12px;
  color: #6b7280;
  margin-bottom: 2px;
  font-weight: 500;
}
.stat-value {
  font-size: 14px;
  color: #1f2937;
  font-weight: 500;
  display: flex;
  align-items: center;
  gap: 4px;
  flex-wrap: wrap;
}
.stat-progress { width: 100%; margin-top: 4px; }
.big-num { font-size: 18px; font-weight: 600; color: #111827; }
.small-num { color: #6b7280; font-size: 13px; }

/* Stepper card + tab card */
.stage-card { margin-bottom: 16px; border: 1px solid #e5e7eb; }
.stage-card :deep(.el-tabs__header) { margin: 0 0 16px 0; }
.stage-card :deep(.el-tabs__nav-wrap)::after { height: 1px; background-color: #e5e7eb; }

/* Bottom spacer to clear the floating action bar */
.bottom-spacer { height: 80px; }

/* Floating status action bar (always visible at viewport bottom) */
.status-action-floating {
  position: fixed;
  left: 220px;  /* clear the AppSidebar width */
  right: 24px;
  bottom: 16px;
  z-index: 100;
  pointer-events: none;
}
.status-action-inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  padding: 12px 20px;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.08), 0 2px 8px rgba(0, 0, 0, 0.04);
  pointer-events: auto;
}
.status-action-label {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: #374151;
}
.status-action-buttons { display: flex; gap: 8px; flex-wrap: wrap; }

/* Transition for the floating bar */
.slide-up-enter-active, .slide-up-leave-active {
  transition: transform 0.2s ease, opacity 0.2s ease;
}
.slide-up-enter-from, .slide-up-leave-to {
  transform: translateY(20px);
  opacity: 0;
}

@media (max-width: 900px) {
  .hero-stats { grid-template-columns: repeat(2, 1fr); }
  .status-action-floating { left: 12px; right: 12px; }
}
</style>
