<template>
  <div>
    <div class="page-header">
      <h1>用户管理</h1>
    </div>
    <div class="card">
      <div class="table-header">
        <el-input
          v-model="searchKeyword"
          placeholder="按手机号或昵称搜索"
          style="width: 250px"
          clearable
          @clear="handleSearch"
          @keyup.enter="handleSearch"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-input
          v-model="searchCity"
          placeholder="按城市搜索"
          style="width: 160px"
          clearable
          @clear="handleSearch"
          @keyup.enter="handleSearch"
        />
        <el-button
          type="primary"
          @click="handleSearch"
        >
          搜索
        </el-button>
      </div>

      <el-table
        v-loading="loading"
        :data="userList"
        style="width: 100%; margin-top: 16px"
      >
        <el-table-column
          prop="id"
          label="ID"
          width="60"
        />
        <el-table-column
          label="手机号"
          width="130"
        >
          <template #default="{ row }">
            {{ maskPhone(row.phone) }}
          </template>
        </el-table-column>
        <el-table-column
          label="用户"
          width="200"
        >
          <template #default="{ row }">
            <div class="user-cell">
              <el-avatar
                :size="32"
                :src="row.avatarUrl"
              >
                {{ row.nickname?.[0] || '?' }}
              </el-avatar>
              <span class="user-cell__name">{{ row.nickname || '-' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column
          label="城市"
          width="80"
        >
          <template #default="{ row }">
            {{ row.city || '-' }}
          </template>
        </el-table-column>
        <el-table-column
          label="明星"
          width="120"
        >
          <template #default="{ row }">
            {{ row.starTier }} / {{ row.starPoints }}分
          </template>
        </el-table-column>
        <el-table-column
          label="派对"
          width="120"
        >
          <template #default="{ row }">
            {{ row.partyTier }} / {{ row.partyPoints }}分
          </template>
        </el-table-column>
        <el-table-column
          label="状态"
          width="80"
        >
          <template #default="{ row }">
            <el-tag :type="row.status === 'BANNED' ? 'danger' : 'success'">
              {{ row.status === 'BANNED' ? '禁赛' : '正常' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          label="注册时间"
          width="170"
        >
          <template #default="{ row }">
            {{ formatDate(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column
          label="操作"
          width="200"
          fixed="right"
        >
          <template #default="{ row }">
            <el-button
              type="primary"
              size="small"
              @click="handleDetail(row)"
            >
              详情
            </el-button>
            <el-button
              v-if="row.status !== 'BANNED'"
              type="warning"
              size="small"
              @click="handleBan(row)"
            >
              禁赛
            </el-button>
            <el-button
              v-else
              type="success"
              size="small"
              @click="handleUnban(row)"
            >
              解禁
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

    <BanDialog
      v-model="banDialogVisible"
      :user="selectedUser"
      @success="fetchUsers"
    />
    <UserDetailDrawer
      v-model="detailVisible"
      :user-id="detailUserId"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getUserList, unbanUser } from '@/api/users'
import type { User } from '@/api/users'
import { formatDate } from '@/utils'
import Pagination from '@/components/common/Pagination.vue'
import BanDialog from './BanDialog.vue'
import UserDetailDrawer from './UserDetailDrawer.vue'

const loading = ref(false)
const searchKeyword = ref('')
const searchCity = ref('')
const userList = ref<User[]>([])

const pagination = reactive({
  page: 1,
  size: 10,
  total: 0
})

const banDialogVisible = ref(false)
const selectedUser = ref<User | null>(null)
const detailVisible = ref(false)
const detailUserId = ref<number | null>(null)

const fetchUsers = async () => {
  loading.value = true
  try {
    const res = await getUserList({
      page: pagination.page,
      size: pagination.size,
      keyword: searchKeyword.value,
      city: searchCity.value
    })
    if (res.code === 0) {
      userList.value = res.data.list || []
      pagination.total = res.data.total || 0
    } else {
      ElMessage.error(res.message || '获取用户列表失败')
    }
  } catch {
    
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.page = 1
  fetchUsers()
}

const handleDetail = (user: User) => {
  detailUserId.value = user.id
  detailVisible.value = true
}

const handleBan = (user: User) => {
  selectedUser.value = user
  banDialogVisible.value = true
}

const handleUnban = async (user: User) => {
  try {
    const res = await unbanUser(user.id)
    if (res.code === 0) {
      ElMessage.success('用户解禁成功')
      fetchUsers()
    } else {
      ElMessage.error(res.message || '解禁用户失败')
    }
  } catch {
    
  }
}

const maskPhone = (phone: string) => {
  if (!phone || phone.length < 7) return phone || '-'
  return phone.slice(0, 3) + '****' + phone.slice(-4)
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
.user-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}
.user-cell__name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
