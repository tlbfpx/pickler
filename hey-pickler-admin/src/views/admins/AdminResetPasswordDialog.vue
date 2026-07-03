<template>
  <el-dialog
    :model-value="modelValue"
    title="重置密码"
    width="500px"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <el-form
      ref="formRef"
      :model="formData"
      :rules="rules"
      label-position="top"
      @submit.prevent
    >
      <el-form-item label="管理员">
        <el-input
          :model-value="admin?.username"
          disabled
        />
      </el-form-item>
      <el-form-item
        label="新密码"
        prop="newPassword"
      >
        <el-input
          v-model="formData.newPassword"
          type="password"
          placeholder="请输入新密码"
          show-password
        />
        <div class="form-tip">
          密码至少8位，须包含字母和数字
        </div>
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
        确定
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { resetAdminPassword } from '@/api/admins'
import type { Admin } from '@/types'

const props = defineProps<{
  modelValue: boolean
  admin: Admin | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  success: []
}>()

const formRef = ref<FormInstance>()
const loading = ref(false)

const formData = reactive<{ newPassword: string }>({
  newPassword: ''
})

const rules: FormRules = {
  newPassword: [
    {
      validator: (_rule, value, callback) => {
        if (!value) {
          callback(new Error('请输入新密码'))
        } else if (value.length < 8) {
          callback(new Error('密码至少8位'))
        } else if (!(/(?=.*[a-zA-Z])(?=.*\d)/.test(value))) {
          callback(new Error('密码须包含字母和数字'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

watch(() => props.modelValue, (val) => {
  if (val) {
    formData.newPassword = ''
  }
})

watch(() => props.modelValue, (val) => {
  if (!val) {
    formRef.value?.resetFields()
    formData.newPassword = ''
  }
})

const handleConfirm = async () => {
  if (!formRef.value || !props.admin) return

  try {
    await formRef.value.validate()
  } catch {
    return
  }

  loading.value = true
  try {
    const res = await resetAdminPassword(props.admin.id, formData.newPassword)
    if (res.code === 0) {
      ElMessage.success('密码重置成功')
      emit('success')
      emit('update:modelValue', false)
    } else {
      ElMessage.error(res.message || '密码重置失败')
    }
  } catch {

  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.form-tip {
  font-size: 12px;
  color: #6b7280;
  margin-top: 4px;
}
</style>
