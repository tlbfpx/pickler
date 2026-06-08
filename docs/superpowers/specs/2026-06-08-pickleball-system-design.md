# Hey Pickler 匹克球系统设计文档

> 版本：1.0 | 日期：2026-06-08 | 状态：待审核

## 1. 概述

Hey Pickler 是一个面向全国匹克球爱好者的赛事活动管理平台，包含微信小程序（用户端）和 Web 后台管理系统（管理端）。

### 1.1 核心定位

- **Star 模式**：中高阶竞技，面向有竞技需求的匹克球玩家
- **Party 模式**：社交聚会，面向以社交娱乐为主的匹克球爱好者

### 1.2 设计约束

| 约束项 | 决策 |
|--------|------|
| 技术栈 | 微信原生小程序 + Spring Boot + Java + MySQL + Redis |
| 架构 | 单体分层架构 |
| 部署 | 已有基础设施 |
| 用户规模 | 全国级别（十万级用户） |
| 排名体系 | 分级排名制（Legend Star / Super Star / Shining Star） |
| 比分录入 | 仅赛后管理员手动录入 |
| 商城模块 | 二期再做 |
| 交互设计 | 以设计稿为准（heypickler-clfodcu4.manus.space） |

## 2. 系统架构

```
┌─────────────────────────────────────────────┐
│              微信小程序 (前端)                │
│  首页 │ 赛事 │ 活动 │ 个人中心              │
└──────────────────┬──────────────────────────┘
                   │ HTTPS
┌──────────────────▼──────────────────────────┐
│           Spring Boot 应用                   │
│                                             │
│  ┌─────────┐ ┌─────────┐ ┌──────────────┐  │
│  │ 用户模块 │ │ 赛事模块 │ │ 活动模块     │  │
│  └────┬────┘ └────┬────┘ └──────┬───────┘  │
│       │           │             │           │
│  ┌────▼────┐ ┌────▼────┐ ┌─────▼────────┐ │
│  │ 排名模块 │ │ 内容模块 │ │ 后台管理 API │ │
│  └────┬────┘ └────┬────┘ └──────┬───────┘  │
│       └───────┬───┴─────────────┘           │
│               │                             │
│  ┌────────────▼────────────┐                │
│  │     通用服务层           │                │
│  │ 认证/文件上传/通知/积分  │                │
│  └─────────────────────────┘                │
└──────────────────┬──────────────────────────┘
                   │
    ┌──────────────┼──────────────┐
    │              │              │
┌───▼───┐  ┌──────▼─────┐  ┌───▼───┐
│ MySQL  │  │   Redis     │  │ OSS   │
│ 主数据库│  │ 会话/缓存   │  │ 文件  │
└────────┘  └────────────┘  └───────┘
```

### 2.1 分层结构

- **Controller 层**：接收请求，参数校验，调用 Service
- **Service 层**：业务逻辑，事务管理
- **Repository 层**：数据访问，MyBatis-Plus
- **通用层**：微信登录、文件上传、积分计算、通知推送

### 2.2 端点区分

- 小程序端：`/api/app/*`
- 后台管理：`/api/admin/*`
- 独立鉴权拦截器区分两端

## 3. 数据模型

### 3.1 ER 关系

```
User ──< Registration >── Event (赛事/活动)
  │                          │
  │                     EventType: STAR/PARTY
  │                          │
  ├──< PointRecord >─────────┘
  │
  ├──< Ranking >──── Tier (Legend/Super/Shining)
  │
  └──< BanRecord >

Banner (首页轮播图)
```

### 3.2 主要数据表

