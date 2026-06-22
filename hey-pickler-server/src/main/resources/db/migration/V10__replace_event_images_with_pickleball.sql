-- 将所有赛事/活动(event)的 banner_url 替换为可正常访问的匹克球比赛图片（Unsplash 直链）。
-- 处理方式与 V9 (banner 表) 一致：Unsplash 直链 + 按 id 取模轮询。
-- 背景：历史 event.banner_url 多为 picsum.photos（小程序端不可达）或 NULL，导致破图/空白。
-- 复用 V9 中已实测通过（HEAD 200 + content-type: image/jpeg）的同一批匹克球图片。
-- 注：event.banner_url 无 @Pattern 校验、EventServiceImpl 也不走 ImageUrlValidator，
--   但仍统一使用 https + .jpg 结尾的可达直链，保持与 banner 一致、确保小程序可加载。
-- 仅更新未软删除的赛事（event 表有 deleted_at 软删除字段，区别于 banner 表）。
UPDATE event
SET banner_url = ELT(
      MOD(id, 5) + 1,
      'https://images.unsplash.com/photo-1737476997205-b3336182f215?w=1600&h=900&fit=crop&q=80&fm=jpg&.jpg',
      'https://images.unsplash.com/photo-1723004714201-cf224222b897?w=1600&h=900&fit=crop&q=80&fm=jpg&.jpg',
      'https://images.unsplash.com/photo-1734161081396-0f0572a16bf6?w=1600&h=900&fit=crop&q=80&fm=jpg&.jpg',
      'https://images.unsplash.com/photo-1722444732959-f50e30ee29d0?w=1600&h=900&fit=crop&q=80&fm=jpg&.jpg',
      'https://images.unsplash.com/photo-1711996151738-657bd95b7cdb?w=1600&h=900&fit=crop&q=80&fm=jpg&.jpg'
    )
WHERE deleted_at IS NULL;
