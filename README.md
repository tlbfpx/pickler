# Hey Pickler - 匹克球赛事管理平台

## 项目概述

Hey Pickler 是一个匹克球（Pickleball）赛事管理平台，包含三个子系统：

| 子系统 | 技术栈 | 目录 |
|--------|--------|------|
| 后端服务 | Spring Boot 3.2 + MyBatis-Plus + MySQL + Redis | `hey-pickler-server/` |
| 管理后台 | Vue 3 + Element Plus + Vite + Playwright | `hey-pickler-admin/` |
| 微信小程序 | 微信原生框架 (WXML/WXSS/JS) | `hey-pickler-wxapp/` |

---

## 系统架构

```
┌──────────────────┐   ┌──────────────────┐
│  微信小程序用户端  │   │  Vue3 管理后台    │
│  hey-pickler-wxapp│   │  hey-pickler-admin│
└────────┬─────────┘   └────────┬─────────┘
         │                      │
         │  /api/app/**         │  /api/admin/**
         ▼                      ▼
┌─────────────────────────────────────────┐
│          Spring Boot Backend            │
│  ┌─────────┐ ┌──────────┐ ┌──────────┐ │
│  │ Security │ │ RateLimit│ │  CORS    │ │
│  │  Filter  │ │  Filter  │ │  Filter  │ │
│  └─────────┘ └──────────┘ └──────────┘ │
│  ┌─────────┐ ┌──────────┐ ┌──────────┐ │
│  │App Auth │ │Admin Auth│ │   XSS    │ │
│  │  Filter │ │  Filter  │ │  Filter  │ │
│  └─────────┘ └──────────┘ └──────────┘ │
│  ┌────────────────────────────────────┐ │
│  │         Controller Layer           │ │
│  │  App*Controller  Admin*Controller  │ │
│  └────────────────────────────────────┘ │
│  ┌────────────────────────────────────┐ │
│  │          Service Layer             │ │
│  └────────────────────────────────────┘ │
│  ┌────────────────────────────────────┐ │
│  │     MyBatis-Plus / Flyway          │ │
│  └────────────────────────────────────┘ │
└──────────┬──────────────┬──────────────┘
           │              │
    ┌──────▼──────┐ ┌─────▼──────┐
    │   MySQL 8   │ │   Redis    │
    │ hey_pickler │ │  (6379)    │
    └─────────────┘ └────────────┘
```

---

## 环境要求

### 基础环境

| 依赖 | 版本要求 | 说明 |
|------|----------|------|
| Java | 17+ | 后端运行环境 |
| Maven | 3.8+ | 后端构建工具 |
| Node.js | 18+ | 管理后台构建 |
| MySQL | 8.0+ | 数据库 |
| Redis | 6.0+ | 缓存 / 限流 / 会话存储 |
| 微信开发者工具 | 最新稳定版 | 小程序开发与调试 |

### 开发工具（可选）

| 工具 | 用途 |
|------|------|
| IntelliJ IDEA | 后端开发 |
| VS Code | 前端开发 |
| Playwright | 管理后台 E2E 测试 |

---

## 一、后端服务部署 (hey-pickler-server)

### 1.1 数据库初始化

```bash
# 登录 MySQL
mysql -u root -p

# 创建数据库
CREATE DATABASE hey_pickler DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

# 退出后，确认 Flyway 会在应用启动时自动执行迁移
# 迁移脚本位于: src/main/resources/db/migration/
#   V1__init_schema.sql  - 建表 (8 张表)
#   V2__init_data.sql    - 默认管理员账号
#   V3__nullable_event_id.sql - 积分记录 event_id 允许 NULL
```

### 1.2 启动 Redis

```bash
# macOS
brew install redis
brew services start redis

# Linux (Ubuntu/Debian)
sudo apt install redis-server
sudo systemctl start redis-server

# Docker
docker run -d --name redis -p 6379:6379 redis:7-alpine
```

### 1.3 配置环境变量

后端通过环境变量注入敏感配置，开发环境有默认值可直接启动：

