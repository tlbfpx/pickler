<template>
  <div class="sidebar">
    <div class="sidebar-logo">Hey Pickler 管理后台</div>
    <el-menu
      :default-active="activeMenu"
      class="sidebar-menu"
      background-color="#001529"
      text-color="#fff"
      active-text-color="#409EFF"
      :router="true"
    >
      <el-menu-item index="/">
        <el-icon><DataBoard /></el-icon>
        <span>控制台</span>
      </el-menu-item>
      <el-menu-item index="/users">
        <el-icon><User /></el-icon>
        <span>用户管理</span>
      </el-menu-item>
      <el-menu-item index="/events">
        <el-icon><Calendar /></el-icon>
        <span>赛事管理</span>
      </el-menu-item>
      <el-menu-item index="/rankings">
        <el-icon><Trophy /></el-icon>
        <span>排名管理</span>
      </el-menu-item>
      <el-menu-item index="/banners">
        <el-icon><Picture /></el-icon>
        <span>Banner管理</span>
      </el-menu-item>
      <el-menu-item index="/admins">
        <el-icon><UserFilled /></el-icon>
        <span>管理员管理</span>
      </el-menu-item>
      <el-menu-item index="/ban-records">
        <el-icon><Document /></el-icon>
        <span>操作日志</span>
      </el-menu-item>
    </el-menu>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const activeMenu = computed(() => route.path)
</script>

<style scoped>
.sidebar-menu {
  border-right: none;
  flex: 1;
}

:deep(.el-menu-item) {
  font-size: 14px;
}

:deep(.el-menu-item span) {
  margin-left: 8px;
}
</style>
