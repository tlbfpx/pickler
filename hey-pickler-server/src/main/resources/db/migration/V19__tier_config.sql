-- 段位配置（方案③ 专用表，双轨 per-track）
-- tier_code 双轨统一 BRONZE..MASTER（不可改，系统绑定）；track/name/color/threshold/icon 可配
-- 软删：deleted_at NULL=未删（@TableLogic，对齐 sys_dict 范式）

CREATE TABLE tier_config (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  track        VARCHAR(16)     NOT NULL,
  tier_code    VARCHAR(16)     NOT NULL,
  tier_name    VARCHAR(32)     NOT NULL,
  tier_color   VARCHAR(16)     NOT NULL,
  threshold    INT             NOT NULL DEFAULT 0,
  icon         VARCHAR(16)     NULL,
  sort         INT             NOT NULL DEFAULT 0,
  description  VARCHAR(255)    NULL,
  created_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted_at   DATETIME        NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_track_tier (track, tier_code),
  KEY idx_track_sort (track, sort)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- seed：STAR 沿用青铜…王者（现有前端色 + STAR 阈值）
INSERT INTO tier_config (track, tier_code, tier_name, tier_color, threshold, icon, sort, description) VALUES
  ('STAR','BRONZE',  '青铜', '#A56C2C', 0,     '🥉', 0, 'STAR 青铜段位'),
  ('STAR','SILVER',  '白银', '#9CA3AF', 500,   '🥈', 1, 'STAR 白银段位'),
  ('STAR','GOLD',    '黄金', '#E6A23C', 1200,  '🥇', 2, 'STAR 黄金段位'),
  ('STAR','PLATINUM','铂金', '#409EFF', 2500,  '💎', 3, 'STAR 铂金段位'),
  ('STAR','DIAMOND', '钻石', '#9C27B0', 5000,  '💠', 4, 'STAR 钻石段位'),
  ('STAR','MASTER',  '王者', '#EF4444', 10000, '👑', 5, 'STAR 王者段位');

-- seed：PARTY 球友称号系（见习→活力→热血→资深→明星→传奇 + 暖色活力渐变 + PARTY 阈值）
INSERT INTO tier_config (track, tier_code, tier_name, tier_color, threshold, icon, sort, description) VALUES
  ('PARTY','BRONZE',  '见习球友', '#94A3B8', 0,    '🌟', 0, 'PARTY 见习球友'),
  ('PARTY','SILVER',  '活力球友', '#FBBF24', 200,  '⭐', 1, 'PARTY 活力球友'),
  ('PARTY','GOLD',    '热血球友', '#F97316', 500,  '✨', 2, 'PARTY 热血球友'),
  ('PARTY','PLATINUM','资深球友', '#EF4444', 1200, '⭐⭐', 3, 'PARTY 资深球友'),
  ('PARTY','DIAMOND', '明星球友', '#EC4899', 2500, '🏅', 4, 'PARTY 明星球友'),
  ('PARTY','MASTER',  '传奇球友', '#F59E0B', 5000, '👑', 5, 'PARTY 传奇球友');
