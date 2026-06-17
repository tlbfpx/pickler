# Design: Banner 图片地址校验强化

## 总体思路

**双层防御**：前端做即时格式反馈，后端做权威校验。后端用「regex 拦截明显非法 + HEAD 预检拦截不可达」两道关。

## 模块改动

### 1. 后端 DTO 校验加严

`BannerCreateRequest.imageUrl` 当前 regex：
```
^https?://.*
```

改为：
```
^https://[^/]+/.*\.(jpg|jpeg|png|webp|gif)(\?.*)?$
```
（大小写不敏感）

**决策**：强制 HTTPS。理由：
- HTTP 图片会被现代浏览器 mixed-content 拦截（生产环境后端是 HTTPS）
- 微信小程序 `<image>` 组件拒绝 HTTP 资源
- 没有理由再允许 HTTP

**决策**：必须以图片扩展名结尾。理由：
- HEAD 预检可以晚一点做，但格式校验必须同步快速反馈
- 业务上 Banner 不会用动态生成的 URL（如 `/img/abc`），都是 OSS 直链

### 2. 后端 HEAD 预检

新增 `BannerServiceImpl.validateImageUrl(String url)`：

```java
private void validateImageUrl(String url) {
    try {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setInstanceFollowRedirects(true);
        conn.setConnectTimeout(3000);
        conn.setReadTimeout(3000);
        conn.setRequestMethod("HEAD");
        int code = conn.getResponseCode();
        String contentType = conn.getContentType();
        if (code < 200 || code >= 300) {
            throw new BizException(ErrorCode.PARAM_ERROR, "图片地址不可访问（HTTP " + code + "）");
        }
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new BizException(ErrorCode.PARAM_ERROR, "图片地址返回非图片内容: " + contentType);
        }
    } catch (IOException e) {
        throw new BizException(ErrorCode.PARAM_ERROR, "图片地址校验失败: " + e.getMessage());
    }
}
```

在 `createBanner` / `updateBanner` 入口调用一次。

**决策**：用 `HttpURLConnection` 而非 OkHttp/WebClient — 不引入新依赖，3s 超时足够。

**决策**：HEAD 而非 GET — 节省带宽。如果对方服务器不支持 HEAD 返回 405，fallback 到 GET range（v2 再做，目前不让完美阻挡进度）。

**决策**：网络异常 → 抛业务异常而非吞掉。理由：吞掉 = 静默接受死链，等于没做校验。

### 3. 前端 rules

`BannerFormDialog.vue` 当前：
```typescript
imageUrl: [{ required: true, message: '请输入图片地址', trigger: 'blur' }]
```

改为：
```typescript
imageUrl: [
  { required: true, message: '请输入图片地址', trigger: 'blur' },
  {
    pattern: /^https:\/\/[^/]+\/.*\.(jpg|jpeg|png|webp|gif)(\?.*)?$/i,
    message: '图片地址必须为 https 开头且以 .jpg/.png/.webp/.gif 结尾',
    trigger: 'blur'
  }
]
```

### 4. 历史数据清理

新增 `V6__cleanup_invalid_banner_urls.sql`：
```sql
-- 把格式不合法的 banner 标为 INACTIVE，让运营在后台看到并人工补图
UPDATE banner
SET status = 'INACTIVE', updated_at = NOW()
WHERE image_url NOT REGEXP '^https://[^/]+/.*\\.(jpg|jpeg|png|webp|gif)(\\?.*)?$';
```

**决策**：软处理（INACTIVE）而非硬删 — 数据保留，运营可以编辑恢复。

**决策**：用 SQL regex 而非做 HEAD 请求 — migration 不应阻塞于网络。

## 数据流

```
管理员填表 → 前端 rules 拦截明显错误
       ↓
POST /api/admin/banners
       ↓
@Valid → BannerCreateRequest regex 二次拦截
       ↓
BannerServiceImpl.createBanner
       ↓
validateImageUrl (HEAD 预检)
       ↓
INSERT banner
```

## 关键决策

| 决策点 | 选择 | 拒绝的备选 | 理由 |
|--------|------|-----------|------|
| 协议 | 强制 HTTPS | 允许 HTTP | 浏览器/小程序已淘汰 HTTP 图片 |
| 扩展名 | 必须显式 | 任意 URL | 业务上只有 OSS 直链，简单可执行 |
| 预检方式 | HEAD | GET range / 不预检 | 平衡精度和成本 |
| 预检超时 | 3s | 1s / 10s | 用户容忍上限内，远端 CDN 足够 |
| 历史数据 | INACTIVE | 删除 / 留作可见死链 | 保留可恢复性 |
| 失败行为 | 抛业务异常 | 吞掉警告 | 静默 = 等于不校验 |

## 风险

| 风险 | 缓解 |
|------|------|
| 图片主机不支持 HEAD（返回 405） | 暂不处理；如线上出现，加 fallback 到 GET range |
| 图片主机临时宕机 → 合法 URL 被拒 | 错误信息明确告知「图片地址不可访问」，运营可重试 |
| regex 不允许新格式（如 .avif） | 留 v2；当前业务未涉及 |
| 预检拖慢保存接口 | 3s 上限 + 同步执行可接受；P95 < 500ms |

## 测试策略

- 单测 `BannerServiceImplTest`：
  - 合法 URL（mock URLConnection 返回 200 + image/jpeg）→ 通过
  - 404 → 抛 BizException
  - 非 image content-type → 抛 BizException
  - 超时 → 抛 BizException
- DTO 校验单测：用 `@Valid` 验证 regex 拒绝 `http://`, `https://foo`, `https://foo.com/x.txt`
- 前端不写自动化（项目无 vitest），手测覆盖
- Integration test `BannerIntegrationTest` 已有 create 流程，加一条「非法 URL → 400」case
