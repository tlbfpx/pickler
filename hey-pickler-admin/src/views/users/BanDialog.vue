<template>
  <el-dialog
    :model-value="modelValue"
    title="禁赛用户"
    width="500px"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <el-form
      ref="formRef"
      :model="formData"
      :rules="rules"
      label-position="top"
      v-if="user"
    >
      <el-form-item label="用户">
        <el-input :value="user.nickname" disabled />
      </el-form-item>
      <el-form-item label="禁赛原因" prop="reason">
        <el-input
          v-model="formData.reason"
          type="textarea"
          :rows="3"
          placeholder="请输入禁赛原因"
        />
      </el-form-item>
      <el-form-item label="禁赛天数" prop="days">
        <el-input-number
          v-model="formData.days"
          :min="1"
          :max="365"
          style="width: 100%"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="danger" :loading="loading" @click="handleConfirm">
        确认禁赛
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { banUser } from '@/api/users'
import type { User } from '@/types'

const props = defineProps<{
  modelValue: boolean
  user: User | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  success: []
}>()

const formRef = ref<FormInstance>()
const loading = ref(false)

const formData = reactive({
  reason: '',
  days: 7
})

const rules: FormRules = {
  reason: [{ required: true, message: '请输入禁赛原因', trigger: 'blur' }],
  days: [{ required: true, message: '请输入禁赛天数', trigger: 'blur' }]
}

watch(() => props.modelValue, (val) => {
  if (!val) {
    formRef.value?.resetFields()
    formData.reason = ''
    formData.days = 7
  }
})

const handleConfirm = async () => {
  if (!formRef.value || !props.user) return

  try {
    await formRef.value.validate()
  } catch {
    return
  }

  loading.value = true
  try {
    const res = await banUser(props.user.id, formData)
    if (res.code === 0) {
      ElMessage.success('用户禁赛成功')
      emit('success')
      emit('update:modelValue', false)
    } else {
      ElMessage.error(res.message || '禁赛用户失败')
    }
  } catch {
    
  } finally {
    loading.value = false
  }
}
</script>