#### user（用户表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键 |
| openid | VARCHAR(64) | 微信 openid |
| union_id | VARCHAR(64) | 微信 unionid |
| nickname | VARCHAR(64) | 昵称 |
| avatar_url | VARCHAR(512) | 头像 URL |
| phone | VARCHAR(20) | 手机号 |
| city | VARCHAR(64) | 城市 |
| star_points | INT DEFAULT 0 | Star 模式总积分（CHECK >= 0） |
| party_points | INT DEFAULT 0 | Party 模式总积分（CHECK >= 0） |
| star_tier | VARCHAR(16) | Star 段位（LEGEND/SUPER/SHINING） |
| party_tier | VARCHAR(16) | Party 段位（LEGEND/SUPER/SHINING） |
| status | VARCHAR(16) | 状态（NORMAL/BANNED） |
| last_login_at | DATETIME | 最后登录时间 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

索引：UNIQUE(openid), UNIQUE(union_id)

#### event（赛事/活动表）

赛事和活动使用同一张表，通过 type 字段区分。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键 |
| type | VARCHAR(8) | STAR（赛事）/ PARTY（活动） |
| title | VARCHAR(128) | 标题 |
| banner_url | VARCHAR(512) | 封面图 |
| description | TEXT | 详情描述 |
| rules | TEXT | 规则说明 |
| location | VARCHAR(256) | 地点 |
| event_time | DATETIME | 比赛时间 |
| registration_deadline | DATETIME | 报名截止时间 |
| max_participants | INT | 最大参与人数 |
| current_participants | INT DEFAULT 0 | 当前报名人数 |
| fee | DECIMAL(10,2) DEFAULT 0 | 报名费 |
| prizes | TEXT | 奖项说明 |
| status | VARCHAR(16) | DRAFT/OPEN/FULL/IN_PROGRESS/COMPLETED/CANCELLED |
| created_by | BIGINT | 创建人（管理员 ID） |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |
| deleted_at | DATETIME NULL | 软删除时间（NULL = 未删除） |

索引：(type, status, event_time), (status, event_time), (created_by)

#### registration（报名表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键 |
| user_id | BIGINT FK | 用户 ID |
| event_id | BIGINT FK | 赛事/活动 ID |
| match_type | VARCHAR(16) | SINGLES/DOUBLES/MIXED |
| partner_id | BIGINT FK NULL | 双打搭档 ID（DOUBLES/MIXED 时必填） |
| status | VARCHAR(16) | REGISTERED/CHECKED_IN/WITHDRAWN |
| created_at | DATETIME | 报名时间 |
| updated_at | DATETIME | 状态变更时间 |

索引：UNIQUE(user_id, event_id)

> 注：同一用户同一赛事只能报名一次（不论单打/双打）。

#### point_record（积分记录表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键 |
| user_id | BIGINT FK | 用户 ID |
| event_id | BIGINT FK | 关联赛事/活动 |
| type | VARCHAR(8) | STAR / PARTY |
| points | INT | 积分值（可为负） |
| reason | VARCHAR(256) | 积分原因 |
| operator_id | BIGINT | 录入人（管理员 ID） |
| created_at | DATETIME | 录入时间 |

索引：(user_id, created_at DESC), (event_id), (operator_id)

#### ranking（排名快照表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键 |
| user_id | BIGINT FK | 用户 ID |
| type | VARCHAR(8) | STAR / PARTY |
| tier | VARCHAR(16) | 段位 |
| rank | INT | 排名 |
| points | INT | 当前积分 |
| change | INT DEFAULT 0 | 排名变化（正=上升，负=下降） |
| season | VARCHAR(32) | 赛季标识 |
| updated_at | DATETIME | 更新时间 |

索引：UNIQUE(user_id, type, season), (type, tier, points DESC)

#### admin_user（管理员表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键 |
| username | VARCHAR(64) | 用户名 |
| password_hash | VARCHAR(256) | 密码哈希（bcrypt） |
| role | VARCHAR(16) | SUPER_ADMIN / ADMIN / OPERATOR |
| status | VARCHAR(16) | ACTIVE / DISABLED |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

索引：UNIQUE(username)

