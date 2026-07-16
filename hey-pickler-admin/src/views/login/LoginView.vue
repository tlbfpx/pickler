<template>
  <div
    class="login-container"
    :style="loginBg"
  >
    <div class="login-card">
      <img
        v-if="brandStore.logoUrl"
        :src="brandStore.logoUrl"
        class="login-logo"
        alt="logo"
      >
      <h1 class="login-title">
        {{ brandStore.appName }} 管理后台
      </h1>
      <p
        v-if="brandStore.slogan"
        class="login-slogan"
      >
        {{ brandStore.slogan }}
      </p>
      <el-form
        ref="formRef"
        :model="formData"
        :rules="rules"
        label-position="top"
      >
        <el-form-item
          label="用户名"
          prop="username"
        >
          <el-input
            v-model="formData.username"
            placeholder="请输入用户名"
            size="large"
            :prefix-icon="User"
          />
        </el-form-item>
        <el-form-item
          label="密码"
          prop="password"
        >
          <el-input
            v-model="formData.password"
            type="password"
            placeholder="请输入密码"
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
            style="width: 100%"
            @click="handleLogin"
          >
            登录
          </el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { login } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'
import { useBrandStore } from '@/stores/brand'
import { mixHex } from '@/utils/color'

const router = useRouter()
const authStore = useAuthStore()
const brandStore = useBrandStore()

// 登录页背景跟随品牌主色（主色 → 加深 25%），覆盖 index.scss 默认紫渐变
const loginBg = computed(() => ({
  background: `linear-gradient(135deg, ${brandStore.primaryColor} 0%, ${mixHex(brandStore.primaryColor, '#000000', 0.25)} 100%)`
}))

const formRef = ref<FormInstance>()
const loading = ref(false)

const formData = reactive({
  username: '',
  password: ''
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const handleLogin = async () => {
  if (!formRef.value) return

  try {
    await formRef.value.validate()
  } catch {
    return
  }

  loading.value = true
  try {
    const res = await login(formData)
    if (res.code === 0) {
      authStore.setToken(res.data.token)
      localStorage.setItem('admin_role', res.data.role)
      ElMessage.success('登录成功！')
      router.push('/')
    } else {
      ElMessage.error(res.message || '登录失败')
    }
  } catch {
    
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
/* 登录页其余样式在 src/styles/index.scss */
.login-logo {
  display: block;
  margin: 0 auto 16px;
  width: 72px;
  height: 72px;
  border-radius: 50%;
  object-fit: cover;
  background: #fff;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.login-slogan {
  text-align: center;
  color: #6b7280;
  font-size: 14px;
  margin-top: -20px;
  margin-bottom: 28px;
}
</style>
