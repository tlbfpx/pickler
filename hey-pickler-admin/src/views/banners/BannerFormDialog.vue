<template>
  <el-dialog
    :model-value="modelValue"
    :title="banner ? 'Edit Banner' : 'Create Banner'"
    width="600px"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <el-form
      ref="formRef"
      :model="formData"
      :rules="rules"
      label-position="top"
    >
      <el-form-item label="Title" prop="title">
        <el-input v-model="formData.title" placeholder="Enter banner title" />
      </el-form-item>
      <el-form-item label="Image" prop="imageUrl">
        <ImageUpload v-model="formData.imageUrl" />
      </el-form-item>
      <el-form-item label="Link URL" prop="linkUrl">
        <el-input v-model="formData.linkUrl" placeholder="Enter link URL" />
      </el-form-item>
      <el-form-item label="Order" prop="order">
        <el-input-number
          v-model="formData.order"
          :min="0"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="Active" prop="isActive">
        <el-switch v-model="formData.isActive" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">Cancel</el-button>
      <el-button type="primary" :loading="loading" @click="handleConfirm">
        {{ banner ? 'Update' : 'Create' }}
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
  title: [{ required: true, message: 'Please enter title', trigger: 'blur' }],
  imageUrl: [{ required: true, message: 'Please upload image', trigger: 'change' }],
  linkUrl: [{ required: true, message: 'Please enter link URL', trigger: 'blur' }],
  order: [{ required: true, message: 'Please enter order', trigger: 'blur' }]
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
        ElMessage.success(props.banner ? 'Banner updated successfully' : 'Banner created successfully')
        emit('success')
        emit('update:modelValue', false)
      } else {
        ElMessage.error(res.message || 'Operation failed')
      }
    } catch (error) {
      ElMessage.error('Operation failed')
    } finally {
      loading.value = false
    }
  })
}
</script>
