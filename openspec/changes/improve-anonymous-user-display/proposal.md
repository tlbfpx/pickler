# Proposal: 改善匿名用户的列表显示

## Why

QA 在 2026-06-16 测试 `/users` 页发现：当用户没填昵称（ nickname 为空）时，列表「用户」列显示为 `? -` — 头像 alt 是 `?`，名称是 `-`，看起来像加载失败的占位符，不像真实用户。

根因：`UserListView.vue:64-66` 的模板：
```vue
{{ row.nickname?.[0] || '?' }}      <!-- 头像 alt -->
{{ row.nickname || '-' }}           <!-- 名称 -->
```
默认 fallback 字符不友好。

## What Changes

- 头像 alt 用 `匿`（昵称首字符位置）
- 名称 fallback 用 `匿名用户` 替代 `-`
- 头像图标改用 Element Plus 内置 User 图标，避免裸 `?` 看起来像错误

## Impact

- **Affected capabilities**: `user`（前端）
- **Affected code**: `hey-pickler-admin/src/views/users/UserListView.vue`
- **Affected API**: 无（纯前端模板）
- **Operational**: 无

## Non-goals

- 不引入默认头像 URL（需 OSS，scope 外）
- 不改用户创建逻辑强制昵称（产品决策）
- 不改其他页面的相似 fallback（如 banner、ranking 等）— 留 v2 统一处理
