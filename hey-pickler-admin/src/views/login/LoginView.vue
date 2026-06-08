<template>
  <div class="login-container">
    <div class="login-card">
      <h1 class="login-title">Hey Pickler Admin</h1>
      <el-form ref="formRef" :model="formData" :rules="rules" label-position="top">
        <el-form-item label="Username" prop="username">
          <el-input
            v-model="formData.username"
            placeholder="Enter username"
            size="large"
            :prefix-icon="User"
          />
        </el-form-item>
        <el-form-item label="Password" prop="password">
          <el-input
            v-model="formData.password"
            type="password"
            placeholder="Enter password"
            size="large"
            :prefix-icon="Lock"
            @keyup.enter="handleLogin"
          />
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            size="large"
            :loading="loading"
            @click="handleLogin"
            style="width: 100%"
          >
            Login
          </el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { login } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()

const formRef = ref<FormInstance>()
const loading = ref(false)

const formData = reactive({
  username: '',
  password: ''
})

const rules: FormRules = {
  username: [{ required: true, message: 'Please enter username', trigger: 'blur' }],
  password: [{ required: true, message: 'Please enter password', trigger: 'blur' }]
}

const handleLogin = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (!valid) return

    loading.value = true
    try {
      const res = await login(formData)
      if (res.code === 0) {
        authStore.setToken(res.data.token)
        authStore.setAdmin(res.data.admin)
        localStorage.setItem('admin_info', JSON.stringify(res.data.admin))
        ElMessage.success('Login successful!')
        router.push('/')
      } else {
        ElMessage.error(res.message || 'Login failed')
      }
    } catch (error) {
      ElMessage.error('Login failed, please try again')
    } finally {
      loading.value = false
    }
  })
}
</script>

<style scoped>
/* Styles are in src/styles/index.scss */
</style>
