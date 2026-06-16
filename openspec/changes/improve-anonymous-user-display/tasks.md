# Tasks: 改善匿名用户列表显示

## 1. 修改模板
- [ ] 1.1 `UserListView.vue` 头部 import `User` icon from `@element-plus/icons-vue`
- [ ] 1.2 修改 avatar slot：把 `{{ row.nickname?.[0] || '?' }}` 替换为 `<el-icon><User /></el-icon>`
- [ ] 1.3 修改 name span：把 `{{ row.nickname || '-' }}` 替换为 `{{ row.nickname || '匿名用户' }}`

## 2. 验证
- [ ] 2.1 启动前端 + 后端，访问 `/users`
- [ ] 2.2 找一个无昵称用户（如 id 2070），验证显示「匿名用户」+ User 图标
- [ ] 2.3 验证有昵称用户不受影响
- [ ] 2.4 检查浏览器控制台无新错误

## 3. 归档
- [ ] 3.1 移动 change 目录到 `archive/`
- [ ] 3.2 合并 spec delta 到 `user/spec.md`
