-- 站内通知（in-app notifications）—— MVP.
--
-- 设计目标：操作员在管理后台看到赛事状态变化 / 队伍邀请等重要事件；
-- 后续小程序端可复用同一张表 + /api/app/notifications 接口。
--
-- 字段说明：
-- - user_id:        接收方 user_id。先做管理员端（事件 createdBy / 队伍搭档），后续补小程序用户。
-- - type:           枚举字符串。常见的: EVENT_IN_PROGRESS / EVENT_COMPLETED /
--                   TEAM_INVITED / BANNER_PUBLISHED / SYSTEM；保持 VARCHAR(32) 以便后续扩展。
-- - title/content:  卡片展示的标题与正文。title 非空校验在应用层。
-- - link_url:       可选「查看详情」跳转目标，使用站内相对路径（如 /events/123?tab=match）。
-- - read_flag:      TINYINT(0/1) —— 0=未读（badge 显示），1=已读。命名 read_flag 避免与 MySQL 关键字 READ 歧义。
--
-- 索引：
-- - idx_user_unread (user_id, read_flag, id) — 头部下拉仅读未读最新 N 条；按 id desc 排
-- - idx_user_time (user_id, id)             — 「全部通知」分页

CREATE TABLE notification (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id      BIGINT UNSIGNED NOT NULL,
  type         VARCHAR(32)     NOT NULL,
  title        VARCHAR(128)    NOT NULL,
  content      VARCHAR(1024)   NULL,
  link_url     VARCHAR(255)    NULL,
  read_flag    TINYINT         NOT NULL DEFAULT 0,
  created_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user_unread (user_id, read_flag, id),
  KEY idx_user_time   (user_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
