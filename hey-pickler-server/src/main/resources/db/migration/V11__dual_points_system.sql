-- V11: 双积分体系（战力/活力）—— season 表 + point_record 来源/赛季 + tier 3 档→6 档重算

-- 1) season 表（按类型独立结算周期）
CREATE TABLE season (
  id         BIGINT NOT NULL AUTO_INCREMENT,
  type       VARCHAR(8)  NOT NULL,
  code       VARCHAR(16) NOT NULL,
  name       VARCHAR(32),
  start_date DATE,
  end_date   DATE,
  status     VARCHAR(8)  NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_type_code (type, code)
);

-- 2) 初始化两条 CURRENT（沿用现有 CURRENT_SEASON='2026-Q2'）
INSERT INTO season (type, code, name, status) VALUES
  ('STAR',  '2026-Q2', '2026 第二季度·战力', 'CURRENT'),
  ('PARTY', '2026-Q2', '2026 第二季度·活力', 'CURRENT');

-- 3) point_record 加来源 + 赛季
ALTER TABLE point_record ADD COLUMN source     VARCHAR(16) NOT NULL DEFAULT 'MANUAL';
ALTER TABLE point_record ADD COLUMN season_code VARCHAR(16);
UPDATE point_record SET season_code = '2026-Q2' WHERE season_code IS NULL;
CREATE INDEX idx_type_season ON point_record (type, season_code);
-- 不建 idx_user(user_id)：V1 已有 idx_user_created(user_id, created_at DESC)，最左前缀覆盖

-- 4) ranking.tier 重算（覆盖旧值 SHINING/SUPER/LEGEND → 6 档）—— STAR 按 star_points
UPDATE ranking r JOIN user u ON r.user_id = u.id
SET r.tier = CASE
  WHEN u.star_points >= 10000 THEN 'MASTER'
  WHEN u.star_points >= 5000  THEN 'DIAMOND'
  WHEN u.star_points >= 2500  THEN 'PLATINUM'
  WHEN u.star_points >= 1200  THEN 'GOLD'
  WHEN u.star_points >= 500   THEN 'SILVER'
  ELSE 'BRONZE' END
WHERE r.type = 'STAR';

UPDATE ranking r JOIN user u ON r.user_id = u.id
SET r.tier = CASE
  WHEN u.party_points >= 5000 THEN 'MASTER'
  WHEN u.party_points >= 2500 THEN 'DIAMOND'
  WHEN u.party_points >= 1200 THEN 'PLATINUM'
  WHEN u.party_points >= 500  THEN 'GOLD'
  WHEN u.party_points >= 200  THEN 'SILVER'
  ELSE 'BRONZE' END
WHERE r.type = 'PARTY';

-- 5) user.star_tier / party_tier 同步重算
UPDATE user SET star_tier = CASE
  WHEN star_points >= 10000 THEN 'MASTER'
  WHEN star_points >= 5000  THEN 'DIAMOND'
  WHEN star_points >= 2500  THEN 'PLATINUM'
  WHEN star_points >= 1200  THEN 'GOLD'
  WHEN star_points >= 500   THEN 'SILVER'
  ELSE 'BRONZE' END;

UPDATE user SET party_tier = CASE
  WHEN party_points >= 5000 THEN 'MASTER'
  WHEN party_points >= 2500 THEN 'DIAMOND'
  WHEN party_points >= 1200 THEN 'PLATINUM'
  WHEN party_points >= 500  THEN 'GOLD'
  WHEN party_points >= 200  THEN 'SILVER'
  ELSE 'BRONZE' END;