```bash
# 必须配置（生产环境）
export JWT_SECRET="your-strong-jwt-secret-at-least-32-chars"
export AES_KEY="your-16-char-aes-key"
export DB_URL="jdbc:mysql://localhost:3306/hey_pickler?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai"
export DB_USERNAME="root"
export DB_PASSWORD="your-db-password"
export REDIS_HOST="localhost"
export REDIS_PORT="6379"
export REDIS_PASSWORD=""
export WX_APPID="your-wechat-appid"
export WX_SECRET="your-wechat-secret"

# 可选配置（开发环境有默认值）
export CORS_ADMIN_ORIGINS="http://localhost:5173,http://localhost:3000"
export CORS_APP_ORIGINS=""
export WX_DEV_MODE="false"             # 开发模式：跳过微信 API 调用
export JWT_APP_EXPIRATION="604800000"   # 小程序 token 有效期 (ms), 默认 7 天
export JWT_ADMIN_EXPIRATION="86400000"  # 管理后台 token 有效期 (ms), 默认 1 天
```

### 1.4 构建与启动

```bash
cd hey-pickler-server

# 编译
mvn clean package -DskipTests

# 运行 (开发环境, profile=dev)
java -jar target/hey-pickler-server-1.0.0.jar

# 运行 (生产环境, profile=prod)
java -jar target/hey-pickler-server-1.0.0.jar \
  --spring.profiles.active=prod \
  -DJWT_SECRET=xxx \
  -DDB_URL=xxx \
  ...
```

后端启动后监听 `http://localhost:8080`

### 1.5 验证后端启动

```bash
# 健康检查 - 管理后台登录接口
# 替换 <your-initial-admin-password> 为 .env 里 INITIAL_ADMIN_PASSWORD 的值
curl -X POST http://localhost:8080/api/admin/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"<your-initial-admin-password>"}'

# 返回 {"code":0,"data":{"token":"..."}} 表示成功
```

### 1.6 运行测试

```bash
cd hey-pickler-server

# 运行全部单元测试
mvn test

# 运行指定测试类
mvn test -Dtest=AuthServiceTest
mvn test -Dtest=EventServiceTest
mvn test -Dtest=AdminUserServiceTest

# 跳过集成测试（需要数据库和 Redis）
mvn test -Dtest="!*IntegrationTest"
```

### 1.7 API 文档

启动后访问 Swagger 文档：

- Knife4j UI: `http://localhost:8080/doc.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

---

## 二、管理后台部署 (hey-pickler-admin)

### 2.1 安装依赖

```bash
cd hey-pickler-admin
npm install
```

### 2.2 开发模式

管理后台通过 Vite 开发服务器运行，代理 `/api` 请求到后端：

```bash
npm run dev
# 启动在 http://localhost:5173
# Vite 自动代理 /api -> http://localhost:8080
```

Vite 代理配置 (`vite.config.ts`)：

```typescript
server: {
  port: 5173,
  proxy: {
    '/api': {
      target: 'http://localhost:8080',
      changeOrigin: true
    }
  }
}
```

### 2.3 生产构建

```bash
npm run build
# 输出到 dist/ 目录
```

生产部署时需要配置 Nginx 反向代理：

```nginx
server {
    listen 80;
    server_name admin.heypickler.com;

    root /var/www/hey-pickler-admin/dist;
    index index.html;

    # SPA 路由回退
    location / {
        try_files $uri $uri/ /index.html;
    }

    # API 代理
    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
}
```

### 2.4 E2E 测试

```bash
# 安装 Playwright 浏览器
npx playwright install

# 运行测试 (headless)
npm run test:e2e

# 运行测试 (有界面)
npm run test:e2e:headed

