-- 把 image_url 不符合新校验规则（https + 图片扩展名）的 banner 标为 INACTIVE。
-- 让运营在后台看到后人工补图，而非硬删数据。
-- 注：以 SQL regex 表达与 BannerCreateRequest 的 @Pattern 同样的约束。
-- 注：banner 表无 updated_at 列（V1 schema 只有 created_at），不更新时间戳。
UPDATE banner
SET status = 'INACTIVE'
WHERE image_url NOT REGEXP '^https://[^/]+/.*\\.(jpg|jpeg|png|webp|gif)(\\?.*)?$';
