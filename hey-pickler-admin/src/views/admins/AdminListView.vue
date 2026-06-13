<template>
  <div>
    <div class="page-header">
      <h1>管理员管理</h1>
      <el-button
        type="primary"
        @click="handleCreate"
      >
        <el-icon><Plus /></el-icon>
        新建管理员
      </el-button>
    </div>
    <div class="card">
      <el-table
        v-loading="loading"
        :data="adminList"
        style="width: 100%"
      >
        <el-table-column
          prop="id"
          label="ID"
          width="80"
        />
        <el-table-column
          prop="username"
          label="用户名"
          width="200"
        />
        <el-table-column
          label="角色"
          width="150"
        >
          <template #default="{ row }">
            <el-tag :type="row.role === 'SUPER_ADMIN' ? 'danger' : 'primary'">
              {{ formatAdminRole(row.role) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          label="创建时间"
          width="200"
        >
          <template #default="{ row }">
            {{ formatDate(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column
          label="操作"
          width="180"
          fixed="right"
        >
          <template #default="{ row }">
            <el-button
              type="primary"
              size="small"
              @click="handleEdit(row)"
            >
              编辑
            </el-button>
            <el-button
              v-if="row.role !== 'SUPER_ADMIN'"
              type="danger"
              size="small"
              @click="handleDelete(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <AdminFormDialog
      v-model="formDialogVisible"
      :admin="selectedAdmin"
      @success="fetchAdmins"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAdminList, deleteAdmin } from '@/api/admins'
import { formatDate, formatAdminRole } from '@/utils'
import AdminFormDialog from './AdminFormDialog.vue'
import type { Admin } from '@/types'

const loading = ref(false)
const adminList = ref<Admin[]>([])

const formDialogVisible = ref(false)
const selectedAdmin = ref<Admin | null>(null)

const fetchAdmins = async () => {
  loading.value = true
  try {
    const res = await getAdminList({ page: 1, size: 100 })
    if (res.code === 0) {
      adminList.value = res.data.list || []
    } else {
      ElMessage.error(res.message || '获取管理员列表失败')
    }
  } catch {
    
  } finally {
    loading.value = false
  }
}

const handleCreate = () => {
  selectedAdmin.value = null
  formDialogVisible.value = true
}

const handleEdit = (admin: Admin) => {
  selectedAdmin.value = admin
  formDialogVisible.value = true
}

const handleDelete = async (admin: Admin) => {
  try {
    await ElMessageBox.confirm(`确定删除管理员「${admin.username}」？`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
    const res = await deleteAdmin(admin.id)
    if (res.code === 0) {
      ElMessage.success('删除成功')
      fetchAdmins()
    } else {
      ElMessage.error(res.message || '删除失败')
    }
  } catch {
    
  }
}

onMounted(() => {
  fetchAdmins()
})
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