# Playwright UI 模式
npm run test:e2e:ui
```

### 2.5 默认管理员账号

初始管理员账号通过环境变量 `INITIAL_ADMIN_USERNAME` / `INITIAL_ADMIN_PASSWORD`
在首次启动时由 `AdminBootstrapper` 创建。dev 环境默认值参考 `.env.example`，
生产环境必须自行设置强密码。详见 `docs/CREDENTIALS.md`。

| 字段 | dev 默认 | 生产 |
|------|---------|------|
| 用户名 | `admin`（可改 `INITIAL_ADMIN_USERNAME`）| 必须显式设置 |
| 密码 | 由 `INITIAL_ADMIN_PASSWORD` 决定 | 必须显式设置（建议 ≥ 12 字符）|
| 角色 | `SUPER_ADMIN` | `SUPER_ADMIN` |

> 首次部署后请立即在「管理员管理」页面修改密码

---

## 三、微信小程序部署 (hey-pickler-wxapp)

### 3.1 开发环境配置

#### 3.1.1 项目导入

1. 打开微信开发者工具
2. 选择「导入项目」
3. 目录选择 `hey-pickler-wxapp/`
4. AppID 填入 `touristappid`（游客模式，仅模拟器可用）

> 游客模式下仅能在模拟器中运行，真机调试需使用真实 AppID

#### 3.1.2 后端地址配置

小程序的 API 地址在 `app.js` 中配置：

```javascript
// app.js
App({
  globalData: {
    baseUrl: 'http://localhost:8080/api/app'  // 开发环境
  }
})
```

#### 3.1.3 开发模式登录

开发环境默认启用 `dev-mode`，跳过微信 `jscode2session` API 调用：

```yaml
# application-dev.yml
hey-pickler:
  wechat:
    dev-mode: true   # 开发模式，openid 使用 "dev_<code>" 前缀
```

模拟器中点击登录即可自动创建开发账号。

### 3.2 生产环境配置

#### 3.2.1 AppID 申请

1. 登录 [微信公众平台](https://mp.weixin.qq.com/)
2. 注册小程序账号
3. 获取 AppID 和 AppSecret
4. 配置后端环境变量：
   ```bash
   export WX_APPID="wx1234567890abcdef"
   export WX_SECRET="your-wechat-app-secret"
   export WX_DEV_MODE="false"
   ```

#### 3.2.2 服务器域名配置

在微信公众平台「开发管理 → 开发设置 → 服务器域名」中配置：

| 类型 | 域名 |
|------|------|
| request 合法域名 | `https://api.heypickler.com` |

> 小程序强制 HTTPS，`urlCheck: false` 仅在开发者工具模拟器生效

#### 3.2.3 修改 API 地址

```javascript
// app.js
App({
  globalData: {
    baseUrl: 'https://api.heypickler.com/api/app'
  }
})
```

同时更新 `project.config.json` 中的 AppID：

```json
{
  "appid": "wx1234567890abcdef"
}
```

#### 3.2.4 上传与发布

1. 在微信开发者工具中点击「上传」
2. 填写版本号和备注
3. 登录微信公众平台「版本管理」
4. 提交审核 → 审核通过后发布

---

## 四、生产部署清单

### 4.1 服务器准备

| 项目 | 要求 |
|------|------|
| 操作系统 | Ubuntu 22.04+ / CentOS 8+ |
| CPU | 2 核+ |
| 内存 | 4GB+ |
| 磁盘 | 40GB+ SSD |
| JDK | 17+ |
| MySQL | 8.0+ |
| Redis | 6.0+ |
| Nginx | 1.20+ |

### 4.2 后端部署

```bash
# 1. 构建
cd hey-pickler-server
mvn clean package -DskipTests

# 2. 上传 jar 包到服务器
scp target/hey-pickler-server-1.0.0.jar user@server:/opt/hey-pickler/

# 3. 创建 systemd 服务
sudo cat > /etc/systemd/system/hey-pickler.service << 'EOF'
[Unit]
Description=Hey Pickler Server
After=network.target mysql.service redis.service

[Service]
Type=simple
User=heypickler
WorkingDirectory=/opt/hey-pickler
ExecStart=/usr/bin/java -jar hey-pickler-server-1.0.0.jar \
  --spring.profiles.active=prod \
  -DJWT_SECRET=CHANGE_ME \
  -DAES_KEY=CHANGE_ME_16CHAR \
  -DDB_URL=jdbc:mysql://localhost:3306/hey_pickler?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai \
  -DDB_USERNAME=heypickler \
  -DDB_PASSWORD=CHANGE_ME \
  -DREDIS_HOST=localhost \
  -DREDIS_PORT=6379 \
  -DREDIS_PASSWORD=CHANGE_ME \
  -DWX_APPID=CHANGE_ME \
  -DWX_SECRET=CHANGE_ME \
  -DCORS_ADMIN_ORIGINS=https://admin.heypickler.com
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF

# 4. 启动服务
sudo systemctl daemon-reload
sudo systemctl enable hey-pickler
sudo systemctl start hey-pickler

# 5. 查看日志
sudo journalctl -u hey-pickler -f
```

