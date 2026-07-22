<template>
  <div>
    <div class="page-header">
      <h1>场馆</h1>
      <el-button
        type="primary"
        @click="handleCreate"
      >
        <el-icon><Plus /></el-icon>
        新建场馆
      </el-button>
    </div>
    <div class="card">
      <div class="filter-bar">
        <el-input
          v-model="keyword"
          placeholder="场馆名称 / 地址"
          clearable
          style="width: 240px"
          @keyup.enter="handleFilter"
          @clear="handleFilter"
        />
        <el-select
          v-model="statusFilter"
          placeholder="状态"
          clearable
          style="width: 140px"
          @change="handleFilter"
        >
          <el-option
            label="启用"
            value="ACTIVE"
          />
          <el-option
            label="停用"
            value="INACTIVE"
          />
        </el-select>
        <el-button
          type="primary"
          @click="handleFilter"
        >
          查询
        </el-button>
      </div>

      <el-table
        v-loading="loading"
        :data="venueList"
        style="width: 100%; margin-top: 16px"
      >
        <el-table-column
          prop="id"
          label="ID"
          width="70"
        />
        <el-table-column
          label="名称"
          min-width="180"
        >
          <template #default="{ row }">
            <div class="venue-name">
              {{ row.name }}
            </div>
            <div
              v-if="row.coverUrl"
              class="venue-sub"
            >
              已设置封面
            </div>
          </template>
        </el-table-column>
        <el-table-column
          prop="address"
          label="地址"
          min-width="220"
          show-overflow-tooltip
        />
        <el-table-column
          label="状态"
          width="100"
        >
          <template #default="{ row }">
            <el-tag
              :type="row.status === 'ACTIVE' ? 'success' : 'info'"
              effect="dark"
            >
              {{ row.status === 'ACTIVE' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          label="联系方式"
          width="100"
        >
          <template #default="{ row }">
            {{ (row.contacts || []).length }}
          </template>
        </el-table-column>
        <el-table-column
          prop="bookingLeadDays"
          label="可订窗口(天)"
          width="120"
        />
        <el-table-column
          label="操作"
          width="160"
          fixed="right"
        >
          <template #default="{ row }">
            <el-button
              type="primary"
              size="small"
              link
              @click="handleEdit(row)"
            >
              编辑
            </el-button>
            <el-button
              type="danger"
              size="small"
              link
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
        @update:page="fetchList"
        @update:size="fetchList"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getVenueList, deleteVenue } from '@/api/venues'
import Pagination from '@/components/common/Pagination.vue'
import type { Venue } from '@/types'

const router = useRouter()
const loading = ref(false)
const keyword = ref('')
const statusFilter = ref('')
const venueList = ref<Venue[]>([])

const pagination = reactive({
  page: 1,
  size: 20,
  total: 0
})

const fetchList = async () => {
  loading.value = true
  try {
    const res = await getVenueList({
      page: pagination.page,
      size: pagination.size,
      keyword: keyword.value || undefined,
      status: statusFilter.value || undefined
    })
    if (res.code === 0) {
      venueList.value = res.data.list || []
      pagination.total = res.data.total || 0
    } else {
      ElMessage.error(res.message || '获取场馆列表失败')
    }
  } catch {

  } finally {
    loading.value = false
  }
}

const handleFilter = () => {
  pagination.page = 1
  fetchList()
}

const handleCreate = () => {
  router.push('/venues/0')
}

const handleEdit = (row: Venue) => {
  router.push(`/venues/${row.id}`)
}

const handleDelete = async (row: Venue) => {
  try {
    await ElMessageBox.confirm(`确定删除场馆「${row.name}」？删除后无法恢复。`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
    const res = await deleteVenue(row.id)
    if (res.code === 0) {
      ElMessage.success('删除成功')
      fetchList()
    } else {
      ElMessage.error(res.message || '删除失败')
    }
  } catch {

  }
}

onMounted(() => {
  fetchList()
})
</script>

<style scoped>
.filter-bar {
  display: flex;
  gap: 12px;
  align-items: center;
}

.venue-name {
  font-weight: 500;
  color: #1f2937;
}

.venue-sub {
  font-size: 12px;
  color: #6b7280;
  margin-top: 2px;
}
</style>
