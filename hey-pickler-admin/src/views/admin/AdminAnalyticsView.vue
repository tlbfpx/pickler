<template>
  <div
    v-loading="loading"
    class="analytics"
  >
    <div class="page-header">
      <h1>数据分析</h1>
      <span class="page-sub">截至 {{ fetchedAt }} 的总体数据快照</span>
    </div>

    <!-- Row 1: 4 KPI cards -->
    <el-card
      v-if="data"
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
              {{ data.totals.users }}
            </div>
            <div class="kpi-label">
              总用户
            </div>
          </div>
          <div class="kpi-delta">
            累计注册用户
          </div>
        </div>
        <div class="kpi-card">
          <div
            class="kpi-top"
            style="background:#f5576c;"
          />
          <div class="kpi-body">
            <div class="kpi-value">
              {{ data.totals.events }}
            </div>
            <div class="kpi-label">
              总赛事
            </div>
          </div>
          <div class="kpi-delta">
            含已结束/已取消
          </div>
        </div>
        <div class="kpi-card">
          <div
            class="kpi-top"
            style="background:#43e97b;"
          />
          <div class="kpi-body">
            <div class="kpi-value">
              {{ data.totals.registrations }}
            </div>
            <div class="kpi-label">
              总报名
            </div>
          </div>
          <div class="kpi-delta">
            有效报名
          </div>
        </div>
        <div class="kpi-card">
          <div
            class="kpi-top"
            style="background:#fa8231;"
          />
          <div class="kpi-body">
            <div class="kpi-value">
              ¥{{ formatMoney(data.totals.revenue) }}
            </div>
            <div class="kpi-label">
              总收入
            </div>
          </div>
          <div class="kpi-delta">
            报名费累计
          </div>
        </div>
      </div>
    </el-card>

    <!-- Row 2: 3 stat cards -->
    <el-card
      v-if="data"
      shadow="never"
      class="page-card"
      :body-style="{ padding: '20px 24px' }"
    >
      <div class="stat-row">
        <div class="stat-card">
          <div class="stat-value">
            {{ formatPct(data.completionRate) }}<span class="stat-unit">%</span>
          </div>
          <div class="stat-label">
            完赛率
          </div>
          <div class="stat-sub">
            已完成赛事 / 非取消赛事
          </div>
        </div>
        <div class="stat-card">
          <div class="stat-value">
            {{ formatNum(data.registrationPerEvent) }}<span class="stat-unit">人/场</span>
          </div>
          <div class="stat-label">
            报名转化率
          </div>
          <div class="stat-sub">
            总报名 / 总赛事
          </div>
        </div>
        <div class="stat-card">
          <div class="stat-value">
            {{ data.activeUsersLast30d }}<span class="stat-unit">人</span>
          </div>
          <div class="stat-label">
            活跃用户（30 天）
          </div>
          <div class="stat-sub">
            报名或创建赛事的去重人数
          </div>
        </div>
      </div>
    </el-card>

    <!-- Row 3: 12-month trend -->
    <el-card
      v-if="data"
      shadow="never"
      class="page-card"
      :body-style="{ padding: '20px 24px' }"
    >
      <div class="panel-head">
        <span>12 个月趋势</span>
        <span class="panel-tag">新增用户 / 新增赛事 / 新增报名</span>
      </div>
      <div
        ref="trendChartRef"
        class="chart-lg"
      />
    </el-card>

    <!-- Row 4: type pie + status bar side-by-side -->
    <el-card
      v-if="data"
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
            <span class="panel-tag">STAR vs PARTY</span>
          </div>
          <div
            ref="typeChartRef"
            class="chart-md"
          />
        </div>
        <div
          class="panel-cell"
          style="flex:1"
        >
          <div class="panel-head">
            <span>赛事状态分布</span>
            <span class="panel-tag">当前全量</span>
          </div>
          <div
            ref="statusChartRef"
            class="chart-md"
          />
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import { getAnalyticsDashboard, type AnalyticsDashboard } from '@/api/analytics'

const loading = ref(true)
const data = ref<AnalyticsDashboard | null>(null)
const fetchedAt = ref<string>('')

