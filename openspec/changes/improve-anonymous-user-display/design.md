# Design: 改善匿名用户列表显示

## 总体思路

**纯前端模板调整**，零数据变更。

## 改动

`UserListView.vue:58-68` 当前：
```vue
<template #default="{ row }">
  <div class="user-cell">
    <el-avatar
      :size="32"
      :src="row.avatarUrl"
    >
      {{ row.nickname?.[0] || '?' }}
    </el-avatar>
    <span class="user-cell__name">{{ row.nickname || '-' }}</span>
  </div>
</template>
```

改为：
```vue
<template #default="{ row }">
  <div class="user-cell">
    <el-avatar :size="32" :src="row.avatarUrl">
      <el-icon><User /></el-icon>
    </el-avatar>
    <span class="user-cell__name">{{ row.nickname || '匿名用户' }}</span>
  </div>
</template>
```

`<script setup>` 头部添加：
```typescript
import { User } from '@element-plus/icons-vue'
```

## 决策

| 决策点 | 选择 | 拒绝的备选 | 理由 |
|--------|------|-----------|------|
| 头像 fallback | `<User />` icon | `?` / `匿` 字符 | 图标更专业，无「错误」语义 |
| 名称 fallback | `匿名用户` | `-` / `用户${id}` | 业内常见做法；不像 `-` 像缺数据 |
| 是否加 tooltip | 不加 | `el-tooltip` 显示 user_id | 当前页面 ID 已单独成列，重复 |

## 风险

无 — 纯展示层调整。

## 测试策略

- 项目无前端单测框架（无 vitest），手测覆盖：
  - 有昵称用户：正常显示
  - 无昵称用户：显示「匿名用户」+ User 图标
