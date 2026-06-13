-- cleanup-e2e-data.sql
--
-- 清理 E2E 测试残留数据。每次跑测试前/后执行，确保 DB 干净。
--
-- 触发条件（任一匹配即删）：
--   - 用户 openid 以 'dev_e2e_' 开头（wxapp dev-mode 测试用户）
--   - 赛事 title 以 'E2E-' 开头（Playwright 创建的测试赛事）
--
-- 删除顺序遵循外键引用：point_record → registration → ranking → event → user
-- 软删的赛事（deleted_at IS NOT NULL）也物理删除，避免长期累积。
--
-- 用法：
--   mysql -u root -proot hey_pickler < scripts/cleanup-e2e-data.sql
--
-- 安全：脚本只删匹配前缀的数据，不会触碰种子数据（seed_* openid、
-- 手工创建的赛事）和生产用户。可重复执行。

START TRANSACTION;

-- 1. 删测试用户的积分记录
DELETE FROM point_record
 WHERE user_id IN (SELECT id FROM user WHERE openid LIKE 'dev\_e2e\_%');

-- 2. 删测试用户的报名记录
DELETE FROM registration
 WHERE user_id IN (SELECT id FROM user WHERE openid LIKE 'dev\_e2e\_%');

-- 3. 删测试用户在 ranking 表的记录
DELETE FROM ranking
 WHERE user_id IN (SELECT id FROM user WHERE openid LIKE 'dev\_e2e\_%');

-- 4. 物理删测试赛事（包括软删的）
DELETE FROM event WHERE title LIKE 'E2E-%';

-- 5. 删测试用户本体
DELETE FROM user WHERE openid LIKE 'dev\_e2e\_%';

COMMIT;

-- 报告清理结果
SELECT
  (SELECT COUNT(*) FROM user WHERE openid LIKE 'dev\_e2e\_%') AS remaining_e2e_users,
  (SELECT COUNT(*) FROM event WHERE title LIKE 'E2E-%') AS remaining_e2e_events;
