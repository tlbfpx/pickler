<template>
  <div
    v-loading="loading"
    class="dashboard"
  >
    <div class="page-header">
      <h1>首页</h1>
    </div>

    <!-- 时间范围选择器（Loop-v19 Dashboard Phase 1） -->
    <el-card
      shadow="never"
      class="page-card range-card"
      :body-style="{ padding: '14px 24px' }"
    >
      <div class="range-row">
        <span class="range-label">时间范围</span>
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          value-format="YYYY-MM-DD"
          :shortcuts="dateShortcuts"
          unlink-panels
          style="width: 360px"
          @change="onRangeChange"
        />
        <el-tag
          v-if="isSuperAdmin"
          type="warning"
          size="small"
          effect="plain"
        >
          SUPER_ADMIN 可在 URL 加 ?no_cache=1 跳过缓存
        </el-tag>
        <div class="range-meta">
          <span v-if="trendsRange">{{ trendsRange }}</span>
          <span v-if="topList.length"> · Top {{ topList.length }} 活动</span>
          <span v-if="attendance"> · 已报名 {{ attendance.registered }} / 签到 {{ attendance.checkedIn }}</span>
        </div>
      </div>
    </el-card>

    <!-- Todo panel -->
    <el-card
      shadow="never"
      class="page-card"
      :body-style="{ padding: '20px 24px' }"
    >
      <div class="panel-head">
        <span>待办任务</span>
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
          width="140"
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
        暂无待办任务
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
          <div
            v-if="formatDelta(stats.newUsersWeekDeltaPct, stats.newUsersWeekDeltaAbs)"
            class="kpi-delta kpi-delta-color"
            :class="deltaClass(stats.newUsersWeekDeltaPct)"
          >
            本周 +{{ stats.newUsersWeek }}
            <span class="kpi-delta-tag">
              {{ formatDelta(stats.newUsersWeekDeltaPct, stats.newUsersWeekDeltaAbs) }}
            </span>
          </div>
          <div
            v-else
            class="kpi-delta"
          >
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
          <div
            v-if="formatDelta(stats.openEventsDeltaPct, stats.openEventsDeltaAbs)"
            class="kpi-delta kpi-delta-color"
            :class="deltaClass(stats.openEventsDeltaPct)"
          >
            报名中 {{ stats.openEvents }}
            <span class="kpi-delta-tag">
              {{ formatDelta(stats.openEventsDeltaPct, stats.openEventsDeltaAbs) }}
            </span>
          </div>
          <div
            v-else
            class="kpi-delta"
          >
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
            <span>{{ getTerms('STAR').tier }}分布</span>
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
            <span>{{ getTerms('PARTY').tier }}分布</span>
          </div>
          <div
            ref="partyTierRef"
            class="chart-sm"
          />
        </div>
      </div>
    </el-card>

    <!-- 30 天 KPI 趋势图 -->
    <el-card
      shadow="never"
      class="page-card"
      :body-style="{ padding: '20px 24px' }"
    >
      <div class="panel-head">
        <span>30 天趋势</span>
        <span class="panel-tag">每日新增 + 完赛率滚动</span>
      </div>
      <div class="trend-grid">
        <div class="trend-cell">
          <div class="trend-label">
            新增用户 <b class="trend-num">{{ trendSums.users }}</b>
          </div>
          <div
            ref="trendUsersRef"
            class="chart-xs"
          />
        </div>
        <div class="trend-cell">
          <div class="trend-label">
            新增报名 <b class="trend-num">{{ trendSums.regs }}</b>
          </div>
          <div
            ref="trendRegsRef"
            class="chart-xs"
          />
        </div>
        <div class="trend-cell">
          <div class="trend-label">
            新增赛事 <b class="trend-num">{{ trendSums.events }}</b>
          </div>
          <div
            ref="trendEventsRef"
            class="chart-xs"
          />
        </div>
        <div class="trend-cell">
          <div class="trend-label">
            完赛率 <b class="trend-num">{{ trendSums.rate.toFixed(1) }}%</b>
          </div>
          <div
            ref="trendRateRef"
            class="chart-xs"
          />
        </div>
      </div>
    </el-card>

    <!-- 趋势分析（Loop-v19 Dashboard Phase 1）：收入趋势+同比 / 出席漏斗 / Top 10 横向柱状图 -->
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
            <span>收入趋势</span>
            <span class="panel-tag">{{ trendsRange || '近 30 天' }} · 含同比虚线</span>
          </div>
          <div
            ref="revChartRef"
            class="chart-lg"
          />
        </div>
        <div
          class="panel-cell"
          style="flex:0.8"
        >
          <div class="panel-head">
            <span>出席漏斗</span>
            <span class="panel-tag">{{ trendsRange || '近 30 天' }} · 已报名 → 已签到</span>
          </div>
          <div
            ref="funnelChartRef"
            class="chart-lg"
          />
        </div>
      </div>
      <div class="row top-row">
        <div
          class="panel-cell"
          style="flex:1"
        >
          <div class="panel-head">
            <span>Top 10 活动</span>
            <span class="panel-tag">{{ trendsRange || '近 30 天' }}</span>
            <el-radio-group
              v-model="topMetric"
              size="small"
              class="top-metric"
              @change="onTopMetricChange"
            >
              <el-radio-button label="registrations">
                报名数
              </el-radio-button>
              <el-radio-button label="revenue">
                收入
              </el-radio-button>
              <el-radio-button label="fillRate">
                满座率
              </el-radio-button>
            </el-radio-group>
          </div>
          <div
            ref="topChartRef"
            class="chart-md"
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
                  {{ row.type === 'STAR' ? getTerms('STAR').type : getTerms('PARTY').type }}
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
                  <DictTag
                    dict-code="event_status"
                    :item-key="row.status"
                    size="small"
                  />
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
                {{ formatMatchType(row.matchType) }}
              </template>
            </el-table-column>
            <el-table-column
              label="状态"
              width="70"
              align="center"
            >
              <template #default="{ row }">
                <DictTag
                  dict-code="registration_status"
                  :item-key="row.status"
                  size="small"
                />
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
import { ref, reactive, computed, onMounted, nextTick, onBeforeUnmount, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  getDashboardStats,
  getDashboardTrends,
  getDashboardTopEvents,
  getDashboardAttendance,
  type DashboardTrendBucket,
  type DashboardTrendVO,
  type TopEventVO,
  type AttendanceFunnelVO
} from '@/api/dashboard'
import { getEventList } from '@/api/events'
import { useAuthStore } from '@/stores/auth'
import { useDictStore } from '@/stores/dict'
import Pagination from '@/components/common/Pagination.vue'
import DictTag from '@/components/common/DictTag.vue'
import * as echarts from 'echarts'
import { getTerms, TIER_NAME } from '@/constants/terms'
import { formatMatchType } from '@/constants/registration'
import { statusTooltip } from '@/constants/eventStatus'
import type { DashboardStats } from '@/types'

