<template>
  <div class="app-container">
    <AppSidebar />
    <div class="main-container">
      <AppHeader />
      <div class="content">
        <router-view />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import AppSidebar from './AppSidebar.vue'
import AppHeader from './AppHeader.vue'
</script>

<style scoped>
/* Styles are in src/styles/index.scss */
</style>
