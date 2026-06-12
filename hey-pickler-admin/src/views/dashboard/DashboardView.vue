<template>
  <div v-loading="loading" class="dashboard">
    <div class="page-header"><h1>首页</h1></div>

    <!-- KPI -->
    <div class="kpi-row">
      <div class="kpi-card">
        <div class="kpi-top" style="background:#667eea;"></div>
        <div class="kpi-body">
          <div class="kpi-value">{{ stats.totalUsers }}</div>
          <div class="kpi-label">总用户</div>
        </div>
        <div class="kpi-delta">本周 +{{ stats.newUsersWeek }}</div>
      </div>
      <div class="kpi-card">
        <div class="kpi-top" style="background:#f5576c;"></div>
        <div class="kpi-body">
          <div class="kpi-value">{{ stats.totalEvents }}</div>
          <div class="kpi-label">总赛事</div>
        </div>
        <div class="kpi-delta">报名中 {{ stats.openEvents }}</div>
      </div>
      <div class="kpi-card">
        <div class="kpi-top" style="background:#43e97b;"></div>
        <div class="kpi-body">
          <div class="kpi-value">{{ stats.recentRegistrationsCount }}</div>
          <div class="kpi-label">本周报名</div>
        </div>
        <div class="kpi-delta">累计 {{ stats.totalRegistrations }} 笔</div>
      </div>
      <div class="kpi-card">
        <div class="kpi-top" style="background:#fa8231;"></div>
        <div class="kpi-body">
          <div class="kpi-value">¥{{ stats.totalRevenue }}</div>
          <div class="kpi-label">报名收入</div>
        </div>
        <div class="kpi-delta">本周 ¥{{ stats.weeklyRevenue }}</div>
      </div>
    </div>

    <!-- Trends: full-width row -->
    <div class="row">
      <div class="panel" style="flex:1.2">
        <div class="panel-head">
          <span>用户增长趋势</span>
          <span class="panel-tag">近 30 天</span>
        </div>
        <div ref="userChartRef" class="chart-lg"></div>
      </div>
      <div class="panel" style="flex:0.8">
        <div class="panel-head">
          <span>报名趋势</span>
          <span class="panel-tag">近 30 天</span>
        </div>
        <div ref="regChartRef" class="chart-lg"></div>
      </div>
    </div>

    <!-- Distribution -->
    <div class="row">
      <div class="panel" style="flex:1">
        <div class="panel-head"><span>赛事类型分布</span></div>
        <div ref="eventTypeRef" class="chart-sm"></div>
      </div>
      <div class="panel" style="flex:1">
        <div class="panel-head"><span>明星段位分布</span></div>
        <div ref="starTierRef" class="chart-sm"></div>
      </div>
      <div class="panel" style="flex:1">
        <div class="panel-head"><span>派对段位分布</span></div>
        <div ref="partyTierRef" class="chart-sm"></div>
      </div>
    </div>

    <!-- Tables -->
    <div class="row">
      <div class="panel" style="flex:1">
        <div class="panel-head"><span>即将开始的赛事</span></div>
        <el-table :data="stats.upcomingEvents" size="small" stripe>
          <el-table-column prop="title" label="赛事" min-width="150" show-overflow-tooltip />
          <el-table-column label="类型" width="70" align="center">
            <template #default="{ row }">
              <el-tag :type="row.type === 'STAR' ? 'warning' : 'danger'" size="small" round>
                {{ row.type === 'STAR' ? '明星' : '派对' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="时间" width="90" align="center">
            <template #default="{ row }">{{ fmtDate(row.eventTime) }}</template>
          </el-table-column>
          <el-table-column label="报名" width="80" align="center">
            <template #default="{ row }">{{ row.currentParticipants }}/{{ row.maxParticipants ?? '-' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="76" align="center">
            <template #default="{ row }">
              <el-tag :type="sType(row.status)" size="small" round>{{ sLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!stats.upcomingEvents?.length" :image-size="40" />
      </div>
      <div class="panel" style="flex:1">
        <div class="panel-head"><span>最新报名</span></div>
        <el-table :data="stats.recentRegistrations" size="small" stripe>
          <el-table-column label="用户" width="80" show-overflow-tooltip>
            <template #default="{ row }">{{ row.nickname || '未知' }}</template>
          </el-table-column>
          <el-table-column prop="eventTitle" label="赛事" min-width="150" show-overflow-tooltip />
          <el-table-column label="类型" width="56" align="center">
            <template #default="{ row }">{{ row.matchType === 'SINGLES' ? '单打' : row.matchType === 'DOUBLES' ? '双打' : '混双' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="70" align="center">
            <template #default="{ row }">
              <el-tag :type="rType(row.status)" size="small" round>{{ rLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="时间" width="90" align="center">
            <template #default="{ row }">{{ fmtDate(row.createdAt) }}</template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!stats.recentRegistrations?.length" :image-size="40" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, nextTick, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { getDashboardStats } from '@/api/dashboard'
import * as echarts from 'echarts'
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

const fmtDate = (d: string | null) => {
  if (!d) return '-'
  const dt = new Date(d)
  return `${(dt.getMonth() + 1).toString().padStart(2, '0')}-${dt.getDate().toString().padStart(2, '0')}`
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
    series: [mkPie([{ value: stats.eventTypes?.STAR || 0, name: '明星赛事', itemStyle: { color: '#E6A23C' } }, { value: stats.eventTypes?.PARTY || 0, name: '派对活动', itemStyle: { color: '#F56C6C' } }])]
  })

  const tc: Record<string, string> = { LEGEND: '#E6A23C', SUPER: '#9C27B0', SHINING: '#909399' }
  const tn: Record<string, string> = { LEGEND: '传奇', SUPER: '超级', SHINING: '闪耀' }

  const mapTier = (td: Record<string, number>) => Object.entries(td).map(([k, v]) => ({ value: v, name: tn[k] || k, itemStyle: { color: tc[k] } }))

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

onMounted(() => { fetchStats(); window.addEventListener('resize', () => charts.forEach(c => c.resize())) })
onBeforeUnmount(() => { charts.forEach(c => c.dispose()) })
</script>

<style scoped>
.dashboard {
  max-width: 1360px;
}

/* KPI */
.kpi-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 16px;
}

.kpi-card {
  background: #fff;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
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
  margin-bottom: 16px;
}

.panel {
  background: #fff;
  border-radius: 8px;
  padding: 20px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
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
  .panel { flex: 1 1 45% !important; min-width: 300px; }
}
</style>