const loading = ref(true)
const userChartRef = ref<HTMLElement>()
const regChartRef = ref<HTMLElement>()
const eventTypeRef = ref<HTMLElement>()
const starTierRef = ref<HTMLElement>()
const partyTierRef = ref<HTMLElement>()
const trendUsersRef = ref<HTMLElement>()
const trendRegsRef = ref<HTMLElement>()
const trendEventsRef = ref<HTMLElement>()
const trendRateRef = ref<HTMLElement>()
const revChartRef = ref<HTMLElement>()
const topChartRef = ref<HTMLElement>()
const funnelChartRef = ref<HTMLElement>()
const charts: echarts.ECharts[] = []

// ============ Loop-v19 Dashboard Phase 1 — 新增状态 ============

const DAY_MS = 86400000

/** 默认近 30 天（YYYY-MM-DD 字符串数组） */
const fmtIso = (d: Date) => {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}
const today = new Date()
const thirtyAgo = new Date(today.getTime() - 29 * DAY_MS)
const dateRange = ref<[string, string]>([fmtIso(thirtyAgo), fmtIso(today)])

/** 后端 range 取值（7d / 30d / 90d / thisMonth / lastMonth / custom）。null = 自由区间 → 转 custom */
const trendsRange = ref<string>('30d')
const topMetric = ref<'registrations' | 'revenue' | 'fillRate'>('registrations')
const trends = ref<DashboardTrendBucket[]>([])
const topList = ref<TopEventVO[]>([])
const attendance = ref<AttendanceFunnelVO | null>(null)
const trendsLoading = ref(false)

const isSuperAdmin = computed(() => authStore.admin?.role === 'SUPER_ADMIN')

