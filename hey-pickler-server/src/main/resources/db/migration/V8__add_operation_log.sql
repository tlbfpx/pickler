-- 后台操作审计日志：记录所有 admin POST/PUT/DELETE 请求
--
-- 字段说明：
-- - operator_id:   操作人 admin_id；登录端点（AdminAuthFilter 跳过）记 NULL
-- - operator_role: 操作人角色；登录失败/未认证记 ANONYMOUS
-- - method:        HTTP 方法（POST/PUT/DELETE），GET 不入此表
-- - module/action: 由 URL 路径分类器推断；RAW 表示未识别
-- - target_*:      目标对象类型与 ID（从 path 提取）
-- - params:        请求参数 JSON（已脱敏 + 截断到 2000 字符）
-- - status:        1=SUCCESS, 0=FAIL（含参数错、权限拒、异常）
-- - error_*:       仅失败时填，error_code 取 BizException.code 或 500
--
-- 索引：4 个，对应 UI 查询模式（操作人 + 模块 + 时间范围 + 状态）
--
-- append-only: 不设 deleted_at，不允许物理删除审计记录

CREATE TABLE operation_log (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  operator_id   BIGINT UNSIGNED NULL,
  operator_role VARCHAR(32)     NOT NULL DEFAULT 'ANONYMOUS',
  method        VARCHAR(8)      NOT NULL,
  module        VARCHAR(32)     NOT NULL,
  action        VARCHAR(64)     NOT NULL,
  target_type   VARCHAR(32)     NULL,
  target_id     VARCHAR(64)     NULL,
  path          VARCHAR(255)    NOT NULL,
  params        TEXT            NULL,
  status        TINYINT         NOT NULL,
  error_code    INT             NULL,
  error_msg     VARCHAR(512)    NULL,
  ip            VARCHAR(64)     NULL,
  user_agent    VARCHAR(512)    NULL,
  latency_ms    INT             NULL,
  created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_operator_time (operator_id, created_at),
  KEY idx_module_time (module, created_at),
  KEY idx_created_at (created_at),
  KEY idx_status_time (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