### 4.3 管理后台部署

```bash
# 1. 构建
cd hey-pickler-admin
npm ci
npm run build

# 2. 上传 dist 目录到服务器
scp -r dist/* user@server:/var/www/hey-pickler-admin/dist/

# 3. Nginx 配置 (见 2.3 节)
```

### 4.4 Nginx 完整配置

```nginx
# 管理后台
server {
    listen 80;
    server_name admin.heypickler.com;
    return 301 https://$server_name$request_uri;
}

server {
    listen 443 ssl http2;
    server_name admin.heypickler.com;

    ssl_certificate     /etc/ssl/heypickler.pem;
    ssl_certificate_key /etc/ssl/heypickler.key;

    root /var/www/hey-pickler-admin/dist;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}

# 小程序 API
server {
    listen 443 ssl http2;
    server_name api.heypickler.com;

    ssl_certificate     /etc/ssl/heypickler.pem;
    ssl_certificate_key /etc/ssl/heypickler.key;

    location /api/app/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
}
```

### 4.5 数据库安全配置

```sql
-- 创建专用数据库用户
CREATE USER 'heypickler'@'localhost' IDENTIFIED BY 'strong-password';
GRANT SELECT, INSERT, UPDATE, DELETE ON hey_pickler.* TO 'heypickler'@'localhost';
FLUSH PRIVILEGES;

-- 生产环境：管理员账号通过 AdminBootstrapper 在应用启动时
-- 从 INITIAL_ADMIN_USERNAME / INITIAL_ADMIN_PASSWORD 环境变量创建
-- 不再有 V2 migration 内嵌的默认 admin/admin123 种子
```

---

## 五、安全配置

### 5.1 必须修改的配置项

| 配置项 | 开发默认值 | 说明 |
|--------|-----------|------|
| `JWT_SECRET` | `HeyPickler2026DevSecret...` | JWT 签名密钥，至少 32 字符 |
| `AES_KEY` | `PicklerDevAesKey` | 数据加密密钥，恰好 16/24/32 字节，启动时严格校验 |
| `INITIAL_ADMIN_PASSWORD` | 无（dev 库已有 admin 行可跳过）| 首次部署时必需，>= 12 字符推荐 |
| `WX_DEV_MODE` | `true` | 生产环境必须设为 `false` |

### 5.2 安全机制

系统内置以下安全措施：

| 机制 | 实现类 | 说明 |
|------|--------|------|
| JWT 认证 | `AppAuthFilter` / `AdminAuthFilter` | 无状态 token 认证 |
| 速率限制 | `RateLimitFilter` | 基于 Redis + Lua 脚本，按 IP/用户限流 |
| XSS 防护 | `XssFilter` | 请求体 XSS 过滤 |
| 安全头 | `SecurityHeadersFilter` | X-Content-Type-Options, X-Frame-Options 等 |
| CORS | `CorsConfig` | 按路径分域配置 /api/admin 和 /api/app |
| BCrypt | `SecurityConfig` | 管理员密码 bcrypt 加密存储 |

### 5.3 速率限制配置

```yaml
hey-pickler:
  rate-limit:
    login: 200       # 登录接口: 每 IP 每分钟 200 次
    admin: 600       # 已认证管理员: 每用户每分钟 600 次
    admin-anon: 200  # 匿名管理员请求: 每 IP 每分钟 200 次
    default: 600     # 默认: 每 IP 每分钟 600 次
```

---

## 六、数据库表结构

| 表名 | 说明 |
|------|------|
| `user` | 小程序用户（openid, 积分, 段位） |
| `admin_user` | 管理后台用户（用户名, 密码, 角色） |
| `event` | 赛事活动（类型, 时间, 地点, 费用） |
| `registration` | 报名记录（用户-赛事, 比赛类型, 状态） |
| `point_record` | 积分变动记录 |
| `ranking` | 排名数据（类型, 段位, 赛季） |
| `banner` | 首页轮播图 |
| `ban_record` | 用户封禁记录 |

