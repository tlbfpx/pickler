# Tasks: Banner 图片地址校验强化

## 1. 后端 DTO regex 加严
- [ ] 1.1 修改 `BannerCreateRequest.imageUrl` 的 `@Pattern` 为 `^https://[^/]+/.*\.(jpg|jpeg|png|webp|gif)(\?.*)?$`（CASE_INSENSITIVE）
- [ ] 1.2 同步修改 `linkUrl` regex，强制 https（与 imageUrl 一致）

## 2. 后端 HEAD 预检
- [ ] 2.1 在 `BannerServiceImpl` 新增私有方法 `validateImageUrl(String url)`，按 design.md 实现
- [ ] 2.2 在 `createBanner` 入口调用 `validateImageUrl`
- [ ] 2.3 在 `updateBanner` 入口调用 `validateImageUrl`（仅当 imageUrl 变化时）

## 3. 后端单测（TDD — 先写测试）
- [ ] 3.1 写 `BannerServiceImplTest.validateImageUrl_ok`（mock URLConnection 返回 200 + image/jpeg）
- [ ] 3.2 写 `BannerServiceImplTest.validateImageUrl_404`
- [ ] 3.3 写 `BannerServiceImplTest.validateImageUrl_nonImageContentType`
- [ ] 3.4 写 `BannerServiceImplTest.validateImageUrl_timeout`
- [ ] 3.5 跑 `mvn test -Dtest=BannerServiceImplTest` 全绿
- [ ] 3.6 加 `BannerIntegrationTest.create_withInvalidUrl_returns400`

## 4. 前端 rules 强化
- [ ] 4.1 修改 `BannerFormDialog.vue` 的 `rules.imageUrl`，添加图片扩展名 pattern
- [ ] 4.2 修改 `rules.linkUrl`，添加 https-only pattern

## 5. 历史数据清理
- [ ] 5.1 新建 `V6__cleanup_invalid_banner_urls.sql`，把格式不合法的 banner 标为 INACTIVE
- [ ] 5.2 启动应用验证 Flyway 自动应用

## 6. 验证
- [ ] 6.1 `mvn test`（全量后端测试）
- [ ] 6.2 启动应用 + 前端，新建 Banner 时填 `https://test.com/x.jpg` → 应被拒
- [ ] 6.3 验证历史 banner 19/20/21 已变为 INACTIVE

## 7. 归档
- [ ] 7.1 移动 `openspec/changes/add-banner-image-url-validation/` 到 `archive/`
- [ ] 7.2 合并 spec delta 到 `openspec/specs/content/spec.md`