const trendChartRef = ref<HTMLElement>()
const typeChartRef = ref<HTMLElement>()
const statusChartRef = ref<HTMLElement>()
const charts: echarts.ECharts[] = []

const tooltipStyle = { backgroundColor: 'rgba(255,255,255,0.96)', borderColor: '#eee', borderWidth: 1, textStyle: { color: '#333', fontSize: 12 }, extraCssText: 'box-shadow:0 4px 12px rgba(0,0,0,0.08);border-radius:6px;' }

const STATUS_LABEL: Record<string, string> = {
  DRAFT: '草稿',
  OPEN: '报名中',
  FULL: '已满员',
  IN_PROGRESS: '进行中',
  COMPLETED: '已结束',
  CANCELLED: '已取消'
}
const STATUS_COLOR: Record<string, string> = {
  DRAFT: '#9ca3af',
  OPEN: '#43e97b',
  FULL: '#fa8231',
  IN_PROGRESS: '#E6A23C',
  COMPLETED: '#6366f1',
  CANCELLED: '#F56C6C'
}

function formatMoney(v: number) {
  return (Math.round(v * 100) / 100).toFixed(2)
}
function formatNum(v: number) {
  return (Math.round(v * 10) / 10).toFixed(1)
}
function formatPct(v: number) {
  return (Math.round(v * 10) / 10).toFixed(1)
}

function mk(el: HTMLElement, opt: echarts.EChartsOption) {
  const c = echarts.init(el)
  c.setOption(opt)
  charts.push(c)
}

function renderCharts() {
  if (!data.value) return

  // 12-month line chart
  if (trendChartRef.value) {
    const months = data.value.byMonth.map(m => m.month.slice(2)) // "26-01"
    mk(trendChartRef.value, {
      tooltip: { trigger: 'axis', ...tooltipStyle },
      legend: { top: 0, left: 0, icon: 'roundRect', itemWidth: 12, itemHeight: 4, itemGap: 16, textStyle: { fontSize: 12, color: '#6b7280' } },
      grid: { top: 36, right: 24, bottom: 28, left: 48 },
      xAxis: { type: 'category', data: months, boundaryGap: false, axisLine: { lineStyle: { color: '#e5e7eb' } }, axisTick: { show: false }, axisLabel: { color: '#9ca3af', fontSize: 11 } },
      yAxis: { type: 'value', minInterval: 1, axisLine: { show: false }, axisTick: { show: false }, splitLine: { lineStyle: { color: '#f3f4f6' } }, axisLabel: { color: '#9ca3af', fontSize: 11 } },
      series: [
        {
          name: '新增用户', type: 'line', smooth: true, showSymbol: false,
          data: data.value.byMonth.map(m => m.users),
          lineStyle: { color: '#6366f1', width: 2 },
          itemStyle: { color: '#6366f1' },
          areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: 'rgba(99,102,241,0.25)' }, { offset: 1, color: 'rgba(99,102,241,0.02)' }]) }
        },
        {
          name: '新增赛事', type: 'line', smooth: true, showSymbol: false,
          data: data.value.byMonth.map(m => m.events),
          lineStyle: { color: '#f5576c', width: 2 },
          itemStyle: { color: '#f5576c' },
          areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: 'rgba(245,87,108,0.22)' }, { offset: 1, color: 'rgba(245,87,108,0.02)' }]) }
        },
        {
          name: '新增报名', type: 'line', smooth: true, showSymbol: false,
          data: data.value.byMonth.map(m => m.registrations),
          lineStyle: { color: '#43e97b', width: 2 },
          itemStyle: { color: '#43e97b' },
          areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: 'rgba(67,233,123,0.20)' }, { offset: 1, color: 'rgba(67,233,123,0.02)' }]) }
        }
      ]
    })
  }

  // Type pie
  if (typeChartRef.value) {
    const star = data.value.byType?.STAR || 0
    const party = data.value.byType?.PARTY || 0
    mk(typeChartRef.value, {
      tooltip: { trigger: 'item', ...tooltipStyle, formatter: '{b}: {c} ({d}%)' },
      legend: { bottom: 4, itemWidth: 10, itemHeight: 10, itemGap: 14, textStyle: { fontSize: 11, color: '#6b7280' } },
      series: [{
        type: 'pie', radius: ['38%', '64%'], center: ['50%', '44%'],
        avoidLabelOverlap: false, label: { show: false },
        itemStyle: { borderRadius: 3, borderColor: '#fff', borderWidth: 2 },
        data: [
          { value: star, name: 'STAR 竞技', itemStyle: { color: '#E6A23C' } },
          { value: party, name: 'PARTY 社交', itemStyle: { color: '#F56C6C' } }
        ]
      }]
    })
  }

  // Status bar
  if (statusChartRef.value) {
    const order = ['DRAFT', 'OPEN', 'FULL', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED']
    mk(statusChartRef.value, {
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, ...tooltipStyle },
      grid: { top: 16, right: 16, bottom: 36, left: 56 },
      xAxis: {
        type: 'category',
        data: order.map(s => STATUS_LABEL[s] || s),
        axisLine: { lineStyle: { color: '#e5e7eb' } },
        axisTick: { show: false },
        axisLabel: { color: '#6b7280', fontSize: 11, interval: 0 }
      },
      yAxis: { type: 'value', minInterval: 1, axisLine: { show: false }, axisTick: { show: false }, splitLine: { lineStyle: { color: '#f3f4f6' } }, axisLabel: { color: '#9ca3af', fontSize: 11 } },
      series: [{
        type: 'bar', barMaxWidth: 28, barCategoryGap: '40%',
        itemStyle: {
          borderRadius: [4, 4, 0, 0],
          color: (params: { dataIndex: number }) => STATUS_COLOR[order[params.dataIndex]] || '#6366f1'
        },
        data: order.map(s => data.value!.byStatus?.[s] || 0)
      }]
    })
  }
}

