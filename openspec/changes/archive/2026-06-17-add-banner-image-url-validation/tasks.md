# Tasks: Banner 图片地址校验强化

## 1. 后端 DTO regex 加严
- [x] 1.1 修改 `BannerCreateRequest.imageUrl` 的 `@Pattern` 为 `^https://[^/]+/.*\.(jpg|jpeg|png|webp|gif)(\?.*)?$`（CASE_INSENSITIVE）
- [x] 1.2 同步修改 `linkUrl` regex，强制 https（与 imageUrl 一致）

## 2. 后端 HEAD 预检
- [x] 2.1 在 `BannerServiceImpl` 注入 `ImageUrlValidator`，按 design.md 实现
- [x] 2.2 在 `createBanner` 入口调用 `validate`
- [x] 2.3 在 `updateBanner` 入口调用 `validate`

## 3. 后端单测（TDD — 先写测试）
- [x] 3.1 写 `ImageUrlValidatorTest.ok`（启动本地 HttpServer 返回 200 + image/jpeg）
- [x] 3.2 写 `ImageUrlValidatorTest.404`
- [x] 3.3 写 `ImageUrlValidatorTest.nonImageContentType`
- [x] 3.4 写 `ImageUrlValidatorTest.unreachableHost`
- [x] 3.5 跑 `mvn test -Dtest='ImageUrlValidatorTest,BannerServiceTest'` 全绿

## 4. 前端 rules 强化
- [x] 4.1 修改 `BannerFormDialog.vue` 的 `rules.imageUrl`，添加图片扩展名 pattern
- [x] 4.2 修改 `rules.linkUrl`，添加 https-only pattern

## 5. 历史数据清理
- [x] 5.1 新建 `V6__cleanup_invalid_banner_urls.sql`，把格式不合法的 banner 标为 INACTIVE
- [x] 5.2 启动应用验证 Flyway 自动应用

## 6. 验证
- [x] 6.1 `mvn test`（全量后端测试）
- [x] 6.2 启动应用，新建 Banner 时填 `https://test.com/x.jpg` → 应被拒（HEAD 超时返回 1001）
- [x] 6.3 验证历史非法 banner URL 已变为 INACTIVE

## 7. 归档
- [x] 7.1 移动 `openspec/changes/add-banner-image-url-validation/` 到 `archive/`
- [x] 7.2 合并 spec delta 到 `openspec/specs/content/spec.md`