#### banner（轮播图表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键 |
| image_url | VARCHAR(512) | 图片地址 |
| link_url | VARCHAR(512) | 跳转链接 |
| sort_order | INT | 排序 |
| status | VARCHAR(16) | ENABLED/DISABLED |
| created_at | DATETIME | 创建时间 |

#### ban_record（封禁记录表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键 |
| user_id | BIGINT FK | 用户 ID |
| operator_id | BIGINT FK | 操作人 |
| action | VARCHAR(8) | BAN / UNBAN |
| reason | VARCHAR(512) | 原因 |
| ban_until | DATETIME NULL | 封禁截止时间（NULL = 永久封禁） |
| created_at | DATETIME | 操作时间 |

### 3.3 设计要点

- 赛事和活动共用 event 表，通过 type 区分，减少重复逻辑
- 积分双轨制：Star 积分和 Party 积分独立累计，各有独立段位
- 段位由系统根据积分阈值自动计算，阈值后台可配
- 排名快照每次积分录入后刷新，支持历史变化追踪
- 排名重新计算为异步操作：积分写入后发布事件，由后台任务异步刷新排名快照并清除缓存
- 赛程安排在 V1 中以富文本形式存储在 event.rules 中，V2 迁移为结构化 match 表

## 4. 功能模块设计

### 4.1 小程序端

#### 首页模块

- **轮播 Banner**：从后台配置加载，点击跳转对应链接
- **双模式入口**：Star（竞技）/ Party（社交），点击进入对应列表
- **热门赛事卡片**：展示最近 3-5 场状态为 OPEN 且报名截止时间未过的赛事/活动
- **排名预览**：当前赛季绝对 TOP 5 排名（不限段位）

#### 赛事模块（Star 模式）

- **赛事列表页**：双 Tab 切换（赛事 / 排名）
  - 赛事 Tab：卡片式列表（封面、标题、时间、地点、报名状态、报名人数）
  - 排名 Tab：分级 Tab（Legend Star / Super Star / Shining Star），排名列表（头像、昵称、城市、积分、排名变化）
- **赛事详情页**：
  - Banner + 基本信息（时间、地点、报名费、人数）
  - 规则说明
  - 赛程安排
  - 奖项说明
  - 报名按钮（未登录引导登录，已报名显示状态）
  - 报名时可选搭档（双打模式）

#### 活动模块（Party 模式）

- 与赛事模块结构相同，入口不同
- 活动更偏社交属性，报名费可能为 0

#### 个人中心

- **个人信息**：头像、昵称、城市、Star 积分、Party 积分、当前段位
- **我的赛事**：已报名/已参加的赛事列表
- **我的活动**：已报名/已参加的活动列表
- **积分明细**：积分变动记录（来源赛事/活动、积分变化、时间）
- **设置**：修改昵称、城市、手机号

### 4.2 登录流程

```
用户打开小程序
    │
    ▼
调用 wx.login() 获取 code
    │
    ▼
后端用 code 换取 openid + session_key
    │
    ▼
检查用户是否已注册 ──是──→ 生成 JWT token 返回
    │
    否
    ▼
引导用户授权手机号
    │
    ▼
创建用户记录，绑定手机号
    │
    ▼
生成 JWT token 返回
```

- Token 存储在小程序端 storage，每次请求携带
- Redis 存储 session_key 用于解密手机号

### 4.3 后台管理系统

**技术选型**：Vue 3 + Element Plus

#### 会员管理

- 用户列表：搜索（昵称/手机号）、筛选（段位/状态）、分页
- 用户详情：查看/编辑基本信息，查看积分历史，查看报名记录
- 封禁/解封：填写原因，记录操作日志（操作人、时间、原因）

#### 赛事/活动管理

- 列表页：按状态/类型/时间筛选
- 创建/编辑：基本信息、封面上传、规则编辑（富文本）、报名费设置
- 积分录入：赛事结束后录入每个参赛者的积分，支持批量导入
- 积分录入后自动触发排名重新计算