const onResize = () => charts.forEach(c => c.resize())

const fetch = async () => {
  loading.value = true
  try {
    const res = await getAnalyticsDashboard()
    if (res.code === 0 && res.data) {
      data.value = res.data
      fetchedAt.value = new Date().toLocaleString('zh-CN', { hour12: false })
      await nextTick()
      renderCharts()
    } else {
      ElMessage.error(res.message || '获取数据失败')
    }
  } finally {
    loading.value = false
  }
}

onMounted(() => { fetch(); window.addEventListener('resize', onResize) })
onBeforeUnmount(() => { window.removeEventListener('resize', onResize); charts.forEach(c => c.dispose()) })
</script>

<style scoped>
.analytics {
  max-width: 1360px;
}

.analytics .page-header {
  margin-bottom: 16px;
  display: flex;
  align-items: baseline;
  gap: 12px;

  h1 {
    font-size: 24px;
    font-weight: 600;
    color: #111827;
  }
}

.page-sub {
  font-size: 12px;
  color: #9ca3af;
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

/* Stats */
.stat-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}

.stat-card {
  background: #fafbfc;
  border: 1px solid #f3f4f6;
  border-radius: 8px;
  padding: 24px 20px;
  text-align: center;
}

.stat-value {
  font-size: 36px;
  font-weight: 700;
  color: #111827;
  line-height: 1;
}

.stat-unit {
  font-size: 14px;
  font-weight: 500;
  color: #9ca3af;
  margin-left: 4px;
}

.stat-label {
  font-size: 14px;
  font-weight: 600;
  color: #374151;
  margin-top: 10px;
}

.stat-sub {
  font-size: 12px;
  color: #9ca3af;
  margin-top: 4px;
}

.row {
  display: flex;
  gap: 16px;
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
  height: 320px;
  width: 100%;
}

.chart-md {
  height: 280px;
  width: 100%;
}

@media (max-width: 1100px) {
  .kpi-row { grid-template-columns: repeat(2, 1fr); }
  .stat-row { grid-template-columns: 1fr; }
  .row { flex-wrap: wrap; }
  .panel-cell { flex: 1 1 100% !important; min-width: 0; }
}
</style>
