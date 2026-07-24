-- V22: 场馆与场地预约领域（P1 基础层 + P2 预约引擎终态建表）
-- P1 使用前 5 张表 + booking_slot（读）；booking 写侧在 P2 启用。

CREATE TABLE venue (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  name            VARCHAR(128) NOT NULL COMMENT '场馆名',
  address         VARCHAR(256) NOT NULL COMMENT '地址',
  latitude        DECIMAL(10,7) NULL COMMENT '纬度(可选,地图预留)',
  longitude       DECIMAL(10,7) NULL COMMENT '经度(可选,地图预留)',
  cover_url       VARCHAR(512) NULL COMMENT '封面图',
  description     VARCHAR(1024) NULL COMMENT '描述',
  status          VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/INACTIVE',
  booking_lead_days INT NOT NULL DEFAULT 14 COMMENT '可订窗口(天)',
  created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted_at      DATETIME NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='场馆';

CREATE TABLE court (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  venue_id    BIGINT UNSIGNED NOT NULL COMMENT '所属场馆',
  name        VARCHAR(64) NOT NULL COMMENT '场地名(如 1号场)',
  court_type  VARCHAR(16) NOT NULL DEFAULT 'INDOOR' COMMENT 'INDOOR/OUTDOOR',
  slot_minutes INT NOT NULL DEFAULT 60 COMMENT '单格时长(分钟)',
  status      VARCHAR(16) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/CLOSED/MAINTENANCE',
  sort_order  INT NOT NULL DEFAULT 0,
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted_at  DATETIME NULL,
  PRIMARY KEY (id),
  -- 软删安全唯一键(spec §5.7):STORED 生成列,deleted_at IS NULL 时=name,软删后 NULL 不占位(MySQL 多 NULL 放行)
  -- 注:这是"STORED 生成列 + 列唯一键",与 V17 的"函数表达式索引"是不同机制,勿混淆
  name_key VARCHAR(64) AS (CASE WHEN deleted_at IS NULL THEN name END) STORED,
  UNIQUE KEY uk_venue_court_name (venue_id, name_key),
  KEY idx_court_venue (venue_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='场地';

CREATE TABLE venue_business_hour (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  venue_id    BIGINT UNSIGNED NOT NULL,
  day_of_week TINYINT NOT NULL COMMENT '0=周日..6=周六',
  open_time   TIME NULL COMMENT 'NULL=当日休',
  close_time  TIME NULL,
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_venue_dow (venue_id, day_of_week)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='场馆营业时间(每周7行,整行覆盖,无软删)';

CREATE TABLE venue_contact (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  venue_id   BIGINT UNSIGNED NOT NULL,
  type       VARCHAR(16) NOT NULL COMMENT 'PHONE/WECHAT/LANDLINE/EMAIL',
  value      VARCHAR(128) NOT NULL,
  label      VARCHAR(64) NULL COMMENT '如 前台',
  sort_order INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted_at DATETIME NULL,
  PRIMARY KEY (id),
  KEY idx_contact_venue (venue_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='场馆联系方式';

CREATE TABLE court_pricing_band (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  court_id    BIGINT UNSIGNED NOT NULL,
  day_type    VARCHAR(8) NOT NULL COMMENT 'WEEKDAY/WEEKEND/ALL',
  start_time  TIME NOT NULL,
  end_time    TIME NOT NULL,
  price       DECIMAL(10,2) NOT NULL,
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted_at  DATETIME NULL,
  PRIMARY KEY (id),
  KEY idx_band_court (court_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='场地时段定价带(同court同day_type禁止重叠,app层校验)';

-- ===== P2 预约引擎(P1 只读 booking_slot 算可用性) =====
CREATE TABLE booking (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  booking_no     VARCHAR(32) NOT NULL COMMENT 'BK{yyyyMMdd}-{seq}',
  user_id        BIGINT UNSIGNED NOT NULL,
  venue_id       BIGINT UNSIGNED NOT NULL,
  court_id       BIGINT UNSIGNED NOT NULL,
  slot_date      DATE NOT NULL,
  slot_start     DATETIME NOT NULL,
  slot_end       DATETIME NOT NULL,
  slots_count    INT NOT NULL,
  price_snapshot DECIMAL(10,2) NOT NULL,
  status         VARCHAR(16) NOT NULL COMMENT 'CONFIRMED/CANCELLED/COMPLETED/NO_SHOW',
  cancel_reason  VARCHAR(256) NULL,
  cancelled_at   DATETIME NULL,
  created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_booking_no (booking_no),
  KEY idx_booking_user (user_id, slot_start),
  KEY idx_booking_court (court_id, slot_start),
  KEY idx_booking_venue_date (venue_id, slot_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='预约主记录(append-only,无软删)';

CREATE TABLE booking_slot (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  booking_id BIGINT UNSIGNED NOT NULL,
  court_id   BIGINT UNSIGNED NOT NULL,
  slot_start DATETIME NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_court_slot (court_id, slot_start),
  KEY idx_slot_booking (booking_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='时段占用(取消即物理删除释放唯一键)';
