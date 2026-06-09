<template>
  <el-dialog
    :model-value="modelValue"
    title="封禁用户"
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
      <el-form-item label="封禁原因" prop="reason">
        <el-input
          v-model="formData.reason"
          type="textarea"
          :rows="3"
          placeholder="请输入封禁原因"
        />
      </el-form-item>
      <el-form-item label="封禁天数" prop="durationDays">
        <el-input-number
          v-model="formData.durationDays"
          :min="1"
          :max="365"
          style="width: 100%"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="danger" :loading="loading" @click="handleConfirm">
        确认封禁
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
  durationDays: 7
})

const rules: FormRules = {
  reason: [{ required: true, message: '请输入封禁原因', trigger: 'blur' }],
  durationDays: [{ required: true, message: '请输入封禁天数', trigger: 'blur' }]
}

watch(() => props.modelValue, (val) => {
  if (!val) {
    formRef.value?.resetFields()
    formData.reason = ''
    formData.durationDays = 7
  }
})

const handleConfirm = async () => {
  if (!formRef.value || !props.user) return

  await formRef.value.validate(async (valid) => {
    if (!valid) return

    loading.value = true
    try {
      const res = await banUser(props.user.id, formData)
      if (res.code === 0) {
        ElMessage.success('封禁成功')
        emit('success')
        emit('update:modelValue', false)
      } else {
        ElMessage.error(res.message || '封禁失败')
      }
    } catch (error) {
      ElMessage.error('封禁失败')
    } finally {
      loading.value = false
    }
  })
}
</script>
