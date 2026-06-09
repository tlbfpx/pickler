<template>
  <div>
    <div class="page-header">
      <h1>Banner管理</h1>
      <el-button type="primary" @click="handleCreate">
        <el-icon><Plus /></el-icon>
        新建Banner
      </el-button>
    </div>
    <div class="card">
      <el-table v-loading="loading" :data="bannerList" style="width: 100%">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column label="图片" width="200">
          <template #default="{ row }">
            <el-image
              :src="row.imageUrl"
              fit="contain"
              style="width: 150px; height: 80px"
              :preview-src-list="[row.imageUrl]"
            />
          </template>
        </el-table-column>
        <el-table-column prop="linkUrl" label="跳转链接" width="250" />
        <el-table-column prop="sortOrder" label="排序" width="80" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">
              {{ row.status === 'ACTIVE' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="handleEdit(row)">
              编辑
            </el-button>
            <el-button type="danger" size="small" @click="handleDelete(row)">
              删除
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
      ElMessage.error(res.message || '获取Banner列表失败')
    }
  } catch (error) {
    ElMessage.error('获取Banner列表失败')
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
      '确定要删除这个Banner吗？',
      '确认删除',
      { type: 'warning' }
    )
    const res = await deleteBanner(banner.id)
    if (res.code === 0) {
      ElMessage.success('Banner删除成功')
      fetchBanners()
    } else {
      ElMessage.error(res.message || '删除Banner失败')
    }
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error('删除Banner失败')
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
