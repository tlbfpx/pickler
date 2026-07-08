# Hey Pickler 部署清单

## 一、系统架构概览

```
┌─────────────┐    ┌─────────────┐    ┌──────────────┐
│  微信小程序   │    │  管理后台    │    │   Nginx      │
│  (wxapp)     │    │  (admin)    │    │   反向代理    │
└──────┬───────┘    └──────┬──────┘    └──────┬───────┘
       │                   │                  │
       │  HTTPS / WSS      │  HTTPS           │
       └───────────────────┴──────────────────┘
                           │
                    ┌──────▼───────┐
                    │  Spring Boot │ :8080
                    │  (后端 API)  │
                    └──┬───────┬───┘
                       │       │
                ┌──────▼──┐ ┌──▼──────┐
                │  MySQL  │ │  Redis  │
                │  :3306  │ │  :6379  │
                └─────────┘ └─────────┘
```

## 二、服务器硬件配置

### 最低配置（开发/测试环境）

| 项目 | 规格 |
|------|------|
| CPU | 8核 |
| 内存 | 4 GB |
| 系统盘 | 40 GB SSD |
| 数据库盘 | 20 GB SSD |
| 带宽 | 5 Mbps |

### 推荐配置（生产环境 - 初期）

| 项目 | 规格 |
|------|------|
| CPU | 4 核 |
| 内存 | 8 GB |
| 系统盘 | 50 GB SSD |
| 数据库盘 | 100 GB SSD |
| 带宽 | 10 Mbps |
| 数量 | 2 台（应用服务器 + 数据库服务器） |

### 推荐配置（生产环境 - 中期 1000+ 并发）

| 项目 | 规格 |
|------|------|
| 应用服务器 | 4C/8G × 2 台（负载均衡） |
| 数据库服务器 | 8C/16G × 1 台（可升级主从） |
| Redis | 2C/4G × 1 台（或云托管） |
| OSS/CDN | 用于图片和静态资源 |
| SLB | 负载均衡 |

## 三、软件清单

### 3.1 操作系统

| 软件 | 版本要求 | 说明 |
|------|---------|------|
| Linux | Ubuntu 22.04 LTS / CentOS 8+ | 推荐 Ubuntu 22.04 |

### 3.2 运行时环境

| 软件 | 版本 | 用途 | 安装方式 |
|------|------|------|---------|
| JDK | **17** | 后端运行 | `apt install openjdk-17-jdk` |
| Maven | **3.9.x** | 后端构建 | `apt install maven` |
| Node.js | **18.x LTS** | 前端构建 | `curl -fsSL https://deb.nodesource.com/setup_18.x` |
| npm | **9.x+** | 前端包管理 | 随 Node.js 安装 |
| Nginx | **1.24+** | 反向代理 / 静态资源 | `apt install nginx` |

### 3.3 中间件

| 软件 | 版本 | 用途 | 配置要求 |
|------|------|------|---------|
| MySQL | **8.0+** | 主数据库 | InnoDB, utf8mb4, max_connections=500 |
| Redis | **6.x+** | 缓存 / 限流 / Session | 开启持久化, maxmemory ≥ 512MB（生产推荐 1GB），eviction 策略 `allkeys-lru` |

### 3.4 微信平台

| 软件 | 版本 | 用途 |
|------|------|------|
| 微信开发者工具 | 最新稳定版 | 小程序编译、预览、上传 |
| 微信公众平台 | — | 小程序管理、版本发布 |

## 四、后端依赖详情

### 4.1 框架版本（pom.xml）

| 依赖 | 版本 |
|------|------|
| Spring Boot | **3.2.5** |
| MyBatis Plus | **3.5.6** |
| Spring Security | 6.2.4 (Boot 管理) |
| SpringDoc OpenAPI | **2.5.0** |
| Knife4j | **4.4.0** |
| JJWT | **0.12.5** |
| Flyway | 9.22.3 (Boot 管理) |
| Lombok | **1.18.46** |
| MySQL Connector | 8.3.0 (Boot 管理) |
| Lettuce (Redis) | 6.3.2 (Boot 管理) |

### 4.2 生产环境变量

**必填：**

| 变量名 | 说明 | 示例 |
|--------|------|------|
| `DB_URL` | 数据库连接地址 | `jdbc:mysql://db.internal:3306/hey_pickler?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai` |
| `DB_USERNAME` | 数据库用户名 | `heypickler` |
| `DB_PASSWORD` | 数据库密码 | `强密码` |
| `REDIS_HOST` | Redis 地址 | `redis.internal` |
| `REDIS_PORT` | Redis 端口 | `6379` |
| `REDIS_PASSWORD` | Redis 密码 | `强密码` |
| `JWT_SECRET` | JWT 签名密钥 | 32+ 字符随机字符串 |
| `AES_KEY` | AES 加密密钥 | 恰好 16 字符 |
| `WX_APPID` | 微信小程序 AppID | `wx1234567890abcdef` |
| `WX_SECRET` | 微信小程序 Secret | 对应的 Secret |

