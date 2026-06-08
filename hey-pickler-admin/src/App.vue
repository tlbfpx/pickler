<template>
  <router-view />
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()

onMounted(() => {
  // Restore admin info from localStorage if available
  const savedAdmin = localStorage.getItem('admin_info')
  if (savedAdmin) {
    try {
      authStore.setAdmin(JSON.parse(savedAdmin))
    } catch (e) {
      console.error('Failed to parse saved admin info', e)
    }
  }
})
</script>

<style>
@import './styles/index.scss';
</style>
