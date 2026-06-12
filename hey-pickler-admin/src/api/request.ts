import axios, { AxiosInstance, AxiosError, InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'

const baseURL = '/api/admin'

const request: AxiosInstance = axios.create({
  baseURL,
  timeout: 30000
})

// Request interceptor
request.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = localStorage.getItem('admin_token')
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// Response interceptor
request.interceptors.response.use(
  (response) => {
    return response.data
  },
  (error: AxiosError) => {
    if (error.response) {
      const status = error.response.status
      const data = error.response.data as any
      const msg = data?.message || ''
      switch (status) {
        case 400:
          ElMessage.error(msg || '请求参数有误，请检查后重试')
          break
        case 401:
          ElMessage.error('登录已过期，请重新登录')
          localStorage.removeItem('admin_token')
          localStorage.removeItem('admin_info')
          router.push('/login')
          break
        case 403:
          ElMessage.error('没有权限执行此操作')
          break
        case 404:
          ElMessage.error(msg || '请求的资源不存在')
          break
        case 429:
          ElMessage.error('操作过于频繁，请稍后再试')
          break
        case 500:
          ElMessage.error(msg || '服务器开小差了，请稍后再试')
          break
        default:
          ElMessage.error(msg || '请求失败，请稍后重试')
      }
    } else {
      ElMessage.error('网络连接异常，请检查网络')
    }
    return Promise.reject(error)
  }
)

export default request