**可选：**

| 变量名 | 默认值 | 说明 |
|--------|--------|------|
| `CORS_ADMIN_ORIGINS` | — | 管理后台允许的域名，逗号分隔 |
| `CORS_APP_ORIGINS` | — | 小程序 API 允许的域名 |
| `WX_DEV_MODE` | `false` | 开发模式（跳过微信验证） |
| `JWT_APP_EXPIRATION` | `604800000` | 小程序 Token 有效期 (ms) |
| `JWT_ADMIN_EXPIRATION` | `86400000` | 管理后台 Token 有效期 (ms) |

### 4.3 数据库初始化

Flyway 在应用启动时自动执行 `hey-pickler-server/src/main/resources/db/migration/` 下未应用的迁移。当前 head：**V17__add_point_record_idempotency.sql**（共 17 个版本，V1 → V17）。

主要演进：
- `V1__init_schema.sql` / `V2__init_data.sql` — 基础表结构 + 种子数据
- `V8__add_operation_log.sql` — 审计日志表（append-only，无软删字段）
- `V11__dual_points_system.sql` — STAR(战力) / PARTY(活力) 双积分体系
- `V12__match_form_team_grouping.sql` — 双打/混打组队 + 分组（含 `uk_event_member` 唯一键）
- `V13__match_play.sql` — 比赛对局表
- `V14__placement_points.sql` — 名次发分配置表
- `V16__add_notification.sql` — 站内通知表
- `V17__add_point_record_idempotency.sql` — 发分幂等（当前 head）

> ⚠️ 完整清单以 `db/migration/` 目录为准（本文档可能滞后于代码）。**已部署环境的旧 migration 禁止改写**——新改动一律追加新版本（V18__...）。MySQL ≥ 8.0.16（V12 的 CHECK 约束依赖此版本）。

## 五、前端构建详情

### 5.1 管理后台 (hey-pickler-admin)

| 依赖 | 版本 |
|------|------|
| Vue | **3.4.x** |
| Vue Router | **4.2.x** |
| Pinia | **2.1.x** |
| Element Plus | **2.5.x** |
| Axios | **1.6.x** |
| Vite | **5.x** |
| TypeScript | **5.3.x** |

**构建命令：**
```bash
cd hey-pickler-admin
npm ci
npm run build        # 输出到 dist/
```

**Nginx 配置要点：**
- `dist/` 目录作为静态资源根目录
- `/api` 反向代理到后端 8080 端口
- 开启 gzip

### 5.2 微信小程序 (hey-pickler-wxapp)

| 依赖 | 版本 |
|------|------|
| 基础库 | **2.19.4** |
| DevTools 库 | **3.16.1** |

**发布流程：**
1. 修改 `app.js` 中 `globalData.baseUrl` 为生产域名
2. 微信开发者工具打开项目 → 上传 → 提交审核

## 六、Nginx 参考配置

```nginx
server {
    listen 80;
    server_name your-domain.com;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl http2;
    server_name your-domain.com;

    ssl_certificate     /etc/nginx/ssl/cert.pem;
    ssl_certificate_key /etc/nginx/ssl/key.pem;

    # 管理后台
    location / {
        root /var/www/heypickler-admin/dist;
        try_files $uri $uri/ /index.html;
    }

    # API 反向代理
    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # API 文档（可选，生产环境可关闭）
    location /doc.html {
        proxy_pass http://127.0.0.1:8080;
    }

    gzip on;
    gzip_types text/plain text/css application/json application/javascript text/xml;
}
```

## 七、端口清单

| 端口 | 服务 | 对外暴露 |
|------|------|---------|
| 80/443 | Nginx | 是 |
| 8080 | Spring Boot | 否（Nginx 代理） |
| 3306 | MySQL | 否 |
| 6379 | Redis | 否 |

## 八、部署步骤

```bash
# 1. 安装基础软件
apt update && apt install -y openjdk-17-jdk maven nodejs npm nginx

# 2. 创建数据库
mysql -u root -p -e "CREATE DATABASE hey_pickler DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# 3. 构建后端
cd hey-pickler-server
mvn clean package -DskipTests
java -jar target/hey-pickler-server-1.0.0.jar --spring.profiles.active=prod

# 4. 构建管理后台
cd ../hey-pickler-admin
npm ci && npm run build
# 将 dist/ 部署到 Nginx

# 5. 配置 Nginx 并启动
systemctl enable nginx && systemctl start nginx

# 6. 上传小程序
# 微信开发者工具打开 hey-pickler-wxapp → 上传 → 提交审核
```

## 九、SSL 证书

| 项目 | 要求 |
|------|------|
| 类型 | DV 域名验证 |
| 域名 | 小程序要求 HTTPS |
| 推荐 | Let's Encrypt（免费）或阿里云/腾讯云证书 |
| 微信要求 | 必须备案域名 + HTTPS |