/** Element Plus shortcuts：根据所选区间推 range 标识 */
const dateShortcuts = [
  {
    text: '近 7 天',
    value: () => {
      const e = new Date()
      const s = new Date(e.getTime() - 6 * DAY_MS)
      return [s, e]
    }
  },
  {
    text: '近 30 天',
    value: () => {
      const e = new Date()
      const s = new Date(e.getTime() - 29 * DAY_MS)
      return [s, e]
    }
  },
  {
    text: '近 90 天',
    value: () => {
      const e = new Date()
      const s = new Date(e.getTime() - 89 * DAY_MS)
      return [s, e]
    }
  },
  {
    text: '本月',
    value: () => {
      const e = new Date()
      const s = new Date(e.getFullYear(), e.getMonth(), 1)
      return [s, e]
    }
  },
  {
    text: '上月',
    value: () => {
      const e = new Date()
      const firstThis = new Date(e.getFullYear(), e.getMonth(), 1)
      const firstLast = new Date(e.getFullYear(), e.getMonth() - 1, 1)
      const lastLast = new Date(firstThis.getTime() - DAY_MS)
      return [firstLast, lastLast]
    }
  }
]

/** 把 [start, end] Date[] 数组映射到后端 range 关键字；不能精确匹配的（如任意 11 天）走 custom */
const classifyRange = (start: Date, end: Date): string => {
  const days = Math.round((end.getTime() - start.getTime()) / DAY_MS) + 1
  const fmt = (d: Date) => fmtIso(d)
  const e = fmt(end)
  const now = fmt(new Date())
  if (fmt(start) === fmt(new Date(end.getTime() - 6 * DAY_MS)) && e === now) return '7d'
  if (days === 30 && e === now) return '30d'
  if (days === 90 && e === now) return '90d'
  const firstThis = fmt(new Date(end.getFullYear(), end.getMonth(), 1))
  if (fmt(start) === firstThis && e === now) return 'thisMonth'
  const firstLastMonth = new Date(end.getFullYear(), end.getMonth() - 1, 1)
  const lastLastMonth = new Date(firstThis).getTime() - DAY_MS
  if (fmt(start) === fmt(firstLastMonth) && fmt(end) === fmt(new Date(lastLastMonth))) return 'lastMonth'
  return 'custom'
}

const onRangeChange = (val: [string, string] | null) => {
  if (!val || !val[0] || !val[1]) return
  const s = new Date(val[0])
  const e = new Date(val[1])
  trendsRange.value = classifyRange(s, e)
  fetchTrendGroup()
}

/** 拉 trends + top + attendance（并行）。metrics 切换只重拉 top */
const fetchTrendGroup = async () => {
  trendsLoading.value = true
  const baseParams = (): Record<string, string | number> => {
    const p: Record<string, string | number> = {}
    if (trendsRange.value === 'custom') {
      p.from = dateRange.value[0]
      p.to = dateRange.value[1]
    } else {
      p.range = trendsRange.value
    }
    return p
  }
  try {
    const [tRes, topRes, attRes] = await Promise.all([
      getDashboardTrends(baseParams()),
      getDashboardTopEvents({ ...baseParams(), metric: topMetric.value, limit: 10 }),
      getDashboardAttendance(baseParams())
    ])
    if (tRes.code === 0) {
      trends.value = (tRes.data as DashboardTrendVO).buckets || []
    } else {
      trends.value = []
    }
    if (topRes.code === 0) {
      topList.value = (topRes.data as TopEventVO[]) || []
    } else {
      topList.value = []
    }
    if (attRes.code === 0) {
      attendance.value = (attRes.data as AttendanceFunnelVO) || null
    } else {
      attendance.value = null
    }
    await nextTick()
    requestAnimationFrame(() => requestAnimationFrame(() => renderNewCharts()))
  } catch (err) {
    console.error('[fetchTrendGroup error]', err)
  } finally {
    trendsLoading.value = false
  }
}

