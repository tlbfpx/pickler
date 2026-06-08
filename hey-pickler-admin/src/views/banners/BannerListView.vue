<template>
  <div>
    <div class="page-header">
      <h1>Banner Management</h1>
      <el-button type="primary" @click="handleCreate">
        <el-icon><Plus /></el-icon>
        Create Banner
      </el-button>
    </div>
    <div class="card">
      <el-table v-loading="loading" :data="bannerList" style="width: 100%">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="title" label="Title" width="200" />
        <el-table-column label="Image" width="200">
          <template #default="{ row }">
            <el-image
              :src="row.imageUrl"
              fit="contain"
              style="width: 150px; height: 80px"
              :preview-src-list="[row.imageUrl]"
            />
          </template>
        </el-table-column>
        <el-table-column prop="linkUrl" label="Link URL" width="250" />
        <el-table-column prop="order" label="Order" width="80" />
        <el-table-column label="Status" width="100">
          <template #default="{ row }">
            <el-tag :type="row.isActive ? 'success' : 'info'">
              {{ row.isActive ? 'Active' : 'Inactive' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="Created At" width="180">
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

    <BannerFormDialog
      v-model="formDialogVisible"
      :banner="selectedBanner"
      @success="fetchBanners"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getBannerList, deleteBanner } from '@/api/banners'
import { formatDate } from '@/utils'
import BannerFormDialog from './BannerFormDialog.vue'
import type { Banner } from '@/types'

const loading = ref(false)
const bannerList = ref<Banner[]>([])

const formDialogVisible = ref(false)
const selectedBanner = ref<Banner | null>(null)

const fetchBanners = async () => {
  loading.value = true
  try {
    const res = await getBannerList()
    if (res.code === 0) {
      bannerList.value = res.data
    } else {
      ElMessage.error(res.message || 'Failed to fetch banners')
    }
  } catch (error) {
    ElMessage.error('Failed to fetch banners')
  } finally {
    loading.value = false
  }
}

const handleCreate = () => {
  selectedBanner.value = null
  formDialogVisible.value = true
}

const handleEdit = (banner: Banner) => {
  selectedBanner.value = banner
  formDialogVisible.value = true
}

const handleDelete = async (banner: Banner) => {
  try {
    await ElMessageBox.confirm(
      `Are you sure you want to delete banner "${banner.title}"?`,
      'Confirm Delete',
      { type: 'warning' }
    )
    const res = await deleteBanner(banner.id)
    if (res.code === 0) {
      ElMessage.success('Banner deleted successfully')
      fetchBanners()
    } else {
      ElMessage.error(res.message || 'Failed to delete banner')
    }
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error('Failed to delete banner')
    }
  }
}

onMounted(() => {
  fetchBanners()
})
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
