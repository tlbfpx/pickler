<template>
  <el-dialog
    :model-value="modelValue"
    :title="banner ? '编辑Banner' : '新建Banner'"
    width="600px"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <el-form
      ref="formRef"
      :model="formData"
      :rules="rules"
      label-position="top"
    >
      <el-form-item label="标题" prop="title">
        <el-input v-model="formData.title" placeholder="请输入Banner标题" />
      </el-form-item>
      <el-form-item label="图片" prop="imageUrl">
        <ImageUpload v-model="formData.imageUrl" />
      </el-form-item>
      <el-form-item label="跳转链接" prop="linkUrl">
        <el-input v-model="formData.linkUrl" placeholder="请输入跳转链接" />
      </el-form-item>
      <el-form-item label="排序" prop="order">
        <el-input-number
          v-model="formData.order"
          :min="0"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="启用" prop="isActive">
        <el-switch v-model="formData.isActive" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="loading" @click="handleConfirm">
        {{ banner ? '更新' : '新建' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { createBanner, updateBanner } from '@/api/banners'
import ImageUpload from '@/components/common/ImageUpload.vue'
import type { Banner, CreateBannerRequest } from '@/types'

const props = defineProps<{
  modelValue: boolean
  banner: Banner | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  success: []
}>()

const formRef = ref<FormInstance>()
const loading = ref(false)

const formData = reactive<CreateBannerRequest>({
  title: '',
  imageUrl: '',
  linkUrl: '',
  order: 0,
  isActive: true
})

const rules: FormRules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  imageUrl: [{ required: true, message: '请上传图片', trigger: 'change' }],
  linkUrl: [{ required: true, message: '请输入跳转链接', trigger: 'blur' }],
  order: [{ required: true, message: '请输入排序', trigger: 'blur' }]
}

watch(() => props.banner, (val) => {
  if (val) {
    formData.title = val.title
    formData.imageUrl = val.imageUrl
    formData.linkUrl = val.linkUrl
    formData.order = val.order
    formData.isActive = val.isActive
  } else {
    formRef.value?.resetFields()
  }
})

watch(() => props.modelValue, (val) => {
  if (!val && !props.banner) {
    formRef.value?.resetFields()
    formData.imageUrl = ''
  }
})

const handleConfirm = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (!valid) return

    loading.value = true
    try {
      let res
      if (props.banner) {
        res = await updateBanner(props.banner.id, formData)
      } else {
        res = await createBanner(formData)
      }

      if (res.code === 0) {
        ElMessage.success(props.banner ? 'Banner更新成功' : 'Banner创建成功')
        emit('success')
        emit('update:modelValue', false)
      } else {
        ElMessage.error(res.message || '操作失败')
      }
    } catch (error) {
      ElMessage.error('操作失败')
    } finally {
      loading.value = false
    }
  })
}
</script>
