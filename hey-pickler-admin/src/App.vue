<template>
  <router-view />
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useDictStore } from '@/stores/dict'
import { useBrandStore } from '@/stores/brand'

const authStore = useAuthStore()
const dictStore = useDictStore()
const brandStore = useBrandStore()

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

  // 品牌（title / favicon / 主题色）+ 字典 bundle 异步加载，不阻塞渲染；
  // 失败时静默保留 localStorage 缓存（store 初始化已用缓存预填主题，首屏不闪）
  brandStore.load()
  dictStore.load()
})
</script>

<style>
@import './styles/index.scss';
</style>