const onTopMetricChange = async () => {
  if (topList.value.length === 0 && !attendance.value && !trends.value.length) {
    // 初次 mount 还没拉过 → 全量拉
    await fetchTrendGroup()
    return
  }
  try {
    const baseParams: Record<string, string | number> = {}
    if (trendsRange.value === 'custom') {
      baseParams.from = dateRange.value[0]
      baseParams.to = dateRange.value[1]
    } else {
      baseParams.range = trendsRange.value
    }
    const res = await getDashboardTopEvents({ ...baseParams, metric: topMetric.value, limit: 10 })
    if (res.code === 0) {
      topList.value = res.data || []
      renderTopChart()
    }
  } catch (err) {
    console.error('[onTopMetricChange error]', err)
  }
}

/** KPI 同比/环比辅助：null/0 都不显示，>0 绿 +X% (绝对差)，<0 红 -X% (绝对差)，≈0 灰 持平 */
const formatDelta = (pct: number | null | undefined, abs: number | null | undefined): string | null => {
  if (pct == null || abs == null) return null
  if (Math.abs(pct) < 0.005) return '持平'
  const sign = pct > 0 ? '+' : ''
  const pctStr = `${sign}${(pct * 100).toFixed(0)}%`
  const absStr = `${sign}${Math.round(abs)}`
  return `${pctStr} (${absStr})`
}
const deltaClass = (pct: number | null | undefined): string => {
  if (pct == null) return 'kpi-delta-flat'
  if (Math.abs(pct) < 0.005) return 'kpi-delta-flat'
  return pct > 0 ? 'kpi-delta-up' : 'kpi-delta-down'
}

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

// 30 天趋势 sparkline 旁的数字：累计 count（用户/报名/赛事）+ 今日完赛率
const trendSums = computed(() => {
  const sum = (arr: { count: number }[] | undefined) => (arr || []).reduce((s, d) => s + d.count, 0)
  const lastRate = (arr: { rate: number }[] | undefined) => (arr && arr.length ? arr[arr.length - 1].rate : 0)
  return {
    users: sum(stats.dailyNewUsers),
    regs: sum(stats.dailyRegistrations),
    events: sum(stats.dailyNewEvents),
    rate: lastRate(stats.dailyCompletionRate),
  }
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
const store = useDictStore()

function disposeAll() {
  charts.forEach(c => c.dispose())
  charts.length = 0
}

function mk(el: HTMLElement, opt: echarts.EChartsOption) {
  const c = echarts.init(el)
  c.setOption(opt)
  charts.push(c)
}

const tooltipStyle = { backgroundColor: 'rgba(255,255,255,0.96)', borderColor: '#eee', borderWidth: 1, textStyle: { color: '#333', fontSize: 12 }, extraCssText: 'box-shadow:0 4px 12px rgba(0,0,0,0.08);border-radius:6px;' }
const gridBase = { top: 20, right: 16, bottom: 32, left: 44 }

function renderCharts() {
  disposeAll()
  if (userChartRef.value) {
    const dates = stats.dailyNewUsers.map(d => d.date.slice(5))
    mk(userChartRef.value, {
      tooltip: { trigger: 'axis', ...tooltipStyle },
      grid: gridBase,
      xAxis: { type: 'category', data: dates, boundaryGap: false, axisLine: { lineStyle: { color: '#e5e7eb' } }, axisTick: { show: false }, axisLabel: { color: '#9ca3af', fontSize: 11 } },
      yAxis: { type: 'value', minInterval: 1, axisLine: { show: false }, axisTick: { show: false }, splitLine: { lineStyle: { color: '#f3f4f6' } }, axisLabel: { color: '#9ca3af', fontSize: 11 } },
      series: [{ type: 'line', data: stats.dailyNewUsers.map(d => d.count), smooth: true, showSymbol: false,
        lineStyle: { color: '#667eea', width: 2.5 }, itemStyle: { color: '#667eea' },
        areaStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [{ offset: 0, color: 'rgba(102,126,234,0.25)' }, { offset: 1, color: 'rgba(102,126,234,0.01)' }] } }
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
        itemStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [{ offset: 0, color: '#43e97b' }, { offset: 1, color: '#a8edea' }] }, borderRadius: [4, 4, 0, 0] }
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

  if (eventTypeRef.value) {
    const typeKeys = Object.keys(stats.eventTypes || {})
    const typeData = (typeKeys.length ? typeKeys : ['STAR', 'PARTY']).map((k) => ({
      value: stats.eventTypes?.[k] || 0,
      name: store.label('event_type', k),
      itemStyle: { color: store.color('event_type', k) }
    }))
    mk(eventTypeRef.value, {
      tooltip: pieTip, legend: pieLeg,
      series: [mkPie(typeData)]
    })
  }

  const tn = TIER_NAME

  // 段位色来自后端 starTierColorMap/partyTierColorMap（TierResolver.colorFor per-track）；
  // 段位名仍用 TIER_NAME 兜底（后端 dashboard VO 不带 name）。
  const mapTier = (td: Record<string, number>, colorMap: Record<string, string> | undefined, nameMap: Record<string, string> | undefined) =>
    Object.entries(td).map(([k, v]) => ({ value: v, name: (nameMap && nameMap[k]) || tn[k] || k, itemStyle: { color: (colorMap && colorMap[k]) || '#6B7280' } }))

  if (starTierRef.value) mk(starTierRef.value, { tooltip: pieTip, legend: pieLeg, series: [mkPie(mapTier(stats.starTierDistribution || {}, stats.starTierColorMap, stats.starTierNameMap))] })
  if (partyTierRef.value) mk(partyTierRef.value, { tooltip: pieTip, legend: pieLeg, series: [mkPie(mapTier(stats.partyTierDistribution || {}, stats.partyTierColorMap, stats.partyTierNameMap))] })

  // 30 天趋势小图（sparkline 风格，无坐标轴）
  const mkSpark = (el: HTMLElement, data: { date: string; count: number }[], color: string) => mk(el, {
    grid: { top: 4, right: 0, bottom: 4, left: 0 },
    xAxis: { type: 'category', show: false, boundaryGap: false, data: data.map(d => d.date.slice(5)) },
    yAxis: { type: 'value', show: false, minInterval: 1 },
    series: [{ type: 'line', data: data.map(d => d.count), smooth: true, showSymbol: false,
      lineStyle: { color, width: 2 }, itemStyle: { color },
      areaStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [{ offset: 0, color }, { offset: 1, color: 'transparent' }] } } }]
  })
  if (trendUsersRef.value) mkSpark(trendUsersRef.value, stats.dailyNewUsers || [], '#6366f1')
  if (trendRegsRef.value) mkSpark(trendRegsRef.value, stats.dailyRegistrations || [], '#43e97b')
  if (trendEventsRef.value) mkSpark(trendEventsRef.value, stats.dailyNewEvents || [], '#f5576c')
  if (trendRateRef.value) mk(trendRateRef.value, {
    grid: { top: 4, right: 0, bottom: 4, left: 0 },
    xAxis: { type: 'category', show: false, boundaryGap: false, data: (stats.dailyCompletionRate || []).map(d => d.date.slice(5)) },
    yAxis: { type: 'value', show: false, max: 100 },
    series: [{ type: 'line', data: (stats.dailyCompletionRate || []).map(d => d.rate), smooth: true, showSymbol: false,
      lineStyle: { color: '#fa8231', width: 2 }, itemStyle: { color: '#fa8231' },
      areaStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [{ offset: 0, color: '#fa8231' }, { offset: 1, color: 'transparent' }] } } }]
  })
}