Flyway 自动管理数据库版本，迁移脚本位于 `hey-pickler-server/src/main/resources/db/migration/`。

---

## 六之二、双积分体系（战力 / 活力）

平台采用「双积分 + 段位 + 赛季」机制，面向竞技与社交两类场景独立计分：

| 积分类型 | 后端字段 | 语义 | 关联赛事 |
|----------|----------|------|----------|
| **战力 (STAR)** | `STAR` | 竞技水平 | 竞技赛事 |
| **活力 (PARTY)** | `PARTY` | 社交参与度 | 社交活动 |

> 后端枚举值沿用历史命名 `STAR` / `PARTY`，未做迁移；前端展示统一为「战力 / 活力」。

### 段位（6 档）

按战力或活力积分独立划分段位，由低到高：

`BRONZE`青铜 → `SILVER`白银 → `GOLD`黄金 → `PLATINUM`铂金 → `DIAMOND`钻石 → `MASTER`王者

段位阈值在 `application.yml` 的 `hey-pickler.tier` 下配置。**修改阈值后必须重启服务才会生效**（无热加载）。

### 积分来源（point_record.source）

| source | 含义 |
|--------|------|
| `REGISTRATION` | 报名赛事 |
| `CHECK_IN` | 签到 |
| `PLACEMENT` | 比赛名次（竞技赛事）|
| `MANUAL` | 后台手动调整 |
| `REDEEM` | 商城兑换（预留，PointWallet）|
| `ADJUST` | 系统校正 |

### 赛季机制

- 赛季按积分类型独立管理，状态为 `CURRENT`（当前）或 `ARCHIVED`（已归档）。
- 在管理后台「赛季管理」页面切换/归档赛季；归档后历史排名仍可查询。
- 当前赛季编号写入 `point_record.season_code`，保证历史可追溯。

### 数据迁移说明（V12）

V12 迁移引入了比赛形式与队伍分组功能：

- `event.format` (SINGLES/DOUBLES/MIXED，默认 SINGLES)，并按 `registration.matchType` 多数回填存量数据
- `event.grouping_locked` (TINYINT(1))，分组锁定后冻结名单
- `team` 表（双打/混打 2 人一队，`status` PENDING/CONFIRMED；解散/拒邀 = 物理删行）
- `match_group` / `group_assignment` 表（CHECK 约束要求 `user_id`/`team_id` 互斥）

**前置 V11**：双积分体系与赛季机制。升级到 V12 后请确认存量赛事 `format` 回填结果符合预期，并通过分组管理页完成首轮手动分组。

---

## 七、本地开发快速启动

---

## 七、本地开发快速启动

```bash
# 前提: MySQL 8.0+ 和 Redis 已运行

# 1. 初始化数据库
mysql -u root -p -e "CREATE DATABASE hey_pickler DEFAULT CHARACTER SET utf8mb4;"

# 2. 启动后端
cd hey-pickler-server
mvn spring-boot:run

# 3. 启动管理后台 (新终端)
cd hey-pickler-admin
npm install
npm run dev

# 4. 小程序 (微信开发者工具导入 hey-pickler-wxapp 目录)

# 5. 验证
# 后端:     http://localhost:8080/doc.html
# 管理后台: http://localhost:5173 (admin / <your INITIAL_ADMIN_PASSWORD>)
# 小程序:   微信开发者工具模拟器
```

---

## 八、常见问题

### Q: 小程序模拟器登录报错 400

开发环境需确认后端 `WX_DEV_MODE=true`（默认已开启），否则会尝试调用微信 API 导致失败。

### Q: 小程序真机无法联网

游客 AppID (`touristappid`) 不允许真机网络请求。真机调试需要：
1. 申请正式 AppID
2. 配置 HTTPS 域名
3. 修改 `project.config.json` 中的 appid

### Q: Flyway 迁移失败

首次启动如果数据库已有表结构，确认 `application-dev.yml` 中 `baseline-on-migrate: true` 已配置。

### Q: Redis 连接失败

```bash
# 检查 Redis 状态
redis-cli ping
# 返回 PONG 表示正常
```
