-- Loop-v19 Dashboard Phase 2 — 埋点基建
-- V21: login_log + access_log 2 张表（append-only，无 deleted_at）
-- 索引设计：Phase 3 同期群/漏斗 SQL + Phase 4 异常告警的基础

-- login_log: 登录行为记录（用户 + 管理员）
-- user_id 与 admin_id 互斥：APP 登录用 user_id，ADMIN 登录用 admin_id
-- login_result 枚举: SUCCESS | FAIL_PWD | FAIL_BANNED | FAIL_RATE_LIMIT | FAIL_INVALID_CODE | FAIL_OTHER
CREATE TABLE login_log (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id       BIGINT UNSIGNED NULL COMMENT 'APP 登录用户 id，ADMIN 登录为 NULL',
  admin_id      BIGINT UNSIGNED NULL COMMENT 'ADMIN 登录管理员 id，APP 登录为 NULL',
  channel       VARCHAR(16)     NOT NULL COMMENT 'APP | ADMIN',
  login_result  VARCHAR(32)     NOT NULL COMMENT 'SUCCESS | FAIL_PWD | FAIL_BANNED | FAIL_RATE_LIMIT | FAIL_INVALID_CODE | FAIL_OTHER',
  error_code    VARCHAR(64)     NULL COMMENT '失败时的 BizException errorCode；SUCCESS 时 NULL',
  ip            VARCHAR(64)     NULL COMMENT 'X-Forwarded-For 第一跳；fallback request.getRemoteAddr()',
  device_id     VARCHAR(64)     NULL COMMENT '小程序持久化 did；ADMIN 登录 NULL',
  user_agent    VARCHAR(256)    NULL COMMENT '请求 UA，截断 256 字符',
  created_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  INDEX idx_login_user_time (user_id, created_at),
  INDEX idx_login_admin_time (admin_id, created_at),
  INDEX idx_login_result_time (login_result, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='登录行为日志（Phase 2 埋点）';

-- access_log: 全量 /api/** 访问记录（请求路径 + 延迟 + 状态码 + 用户）
-- 写入量：每请求一行；10 万 PV/天 ≈ 1000 万行/月 → Phase 3 前评估分表
-- user_id 与 admin_id 互斥：app 路径走 user_id，admin 路径走 admin_id，匿名请求都为 NULL
-- error_msg 在 Phase 2 R3 复用：写 track/event 时的 event name（最小 schema 原则）
CREATE TABLE access_log (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  path          VARCHAR(256)    NOT NULL COMMENT '请求 URI（不带 query string）',
  method        VARCHAR(8)      NOT NULL COMMENT 'HTTP method',
  status_code   INT             NOT NULL COMMENT 'HTTP 响应状态码',
  latency_ms    INT             NOT NULL COMMENT '请求处理耗时（ms）',
  user_id       BIGINT UNSIGNED NULL COMMENT 'APP 用户 id，admin 路径为 NULL',
  admin_id      BIGINT UNSIGNED NULL COMMENT '管理员 id，app 路径为 NULL',
  ip            VARCHAR(64)     NULL,
  user_agent    VARCHAR(256)    NULL,
  error_msg     VARCHAR(256)    NULL COMMENT 'track/event 时复用：event name；错误请求时：异常摘要',
  created_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  INDEX idx_access_user_time (user_id, created_at),
  INDEX idx_access_path_time (path(64), created_at),
  INDEX idx_access_status_time (status_code, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='API 访问日志（Phase 2 埋点）';