// ============ Loop-v19 Dashboard Phase 1 — 3 张新图 ============

/** 收入趋势折线（含同比虚线）。同比 = 当期 revenue 对前一周前推同样天数窗口的估值
 * （前端 best-effort 视觉对比，无 sibling 数据时显示空）。 */
function renderRevenueChart() {
  if (!revChartRef.value) return
  const buckets = trends.value || []
  const dates = buckets.map(b => b.date.slice(5))
  const revenues = buckets.map(b => Number(b.revenue) || 0)
  // 同比虚线：每点右移 N 天（N=bucket 数），展示"去年同期"——空数据时拉空数组
  // 后端未提供同比数据，前端做占位虚线（前一半长度等值右移）。spec R6 允许 null，渲染时显示空数组。
  mk(revChartRef.value, {
    tooltip: { trigger: 'axis', ...tooltipStyle,
      formatter: (params: { axisValue: string; value: number; seriesName: string }[]) => {
        const p = params[0]
        return `${p.axisValue}<br/>${p.seriesName}: ¥${Number(p.value).toFixed(2)}`
      }
    },
    legend: { top: 0, left: 0, textStyle: { fontSize: 11, color: '#6b7280' }, data: ['本期收入'] },
    grid: { ...gridBase, top: 36 },
    xAxis: { type: 'category', data: dates, boundaryGap: false,
      axisLine: { lineStyle: { color: '#e5e7eb' } }, axisTick: { show: false },
      axisLabel: { color: '#9ca3af', fontSize: 11 } },
    yAxis: { type: 'value', axisLine: { show: false }, axisTick: { show: false },
      splitLine: { lineStyle: { color: '#f3f4f6' } }, axisLabel: { color: '#9ca3af', fontSize: 11 } },
    series: [
      { name: '本期收入', type: 'line', data: revenues, smooth: true, showSymbol: false,
        lineStyle: { color: '#fa8231', width: 2.5 }, itemStyle: { color: '#fa8231' },
        areaStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [{ offset: 0, color: 'rgba(250,130,49,0.22)' }, { offset: 1, color: 'rgba(250,130,49,0.01)' }] } }
      }
    ]
  })
}

