import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { Admin } from '@/types'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(localStorage.getItem('admin_token'))
  const admin = ref<Admin | null>(null)

  const setToken = (newToken: string) => {
    token.value = newToken
    localStorage.setItem('admin_token', newToken)
  }

  const setAdmin = (newAdmin: Admin) => {
    admin.value = newAdmin
  }

  const logout = () => {
    token.value = null
    admin.value = null
    localStorage.removeItem('admin_token')
  }

  const isAuthenticated = () => {
    return !!token.value
  }

  return {
    token,
    admin,
    setToken,
    setAdmin,
    logout,
    isAuthenticated
  }
})
