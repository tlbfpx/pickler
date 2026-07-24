<template>
  <div>
    <div class="page-header">
      <h1>预约管理</h1>
    </div>
    <div class="card">
      <!-- filter bar -->
      <div class="filter-bar">
        <el-input v-model.number="filterVenueId" placeholder="场馆 ID" clearable
                  type="number" style="width: 120px" @change="handleFilter" />
        <el-input v-model.number="filterCourtId" placeholder="场地 ID" clearable
                  type="number" style="width: 120px" @change="handleFilter" />
        <el-date-picker v-model="filterDateRange" type="daterange" range-separator="至"
                        start-placeholder="起始日期" end-placeholder="结束日期"
                        value-format="YYYY-MM-DD"
                        style="width: 240px" @change="handleFilter" />
        <el-select v-model="filterStatus" placeholder="状态" clearable
                   style="width: 140px" @change="handleFilter">
          <el-option label="待履约" value="CONFIRMED" />
          <el-option label="已取消" value="CANCELLED" />
          <el-option label="已完成" value="COMPLETED" />
          <el-option label="未到" value="NO_SHOW" />
        </el-select>
        <el-input v-model="filterKeyword" placeholder="订单号 / 用户 ID" clearable
                  style="width: 180px" @keyup.enter="handleFilter" />
        <el-button type="primary" @click="handleFilter">查询</el-button>
      </div>

      <el-table v-loading="loading" :data="bookingList" style="width: 100%; margin-top: 16px">
        <el-table-column prop="bookingNo" label="订单号" width="180" />
        <el-table-column label="用户" width="220">
          <template #default="{ row }">
            <div>{{ row.userNickname || '用户' + row.userId }}</div>
            <div class="cell-sub">
              {{ row.userPhone || '-' }} · ID: {{ row.userId }}
            </div>
          </template>
        </el-table-column>
        <el-table-column label="场地" width="200">
          <template #default="{ row }">
            <div>{{ row.venueName }}</div>
            <div class="cell-sub">{{ row.courtName }}（{{ row.courtType === 'INDOOR' ? '室内' : '室外' }}）</div>
          </template>
        </el-table-column>
        <el-table-column prop="slotDate" label="日期" width="110" />
        <el-table-column label="起止" width="170">
          <template #default="{ row }">
            <div>{{ (row.slotStart || '').slice(11, 16) }}</div>
            <div class="cell-sub">至 {{ (row.slotEnd || '').slice(11, 16) }} ({{ row.slotsCount }}格)</div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusColor(row.status)" effect="dark">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="金额" width="90">
          <template #default="{ row }">¥{{ Number(row.priceSnapshot).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="openDetail(row)">详情</el-button>
            <el-button type="success" size="small"
                       :disabled="row.status !== 'CONFIRMED'"
                       @click="handleComplete(row)">完成</el-button>
            <el-button type="warning" size="small"
                       :disabled="row.status !== 'CONFIRMED'"
                       @click="handleNoShow(row)">爽约</el-button>
            <el-button type="danger" size="small"
                       :disabled="row.status !== 'CONFIRMED'"
                       @click="handleForceCancel(row)">强制取消</el-button>
          </template>
        </el-table-column>
      </el-table>

      <Pagination
        v-model:page="pagination.page"
        v-model:size="pagination.size"
        :total="pagination.total"
        @update:page="fetchBookings"
        @update:size="fetchBookings"
      />
    </div>

    <!-- detail dialog -->
    <el-dialog v-model="detailVisible" title="预约详情" width="600px">
      <template v-if="current">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="订单号">{{ current.bookingNo }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusColor(current.status)" effect="dark">{{ statusLabel(current.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="用户">{{ current.userNickname }}（{{ current.userPhone }}）ID:{{ current.userId }}</el-descriptions-item>
          <el-descriptions-item label="场地">{{ current.venueName }} / {{ current.courtName }}（{{ current.courtType === 'INDOOR' ? '室内' : '室外' }}）</el-descriptions-item>
          <el-descriptions-item label="日期">{{ current.slotDate }}</el-descriptions-item>
          <el-descriptions-item label="起止">
            {{ (current.slotStart || '').slice(0, 16) }} → {{ (current.slotEnd || '').slice(0, 16) }}（{{ current.slotsCount }}格）
          </el-descriptions-item>
          <el-descriptions-item label="金额">¥{{ Number(current.priceSnapshot).toFixed(2) }}</el-descriptions-item>
          <el-descriptions-item label="下单时间">{{ current.createdAt }}</el-descriptions-item>
          <el-descriptions-item v-if="current.cancelledAt" label="取消时间">
            {{ current.cancelledAt }}（{{ current.cancelReason }}）
          </el-descriptions-item>
        </el-descriptions>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getBookingList,
  getBookingDetail,
  completeBooking,
  noShowBooking,
  forceCancelBooking
} from '@/api/bookings'
import Pagination from '@/components/common/Pagination.vue'
import type { BookingAdmin, BookingStatus } from '@/types'

