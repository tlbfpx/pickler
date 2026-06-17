# Proposal: 加严 Banner 图片地址校验

## Why

QA 在 2026-06-16 测试 Banner 管理时发现：管理员可以保存 `https://test.com/banner.jpg` 这种不解析的 URL 作为 Banner 图片地址，导致：

1. 列表渲染时浏览器报 `net::ERR_CONNECTION_CLOSED`，Banner 缩略图永久显示「加载失败」
2. 微信小程序 / 管理后台首页消费该 Banner 时，终端用户看到破损图
3. 已有 3 条历史数据（banner id 19/20/21）处于此状态，需清理

根因：
- 后端 `BannerCreateRequest.imageUrl` 仅要求 `^https?://.*`，过于宽松
- 前端 `BannerFormDialog.vue` 只校验 `required`，没有格式校验
- 保存路径无任何可达性预检

## What Changes

- **后端 DTO**：加严 `imageUrl` regex，要求 https + 图片扩展名
- **后端 service**：创建/更新前做 HEAD 预检（3s 超时，要求 2xx + `Content-Type: image/*`）
- **前端 form**：补 `type: 'url'` + 图片扩展名 pattern 校验
- **历史数据**：通过 V6 migration 把现存死链 banner 标记为 `INACTIVE`，等运营人工补图

## Impact

- **Affected capabilities**: `content`
- **Affected code**:
  - `hey-pickler-server/.../dto/admin/BannerCreateRequest.java`
  - `hey-pickler-server/.../service/impl/BannerServiceImpl.java`（新增 `validateImageUrl` 私有方法）
  - `hey-pickler-server/.../db/migration/V6__cleanup_invalid_banner_urls.sql`
  - `hey-pickler-admin/src/views/banners/BannerFormDialog.vue`
- **Affected API**: `POST/PUT /api/admin/banners` 行为变化 — 不合法 URL 返回 400 而非 201
- **Operational**: 网络不通的图片主机会让保存失败（可接受 — 比保存死链好）

## Non-goals

- 不引入 OSS 上传 — 外链模式保留
- 不做异步轮询所有现存 Banner 健康度 — 只在写入时校验
- 不重写 BannerFormDialog 的 UI — 只加 rules
