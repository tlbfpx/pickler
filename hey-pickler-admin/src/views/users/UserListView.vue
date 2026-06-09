<template>
  <div>
    <div class="page-header">
      <h1>用户管理</h1>
    </div>
    <div class="card">
      <div class="table-header">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索手机号或昵称"
          style="width: 300px"
          clearable
          @clear="handleSearch"
          @keyup.enter="handleSearch"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-button type="primary" @click="handleSearch">搜索</el-button>
      </div>

      <el-table v-loading="loading" :data="userList" style="width: 100%; margin-top: 16px">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="phone" label="手机号" width="120" />
        <el-table-column prop="nickname" label="昵称" width="150" />
        <el-table-column label="头像" width="100">
          <template #default="{ row }">
            <el-avatar :src="row.avatar" :size="50" />
          </template>
        </el-table-column>
        <el-table-column label="等级" width="120">
          <template #default="{ row }">
            <span
              class="tier-badge"
              :style="{ backgroundColor: getTierColor(row.tier) }"
            >
              {{ formatTier(row.tier) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="totalPoints" label="总积分" width="120" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.isBanned ? 'danger' : 'success'">
              {{ row.isBanned ? '已封禁' : '正常' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="180">
          <template #default="{ row }">
            {{ formatDate(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="!row.isBanned"
              type="warning"
              size="small"
              @click="handleBan(row)"
            >
              封禁
            </el-button>
            <el-button
              v-else
              type="success"
              size="small"
              @click="handleUnban(row)"
            >
              解封
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <Pagination
        v-model:page="pagination.page"
        v-model:size="pagination.size"
        :total="pagination.total"
        @update:page="fetchUsers"
        @update:size="fetchUsers"
      />
    </div>

    <BanDialog v-model="banDialogVisible" :user="selectedUser" @success="fetchUsers" />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getUserList, unbanUser } from '@/api/users'
import { formatDate, formatTier, getTierColor } from '@/utils'
import Pagination from '@/components/common/Pagination.vue'
import BanDialog from './BanDialog.vue'
import type { User } from '@/types'

const loading = ref(false)
const searchKeyword = ref('')
const userList = ref<User[]>([])

const pagination = reactive({
  page: 1,
  size: 10,
  total: 0
})

const banDialogVisible = ref(false)
const selectedUser = ref<User | null>(null)

const fetchUsers = async () => {
  loading.value = true
  try {
    const res = await getUserList({
      page: pagination.page,
      size: pagination.size,
      keyword: searchKeyword.value
    })
    if (res.code === 0) {
      userList.value = res.data.users
      pagination.total = res.data.total
    } else {
      ElMessage.error(res.message || '获取用户列表失败')
    }
  } catch (error) {
    ElMessage.error('获取用户列表失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.page = 1
  fetchUsers()
}

const handleBan = (user: User) => {
  selectedUser.value = user
  banDialogVisible.value = true
}

const handleUnban = async (user: User) => {
  try {
    const res = await unbanUser(user.id)
    if (res.code === 0) {
      ElMessage.success('解封成功')
      fetchUsers()
    } else {
      ElMessage.error(res.message || '解封失败')
    }
  } catch (error) {
    ElMessage.error('解封失败')
  }
}

onMounted(() => {
  fetchUsers()
})
</script>

<style scoped>
.table-header {
  display: flex;
  gap: 12px;
}
</style>