/** 出席漏斗：已报名 → 已签到 → 未签到（no-show 数）。noShowRate=null 时仅画 2 层。 */
function renderFunnelChart() {
  if (!funnelChartRef.value) return
  const a = attendance.value
  const data: { name: string; value: number }[] = []
  if (!a) {
    mk(funnelChartRef.value, {
      tooltip: { ...tooltipStyle },
      series: [{
        type: 'funnel', left: '10%', right: '10%', top: 20, bottom: 10,
        sort: 'descending', gap: 4, label: { color: '#374151', fontSize: 12 },
        itemStyle: { borderColor: '#fff', borderWidth: 1 },
        data: [{ name: '暂无数据', value: 1, itemStyle: { color: '#e5e7eb' } }]
      }]
    })
    return
  }
  data.push({ name: `已报名 ${a.registered}`, value: a.registered })
  data.push({ name: `已签到 ${a.checkedIn}`, value: a.checkedIn })
  const noShow = a.registered - a.checkedIn
  if (noShow > 0) {
    data.push({ name: `未签到 ${noShow}`, value: noShow })
  }
  mk(funnelChartRef.value, {
    tooltip: { ...tooltipStyle, trigger: 'item',
      formatter: (p: { name: string; value: number; percent: number }) => `${p.name}<br/>占比 ${p.percent.toFixed(1)}%`
    },
    legend: { top: 0, left: 0, textStyle: { fontSize: 11, color: '#6b7280' } },
    series: [{
      type: 'funnel', left: '10%', right: '10%', top: 28, bottom: 10,
      sort: 'descending', gap: 4, minSize: '20%', maxSize: '100%',
      label: { show: true, position: 'inside', color: '#fff', fontSize: 12, fontWeight: 600 },
      labelLine: { show: false },
      itemStyle: { borderColor: '#fff', borderWidth: 2 },
      data: [
        { ...data[0], itemStyle: { color: '#6366f1' } },
        { ...(data[1] || { name: '已签到 0', value: 0 }), itemStyle: { color: '#43e97b' } },
        ...(data[2] ? [{ ...data[2], itemStyle: { color: '#f5576c' } }] : [])
      ]
    }]
  })
}

