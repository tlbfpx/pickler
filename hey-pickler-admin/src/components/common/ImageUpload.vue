<template>
  <div class="image-upload">
    <el-upload
      :action="`${baseURL}/files/upload`"
      :headers="headers"
      :show-file-list="false"
      :on-success="handleSuccess"
      :before-upload="beforeUpload"
      accept="image/*"
    >
      <el-button type="primary">
        <el-icon><Upload /></el-icon>
        上传图片
      </el-button>
    </el-upload>
    <div
      v-if="modelValue"
      class="image-preview"
    >
      <el-image
        :src="modelValue"
        fit="contain"
        :preview-src-list="[modelValue]"
      />
      <el-button
        type="danger"
        size="small"
        @click="handleRemove"
      >
        <el-icon><Delete /></el-icon>
        删除
      </el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import type { UploadProps } from 'element-plus'

defineProps<{
  modelValue: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const authStore = useAuthStore()
const baseURL = '/api/admin'
const headers = computed(() => ({
  Authorization: `Bearer ${authStore.token}`
}))

const beforeUpload: UploadProps['beforeUpload'] = (file) => {
  const isImage = file.type.startsWith('image/')
  if (!isImage) {
    ElMessage.error('只能上传图片文件！')
    return false
  }
  const isLt5M = file.size / 1024 / 1024 < 5
  if (!isLt5M) {
    ElMessage.error('图片大小不能超过5MB！')
    return false
  }
  return true
}

const handleSuccess: UploadProps['onSuccess'] = (response) => {
  const r = response as { code: number; data?: { url: string }; message?: string } | undefined
  if (r && r.code === 0 && r.data) {
    emit('update:modelValue', r.data.url)
    ElMessage.success('图片上传成功！')
  } else {
    ElMessage.error(r?.message || '上传失败')
  }
}

const handleRemove = () => {
  emit('update:modelValue', '')
}
</script>

<style scoped>
.image-upload {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.image-preview {
  position: relative;
  width: 200px;
  height: 200px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  overflow: hidden;
}

.image-preview :deep(.el-image) {
  width: 100%;
  height: 100%;
}

.image-preview .el-button {
  position: absolute;
  bottom: 8px;
  right: 8px;
}
</style>
