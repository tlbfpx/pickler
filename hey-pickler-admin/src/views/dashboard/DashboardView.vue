<template>
  <div
    v-loading="loading"
    class="dashboard"
  >
    <div class="page-header">
      <h1>首页</h1>
    </div>

    <!-- Todo panel -->
    <el-card
      shadow="never"
      class="page-card"
      :body-style="{ padding: '20px 24px' }"
    >
      <div class="panel-head">
        <span>异常事件</span>
        <span class="panel-tag">需要处理</span>
      </div>
      <el-table
        v-if="todos.length"
        :data="pagedTodos"
        size="small"
        stripe
        class="todo-table"
        row-class-name="todo-row-clickable"
        @row-click="(row: any) => goAction(row)"
      >
        <el-table-column
          label="标签"
          width="96"
          align="center"
        >
          <template #default="{ row }">
            <el-tag
              size="small"
              :type="row.tagType"
            >
              {{ row.label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          label="标题"
          min-width="160"
          show-overflow-tooltip
          prop="title"
        />
        <el-table-column
          label="异常详情"
          min-width="220"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            {{ row.detail }}
          </template>
        </el-table-column>
        <el-table-column
          label="比赛时间"
          width="110"
          align="center"
        >
          <template #default="{ row }">
            {{ formatDate(row.eventTime) }}
          </template>
        </el-table-column>
        <el-table-column
          label="活动负责人"
          width="120"
          align="center"
          prop="organizer"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            {{ row.organizer || '—' }}
          </template>
        </el-table-column>
        <el-table-column
          label="操作"
          width="100"
          align="center"
        >
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              size="small"
              @click.stop="goAction(row)"
            >
              {{ row.action }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <div
        v-else
        class="muted todo-empty"
      >
        暂无异常事件
      </div>
      <Pagination
        v-if="todos.length > pageSize"
        v-model:page="currentPage"
        v-model:size="pageSize"
        :total="todos.length"
      />
    </el-card>

    <!-- KPI -->
    <el-card
      shadow="never"
      class="page-card"
      :body-style="{ padding: '20px 24px' }"
    >
      <div class="kpi-row">
        <div class="kpi-card">
          <div
            class="kpi-top"
            style="background:#6366f1;"
          />
          <div class="kpi-body">
            <div class="kpi-value">
              {{ stats.totalUsers }}
            </div>
            <div class="kpi-label">
              总用户
            </div>
          </div>
          <div class="kpi-delta">
            本周 +{{ stats.newUsersWeek }}
          </div>
        </div>
        <div class="kpi-card">
          <div
            class="kpi-top"
            style="background:#f5576c;"
          />
          <div class="kpi-body">
            <div class="kpi-value">
              {{ stats.totalEvents }}
            </div>
            <div class="kpi-label">
              总赛事
            </div>
          </div>
          <div class="kpi-delta">
            报名中 {{ stats.openEvents }}
          </div>
        </div>
        <div class="kpi-card">
          <div
            class="kpi-top"
            style="background:#43e97b;"
          />
          <div class="kpi-body">
            <div class="kpi-value">
              {{ stats.recentRegistrationsCount }}
            </div>
            <div class="kpi-label">
              本周报名
            </div>
          </div>
          <div class="kpi-delta">
            累计 {{ stats.totalRegistrations }} 笔
          </div>
        </div>
        <div class="kpi-card">
          <div
            class="kpi-top"
            style="background:#fa8231;"
          />
          <div class="kpi-body">
            <div class="kpi-value">
              ¥{{ stats.totalRevenue }}
            </div>
            <div class="kpi-label">
              报名收入
            </div>
          </div>
          <div class="kpi-delta">
            本周 ¥{{ stats.weeklyRevenue }}
          </div>
        </div>
      </div>
    </el-card>

    <!-- Trends: full-width row -->
    <el-card
      shadow="never"
      class="page-card"
      :body-style="{ padding: '20px 24px' }"
    >
      <div class="row">
        <div
          class="panel-cell"
          style="flex:1.2"
        >
          <div class="panel-head">
            <span>用户增长趋势</span>
            <span class="panel-tag">近 30 天</span>
          </div>
          <div
            ref="userChartRef"
            class="chart-lg"
          />
        </div>
        <div
          class="panel-cell"
          style="flex:0.8"
        >
          <div class="panel-head">
            <span>报名趋势</span>
            <span class="panel-tag">近 30 天</span>
          </div>
          <div
            ref="regChartRef"
            class="chart-lg"
          />
        </div>
      </div>
    </el-card>

    <!-- Distribution -->
    <el-card
      shadow="never"
      class="page-card"
      :body-style="{ padding: '20px 24px' }"
    >
      <div class="row">
        <div
          class="panel-cell"
          style="flex:1"
        >
          <div class="panel-head">
            <span>赛事类型分布</span>
          </div>
          <div
            ref="eventTypeRef"
            class="chart-sm"
          />
        </div>
        <div
          class="panel-cell"
          style="flex:1"
        >
          <div class="panel-head">
            <span>{{ TERMS.STAR.tier }}分布</span>
          </div>
          <div
            ref="starTierRef"
            class="chart-sm"
          />
        </div>
        <div
          class="panel-cell"
          style="flex:1"
        >
          <div class="panel-head">
            <span>{{ TERMS.PARTY.tier }}分布</span>
          </div>
          <div
            ref="partyTierRef"
            class="chart-sm"
          />
        </div>
      </div>
    </el-card>

    <!-- Tables -->
    <el-card
      shadow="never"
      class="page-card"
      :body-style="{ padding: '20px 24px' }"
    >
      <div class="row">
        <div
          class="panel-cell"
          style="flex:1"
        >
          <div class="panel-head">
            <span>即将开始的赛事</span>
          </div>
          <el-table
            :data="stats.upcomingEvents"
            size="small"
            stripe
          >
            <el-table-column
              prop="title"
              label="赛事"
              min-width="150"
              show-overflow-tooltip
            />
            <el-table-column
              label="类型"
              width="70"
              align="center"
            >
              <template #default="{ row }">
                <el-tag
                  :type="row.type === 'STAR' ? 'warning' : 'danger'"
                  size="small"
                  round
                >
                  {{ row.type === 'STAR' ? TERMS.STAR.type : TERMS.PARTY.type }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column
              label="时间"
              width="90"
              align="center"
            >
              <template #default="{ row }">
                {{ fmtDate(row.eventTime) }}
              </template>
            </el-table-column>
            <el-table-column
              label="报名"
              width="80"
              align="center"
            >
              <template #default="{ row }">
                {{ row.currentParticipants }}/{{ row.maxParticipants ?? '-' }}
              </template>
            </el-table-column>
            <el-table-column
              label="状态"
              width="76"
              align="center"
            >
              <template #default="{ row }">
                <el-tooltip
                  :content="statusTooltip(row.status)"
                  placement="top"
                >
                  <el-tag
                    :type="sType(row.status)"
                    size="small"
                    round
                  >
                    {{ sLabel(row.status) }}
                  </el-tag>
                </el-tooltip>
              </template>
            </el-table-column>
          </el-table>
          <el-empty
            v-if="!stats.upcomingEvents?.length"
            :image-size="40"
          />
        </div>
        <div
          class="panel-cell"
          style="flex:1"
        >
          <div class="panel-head">
            <span>最新报名</span>
          </div>
          <el-table
            :data="stats.recentRegistrations"
            size="small"
            stripe
          >
            <el-table-column
              label="用户"
              width="80"
              show-overflow-tooltip
            >
              <template #default="{ row }">
                {{ row.nickname || '未知' }}
              </template>
            </el-table-column>
            <el-table-column
              prop="eventTitle"
              label="赛事"
              min-width="150"
              show-overflow-tooltip
            />
            <el-table-column
              label="类型"
              width="56"
              align="center"
            >
              <template #default="{ row }">
                {{ row.matchType === 'SINGLES' ? '单打' : row.matchType === 'DOUBLES' ? '双打' : '混双' }}
              </template>
            </el-table-column>
            <el-table-column
              label="状态"
              width="70"
              align="center"
            >
              <template #default="{ row }">
                <el-tag
                  :type="rType(row.status)"
                  size="small"
                  round
                >
                  {{ rLabel(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column
              label="时间"
              width="90"
              align="center"
            >
              <template #default="{ row }">
                {{ fmtDate(row.createdAt) }}
              </template>
            </el-table-column>
          </el-table>
          <el-empty
            v-if="!stats.recentRegistrations?.length"
            :image-size="40"
          />
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, nextTick, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getDashboardStats } from '@/api/dashboard'
import { getEventList } from '@/api/events'
import { useAuthStore } from '@/stores/auth'
import Pagination from '@/components/common/Pagination.vue'
import * as echarts from 'echarts'
import { TERMS, TIER_NAME, TIER_COLOR } from '@/constants/terms'
import { statusTooltip } from '@/constants/eventStatus'
import type { DashboardStats } from '@/types'

const loading = ref(true)
const userChartRef = ref<HTMLElement>()
const regChartRef = ref<HTMLElement>()
const eventTypeRef = ref<HTMLElement>()
const starTierRef = ref<HTMLElement>()
const partyTierRef = ref<HTMLElement>()
const charts: echarts.ECharts[] = []

const stats = reactive<DashboardStats>({
  totalUsers: 0, bannedUsers: 0, newUsersWeek: 0,
  totalEvents: 0, openEvents: 0, inProgressEvents: 0,
  totalRegistrations: 0, recentRegistrationsCount: 0,
  totalRevenue: 0, weeklyRevenue: 0,
  starTierDistribution: {}, partyTierDistribution: {}, eventTypes: {},
  dailyNewUsers: [], dailyRegistrations: [],
  recentRegistrations: [], upcomingEvents: []
})

const router = useRouter()
const authStore = useAuthStore()
const todos = ref<Array<{ id: number; title: string; eventTime: string | null; label: string; detail: string; action: string; tagType: string; organizer: string | null; tab?: string }>>([])
const currentPage = ref(1)
const pageSize = ref(10)

const pagedTodos = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return todos.value.slice(start, start + pageSize.value)
})

const fetchBy = (status: string) =>
  getEventList({ page: 1, size: 50, status })
    .then(r => (r.code === 0 ? (r.data?.list || []) : []))
    .catch(() => [])

type TodoEvent = {
  id: number
  title: string
  eventTime: string | null
  registrationDeadline: string | null
  status: string
  currentParticipants: number
  maxParticipants: number | null
  createdByUsername?: string | null
}

const DAY_MS = 86400000
const ACTION_TAB: Record<string, string | undefined> = {
  draftStale: undefined,         // 草稿编辑
  openUnderfilled: 'reg',        // 报名 Tab（关注流量）
  inProgressStale: 'match',      // 对阵/比赛 Tab
  deadlineMissed: 'reg'          // 报名 Tab
}

const buildTodos = async () => {
  // 拉所有相关状态的赛事；后端对 status 字段做了精确过滤。
  // FULL 状态对应 OPEN 已满员，OPEN 列表里 currentParticipants 可能已等于 max。
  const [draftAll, openFullAll, progAll] = await Promise.all([
    fetchBy('DRAFT'),
    fetchBy('OPEN').then(async list => {
      // 合并 FULL 状态一起判断「报名过少」
      const full = await fetchBy('FULL')
      return [...list, ...full]
    }),
    fetchBy('IN_PROGRESS')
  ])
  // 待办只展示当前管理员负责的赛事；超级管理员看全部。
  const isSuperAdmin = authStore.admin?.role === 'SUPER_ADMIN'
  const currentUsername = authStore.admin?.username ?? null
  const filterMine = <T extends TodoEvent>(list: T[]): T[] => {
    if (isSuperAdmin || !currentUsername) return list
    return list.filter(e => e.createdByUsername === currentUsername)
  }
  const draft = filterMine(draftAll)
  const open = filterMine(openFullAll)
  const prog = filterMine(progAll)
  const now = Date.now()
  const list: { id: number; title: string; eventTime: string | null; label: string; detail: string; action: string; tagType: string; organizer: string | null; tab: string | undefined }[] = []

  // 异常 1：草稿超过 3 天未发布
  draft.forEach((e: TodoEvent) => {
    // 后端 EventVO 不暴露 createdAt；以「比赛时间 > 3 天后」作为草稿长期未发布的保守近似，
    // 更准确的 createdAt 需要后端扩展；这里若 eventTime 缺失，仍提示去发布。
    const eventMs = e.eventTime ? new Date(e.eventTime).getTime() : Infinity
    if (eventMs - now > 3 * DAY_MS || !e.eventTime) {
      list.push({
        id: e.id, title: e.title, eventTime: e.eventTime,
        label: '草稿长期未发布',
        detail: '比赛时间 > 3 天但仍为草稿，请尽快发布',
        action: '去发布',
        tagType: 'info',
        organizer: e.createdByUsername ?? null,
        tab: ACTION_TAB.draftStale
      })
    }
  })

  // 异常 2：OPEN/FULL 即将开赛（7 天内）但报名未满 30%
  open.forEach((e: TodoEvent) => {
    const eventMs = e.eventTime ? new Date(e.eventTime).getTime() : Infinity
    const within7d = eventMs - now > 0 && eventMs - now <= 7 * DAY_MS
    if (!within7d) return
    const max = e.maxParticipants ?? 0
    if (max <= 0) return
    const fillRatio = e.currentParticipants / max
    if (fillRatio < 0.3) {
      list.push({
        id: e.id, title: e.title, eventTime: e.eventTime,
        label: '即将开赛 报名过少',
        detail: `${e.currentParticipants}/${max} 人（${Math.round(fillRatio * 100)}%），<30%`,
        action: '查看报名',
        tagType: 'warning',
        organizer: e.createdByUsername ?? null,
        tab: ACTION_TAB.openUnderfilled
      })
    }
  })

  // 异常 3：IN_PROGRESS 已开赛超过 14 天无进展
  prog.forEach((e: TodoEvent) => {
    const eventMs = e.eventTime ? new Date(e.eventTime).getTime() : 0
    if (!eventMs) return
    const daysInProgress = (now - eventMs) / DAY_MS
    if (daysInProgress > 14) {
      list.push({
        id: e.id, title: e.title, eventTime: e.eventTime,
        label: '进行中超 14 天',
        detail: `已开赛 ${Math.floor(daysInProgress)} 天，请检查对阵/比分是否停滞`,
        action: '去看对阵',
        tagType: 'danger',
        organizer: e.createdByUsername ?? null,
        tab: ACTION_TAB.inProgressStale
      })
    }
  })

  // 异常 4：报名截止已过但未满员（任意状态）
  const deadlineMissedCandidates = [...draft, ...open, ...prog]
  deadlineMissedCandidates.forEach((e: TodoEvent) => {
    const deadlineMs = e.registrationDeadline ? new Date(e.registrationDeadline).getTime() : null
    if (deadlineMs == null || deadlineMs > now) return
    const max = e.maxParticipants ?? 0
    if (max > 0 && e.currentParticipants < max) {
      // 避免与上方已加的「即将开赛 报名过少」重复
      const alreadyAdded = list.some(t => t.id === e.id && t.label === '即将开赛 报名过少')
      if (alreadyAdded) return
      list.push({
        id: e.id, title: e.title, eventTime: e.eventTime,
        label: '报名期已过未满员',
        detail: `截止已过 ${Math.floor((now - deadlineMs) / DAY_MS)} 天，${e.currentParticipants}/${max} 人`,
        action: '查看报名',
        tagType: 'info',
        organizer: e.createdByUsername ?? null,
        tab: ACTION_TAB.deadlineMissed
      })
    }
  })

  todos.value = list
    .sort((a, b) => {
      // 危险的（danger）优先；其次按比赛时间升序
      if (a.tagType !== b.tagType) {
        const order: Record<string, number> = { danger: 0, warning: 1, info: 2, success: 3 }
        return (order[a.tagType] ?? 9) - (order[b.tagType] ?? 9)
      }
      return new Date(a.eventTime || 0).getTime() - new Date(b.eventTime || 0).getTime()
    })
    .slice(0, 20)
  currentPage.value = 1
}

const goAction = (row: { id: number; tab?: string }) => {
  router.push({ path: `/events/${row.id}`, query: row.tab ? { tab: row.tab } : undefined })
}

const fmtDate = (d: string | null) => {
  if (!d) return '-'
  const dt = new Date(d)
  return `${(dt.getMonth() + 1).toString().padStart(2, '0')}-${dt.getDate().toString().padStart(2, '0')}`
}
const formatDate = (d: string | null) => {
  if (!d) return '-'
  const dt = new Date(d)
  return `${dt.getFullYear()}-${(dt.getMonth() + 1).toString().padStart(2, '0')}-${dt.getDate().toString().padStart(2, '0')}`
}
const sType = (s: string) => ({ OPEN: 'success', IN_PROGRESS: 'warning', FULL: 'danger', DRAFT: 'info' }[s] || 'info')
const sLabel = (s: string) => ({ OPEN: '报名中', IN_PROGRESS: '进行中', FULL: '已满', DRAFT: '草稿', COMPLETED: '已结束', CANCELLED: '已取消' }[s] || s)
const rType = (s: string) => ({ REGISTERED: 'success', CANCELLED: 'danger' }[s] || 'info')
const rLabel = (s: string) => ({ REGISTERED: '已报名', CANCELLED: '已取消' }[s] || s)

function mk(el: HTMLElement, opt: echarts.EChartsOption) {
  const c = echarts.init(el)
  c.setOption(opt)
  charts.push(c)
}

const tooltipStyle = { backgroundColor: 'rgba(255,255,255,0.96)', borderColor: '#eee', borderWidth: 1, textStyle: { color: '#333', fontSize: 12 }, extraCssText: 'box-shadow:0 4px 12px rgba(0,0,0,0.08);border-radius:6px;' }
const gridBase = { top: 20, right: 16, bottom: 32, left: 44 }

function renderCharts() {
  if (userChartRef.value) {
    const dates = stats.dailyNewUsers.map(d => d.date.slice(5))
    mk(userChartRef.value, {
      tooltip: { trigger: 'axis', ...tooltipStyle },
      grid: gridBase,
      xAxis: { type: 'category', data: dates, boundaryGap: false, axisLine: { lineStyle: { color: '#e5e7eb' } }, axisTick: { show: false }, axisLabel: { color: '#9ca3af', fontSize: 11 } },
      yAxis: { type: 'value', minInterval: 1, axisLine: { show: false }, axisTick: { show: false }, splitLine: { lineStyle: { color: '#f3f4f6' } }, axisLabel: { color: '#9ca3af', fontSize: 11 } },
      series: [{ type: 'line', data: stats.dailyNewUsers.map(d => d.count), smooth: true, showSymbol: false,
        lineStyle: { color: '#667eea', width: 2.5 }, itemStyle: { color: '#667eea' },
        areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: 'rgba(102,126,234,0.25)' }, { offset: 1, color: 'rgba(102,126,234,0.01)' }]) }
      }]
    })
  }

  if (regChartRef.value) {
    const dates = stats.dailyRegistrations.map(d => d.date.slice(5))
    mk(regChartRef.value, {
      tooltip: { trigger: 'axis', ...tooltipStyle },
      grid: gridBase,
      xAxis: { type: 'category', data: dates, axisLine: { lineStyle: { color: '#e5e7eb' } }, axisTick: { show: false }, axisLabel: { color: '#9ca3af', fontSize: 11 } },
      yAxis: { type: 'value', minInterval: 1, axisLine: { show: false }, axisTick: { show: false }, splitLine: { lineStyle: { color: '#f3f4f6' } }, axisLabel: { color: '#9ca3af', fontSize: 11 } },
      series: [{ type: 'bar', data: stats.dailyRegistrations.map(d => d.count), barMaxWidth: 14,
        itemStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: '#43e97b' }, { offset: 1, color: '#a8edea' }]), borderRadius: [4, 4, 0, 0] }
      }]
    })
  }

  const pieTip = { trigger: 'item' as const, ...tooltipStyle }
  const pieLeg = { bottom: 4, itemWidth: 10, itemHeight: 10, itemGap: 14, textStyle: { fontSize: 11, color: '#6b7280' } }
  const mkPie = (data: { value: number; name: string; itemStyle: { color: string } }[]) => ({
    type: 'pie' as const, radius: ['38%', '64%'], center: ['50%', '40%'], label: { show: false },
    emphasis: { itemStyle: { shadowBlur: 8, shadowColor: 'rgba(0,0,0,0.08)' } },
    itemStyle: { borderRadius: 3, borderColor: '#fff', borderWidth: 2 }, data
  })

  if (eventTypeRef.value) mk(eventTypeRef.value, {
    tooltip: pieTip, legend: pieLeg,
    series: [mkPie([{ value: stats.eventTypes?.STAR || 0, name: TERMS.STAR.type, itemStyle: { color: '#E6A23C' } }, { value: stats.eventTypes?.PARTY || 0, name: TERMS.PARTY.type, itemStyle: { color: '#F56C6C' } }])]
  })

  const tc = TIER_COLOR
  const tn = TIER_NAME

  const mapTier = (td: Record<string, number>) => Object.entries(td).map(([k, v]) => ({ value: v, name: tn[k] || k, itemStyle: { color: tc[k] || '#6B7280' } }))

  if (starTierRef.value) mk(starTierRef.value, { tooltip: pieTip, legend: pieLeg, series: [mkPie(mapTier(stats.starTierDistribution || {}))] })
  if (partyTierRef.value) mk(partyTierRef.value, { tooltip: pieTip, legend: pieLeg, series: [mkPie(mapTier(stats.partyTierDistribution || {}))] })
}

