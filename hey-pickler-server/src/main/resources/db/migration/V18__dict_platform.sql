-- 字典平台：通用枚举字典（方案③ 基础设施）
-- sys_dict      字典目录（一类枚举一行）
-- sys_dict_item 字典项（每个枚举值一行；item_key 系统绑定不可改，label/color/sort/status 可配）
-- 软删：deleted_at NULL=未删（@TableLogic）

CREATE TABLE sys_dict (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  dict_code    VARCHAR(64)     NOT NULL,
  dict_name    VARCHAR(64)     NOT NULL,
  description  VARCHAR(255)    NULL,
  status       VARCHAR(16)     NOT NULL DEFAULT 'ENABLED',
  created_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted_at   DATETIME        NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_dict_code (dict_code),
  KEY idx_dict_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sys_dict_item (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  dict_code    VARCHAR(64)     NOT NULL,
  item_key     VARCHAR(64)     NOT NULL,
  item_label   VARCHAR(64)     NOT NULL,
  item_color   VARCHAR(16)     NULL,
  sort         INT             NOT NULL DEFAULT 0,
  status       VARCHAR(16)     NOT NULL DEFAULT 'ENABLED',
  extra_json   JSON            NULL,
  created_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted_at   DATETIME        NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_dict_item (dict_code, item_key),
  KEY idx_dict_item_code (dict_code, sort)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- seed：9 个枚举字典 + track_term（STAR=积分排名 / PARTY=匹克豆）
-- item_key 与后端 enum 值严格对齐，永不可改；label/color 运营方可调

INSERT INTO sys_dict (dict_code, dict_name, description) VALUES
  ('event_type',          '赛事类型', 'STAR 竞技 / PARTY 社交'),
  ('event_format',        '赛事形式', '单打 / 双打 / 混打'),
  ('event_status',        '赛事状态', '赛事生命周期状态（仅展示名/色可配，状态机值不可改）'),
  ('user_status',         '用户状态', '正常 / 已封禁'),
  ('registration_status', '报名状态', '已报名 / 已签到 / 已退出'),
  ('team_status',         '队伍状态', '待确认 / 已确认'),
  ('match_status',        '比赛状态', '待开打 / 进行中 / 已结束'),
  ('point_source',        '积分来源', '积分明细展示名'),
  ('notification_type',   '通知类型', '通知分类展示名'),
  ('track_term',          '积分体系命名', 'STAR=积分排名 / PARTY=匹克豆；extra_json 含 unit/pointsName/tierName/rankingName');

INSERT INTO sys_dict_item (dict_code, item_key, item_label, item_color, sort) VALUES
  ('event_type','STAR','竞技赛事','#F59E0B',0),
  ('event_type','PARTY','社交活动','#8B5CF6',1),
  ('event_format','SINGLES','单打','#3B82F6',0),
  ('event_format','DOUBLES','双打','#10B981',1),
  ('event_format','MIXED','混打','#EC4899',2),
  ('event_status','DRAFT','草稿','#909399',0),
  ('event_status','OPEN','报名中','#10B981',1),
  ('event_status','FULL','已满员','#F59E0B',2),
  ('event_status','IN_PROGRESS','进行中','#3B82F6',3),
  ('event_status','COMPLETED','已结束','#8B5CF6',4),
  ('event_status','CANCELLED','已取消','#EF4444',5),
  ('user_status','NORMAL','正常','#10B981',0),
  ('user_status','BANNED','已封禁','#EF4444',1),
  ('registration_status','REGISTERED','已报名','#3B82F6',0),
  ('registration_status','CHECKED_IN','已签到','#10B981',1),
  ('registration_status','WITHDRAWN','已退出','#9CA3AF',2),
  ('team_status','PENDING','待确认','#F59E0B',0),
  ('team_status','CONFIRMED','已确认','#10B981',1),
  ('match_status','SCHEDULED','待开打','#909399',0),
  ('match_status','IN_PROGRESS','进行中','#3B82F6',1),
  ('match_status','COMPLETED','已结束','#10B981',2),
  ('point_source','REGISTRATION','报名','#3B82F6',0),
  ('point_source','CHECK_IN','签到','#10B981',1),
  ('point_source','PLACEMENT','名次发分','#8B5CF6',2),
  ('point_source','MANUAL','管理员手动','#F59E0B',3),
  ('point_source','REDEEM','商城兑换','#06B6D4',4),
  ('point_source','ADJUST','系统纠错','#EF4444',5),
  ('notification_type','EVENT_IN_PROGRESS','赛事开始','#3B82F6',0),
  ('notification_type','EVENT_COMPLETED','赛事结束','#8B5CF6',1),
  ('notification_type','TEAM_INVITED','组队邀请','#10B981',2),
  ('notification_type','BANNER_PUBLISHED','Banner 发布','#06B6D4',3),
  ('notification_type','SYSTEM','系统通知','#EF4444',4);

-- track_term：extra_json 携带单位与各展示名（阶段 D 前端拉取后替换硬编码 terms）
INSERT INTO sys_dict_item (dict_code, item_key, item_label, sort, extra_json) VALUES
  ('track_term','STAR','积分排名',0,'{"unit":"积分","pointsName":"积分","tierName":"段位","rankingName":"积分排名"}'),
  ('track_term','PARTY','匹克豆',1,'{"unit":"匹克豆","pointsName":"匹克豆","tierName":"球友等级","rankingName":"匹克豆排名"}');
