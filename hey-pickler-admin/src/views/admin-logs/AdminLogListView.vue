<template>
  <div>
    <div class="page-header">
      <h1>操作日志</h1>
    </div>
    <div class="card">
      <div class="filter-bar">
        <el-input
          v-model="filterOperatorId"
          placeholder="操作人 ID"
          style="width: 130px"
          clearable
          @clear="handleFilter"
          @keyup.enter="handleFilter"
        />
        <el-select
          v-model="filterModule"
          placeholder="模块"
          clearable
          style="width: 150px"
          @change="handleFilter"
        >
          <el-option
            v-for="opt in moduleOptions"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
        <el-select
          v-model="filterStatus"
          placeholder="状态"
          clearable
          style="width: 130px"
          @change="handleFilter"
        >
          <el-option
            label="成功"
            :value="1"
          />
          <el-option
            label="失败"
            :value="0"
          />
        </el-select>
        <el-date-picker
          v-model="filterTimeRange"
          type="datetimerange"
          range-separator="至"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          format="YYYY-MM-DD HH:mm:ss"
          value-format="YYYY-MM-DDTHH:mm:ss"
          @change="handleFilter"
        />
        <el-button
          type="primary"
          @click="handleFilter"
        >
          搜索
        </el-button>
        <el-button @click="handleReset">
          重置
        </el-button>
      </div>

      <el-table
        v-loading="loading"
        :data="logList"
        style="width: 100%; margin-top: 16px"
        @row-click="handleRowClick"
      >
        <el-table-column
          label="时间"
          width="170"
        >
          <template #default="{ row }">
            {{ formatDate(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column
          label="操作人"
          width="150"
        >
          <template #default="{ row }">
            <div class="operator-cell">
              <span class="operator-name">{{ row.operatorName || '匿名' }}</span>
              <span class="operator-sub">#{{ row.operatorId ?? '-' }} · {{ row.operatorRole }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column
          prop="method"
          label="方法"
          width="80"
        >
          <template #default="{ row }">
            <el-tag
              :type="methodTagType(row.method)"
              size="small"
            >
              {{ row.method }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          prop="module"
          label="模块"
          width="120"
        />
        <el-table-column
          prop="action"
          label="动作"
          width="130"
        />
        <el-table-column
          label="目标"
          width="180"
        >
          <template #default="{ row }">
            <span v-if="row.targetType">{{ row.targetType }}<span v-if="row.targetId"> #{{ row.targetId }}</span></span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column
          label="状态"
          width="100"
        >
          <template #default="{ row }">
            <el-tag
              :type="row.status === 1 ? 'success' : 'danger'"
              size="small"
            >
              {{ row.status === 1 ? '成功' : '失败' }}
            </el-tag>
            <div
              v-if="row.status === 0"
              class="error-sub"
            >
              {{ row.errorCode }}{{ row.errorMsg ? ': ' + truncate(row.errorMsg, 40) : '' }}
            </div>
          </template>
        </el-table-column>
        <el-table-column
          label="耗时"
          width="80"
        >
          <template #default="{ row }">
            {{ row.latencyMs != null ? row.latencyMs + 'ms' : '-' }}
          </template>
        </el-table-column>
        <el-table-column
          prop="ip"
          label="IP"
          width="130"
        />
        <el-table-column
          label="路径"
          min-width="220"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            <span class="path-text">{{ row.path }}</span>
          </template>
        </el-table-column>
      </el-table>

      <Pagination
        v-model:page="pagination.page"
        v-model:size="pagination.size"
        :total="pagination.total"
        @update:page="fetchLogs"
        @update:size="fetchLogs"
      />
    </div>

    <el-drawer
      v-model="detailVisible"
      title="操作详情"
      size="45%"
    >
      <div
        v-if="selectedLog"
        class="detail-content"
      >
        <el-descriptions
          :column="2"
          border
        >
          <el-descriptions-item label="时间">
            {{ formatDate(selectedLog.createdAt) }}
          </el-descriptions-item>
          <el-descriptions-item label="操作人">
            {{ selectedLog.operatorName || '匿名' }} (#{{ selectedLog.operatorId ?? '-' }})
          </el-descriptions-item>
          <el-descriptions-item label="角色">
            {{ selectedLog.operatorRole }}
          </el-descriptions-item>
          <el-descriptions-item label="方法/模块/动作">
            {{ selectedLog.method }} / {{ selectedLog.module }} / {{ selectedLog.action }}
          </el-descriptions-item>
          <el-descriptions-item label="目标">
            {{ selectedLog.targetType || '-' }} {{ selectedLog.targetId ? '#' + selectedLog.targetId : '' }}
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag
              :type="selectedLog.status === 1 ? 'success' : 'danger'"
              size="small"
            >
              {{ selectedLog.status === 1 ? '成功' : '失败 ' + (selectedLog.errorCode ?? '') }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="IP">
            {{ selectedLog.ip || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="耗时">
            {{ selectedLog.latencyMs ?? '-' }} ms
          </el-descriptions-item>
          <el-descriptions-item
            label="路径"
            :span="2"
          >
            {{ selectedLog.path }}
          </el-descriptions-item>
          <el-descriptions-item
            v-if="selectedLog.errorMsg"
            label="错误"
            :span="2"
          >
            {{ selectedLog.errorMsg }}
          </el-descriptions-item>
          <el-descriptions-item
            label="User-Agent"
            :span="2"
          >
            {{ selectedLog.userAgent || '-' }}
          </el-descriptions-item>
        </el-descriptions>

        <div
          v-if="selectedLog.params"
          class="params-block"
        >
          <div class="params-label">
            请求参数（已脱敏）
          </div>
          <pre class="params-json">{{ formatParams(selectedLog.params) }}</pre>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getOperationLogs, type OperationLogItem, type OperationLogQuery } from '@/api/admin-logs'
import { formatDate } from '@/utils'
import Pagination from '@/components/common/Pagination.vue'

const loading = ref(false)
const logList = ref<OperationLogItem[]>([])
const detailVisible = ref(false)
const selectedLog = ref<OperationLogItem | null>(null)

const filterOperatorId = ref('')
const filterModule = ref('')
const filterStatus = ref<number | ''>('')
const filterTimeRange = ref<[string, string] | null>(null)

const pagination = reactive({
  page: 1,
  size: 20,
  total: 0
})

const moduleOptions = [
  { label: '用户', value: 'USER' },
  { label: '赛事', value: 'EVENT' },
  { label: 'Banner', value: 'BANNER' },
  { label: '管理员', value: 'ADMIN' },
  { label: '排名', value: 'RANKING' },
  { label: '用户日志', value: 'BAN_RECORD' },
  { label: '认证', value: 'AUTH' },
  { label: '仪表盘', value: 'DASHBOARD' },
  { label: '操作日志', value: 'OPERATION_LOG' },
  { label: '未识别', value: 'RAW' }
]

const fetchLogs = async () => {
  loading.value = true
  try {
    const params: OperationLogQuery = {
      page: pagination.page,
      size: pagination.size,
      operatorId: filterOperatorId.value ? Number(filterOperatorId.value) : undefined,
      module: filterModule.value || undefined,
      status: filterStatus.value === '' ? undefined : filterStatus.value,
      startTime: filterTimeRange.value?.[0] || undefined,
      endTime: filterTimeRange.value?.[1] || undefined
    }
    const res = await getOperationLogs(params)
    if (res.code === 0) {
      logList.value = res.data.list || []
      pagination.total = res.data.total || 0
    } else {
      ElMessage.error(res.message || '获取操作日志失败')
    }
  } catch {
    // swallow
  } finally {
    loading.value = false
  }
}

const handleFilter = () => {
  pagination.page = 1
  fetchLogs()
}

const handleReset = () => {
  filterOperatorId.value = ''
  filterModule.value = ''
  filterStatus.value = ''
  filterTimeRange.value = null
  pagination.page = 1
  fetchLogs()
}

const handleRowClick = (row: OperationLogItem) => {
  selectedLog.value = row
  detailVisible.value = true
}

const methodTagType = (method: string) => {
  switch (method) {
    case 'POST': return 'success'
    case 'PUT': return 'warning'
    case 'DELETE': return 'danger'
    default: return 'info'
  }
}

const truncate = (s: string | null, max: number) => {
  if (!s) return ''
  return s.length <= max ? s : s.substring(0, max) + '...'
}

const formatParams = (raw: string | null) => {
  if (!raw) return ''
  try {
    return JSON.stringify(JSON.parse(raw), null, 2)
  } catch {
    return raw
  }
}

onMounted(() => {
  fetchLogs()
})
</script>

<style scoped>
.filter-bar {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  align-items: center;
}
.operator-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.operator-name {
  font-weight: 500;
  color: #1f2937;
}
.operator-sub {
  font-size: 12px;
  color: #6b7280;
}
.error-sub {
  font-size: 11px;
  color: #b91c1c;
  margin-top: 2px;
}
.path-text {
  font-family: ui-monospace, SFMono-Regular, monospace;
  font-size: 12px;
  color: #4b5563;
}
.detail-content {
  padding: 0 16px;
}
.params-block {
  margin-top: 16px;
}
.params-label {
  font-size: 13px;
  color: #6b7280;
  margin-bottom: 6px;
}
.params-json {
  background: #f9fafb;
  padding: 12px;
  border-radius: 4px;
  font-size: 12px;
  font-family: ui-monospace, SFMono-Regular, monospace;
  max-height: 400px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