const fetchStats = async () => {
  loading.value = true
  try {
    const res = await getDashboardStats()
    if (res.code === 0) { Object.assign(stats, res.data); await nextTick(); renderCharts() }
    else ElMessage.error(res.message || '获取数据失败')
  } catch {
    
  }
  finally { loading.value = false }
}

const onResize = () => charts.forEach(c => c.resize())

onMounted(() => { fetchStats(); buildTodos(); window.addEventListener('resize', onResize) })
onBeforeUnmount(() => { window.removeEventListener('resize', onResize); charts.forEach(c => c.dispose()) })
</script>

<style scoped>
.dashboard {
  max-width: 1360px;
}

.dashboard .page-header {
  margin-bottom: 16px;

  h1 {
    font-size: 24px;
    font-weight: 600;
    color: #111827;
  }
}

/* KPI */
.kpi-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.kpi-card {
  background: #fff;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid #f3f4f6;
}

.kpi-top {
  height: 3px;
}

.kpi-body {
  padding: 20px 20px 12px;
}

.kpi-value {
  font-size: 32px;
  font-weight: 700;
  color: #111827;
  line-height: 1;
}

.kpi-label {
  font-size: 13px;
  color: #9ca3af;
  margin-top: 6px;
}

.kpi-delta {
  padding: 0 20px 16px;
  font-size: 12px;
  color: #b0b8c4;
}