#### 报名取消规则

- 在 `registration_deadline` 前可取消报名，状态变为 WITHDRAWN
- 取消后 `current_participants` 原子递减
- `registration_deadline` 后不可自行取消，需联系管理员

#### 内容管理

- Banner 管理：上传图片、设置跳转链接、拖拽排序、启用/禁用

#### 管理员认证

- 独立于小程序用户体系
- 账号密码登录 + JWT 会话管理
- Redis 存储会话，支持强制下线

## 5. 积分与排名系统

### 5.1 积分规则

**Star 模式（竞技积分）：**

- 赛事结束后，管理员根据比赛成绩录入积分
- 积分示例：冠军 +100，亚军 +60，四强 +30，八强 +15，参赛 +5
- 具体积分规则由管理员在后台配置

**Party 模式（活动积分）：**

- 活动结束后，管理员录入参与积分
- 积分示例：参与 +10，组织者 +20
- 由管理员配置

### 5.2 段位划分

| 段位 | Star 积分要求 | Party 积分要求 |
|------|-------------|---------------|
| Legend Star | 1000+ | 500+ |
| Super Star | 500-999 | 200-499 |
| Shining Star | 0-499 | 0-199 |

阈值可在后台动态配置。

### 5.3 排名计算流程

```
管理员录入积分
    │
    ▼
写入 point_record 表（单事务）
    │
    ▼
累加用户总积分 (user.star_points / party_points)
    │
    ▼
发布积分变更事件
    │
    ▼ [异步] 后台任务消费事件
    │
    ▼
重新计算用户段位（star_tier / party_tier）
    │
    ▼
刷新排名快照（按段位分组，按积分降序）
    │
    ▼
计算排名变化（与上次快照对比）
    │
    ▼
主动清除 Redis 排名缓存（DELETE key）
```

### 5.4 缓存策略

- 排名数据缓存到 Redis（Cache-Aside 模式）
- 积分录入后主动清除缓存（DELETE），TTL 5 分钟作为兜底
- 首页 TOP 5 直接从 Redis 读取
- 排名详情页从 MySQL 分页查询，Redis 做缓存加速

### 5.5 积分历史追溯

- 每次积分变动都有 point_record 记录
- 用户可在个人中心查看积分明细

## 6. API 设计

### 6.0 API 通用约定

**请求格式：**
- 分页：`page`（从 1 开始）、`size`（默认 20，最大 100）
- 排序：`sort_by`、`order`（asc/desc）

**响应格式：**
```json
{
  "code": 0,
  "message": "success",
  "data": { ... }
}
```

