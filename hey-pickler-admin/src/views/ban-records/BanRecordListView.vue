<template>
  <div>
    <div class="page-header">
      <h1>操作日志</h1>
    </div>
    <div class="card">
      <div class="filter-bar">
        <el-select
          v-model="filterAction"
          placeholder="操作类型"
          clearable
          style="width: 150px"
          @change="handleFilter"
        >
          <el-option
            label="封禁"
            value="BAN"
          />
          <el-option
            label="解禁"
            value="UNBAN"
          />
        </el-select>
      </div>

      <el-table
        v-loading="loading"
        :data="recordList"
        style="width: 100%; margin-top: 16px"
      >
        <el-table-column
          prop="id"
          label="ID"
          width="70"
        />
        <el-table-column
          label="用户"
          width="220"
        >
          <template #default="{ row }">
            <div class="user-cell">
              <div class="user-info">
                <div class="user-name">
                  {{ row.userNickname || '-' }}
                </div>
                <div class="user-sub">
                  {{ maskPhone(row.userPhone) }}  ·  ID: {{ row.userId }}
                </div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column
          label="操作"
          width="100"
        >
          <template #default="{ row }">
            <el-tag
              :type="row.action === 'BAN' ? 'danger' : 'success'"
              effect="dark"
            >
              {{ row.action === 'BAN' ? '封禁' : '解禁' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          prop="reason"
          label="原因"
          min-width="200"
          show-overflow-tooltip
        />
        <el-table-column
          label="封禁至"
          width="170"
        >
          <template #default="{ row }">
            {{ row.banUntil ? formatDate(row.banUntil) : '-' }}
          </template>
        </el-table-column>
        <el-table-column
          label="操作人"
          width="130"
        >
          <template #default="{ row }">
            {{ row.operatorName || '-' }}
          </template>
        </el-table-column>
        <el-table-column
          label="操作时间"
          width="170"
        >
          <template #default="{ row }">
            {{ formatDate(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column
          label="操作"
          width="80"
          fixed="right"
        >
          <template #default="{ row }">
            <el-button
              type="danger"
              size="small"
              @click="handleDelete(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <Pagination
        v-model:page="pagination.page"
        v-model:size="pagination.size"
        :total="pagination.total"
        @update:page="fetchRecords"
        @update:size="fetchRecords"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getBanRecords, deleteBanRecord } from '@/api/ban-records'
import { formatDate } from '@/utils'
import Pagination from '@/components/common/Pagination.vue'
import type { BanRecordItem } from '@/types'

const loading = ref(false)
const filterAction = ref('')
const recordList = ref<BanRecordItem[]>([])

const pagination = reactive({
  page: 1,
  size: 20,
  total: 0
})

const fetchRecords = async () => {
  loading.value = true
  try {
    const res = await getBanRecords({
      page: pagination.page,
      size: pagination.size,
      action: filterAction.value || undefined
    })
    if (res.code === 0) {
      recordList.value = res.data.list || []
      pagination.total = res.data.total || 0
    } else {
      ElMessage.error(res.message || '获取操作日志失败')
    }
  } catch {
    
  } finally {
    loading.value = false
  }
}

const handleFilter = () => {
  pagination.page = 1
  fetchRecords()
}

const maskPhone = (phone: string) => {
  if (!phone || phone.length < 7) return phone || '-'
  return phone.slice(0, 3) + '****' + phone.slice(-4)
}

const handleDelete = async (row: BanRecordItem) => {
  try {
    await ElMessageBox.confirm('确定删除该操作日志？', '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
    const res = await deleteBanRecord(row.id)
    if (res.code === 0) {
      ElMessage.success('删除成功')
      fetchRecords()
    } else {
      ElMessage.error(res.message || '删除失败')
    }
  } catch {
    
  }
}

onMounted(() => {
  fetchRecords()
})
</script>

<style scoped>
.filter-bar {
  display: flex;
  gap: 12px;
}

.user-cell {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.user-name {
  font-weight: 500;
  color: #1f2937;
}

.user-sub {
  font-size: 12px;
  color: #6b7280;
}
</style>
