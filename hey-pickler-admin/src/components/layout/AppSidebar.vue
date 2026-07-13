<template>
  <div class="sidebar">
    <div class="sidebar-logo">
      Hey Pickler 管理后台
    </div>
    <el-menu
      :default-active="activeMenu"
      class="sidebar-menu"
      background-color="#001529"
      text-color="#fff"
      active-text-color="#409EFF"
      :router="true"
    >
      <el-sub-menu
        v-for="g in groups"
        :key="g.name"
        :index="g.name"
      >
        <template #title>
          <span>{{ g.name }}</span>
        </template>
        <el-menu-item
          v-for="r in g.items"
          :key="r.path"
          :index="r.path"
        >
          <el-icon><component :is="iconMap[r.icon]" /></el-icon>
          <span>{{ r.title }}</span>
        </el-menu-item>
      </el-sub-menu>
    </el-menu>
  </div>
</template>

<script setup lang="ts">
import { computed, type Component } from 'vue'
import { useRoute, type RouteRecordRaw } from 'vue-router'
import * as ElIcons from '@element-plus/icons-vue'
import router from '@/router'

const route = useRoute()
const activeMenu = computed(() => route.path)
const iconMap: Record<string, Component> = ElIcons as Record<string, Component>

// 直接读静态路由表，避免 getRoutes() 返回 /login、/events/:id 等噪音
// 布局路由（path:'/'）的 children 才是菜单项；meta.hidden 不进菜单
const menuRoutes = (router.options.routes.find((r: RouteRecordRaw) => r.path === '/')?.children || [])
  .filter((r): r is RouteRecordRaw & { meta: { group: string; hidden?: boolean; title?: string; icon?: string } } =>
    Boolean(r.meta?.group) && !r.meta?.hidden
  )
  .map(r => ({ path: '/' + r.path, title: r.meta.title, icon: r.meta.icon, group: r.meta.group }))

const GROUP_ORDER = ['运营管理', '积分与排名', '内容运营', '数据', '系统']
const groups = computed(() => GROUP_ORDER
  .map(name => ({ name, items: menuRoutes.filter(r => r.group === name) }))
  .filter(g => g.items.length))
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