**分页响应：**
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "total": 100,
    "page": 1,
    "size": 20,
    "list": [ ... ]
  }
}
```

**错误码：**
- 0：成功
- 401：未认证
- 403：无权限
- 404：资源不存在
- 429：请求过于频繁
- 1001：参数校验失败
- 1002：用户已封禁
- 1003：报名已满
- 1004：重复报名
- 1005：报名已截止

### 6.1 小程序端 API

| 路由 | 方法 | 说明 |
|------|------|------|
| `/api/app/auth/login` | POST | 微信登录 |
| `/api/app/auth/phone` | POST | 绑定手机号 |
| `/api/app/auth/refresh` | POST | 刷新 token |
| `/api/app/banners` | GET | 获取首页 Banner |
| `/api/app/events` | GET | 赛事/活动列表（type 参数区分） |
| `/api/app/events/{id}` | GET | 赛事/活动详情 |
| `/api/app/events/{id}/register` | POST | 报名 |
| `/api/app/events/{id}/cancel` | POST | 取消报名 |
| `/api/app/rankings` | GET | 排名列表（type + tier 参数） |
| `/api/app/user/profile` | GET | 个人信息 |
| `/api/app/user/profile` | PUT | 更新个人信息 |
| `/api/app/user/events` | GET | 我参与的赛事/活动 |
| `/api/app/user/points` | GET | 积分历史 |

### 6.2 后台管理 API

| 路由 | 方法 | 说明 |
|------|------|------|
| `/api/admin/auth/login` | POST | 管理员登录 |
| `/api/admin/users` | GET | 用户列表 |
| `/api/admin/users/{id}` | GET/PUT | 用户详情/编辑 |
| `/api/admin/users/{id}/ban` | POST | 封禁 |
| `/api/admin/users/{id}/unban` | POST | 解封 |
| `/api/admin/events` | GET/POST | 赛事列表/创建 |
| `/api/admin/events/{id}` | GET/PUT/DELETE | 赛事详情/编辑/删除 |
| `/api/admin/events/{id}/points` | POST | 积分录入 |
| `/api/admin/banners` | GET/POST | Banner 列表/创建 |
| `/api/admin/banners/{id}` | PUT/DELETE | Banner 编辑/删除 |
| `/api/admin/admin-users` | GET/POST | 管理员列表/创建（SUPER_ADMIN） |
| `/api/admin/admin-users/{id}` | GET/PUT | 管理员详情/编辑（SUPER_ADMIN） |
| `/api/admin/admin-users/{id}/reset-password` | POST | 重置密码（SUPER_ADMIN） |

### 6.3 鉴权方案

- **小程序端**：JWT token，通过微信登录获取，有效期 7 天；小程序每次启动静默调用 `wx.login()` 刷新 token
- **后台管理**：JWT token + Redis 会话管理，支持强制下线
- **角色权限**：SUPER_ADMIN（全部权限）、ADMIN（除管理员管理外的全部权限）、OPERATOR（赛事/活动/内容管理）
- **拦截器链**：AuthFilter → RoleFilter → RateLimitFilter

## 7. 技术选型

| 组件 | 选型 | 理由 |
|------|------|------|
| 后端框架 | Spring Boot 3.x | Java 生态主流 |
| ORM | MyBatis-Plus | 国内主流，灵活性好 |
| 缓存 | Redis + Spring Cache | 排名/会话缓存 |
| 认证 | JWT + Spring Security | 无状态，适合小程序 |
| 文件存储 | OSS SDK | 阿里云/腾讯云对象存储 |
| 后台前端 | Vue 3 + Element Plus | 管理端标配 |
| 小程序 | 微信原生开发 | 微信生态集成最好 |
| 接口文档 | Swagger/Knife4j | 自动生成 |
| 构建工具 | Maven | Java 标准 |

## 8. 非功能需求

### 8.1 性能

- API 响应时间 < 200ms（P95）
- 排名查询走 Redis 缓存，目标 < 50ms
- 支持 1000 QPS 峰值

### 8.2 安全

- 所有 API HTTPS
- SQL 注入防护（MyBatis-Plus 参数化查询）
- XSS 防护（输入过滤 + 输出转义）
- 敏感信息加密存储（手机号 AES 加密）
- 接口限流（Redis + 令牌桶）
- 后台管理 CORS 白名单（仅限管理域名）
- 文件上传限制（最大 5MB，仅 JPG/PNG，OSS 直传）
- 请求体大小限制（1MB）

### 8.3 可观测性

- 统一日志格式（JSON）
- 关键操作审计日志（封禁、积分录入、管理员登录）
- 健康检查端点

### 8.4 基础设施最低配置

- JVM：`-Xmx1g -Xms1g`
- HikariCP：`maximumPoolSize=20`
- Redis 连接池：`maxActive=50`
- MySQL：`innodb_buffer_pool_size=512M`

## 9. 二期规划

以下功能在二期实现：

- **品牌商城**：商品展示、购物车、订单管理
- **实时比分**：比赛进行中实时更新
- **消息推送**：赛事提醒、报名通知
- **数据分析**：用户行为统计、赛事热度分析
