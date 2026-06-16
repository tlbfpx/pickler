-- 清理 ranking 表和 registration 表里指向已删用户的孤儿行
--
-- 根因：历史测试期间产生了一些 ranking/registration 行，对应的 user 后来被
-- 直接物理删除（绕过 MyBatis-Plus 软删除机制），导致 UI 上排名/最新报名
-- 列表出现"空白用户"或"未知用户"。
--
-- 修复策略：
-- - ranking 表硬删：排名是衍生数据，可由 refreshRankings 重建，无审计价值
-- - registration 表改 status=CANCELLED：保留业务原始数据可审计，
--   AdminDashboardController.notIn(WITHDRAWN, CANCELLED) 自动过滤掉

DELETE r FROM ranking r
LEFT JOIN user u ON r.user_id = u.id AND u.deleted_at IS NULL
WHERE u.id IS NULL;

UPDATE registration reg
LEFT JOIN user u ON reg.user_id = u.id AND u.deleted_at IS NULL
SET reg.status = 'CANCELLED', reg.updated_at = NOW()
WHERE u.id IS NULL AND reg.status NOT IN ('WITHDRAWN', 'CANCELLED');
