# Tasks: 改善匿名用户列表显示

## 1. 修改模板
- [x] 1.1 `UserListView.vue` 头部 import `User` icon from `@element-plus/icons-vue`
- [x] 1.2 修改 avatar slot：把 `{{ row.nickname?.[0] || '?' }}` 替换为 `<el-icon><User /></el-icon>`
- [x] 1.3 修改 name span：把 `{{ row.nickname || '-' }}` 替换为 `{{ row.nickname || '匿名用户' }}`

## 2. 验证
- [x] 2.1 启动前端 + 后端，访问 `/users`
- [x] 2.2 验证无昵称用户显示「匿名用户」+ User 图标
- [x] 2.3 验证有昵称用户不受影响

## 3. 归档
- [x] 3.1 移动 change 目录到 `archive/`
- [x] 3.2 合并 spec delta 到 `user/spec.md`
