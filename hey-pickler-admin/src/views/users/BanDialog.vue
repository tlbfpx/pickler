<template>
  <el-dialog
    :model-value="modelValue"
    title="Ban User"
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
      <el-form-item label="User">
        <el-input :value="user.nickname" disabled />
      </el-form-item>
      <el-form-item label="Ban Reason" prop="reason">
        <el-input
          v-model="formData.reason"
          type="textarea"
          :rows="3"
          placeholder="Enter ban reason"
        />
      </el-form-item>
      <el-form-item label="Duration (Days)" prop="durationDays">
        <el-input-number
          v-model="formData.durationDays"
          :min="1"
          :max="365"
          style="width: 100%"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">Cancel</el-button>
      <el-button type="danger" :loading="loading" @click="handleConfirm">
        Confirm Ban
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
  reason: [{ required: true, message: 'Please enter ban reason', trigger: 'blur' }],
  durationDays: [{ required: true, message: 'Please enter duration', trigger: 'blur' }]
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
        ElMessage.success('User banned successfully')
        emit('success')
        emit('update:modelValue', false)
      } else {
        ElMessage.error(res.message || 'Failed to ban user')
      }
    } catch (error) {
      ElMessage.error('Failed to ban user')
    } finally {
      loading.value = false
    }
  })
}
</script>
