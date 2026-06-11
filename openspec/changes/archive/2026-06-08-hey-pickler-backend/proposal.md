## Why

Hey Pickler 需要一个完整的后端 API 服务，支撑微信小程序（用户端）和 Web 后台管理系统（管理端）。当前只有设计文档，没有任何可运行的代码。需要从零构建 Spring Boot 后端，实现用户认证、赛事活动管理、积分排名、内容管理等全部 V1 功能，使小程序和后台管理系统能够上线运行。

## What Changes

- 新建 Spring Boot 3.x 项目（Maven、Java 17），包含完整的项目脚手架和配置
- 实现微信小程序登录流程（code 换取 openid、手机号绑定、JWT 颁发）
- 实现管理员账号密码登录 + JWT 双轨鉴权体系（小程序端 / 管理端独立拦截器）
- 实现 User CRUD，含段位自动计算、软删除、封禁/解封
- 实现 Event CRUD（赛事/活动共用表，type 区分），含报名/取消/状态流转/软删除
- 实现积分录入（PointRecord），录入后异步触发排名重算 + 段位更新
- 实现排名快照（Ranking），分 Star/Party 双轨、分 Legend/Super/Shining 三级
- 实现 Banner CRUD（轮播图管理）
- 实现管理员管理（AdminUser CRUD，SUPER_ADMIN 专属）
- 实现通用基础设施：统一响应格式、全局异常处理、Redis 缓存、接口限流、CORS、Swagger 文档
- 数据库建表脚本（Flyway migration）

## Capabilities

### New Capabilities

- `auth`: 微信小程序登录（wx.login code 换 token、手机号绑定、token 刷新）+ 管理员账号密码登录，JWT 双轨鉴权拦截器
- `user`: 小程序用户管理（个人资料查看/编辑、积分查询、参赛记录查询）+ 后台用户管理（列表搜索、详情编辑、封禁/解封）
- `event`: 赛事/活动管理（小程序端列表/详情/报名/取消 + 管理端 CRUD/软删除/状态流转），报名截止前可取消、原子计数
- `ranking`: 积分与排名系统（管理员录入积分、异步排名重算、段位自动计算、Redis 缓存 + 主动失效、排名变化追踪）
- `content`: 内容管理（Banner 增删改查、排序、启用/禁用）
- `admin`: 管理员管理（AdminUser CRUD、角色权限 SUPER_ADMIN/ADMIN/OPERATOR、密码重置），仅 SUPER_ADMIN 可操作
- `infrastructure`: 项目基础设施（统一响应 Result/PageResult、全局异常处理、错误码枚举、Redis 配置、MyBatis-Plus 配置、CORS、接口限流、Swagger、异步线程池、AES 加密工具、JWT 工具）

### Modified Capabilities

（无已有能力，全部为新建）

## Impact

- **新增代码**: 完整 Spring Boot 项目 `hey-pickler-server/`，约 60+ Java 文件
- **API 端点**: 小程序端 13 个 + 管理端 14 个，共 27 个 HTTP 端点
- **数据库**: 8 张表（user, event, registration, point_record, ranking, admin_user, banner, ban_record）
- **依赖**: Spring Boot 3.x, MyBatis-Plus, Redis, jjwt, Swagger/Knife4j, MySQL Connector, Flyway
- **外部服务**: 微信开放平台（code2Session）、OSS（文件上传）