const loading = ref(false)
const bookingList = ref<BookingAdmin[]>([])
const detailVisible = ref(false)
const current = ref<BookingAdmin | null>(null)

const filterVenueId = ref<number | undefined>(undefined)
const filterCourtId = ref<number | undefined>(undefined)
const filterDateRange = ref<[string, string] | null>(null)
const filterStatus = ref<BookingStatus | ''>('')
const filterKeyword = ref('')

const pagination = reactive({ page: 1, size: 20, total: 0 })

const statusLabel = (s: BookingStatus) =>
  ({ CONFIRMED: '待履约', CANCELLED: '已取消', COMPLETED: '已完成', NO_SHOW: '未到' } as const)[s]
const statusColor = (s: BookingStatus) =>
  ({ CONFIRMED: 'primary', CANCELLED: 'danger', COMPLETED: 'success', NO_SHOW: 'warning' } as const)[s]

const buildQuery = () => ({
  venueId: filterVenueId.value,
  courtId: filterCourtId.value,
  dateFrom: filterDateRange.value?.[0],
  dateTo: filterDateRange.value?.[1],
  status: filterStatus.value || undefined,
  keyword: filterKeyword.value || undefined,
  page: pagination.page,
  size: pagination.size
})

const fetchBookings = async () => {
  loading.value = true
  try {
    const res = await getBookingList(buildQuery())
    if (res.code === 0) {
      bookingList.value = res.data.list || []
      pagination.total = res.data.total || 0
    } else {
      ElMessage.error(res.message || '获取预约列表失败')
    }
  } catch {
    /* interceptor already toasted */
  } finally {
    loading.value = false
  }
}

const handleFilter = () => { pagination.page = 1; fetchBookings() }

const openDetail = async (row: BookingAdmin) => {
  const res = await getBookingDetail(row.id)
  if (res.code === 0) {
    current.value = res.data
    detailVisible.value = true
  } else {
    ElMessage.error(res.message || '获取详情失败')
  }
}

const handleComplete = async (row: BookingAdmin) => {
  try {
    await ElMessageBox.confirm(`将「${row.bookingNo}」标记为已完成？`, '确认', { type: 'warning' })
  } catch { return }
  const res = await completeBooking(row.id)
  if (res.code === 0) { ElMessage.success('已标记完成'); fetchBookings() }
  else ElMessage.error(res.message || '操作失败')
}

const handleNoShow = async (row: BookingAdmin) => {
  try {
    await ElMessageBox.confirm(`将「${row.bookingNo}」标记为爽约？`, '确认', { type: 'warning' })
  } catch { return }
  const res = await noShowBooking(row.id)
  if (res.code === 0) { ElMessage.success('已标记爽约'); fetchBookings() }
  else ElMessage.error(res.message || '操作失败')
}

const handleForceCancel = async (row: BookingAdmin) => {
  let reason = ''
  try {
    const r = await ElMessageBox.prompt('管理员强制取消(可填原因):', '强制取消', {
      confirmButtonText: '确认取消',
      cancelButtonText: '返回',
      inputPlaceholder: '可选 - 取消原因',
      inputValue: ''
    })
    reason = (r.value as string) || ''
  } catch { return }
  const res = await forceCancelBooking(row.id, { reason })
  if (res.code === 0) { ElMessage.success('已强制取消'); fetchBookings() }
  else ElMessage.error(res.message || '操作失败')
}

onMounted(() => { fetchBookings() })
</script>

<style scoped>
.cell-sub {
  font-size: 12px;
  color: #909399;
}
</style>
