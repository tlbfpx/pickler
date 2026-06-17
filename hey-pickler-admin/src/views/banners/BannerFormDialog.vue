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
      <el-form-item
        label="图片地址"
        prop="imageUrl"
      >
        <el-input
          v-model="formData.imageUrl"
          placeholder="请输入Banner图片地址"
        />
      </el-form-item>
      <el-form-item
        label="跳转链接"
        prop="linkUrl"
      >
        <el-input
          v-model="formData.linkUrl"
          placeholder="请输入跳转链接"
        />
      </el-form-item>
      <el-form-item
        label="排序"
        prop="sortOrder"
      >
        <el-input-number
          v-model="formData.sortOrder"
          :min="0"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item
        label="状态"
        prop="status"
      >
        <el-select
          v-model="formData.status"
          placeholder="请选择状态"
          style="width: 100%"
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
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">
        取消
      </el-button>
      <el-button
        type="primary"
        :loading="loading"
        @click="handleConfirm"
      >
        {{ banner ? '更新' : '新建' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { createBanner, updateBanner } from '@/api/banners'
import type { Banner } from '@/types'

interface FormData {
  imageUrl: string
  linkUrl: string
  sortOrder: number
  status: string
}

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

const formData = reactive<FormData>({
  imageUrl: '',
  linkUrl: '',
  sortOrder: 0,
  status: 'ACTIVE'
})

const rules: FormRules = {
  imageUrl: [
    { required: true, message: '请输入图片地址', trigger: 'blur' },
    {
      pattern: /^https:\/\/[^/]+\/.*\.(jpg|jpeg|png|webp|gif)(\?.*)?$/i,
      message: '图片地址必须为 https 开头且以 .jpg/.jpeg/.png/.webp/.gif 结尾',
      trigger: 'blur'
    }
  ],
  linkUrl: [
    {
      pattern: /^(https:\/\/[^/]+\/.*)?$/,
      message: '跳转链接必须为 https 开头',
      trigger: 'blur'
    }
  ],
  sortOrder: [{ required: true, message: '请输入排序', trigger: 'blur' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }]
}

watch(() => props.banner, (val) => {
  if (val) {
    formData.imageUrl = val.imageUrl
    formData.linkUrl = val.linkUrl || ''
    formData.sortOrder = val.sortOrder
    formData.status = val.status === 'ACTIVE' ? 'ACTIVE' : 'INACTIVE'
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

  try {
    await formRef.value.validate()
  } catch {
    return
  }

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
  } catch {
    
  } finally {
    loading.value = false
  }
}
</script>
