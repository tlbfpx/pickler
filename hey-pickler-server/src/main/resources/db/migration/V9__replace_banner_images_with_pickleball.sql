-- 将 banner 图片全部替换为可正常访问的匹克球比赛图片（Unsplash 直链）。
--
-- 背景：历史 banner 使用百度图片（img0.baidu.com）、test.com 等不可达或不符合
--   校验规则的 URL，导致小程序端破图，其中百度图片那条还被 V6 标记为 INACTIVE。
-- 来源：Unsplash，免费商用、无需署名（https://unsplash.com/license）。
--
-- URL 形如 https://images.unsplash.com/photo-xxx?w=1600&h=900&fit=crop&q=80&fm=jpg&.jpg：
--   * 以 .jpg 结尾 → 满足 BannerCreateRequest 的 @Pattern 正则；
--   * 实测 HEAD 返回 200 + content-type: image/jpeg → 通过 HeadBasedImageUrlValidator；
--   * imgix CDN 无防盗链 → 小程序/浏览器均可直链加载。
--
-- 按 id 取模轮询分配 5 张图；同时把历史 INACTIVE 恢复为 ACTIVE 以正常展示。
-- banner 表无 updated_at 列（V1 schema 只有 created_at），不更新时间戳。
UPDATE banner
SET image_url = ELT(
      MOD(id, 5) + 1,
      'https://images.unsplash.com/photo-1737476997205-b3336182f215?w=1600&h=900&fit=crop&q=80&fm=jpg&.jpg',
      'https://images.unsplash.com/photo-1723004714201-cf224222b897?w=1600&h=900&fit=crop&q=80&fm=jpg&.jpg',
      'https://images.unsplash.com/photo-1734161081396-0f0572a16bf6?w=1600&h=900&fit=crop&q=80&fm=jpg&.jpg',
      'https://images.unsplash.com/photo-1722444732959-f50e30ee29d0?w=1600&h=900&fit=crop&q=80&fm=jpg&.jpg',
      'https://images.unsplash.com/photo-1711996151738-657bd95b7cdb?w=1600&h=900&fit=crop&q=80&fm=jpg&.jpg'
    ),
    status = 'ACTIVE';
