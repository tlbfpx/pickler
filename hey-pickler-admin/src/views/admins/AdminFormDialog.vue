<template>
  <el-dialog
    :model-value="modelValue"
    :title="admin ? 'Edit Admin' : 'Create Admin'"
    width="500px"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <el-form
      ref="formRef"
      :model="formData"
      :rules="rules"
      label-position="top"
    >
      <el-form-item label="Username" prop="username">
        <el-input
          v-model="formData.username"
          placeholder="Enter username"
          :disabled="!!admin"
        />
      </el-form-item>
      <el-form-item label="Password" prop="password">
        <el-input
          v-model="formData.password"
          type="password"
          placeholder="Enter password"
          show-password
        />
        <div v-if="admin" class="form-tip">
          Leave empty to keep current password
        </div>
      </el-form-item>
      <el-form-item label="Role" prop="role">
        <el-select v-model="formData.role" placeholder="Select role" style="width: 100%">
          <el-option label="Super Admin" value="SUPER_ADMIN" />
          <el-option label="Admin" value="ADMIN" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">Cancel</el-button>
      <el-button type="primary" :loading="loading" @click="handleConfirm">
        {{ admin ? 'Update' : 'Create' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { createAdmin, updateAdmin } from '@/api/admins'
import type { Admin, CreateAdminRequest, UpdateAdminRequest } from '@/types'

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

const formData = reactive<CreateAdminRequest & { password?: string }>({
  username: '',
  password: '',
  role: 'ADMIN'
})

const rules: FormRules = {
  username: [{ required: true, message: 'Please enter username', trigger: 'blur' }],
  password: [
    {
      validator: (rule, value, callback) => {
        if (!props.admin && !value) {
          callback(new Error('Please enter password'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ],
  role: [{ required: true, message: 'Please select role', trigger: 'change' }]
}

watch(() => props.admin, (val) => {
  if (val) {
    formData.username = val.username
    formData.password = ''
    formData.role = val.role
  } else {
    formRef.value?.resetFields()
  }
})

watch(() => props.modelValue, (val) => {
  if (!val && !props.admin) {
    formRef.value?.resetFields()
  }
})

const handleConfirm = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (!valid) return

    loading.value = true
    try {
      let res
      if (props.admin) {
        const updateData: UpdateAdminRequest = {
          role: formData.role
        }
        if (formData.password) {
          updateData.password = formData.password
        }
        res = await updateAdmin(props.admin.id, updateData)
      } else {
        res = await createAdmin({
          username: formData.username,
          password: formData.password!,
          role: formData.role
        })
      }

      if (res.code === 0) {
        ElMessage.success(props.admin ? 'Admin updated successfully' : 'Admin created successfully')
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

<style scoped>
.form-tip {
  font-size: 12px;
  color: #6b7280;
  margin-top: 4px;
}
</style>
