CREATE TABLE `user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `openid` VARCHAR(64) NOT NULL,
  `union_id` VARCHAR(64) DEFAULT NULL,
  `nickname` VARCHAR(64) DEFAULT NULL,
  `avatar_url` VARCHAR(512) DEFAULT NULL,
  `phone` VARCHAR(64) DEFAULT NULL,
  `city` VARCHAR(64) DEFAULT NULL,
  `star_points` INT NOT NULL DEFAULT 0,
  `party_points` INT NOT NULL DEFAULT 0,
  `star_tier` VARCHAR(16) DEFAULT 'SHINING',
  `party_tier` VARCHAR(16) DEFAULT 'SHINING',
  `status` VARCHAR(16) NOT NULL DEFAULT 'NORMAL',
  `last_login_at` DATETIME DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_openid` (`openid`),
  UNIQUE KEY `uk_union_id` (`union_id`),
  CONSTRAINT `chk_star_points` CHECK (`star_points` >= 0),
  CONSTRAINT `chk_party_points` CHECK (`party_points` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `event` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `type` VARCHAR(8) NOT NULL,
  `title` VARCHAR(128) NOT NULL,
  `banner_url` VARCHAR(512) DEFAULT NULL,
  `description` TEXT,
  `rules` TEXT,
  `location` VARCHAR(256) DEFAULT NULL,
  `event_time` DATETIME DEFAULT NULL,
  `registration_deadline` DATETIME DEFAULT NULL,
  `max_participants` INT DEFAULT NULL,
  `current_participants` INT NOT NULL DEFAULT 0,
  `fee` DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  `prizes` TEXT,
  `status` VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
  `created_by` BIGINT DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at` DATETIME DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_type_status_time` (`type`, `status`, `event_time`),
  KEY `idx_status_time` (`status`, `event_time`),
  KEY `idx_created_by` (`created_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `registration` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `event_id` BIGINT NOT NULL,
  `match_type` VARCHAR(16) NOT NULL,
  `partner_id` BIGINT DEFAULT NULL,
  `status` VARCHAR(16) NOT NULL DEFAULT 'REGISTERED',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_event` (`user_id`, `event_id`),
  KEY `idx_event_id` (`event_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `point_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `event_id` BIGINT NOT NULL,
  `type` VARCHAR(8) NOT NULL,
  `points` INT NOT NULL,
  `reason` VARCHAR(256) DEFAULT NULL,
  `operator_id` BIGINT NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_created` (`user_id`, `created_at` DESC),
  KEY `idx_event_id` (`event_id`),
  KEY `idx_operator_id` (`operator_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `ranking` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `type` VARCHAR(8) NOT NULL,
  `tier` VARCHAR(16) NOT NULL,
  `rank` INT NOT NULL,
  `points` INT NOT NULL,
  `change` INT NOT NULL DEFAULT 0,
  `season` VARCHAR(32) NOT NULL,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_type_season` (`user_id`, `type`, `season`),
  KEY `idx_type_tier_points` (`type`, `tier`, `points` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `admin_user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `username` VARCHAR(64) NOT NULL,
  `password_hash` VARCHAR(256) NOT NULL,
  `role` VARCHAR(16) NOT NULL DEFAULT 'OPERATOR',
  `status` VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `banner` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `image_url` VARCHAR(512) NOT NULL,
  `link_url` VARCHAR(512) DEFAULT NULL,
  `sort_order` INT NOT NULL DEFAULT 0,
  `status` VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `ban_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `operator_id` BIGINT NOT NULL,
  `action` VARCHAR(8) NOT NULL,
  `reason` VARCHAR(512) DEFAULT NULL,
  `ban_until` DATETIME DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
