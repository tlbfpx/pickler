import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { Admin } from '@/types'

function isTokenExpired(token: string): boolean {
  try {
    const payload = JSON.parse(atob(token.split('.')[1]))
    return payload.exp * 1000 < Date.now() + 30000 // 30s clock skew tolerance
  } catch {
    return true
  }
}

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
    localStorage.removeItem('admin_info')
    localStorage.removeItem('admin_role')
  }

  const isAuthenticated = () => {
    if (!token.value) return false
    if (isTokenExpired(token.value)) {
      logout()
      return false
    }
    return true
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
