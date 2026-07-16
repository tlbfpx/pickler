-- 品牌配置（方案③ 专用表，单行 id=1）
-- app 名称 / slogan / logo URL / 主题色：三端配置驱动，运营在 admin 改即生效。
-- 软删：deleted_at NULL=未删（@TableLogic，对齐 sys_dict / tier_config 范式）。

CREATE TABLE brand_config (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  app_name      VARCHAR(64)     NOT NULL,
  slogan        VARCHAR(128)    NULL,
  logo_url      VARCHAR(512)    NULL,
  primary_color VARCHAR(16)     NOT NULL DEFAULT '#4CAF50',
  created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted_at    DATETIME        NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- seed 单行（id=1）：默认品牌；logo_url NULL → 前端回退内置资源（favicon / images/logo.png）
INSERT INTO brand_config (id, app_name, slogan, logo_url, primary_color) VALUES
  (1, 'Hey Pickler', '匹克球赛事活动管理平台', NULL, '#4CAF50');