/* Row */
.row {
  display: flex;
  gap: 16px;
}

/* Todo panel */
.todo-table {
  margin-top: 4px;
}

.todo-empty {
  padding: 24px 0;
  text-align: center;
}

:deep(.todo-row-clickable) {
  cursor: pointer;
}

.muted {
  font-size: 12px;
  color: #9ca3af;
}

.panel-cell {
  background: #fff;
  border-radius: 8px;
  padding: 20px;
  border: 1px solid #f3f4f6;
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  font-size: 14px;
  font-weight: 600;
  color: #374151;
}

.panel-tag {
  font-size: 11px;
  color: #b0b8c4;
  font-weight: 400;
  background: #f9fafb;
  padding: 2px 8px;
  border-radius: 4px;
}

.chart-lg {
  height: 300px;
  width: 100%;
}

.chart-sm {
  height: 220px;
  width: 100%;
}

/* Table tweaks */
:deep(.el-table th.el-table__cell) {
  background: #fafbfc;
  font-weight: 500;
  color: #6b7280;
  font-size: 12px;
}

:deep(.el-table td.el-table__cell) {
  font-size: 13px;
}

@media (max-width: 1100px) {
  .kpi-row { grid-template-columns: repeat(2, 1fr); }
  .row { flex-wrap: wrap; }
  .panel-cell { flex: 1 1 45% !important; min-width: 300px; }
}
</style>
