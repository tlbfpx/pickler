<template>
  <el-dialog
    :model-value="modelValue"
    :title="admin ? '编辑管理员' : '新建管理员'"
    width="500px"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <el-form
      ref="formRef"
      :model="formData"
      :rules="rules"
      label-position="top"
    >
      <el-form-item label="用户名" prop="username">
        <el-input
          v-model="formData.username"
          placeholder="请输入用户名"
          :disabled="!!admin"
        />
      </el-form-item>
      <el-form-item label="密码" prop="password">
        <el-input
          v-model="formData.password"
          type="password"
          placeholder="请输入密码"
          show-password
        />
        <div v-if="admin" class="form-tip">
          留空则保持原密码不变
        </div>
      </el-form-item>
      <el-form-item label="角色" prop="role">
        <el-select v-model="formData.role" placeholder="请选择角色" style="width: 100%">
          <el-option label="超级管理员" value="SUPER_ADMIN" />
          <el-option label="管理员" value="ADMIN" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="loading" @click="handleConfirm">
        {{ admin ? '更新' : '新建' }}
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
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [
    {
      validator: (rule, value, callback) => {
        if (!props.admin && !value) {
          callback(new Error('请输入密码'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }]
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
        ElMessage.success(props.admin ? '管理员更新成功' : '管理员创建成功')
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

<style scoped>
.form-tip {
  font-size: 12px;
  color: #6b7280;
  margin-top: 4px;
}
</style>
