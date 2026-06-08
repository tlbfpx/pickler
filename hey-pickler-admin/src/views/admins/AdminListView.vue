<template>
  <div>
    <div class="page-header">
      <h1>Admin Account Management</h1>
      <el-button type="primary" @click="handleCreate">
        <el-icon><Plus /></el-icon>
        Create Admin
      </el-button>
    </div>
    <div class="card">
      <el-table v-loading="loading" :data="adminList" style="width: 100%">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="username" label="Username" width="200" />
        <el-table-column label="Role" width="150">
          <template #default="{ row }">
            <el-tag :type="row.role === 'SUPER_ADMIN' ? 'danger' : 'primary'">
              {{ formatAdminRole(row.role) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="Created At" width="200">
          <template #default="{ row }">
            {{ formatDate(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="Actions" width="150" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="handleEdit(row)">
              Edit
            </el-button>
            <el-button type="danger" size="small" @click="handleDelete(row)">
              Delete
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
    const res = await getAdminList()
    if (res.code === 0) {
      adminList.value = res.data
    } else {
      ElMessage.error(res.message || 'Failed to fetch admins')
    }
  } catch (error) {
    ElMessage.error('Failed to fetch admins')
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
    await ElMessageBox.confirm(
      `Are you sure you want to delete admin "${admin.username}"?`,
      'Confirm Delete',
      { type: 'warning' }
    )
    const res = await deleteAdmin(admin.id)
    if (res.code === 0) {
      ElMessage.success('Admin deleted successfully')
      fetchAdmins()
    } else {
      ElMessage.error(res.message || 'Failed to delete admin')
    }
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error('Failed to delete admin')
    }
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