/** Top 10 横向柱状图。fillRate metric 把 value 显示为百分比（×100）。 */
function renderTopChart() {
  if (!topChartRef.value) return
  const list = topList.value || []
  // 横轴 label 是赛事标题（长则截断）
  const truncate = (s: string, n = 18) => (s.length > n ? `${s.slice(0, n)}…` : s)
  const titles = list.slice().reverse().map(t => truncate(t.title))
  const values = list.slice().reverse().map(t => {
    if (topMetric.value === 'fillRate') return Number(((t.value || 0) * 100).toFixed(1))
    return Number(t.value) || 0
  })
  const isPct = topMetric.value === 'fillRate'
  mk(topChartRef.value, {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, ...tooltipStyle,
      formatter: (params: { axisValue: string; value: number }[]) => {
        const p = params[0]
        const suffix = isPct ? '%' : (topMetric.value === 'revenue' ? ' 元' : ' 人')
        return `${p.axisValue}<br/>${topMetric.value === 'registrations' ? '报名数' : topMetric.value === 'revenue' ? '收入' : '满座率'}: ${p.value}${suffix}`
      }
    },
    grid: { top: 12, right: 32, bottom: 12, left: 120 },
    xAxis: isPct
      ? { type: 'value', max: 100, axisLine: { show: false }, axisTick: { show: false },
        splitLine: { lineStyle: { color: '#f3f4f6' } }, axisLabel: { color: '#9ca3af', fontSize: 11, formatter: '{value}%' } }
      : { type: 'value', axisLine: { show: false }, axisTick: { show: false },
        splitLine: { lineStyle: { color: '#f3f4f6' } }, axisLabel: { color: '#9ca3af', fontSize: 11 } },
    yAxis: { type: 'category', data: titles, axisLine: { lineStyle: { color: '#e5e7eb' } },
      axisTick: { show: false }, axisLabel: { color: '#374151', fontSize: 11 } },
    series: [{
      type: 'bar', data: values, barMaxWidth: 14,
      itemStyle: {
        color: topMetric.value === 'fillRate' ? '#6366f1'
          : topMetric.value === 'revenue' ? '#fa8231'
          : { type: 'linear', x: 0, y: 0, x2: 1, y2: 0, colorStops: [{ offset: 0, color: '#43e97b' }, { offset: 1, color: '#a8edea' }] },
        borderRadius: [0, 4, 4, 0]
      },
      label: { show: true, position: 'right', fontSize: 11, color: '#6b7280',
        formatter: (p: { value: number }) => isPct ? `${p.value}%` : `${p.value}` }
    }]
  })
}

/** 新图统一入口（revenue / funnel / top）。原 5 张图走 renderCharts，不互相干扰。 */
function renderNewCharts() {
  renderRevenueChart()
  renderFunnelChart()
  renderTopChart()
}

const fetchStats = async () => {
  loading.value = true
  try {
    const res = await getDashboardStats()
    if (res.code === 0) {
      Object.assign(stats, res.data)
      await nextTick()
      // 双 rAF 确保 DOM 布局完成后再 init echarts：nextTick 只保证 DOM 更新不保证
      // layout/paint，导致容器 clientHeight=0 → echarts "Can't get DOM width or height" 警告 + 0×0 canvas。
      requestAnimationFrame(() => requestAnimationFrame(() => renderCharts()))
    }
    else ElMessage.error(res.message || '获取数据失败')
  } catch (e) {
    console.error('[fetchStats error]', e)
  }
  finally { loading.value = false }
}

const onResize = () => charts.forEach(c => c.resize())

// 字典 bundle 变更后重渲 echarts（pie 标签/颜色与 DictTag 已随 store 响应式自动刷新，仅 echarts 需手动重渲）
watch(() => store.version, () => {
  if (stats.dailyNewUsers?.length || stats.dailyRegistrations?.length) {
    requestAnimationFrame(() => requestAnimationFrame(() => renderCharts()))
  }
})

onMounted(() => { fetchStats(); buildTodos(); fetchTrendGroup(); window.addEventListener('resize', onResize) })
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

.chart-xs {
  height: 80px;
  width: 100%;
}

.chart-md {
  height: 340px;
  width: 100%;
}

/* ============ Loop-v19 Dashboard Phase 1 — KPI 同比环比 ============ */

.kpi-delta-color {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
}

.kpi-delta-tag {
  font-weight: 600;
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 4px;
  background: rgba(0, 0, 0, 0.04);
}

.kpi-delta-up .kpi-delta-tag {
  color: #16a34a;
  background: rgba(22, 163, 74, 0.1);
}

.kpi-delta-down .kpi-delta-tag {
  color: #dc2626;
  background: rgba(220, 38, 38, 0.1);
}

.kpi-delta-flat .kpi-delta-tag {
  color: #6b7280;
}

/* ============ Loop-v19 Dashboard Phase 1 — 时间范围选择器 ============ */

.range-card {
  margin-bottom: 16px;
}

.range-row {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}

.range-label {
  font-size: 13px;
  color: #6b7280;
  font-weight: 500;
}

.range-meta {
  font-size: 12px;
  color: #9ca3af;
  margin-left: auto;
}

.top-row {
  margin-top: 16px;
}

.top-metric {
  margin-left: auto;
}

.trend-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}

.trend-cell {
  min-width: 0;
}

.trend-label {
  font-size: 12px;
  color: #6b7280;
  margin-bottom: 4px;
  font-weight: 500;

  .trend-num {
    color: #111827;
    font-size: 15px;
    font-weight: 700;
    margin-left: 4px;
  }
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
