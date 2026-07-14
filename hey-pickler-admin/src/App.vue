<template>
  <router-view />
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useDictStore } from '@/stores/dict'

const authStore = useAuthStore()
const dictStore = useDictStore()

onMounted(() => {
  // Restore admin info from localStorage if available
  const savedAdmin = localStorage.getItem('admin_info')
  if (savedAdmin && savedAdmin !== 'undefined') {
    try {
      authStore.setAdmin(JSON.parse(savedAdmin))
    } catch (e) {
      localStorage.removeItem('admin_info')
    }
  }

  // 异步加载字典 bundle，不阻塞渲染；失败时静默保留 localStorage 缓存
  dictStore.load()
})
</script>

<style>
@import './styles/index.scss';
</style>
