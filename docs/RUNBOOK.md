# Hey Pickler 运维手册

> 本手册面向客户运营团队，目标是**无开发协助完成首次部署、日常运营、备份恢复、调优、故障响应**。
>
> 与 `docs/DEPLOYMENT-REQUIREMENTS.md` 的区别：DEPLOYMENT-REQUIREMENTS 描述**部署前的硬件软件清单**，本手册描述**部署后的运维操作**。
>
> 目标环境：阿里云 ECS（Ubuntu 22.04+ / Alibaba Cloud Linux 3）+ 阿里云 OSS 备份。

---

## 目录

1. [首次部署](#1-首次部署)
2. [日常运营](#2-日常运营)
3. [备份与恢复](#3-备份与恢复)
4. [限流调优](#4-限流调优)
5. [常见故障响应](#5-常见故障响应)

---

## 1. 首次部署

> 目标：从「干净的 ECS 实例」到「线上可访问的后台 + 小程序 API」全流程 30 分钟内完成。

### 1.1 前置条件

#### 1.1.1 服务器规格（推荐）

| 项目 | 规格 |
|------|------|
| ECS 实例 | 4 vCPU / 8 GB / 40 GB SSD |
| 操作系统 | Ubuntu 22.04 LTS 或 Alibaba Cloud Linux 3 |
| 公网带宽 | 5 Mbps（或按 SLA） |
| 公网 IP | 必须固定 |
| 域名 | `admin.heypickler.com`（管理后台）+ `api.heypickler.com`（小程序 API） |

#### 1.1.2 必装软件

```bash
# JDK 17+
sudo apt update
sudo apt install -y openjdk-17-jdk-headless
java -version  # 确认输出 "17" 以上

# MySQL 8
sudo apt install -y mysql-server
sudo mysql_secure_installation
mysql --version  # 确认 8.0+

# Redis 6+
sudo apt install -y redis-server
redis-cli ping  # 期望 PONG

# Nginx 1.20+
sudo apt install -y nginx
nginx -v

# 基础工具
sudo apt install -y curl unzip vim logrotate
```

#### 1.1.3 阿里云控制台配置

| 配置项 | 操作 |
|--------|------|
| 安全组 | 开放 22（SSH）、80（HTTP）、443（HTTPS）；**不开放 3306/6379/8080**（仅本机访问）|
| 域名解析 | `admin.heypickler.com` 与 `api.heypickler.com` A 记录指到 ECS 公网 IP |
| OSS bucket | 创建 `heypickler-backup`（同 region，私有 ACL），详见 §3.1 |
| SSL 证书 | 申请 admin + api 两个域名的证书（DV 免费证书即可），下载 Nginx 格式 |

#### 1.1.4 数据库初始化

```bash
sudo mysql <<'EOF'
CREATE DATABASE hey_pickler DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'heypickler'@'localhost' IDENTIFIED BY '<strong-db-password>';
GRANT SELECT, INSERT, UPDATE, DELETE ON hey_pickler.* TO 'heypickler'@'localhost';
FLUSH PRIVILEGES;
EOF
```

> Flyway 会在后端首次启动时自动建表（V1-V8 迁移脚本），无需手工建表。

---

### 1.2 一键安装

```bash
# 在 ECS 上，以 root 身份
git clone <repo-url> /opt/heypickler-repo  # 或上传 tarball
cd /opt/heypickler-repo

sudo bash deploy/scripts/install.sh
```

**预期输出**：

```
[INFO] Step 1/5: 检查前置条件
  OS: Ubuntu 22.04.x LTS
  ✓ all prereqs satisfied
[INFO] Step 2/5: 创建 heypickler 用户与目录
  ✓ created user heypickler
  ✓ /opt/heypickler /var/log/heypickler /etc/heypickler ready
[INFO] Step 3/5: 安装 systemd unit
  ✓ unit installed and enabled
[INFO] Step 4/5: 生成 env 文件
  ✓ /etc/heypickler/heypickler.env created (mode 0600)
[INFO] Step 5/5: 安装 logrotate
  ✓ /etc/logrotate.d/heypickler installed

============================================================
  ✓ 部署工件就绪
============================================================

下一步操作：
  1. 上传 jar 包：
     scp hey-pickler-server-1.0.0.jar root@<ecs>:/opt/heypickler/
  ...
```

脚本结束后**不会自动启动服务**，需要先填 env 文件。

---

### 1.3 环境变量配置

```bash
sudo vi /etc/heypickler/heypickler.env
```

每个字段的填写规则：

| 字段 | 必填 | 生成命令 / 取值 |
|------|------|----------------|
| `JWT_SECRET` | ✓ | `openssl rand -base64 48`（≥32 字符）|
| `AES_KEY` | ✓ | `openssl rand -base64 32`（截断到精确 16/24/32 字节）|
| `INITIAL_ADMIN_USERNAME` | ✓ | 默认 `admin`，可改 |
| `INITIAL_ADMIN_PASSWORD` | ✓（首次） | ≥12 字符强密码，首次启动后立即改 |
| `PROD_GUARD` | ✓ | `true`（生产必开，深度防御）|
| `SPRING_PROFILES_ACTIVE` | ✓ | `prod`（必须）|
| `DB_USERNAME` | ✓ | `heypickler`（按 §1.1.4 创建）|
| `DB_PASSWORD` | ✓ | 数据库用户密码 |
| `REDIS_PASSWORD` | 可空 | 如 Redis 设了密码则填 |
| `WX_APPID` / `WX_SECRET` | ✓ | 微信公众平台获取，**生产 WX_DEV_MODE 自动 false** |
| `CORS_ADMIN_ORIGINS` | ✓ | `https://admin.heypickler.com` |
| `OSS_BUCKET` | 推荐 | `oss://heypickler-backup/mysql`（详见 §3.1）|

填完后**校验文件权限**：

```bash
ls -l /etc/heypickler/heypickler.env
# 期望：-rw------- 1 heypickler heypickler ... /etc/heypickler/heypickler.env
```

如权限不对，修复：

```bash
sudo chown heypickler:heypickler /etc/heypickler/heypickler.env
sudo chmod 0600 /etc/heypickler/heypickler.env
```

---

### 1.4 启动与验证

#### 1.4.1 上传 jar 包

```bash
# 在本地
scp hey-pickler-server-1.0.0.jar root@<ecs-ip>:/opt/heypickler/

# 在 ECS 上
sudo chown heypickler:heypickler /opt/heypickler/hey-pickler-server-1.0.0.jar
```

#### 1.4.2 启动服务

```bash
sudo systemctl start hey-pickler
sudo systemctl status hey-pickler
```

期望看到 `Active: active (running)`，并有最近日志输出。

#### 1.4.3 校验 systemd unit 语法

如部署时 `systemd-analyze verify` 未跑过，现在跑一遍：

```bash
systemd-analyze verify /etc/systemd/system/hey-pickler.service
# 无输出 = 通过
```

#### 1.4.4 验证后端启动

```bash
# 看实时日志（admin_bootstrapper 应创建 INITIAL_ADMIN_USERNAME 行）
sudo tail -f /var/log/heypickler/app.log

# 期望看到：
#   Migrating schema `hey_pickler` to version "1 - init schema"
#   ... (V1-V8 全部应用)
#   Started HeyPicklerApplication in X.XXX seconds
#   INFO c.heypickler.config.AdminBootstrapper : Initial admin 'admin' created from env vars

# API 探活（变更 #2 actuator 接入前的 fallback）
curl -sf -o /dev/null -w "%{http_code}\n" \
  -X POST http://localhost:8080/api/admin/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"<your-INITIAL_ADMIN_PASSWORD>"}'
# 期望：200
```

#### 1.4.5 验证管理员登录

```bash
# 用 INITIAL_ADMIN_PASSWORD 登录，期望返回 {"code":0,"data":{"token":"..."}}
curl -X POST http://localhost:8080/api/admin/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"<your-INITIAL_ADMIN_PASSWORD>"}'
```

**登录成功后立即在「管理员管理」页面改密码**（脱离 INITIAL_ADMIN_PASSWORD 依赖）。

---

### 1.5 Nginx 接入与 SSL

#### 1.5.1 上传 SSL 证书

```bash
# 阿里云控制台下载 Nginx 格式证书，得到两个文件：
#   <domain>.pem (证书链)
#   <domain>.key (私钥)

# 上传到 ECS
sudo mkdir -p /etc/ssl/heypickler
sudo cp admin.heypickler.com.pem /etc/ssl/heypickler.pem
sudo cp admin.heypickler.com.key /etc/ssl/heypickler.key
sudo chmod 644 /etc/ssl/heypickler.pem
sudo chmod 600 /etc/ssl/heypickler.key
```

#### 1.5.2 部署 admin 后台静态资源

```bash
# 本地构建
cd hey-pickler-admin
npm ci
npm run build
# 上传 dist/ 到 ECS
scp -r dist/* root@<ecs-ip>:/var/www/hey-pickler-admin/dist/

# ECS 上调整权限
sudo chown -R www-data:www-data /var/www/hey-pickler-admin/dist/
```

#### 1.5.3 接入 Nginx 配置

```bash
sudo cp /opt/heypickler-repo/deploy/nginx/heypickler.conf /etc/nginx/sites-available/
sudo ln -s /etc/nginx/sites-available/heypickler.conf /etc/nginx/sites-enabled/

# 移除默认站点（避免冲突）
sudo rm -f /etc/nginx/sites-enabled/default

# 校验配置
sudo nginx -t
# 期望：syntax is ok / test is successful

# reload
sudo systemctl reload nginx
```

#### 1.5.4 端到端验证

```bash
# 浏览器访问 https://admin.heypickler.com，应看到登录页
# 用 INITIAL_ADMIN_PASSWORD 登录，进入控制台

# 小程序 API 探活
curl -sf -o /dev/null -w "%{http_code}\n" \
  https://api.heypickler.com/api/app/health 2>/dev/null || \
  echo "（小程序无 health endpoint，登录接口探活）"
curl -X POST https://api.heypickler.com/api/app/auth/login \
  -H "Content-Type: application/json" \
  -d '{"code":"test"}' 
# 期望：400 或业务错误（证明请求到后端）
```

---

### 1.6 故障排查

#### Pitfall 1: JDK 版本不对

**症状**：`install.sh` 报 `JDK 17+ required, got 11`

**修复**：

```bash
sudo apt remove -y openjdk-11-jdk-headless
sudo apt install -y openjdk-17-jdk-headless
sudo update-alternatives --config java  # 选 17
```

#### Pitfall 2: MySQL 连接拒绝

**症状**：后端日志 `Communications link failure` 或 `Access denied`

**诊断**：

```bash
# 1. MySQL 起来了吗
sudo systemctl status mysql
# 2. 端口在监听吗
sudo ss -tlnp | grep 3306
# 3. 凭据对吗
mysql -u heypickler -p hey_pickler -e 'SELECT 1'
```

**修复**：

- 端口未监听：`sudo vi /etc/mysql/mysql.conf.d/mysqld.cnf`，确保 `bind-address = 127.0.0.1`（不要 0.0.0.0）
- 凭据错：`sudo mysql -e "ALTER USER 'heypickler'@'localhost' IDENTIFIED BY '<new-pwd>';"`
- 数据库不存在：见 §1.1.4

#### Pitfall 3: 端口 8080 被占用

**症状**：后端日志 `Web server failed to start. Port 8080 was already in use`

**诊断**：

```bash
sudo ss -tlnp | grep 8080
# 或
sudo lsof -i :8080
```

**修复**：

```bash
# 杀掉占用进程（确认是无关进程）
sudo kill -9 <pid>

# 或改后端端口
sudo vi /etc/heypickler/heypickler.env
# 添加：SERVER_PORT=8081
# 同步改 nginx conf 里的 proxy_pass
sudo systemctl restart hey-pickler
sudo systemctl reload nginx
```

#### Pitfall 4: PROD_GUARD 启动拒绝

**症状**：日志 `FATAL: Profile guard violation`，退出码 2

**修复**：

```bash
# 检查 env 文件
sudo cat /etc/heypickler/heypickler.env | grep -E 'SPRING_PROFILES|JWT_SECRET|AES_KEY|PROD_GUARD'
# 确保：
#   SPRING_PROFILES_ACTIVE=prod
#   JWT_SECRET 不是 HeyPickler2026DevSecret... 默认值
#   AES_KEY 不是 PicklerDevAesKey 默认值
#   PROD_GUARD=true
```

---

## 2. 日常运营

### 2.1 服务管理

| 操作 | 命令 |
|------|------|
| 启动 | `sudo systemctl start hey-pickler` |
| 停止 | `sudo systemctl stop hey-pickler` |
| 重启 | `sudo systemctl restart hey-pickler` |
| 状态 | `sudo systemctl status hey-pickler` |
| 是否启用开机自启 | `systemctl is-enabled hey-pickler` |
| 查看启动日志 | `sudo journalctl -u hey-pickler -b` |
| 跟踪实时日志 | `sudo journalctl -u hey-pickler -f` |

### 2.2 日志查看

| 日志 | 路径 | 用途 |
|------|------|------|
| 应用主日志 | `/var/log/heypickler/app.log` | 业务日志、Spring 启动、`INFO` 以上 |
| 应用错误日志 | `/var/log/heypickler/app.err.log` | `ERROR` 级堆栈 |
| Heap dump | `/var/log/heypickler/java_pid*.hprof` | OOM 自动 dump（如发生）|
| Admin 后台访问 | `/var/log/nginx/heypickler-admin-access.log` | admin.heypickler.com 流量 |
| Admin 后台错误 | `/var/log/nginx/heypickler-admin-error.log` | nginx 端 4xx/5xx |
| API 访问 | `/var/log/nginx/heypickler-api-access.log` | api.heypickler.com 流量 |
| 备份日志 | `/var/log/heypickler/backup.log` | cron 备份输出（如配置）|

常用命令：

```bash
# 实时跟踪
sudo tail -f /var/log/heypickler/app.log

# 查 ERROR 级
sudo grep -E '^\d{4}-\d{2}-\d{2}.*ERROR' /var/log/heypickler/app.log | tail -50

# 查特定异常堆栈
sudo grep -A 30 NullPointerException /var/log/heypickler/app.err.log | head -60

# nginx 错误趋势
sudo awk '{print $9}' /var/log/nginx/heypickler-admin-access.log | sort | uniq -c
```

日志自动轮转：`/etc/logrotate.d/heypickler` 已配置，daily 保留 30 天，无需人工干预。

### 2.3 健康检查

#### 当前可用方式（变更 #2 actuator 接入前）

```bash
# API 探活（HTTP 200 = 服务在跑）
curl -sf -o /dev/null -w "%{http_code}\n" \
  -X POST http://localhost:8080/api/admin/auth/login \
  -H "Content-Type: application/json" -d '{}'
# 期望：400（参数错误，证明服务在跑）

# 数据库连通
mysql -u heypickler -p hey_pickler -e 'SELECT 1'

# Redis 连通
redis-cli ping  # PONG
```

#### 变更 #2 接入后

```bash
curl http://localhost:8080/actuator/health
# 期望：{"status":"UP"}
```

#### Nagios / Zabbix 集成

简单的 NRPE 插件示例：

```bash
#!/usr/bin/env bash
# /usr/local/bin/check_heypickler
status=$(curl -sf -o /dev/null -w "%{http_code}" -X POST \
  http://localhost:8080/api/admin/auth/login \
  -H "Content-Type: application/json" -d '{}')
case $status in
  200|400) echo "OK: service responding"; exit 0 ;;
  000)    echo "CRITICAL: connection refused"; exit 2 ;;
  *)      echo "WARNING: unexpected status $status"; exit 1 ;;
esac
```

### 2.4 配置变更与重启

#### 修改环境变量

```bash
sudo vi /etc/heypickler/heypickler.env
# 改完
sudo systemctl restart hey-pickler
sudo journalctl -u hey-pickler -f  # 看启动是否成功
```

#### 修改限流配置

见 §4 限流调优。

### 2.5 升级流程

```bash
# 1. 备份当前数据库（必做！）
sudo -u heypickler bash /opt/heypickler-repo/deploy/scripts/backup-mysql.sh

# 2. 上传新 jar
scp hey-pickler-server-1.1.0.jar root@<ecs>:/opt/heypickler/
sudo chown heypickler:heypickler /opt/heypickler/hey-pickler-server-1.1.0.jar

# 3. 备份旧 jar（如需回滚）
sudo cp /opt/heypickler/hey-pickler-server-1.0.0.jar /opt/heypickler/backup/

# 4. 修改 systemd unit 的 jar 路径（如版本号变了）
sudo sed -i 's/hey-pickler-server-1.0.0/hey-pickler-server-1.1.0/' \
  /etc/systemd/system/hey-pickler.service
sudo systemctl daemon-reload

# 5. 重启
sudo systemctl restart hey-pickler

# 6. 看 Flyway 迁移日志
sudo tail -f /var/log/heypickler/app.log | grep -E 'Migrating|Started Hey'

# 7. 验证（同 §1.4.4）
curl -sf -o /dev/null -w "%{http_code}\n" -X POST \
  http://localhost:8080/api/admin/auth/login \
  -H "Content-Type: application/json" -d '{}'
```

#### 升级失败回滚

```bash
# 1. 停止服务
sudo systemctl stop hey-pickler

# 2. 数据库回滚（如有 migration 已执行）
#    手工逆向执行 V<x>__xxx.sql，或从备份恢复（见 §3.4）

# 3. 还原旧 jar
sudo cp /opt/heypickler/backup/hey-pickler-server-1.0.0.jar /opt/heypickler/
sudo sed -i 's/hey-pickler-server-1.1.0/hey-pickler-server-1.0.0/' \
  /etc/systemd/system/hey-pickler.service
sudo systemctl daemon-reload

# 4. 启动
sudo systemctl start hey-pickler
```

---

## 3. 备份与恢复

### 3.1 OSS bucket 与 RAM 子账号准备

#### 3.1.1 创建 OSS bucket

阿里云控制台 → 对象存储 OSS → Bucket 列表 → 创建：

| 项 | 值 |
|----|----|
| Bucket 名称 | `heypickler-backup`（全局唯一，可改）|
| 地域 | 与 ECS 同 region（如 `cn-shanghai`）|
| 读写权限 | **私有** |
| 服务端加密 | OSS 完全托管（KMS） |
| 版本控制 | 开启（防误删）|
| 生命周期 | 30 天后自动删除（mysql/ 前缀）|

#### 3.1.2 创建 RAM 子账号

阿里云控制台 → 访问控制 RAM → 用户 → 创建用户：

| 项 | 值 |
|----|----|
| 登录名 | `heypickler-backup` |
| 访问方式 | 编程访问（**不要**控制台访问）|
| AccessKey | 创建后立即下载保管 |

授权策略（自定义策略）：

```json
{
  "Version": "1",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": ["oss:PutObject", "oss:GetObject", "oss:DeleteObject"],
      "Resource": [
        "acs:oss:*:*:heypickler-backup/mysql/*"
      ]
    },
    {
      "Effect": "Allow",
      "Action": ["oss:ListBucket"],
      "Resource": ["acs:oss:*:*:heypickler-backup"]
    }
  ]
}
```

#### 3.1.3 安装配置 ossutil

```bash
# 安装
sudo apt install -y ossutil  # 或：
curl -L https://gosspublic.alicdn.com/ossutil/install.sh | sudo bash

# 配置（在 root 或 heypickler 用户家目录）
ossutil config
# 提示输入：
#   config file: /root/.ossutilconfig  (heypickler 用户用 /home/heypickler/.ossutilconfig)
#   language: CH
#   endpoint: https://oss-cn-shanghai.aliyuncs.com  (按实际 region)
#   access key id: <RAM 子账号的 AccessKeyId>
#   access key secret: <RAM 子账号的 AccessKeySecret>

# 测试
ossutil ls oss://heypickler-backup/  # 期望列出（空 bucket 也返回成功）
```

#### 3.1.4 写入 env 文件

```bash
sudo vi /etc/heypickler/heypickler.env
# 追加：
OSS_BUCKET=oss://heypickler-backup/mysql
```

---

### 3.2 自动备份（cron）

#### 3.2.1 拷贝备份脚本

```bash
sudo cp /opt/heypickler-repo/deploy/scripts/backup-mysql.sh /opt/heypickler/scripts/
sudo chown heypickler:heypickler /opt/heypickler/scripts/backup-mysql.sh
sudo chmod +x /opt/heypickler/scripts/backup-mysql.sh
```

#### 3.2.2 配置 cron

```bash
sudo crontab -u heypickler -e
# 追加（每天凌晨 2 点备份）：
0 2 * * * /opt/heypickler/scripts/backup-mysql.sh >> /var/log/heypickler/backup.log 2>&1
```

#### 3.2.3 首次手动跑确认

```bash
sudo -u heypickler bash /opt/heypickler/scripts/backup-mysql.sh
# 期望输出：
#   [2026-...] starting mysqldump → /var/backups/.../hey_pickler-XXX.sql.gz
#   [2026-...] mysqldump OK (NNN bytes)
#   [2026-...] uploading to OSS bucket=oss://heypickler-backup/mysql
#   [2026-...] OSS upload OK
#   [2026-...] backup complete

# 验证 OSS
ossutil ls oss://heypickler-backup/mysql/ | head -5
```

---

### 3.3 手动备份

需要临时备份（如升级前）：

```bash
sudo -u heypickler bash /opt/heypickler/scripts/backup-mysql.sh
```

脚本立即执行一次全量备份，不依赖 cron。

---

### 3.4 恢复流程

> **警告**：恢复会覆盖目标数据库现有数据。务必先在测试环境演练（§3.5）。

#### 3.4.1 从本地备份恢复

```bash
# 1. 停止服务（避免恢复期间有写入）
sudo systemctl stop hey-pickler

# 2. 找到要恢复的备份
ls -lh /var/backups/heypickler/mysql/

# 3. 执行恢复
sudo bash /opt/heypickler-repo/deploy/scripts/restore-mysql.sh \
  /var/backups/heypickler/mysql/hey_pickler-20260101-020000.sql.gz

# 脚本会打印 binlog 坐标（如需 PITR），并要求确认
# 恢复完成后启动服务
sudo systemctl start hey-pickler
```

#### 3.4.2 从 OSS 恢复

```bash
sudo bash /opt/heypickler-repo/deploy/scripts/restore-mysql.sh \
  oss://heypickler-backup/mysql/hey_pickler-20260101-020000.sql.gz
```

#### 3.4.3 时间点恢复（PITR）

适用场景：用户误删了今天上午 10 点的数据，需要恢复到 10:00:00 的状态。

```bash
# 1. 找到 10 点之前的最近一次全量备份（如昨晚 2 点的）
# 2. 恢复全量（步骤同 §3.4.1），记录脚本输出的 binlog 坐标
#    如：CHANGE MASTER TO MASTER_LOG_FILE='mysql-bin.000123', MASTER_LOG_POS=12345

# 3. 用 mysqlbinlog 重放从该坐标到 10:00 的 binlog
mysql -u heypickler -p hey_pickler -e "SHOW BINARY LOGS"
# 找到 mysql-bin.000123 之后到当前的所有 binlog

# 4. 提取指定时间段的 SQL
mysqlbinlog \
  --start-position=12345 \
  --stop-datetime="2026-01-01 10:00:00" \
  /var/lib/mysql/mysql-bin.000123 \
  /var/lib/mysql/mysql-bin.000124 \
  /var/lib/mysql/mysql-bin.000125 \
  | mysql -u heypickler -p hey_pickler
```

---

### 3.5 季度恢复演练

> 客户运营团队**每季度**执行一次，确认备份可恢复、流程可执行、人员熟悉。

#### Checklist

```
□ 1. 在测试 ECS（非生产）上演练，避免影响线上
□ 2. 从 OSS 随机选一份 30 天内的备份，下载到测试机
□ 3. 执行 restore-mysql.sh 恢复
□ 4. 验证关键表数据：
     mysql -e "SELECT COUNT(*) FROM admin_user, event, registration, point_record, ranking, banner"
     # 与备份时间点的生产数据对比
□ 5. 模拟登录 admin / 用户报名 / 查看排名等核心流程
□ 6. 演练记录归档（含耗时、问题、改进）
□ 7. 测试 ECS 上清理演练数据，避免长期占用
```

---

## 4. 限流调优

### 4.1 限流维度说明

后端通过 `RateLimitFilter`（Redis + Lua 令牌桶）实现，配置在 `application-prod.yml`：

| 配置项 | 含义 | 默认值（生产） |
|--------|------|---------------|
| `hey-pickler.rate-limit.login` | 登录接口（每 IP/分钟）| 60 |
| `hey-pickler.rate-limit.admin` | 已登录 admin 请求（每用户/分钟）| 120 |
| `hey-pickler.rate-limit.admin-anon` | 未登录 admin 请求（每 IP/分钟）| 30 |
| `hey-pickler.rate-limit.default` | 兜底（每 IP/分钟）| 60 |

### 4.2 调整方法

#### 方法 1：修改 application-prod.yml（需重新打包 jar）

```yaml
# hey-pickler-server/src/main/resources/application-prod.yml
hey-pickler:
  rate-limit:
    login: 100      # 改为 100
    admin: 200
    admin-anon: 50
    default: 100
```

重新 `mvn clean package -DskipTests` → scp 上传 → systemctl restart。

#### 方法 2：命令行覆盖（无需重新打包）

```bash
sudo vi /etc/systemd/system/hey-pickler.service
# 在 ExecStart 行追加：
#   --hey-pickler.rate-limit.login=100
#   --hey-pickler.rate-limit.admin=200
sudo systemctl daemon-reload
sudo systemctl restart hey-pickler
```

#### 方法 3：环境变量（最灵活）

```bash
sudo vi /etc/heypickler/heypickler.env
# Spring Boot relaxed binding 自动映射：
HEY_PICKLER_RATE_LIMIT_LOGIN=100
HEY_PICKLER_RATE_LIMIT_ADMIN=200
sudo systemctl restart hey-pickler
```

### 4.3 触发限流的客户端表现

| 客户端 | 限流响应 | 用户感知 |
|--------|---------|---------|
| Admin 后台 | HTTP 429 + `{"code":429,"message":"请求过于频繁"}` | 弹窗「请求过于频繁，请稍后再试」|
| 微信小程序 | 同上 | toast「操作太频繁了」|

#### 限流 key 命名规则（Redis）

```
heypickler:ratelimit:{dimension}:{identifier}:{minute}
  dimension: login / admin / admin-anon / default
  identifier: IP（匿名）/ userId（已认证）
  minute: YYYYMMDDHHmm

# 查看 Redis 实时 key
redis-cli --scan --pattern 'heypickler:ratelimit:*' | head -20
```

---

## 5. 常见故障响应

### 5.1 服务 5xx 飙升

**症状**：admin 后台 / 小程序 API 突然大量 500 错误。

**诊断步骤**：

```bash
# 1. 后端是否还活着
sudo systemctl status hey-pickler
# 如 inactive / failed：见 5.5 OOM kill 或直接 systemctl restart

# 2. 应用错误日志
sudo tail -100 /var/log/heypickler/app.err.log
# 关注：
#   - SQLException（数据库问题，见 5.2）
#   - RedisConnectionException（Redis 问题，见 5.3）
#   - NullPointerException（应用 bug，记录堆栈后联系开发）

# 3. nginx 错误
sudo tail -50 /var/log/nginx/heypickler-*-error.log
# 关注：
#   - upstream timed out（后端慢，检查 DB / Redis）
#   - connect() refused（后端挂了）

# 4. DB 慢查询
mysql -u heypickler -p hey_pickler -e "SHOW FULL PROCESSLIST"
sudo tail -100 /var/log/mysql/slow.log 2>/dev/null
```

**修复方向**：

- 后端进程挂了：`sudo systemctl restart hey-pickler`，看启动日志
- DB 慢查询：杀掉长事务 `KILL <id>`，定位慢 SQL，加索引
- Redis 阻塞：见 §5.3
- 应用 bug：联系开发提供堆栈

---

### 5.2 数据库连接耗尽

**症状**：后端日志 `HikariPool-1 - Connection is not available` 或 `Too many connections`。

**诊断**：

```bash
# 1. MySQL 连接数
mysql -u root -p -e "SHOW STATUS LIKE 'Threads_connected'; SHOW VARIABLES LIKE 'max_connections';"

# 2. 当前活跃连接
mysql -u root -p -e "SHOW FULL PROCESSLIST" | head -30

# 3. HikariCP 配置（默认 maximum-pool-size=10）
sudo grep -A 5 'hikari\|maximum-pool' /etc/systemd/system/hey-pickler.service
```

**修复**：

```bash
# 短期：杀掉长事务
mysql -u root -p -e "KILL <connection_id>"

# 中期：提升 maximum-pool-size
sudo vi /etc/heypickler/heypickler.env
# 追加：SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=30
sudo systemctl restart hey-pickler

# 长期：定位慢 SQL 加索引
mysql -u heypickler -p hey_pickler -e "SHOW STATUS LIKE 'Slow_queries';"
sudo vi /etc/mysql/mysql.conf.d/mysqld.cnf
# [mysqld]
# slow_query_log = 1
# long_query_time = 1
sudo systemctl restart mysql
```

---

### 5.3 Redis 故障

**症状**：限流失效、登录后立即掉登录、`RateLimitFilter` 报错。

**诊断**：

```bash
# 1. Redis 进程
sudo systemctl status redis-server

# 2. 网络连通
redis-cli ping
# 期望 PONG，如返回 (error) ERR AUTH... 则密码错

# 3. 内存使用
redis-cli info memory | head -10
# 关注 used_memory_human 和 used_memory_peak_human

# 4. 持久化状态
redis-cli info persistence | grep -E 'rdb_|aof_'

# 5. 后端连接
sudo grep -i 'redis\|lettuce' /var/log/heypickler/app.err.log | tail -20
```

**修复**：

```bash
# Redis 进程挂了
sudo systemctl start redis-server

# 内存满（OOM）
redis-cli info memory | grep used_memory_human
# 找出大 key
redis-cli --bigkeys
# 视情况清理（限流 key 会自动过期，不要乱删业务 key）

# 持久化失败（磁盘满）
df -h /var/lib/redis  # 见 §5.4

# 密码改了
sudo vi /etc/heypickler/heypickler.env
# 改：REDIS_PASSWORD=...
sudo systemctl restart hey-pickler
```

---

### 5.4 磁盘满

**症状**：写入失败、备份失败、日志无法落盘、`No space left on device`。

**诊断**：

```bash
df -h
# 找出 100% 满的分区

# 大目录排查
sudo du -h /var/log /var/lib/mysql /var/backups /opt/heypickler --max-depth=2 | sort -h | tail -20
```

**修复**：

```bash
# 1. 日志爆了
sudo ls -lhS /var/log/heypickler/
# 紧急清空（不删文件，保留 inode）
sudo truncate -s 0 /var/log/heypickler/app.log
# 检查 logrotate 是否在跑
sudo logrotate -d /etc/logrotate.d/heypickler

# 2. 备份目录爆了
sudo ls -lh /var/backups/heypickler/mysql/
# 本地 7 天滚动应由 backup-mysql.sh 自动清理
# 紧急手动清理 7 天前
sudo find /var/backups/heypickler/mysql -mtime +7 -delete

# 3. MySQL data 满了
# 不要直接删 .ibd 文件！检查 binlog 是否堆积
mysql -u root -p -e "SHOW BINARY LOGS;"
# 清理过期 binlog（保留最近 7 天）
mysql -u root -p -e "PURGE BINARY LOGS BEFORE DATE_SUB(NOW(), INTERVAL 7 DAY);"
# 长期方案：expire_logs_days = 7
sudo vi /etc/mysql/mysql.conf.d/mysqld.cnf
# [mysqld]
# expire_logs_days = 7
# binlog_expire_logs_seconds = 604800
sudo systemctl restart mysql

# 4. OSS 同步异常（OSS bucket 在 ECS 本地挂载？）
# OSS 不占 ECS 磁盘，但如用 ossfs 挂载需检查缓存
df -h | grep ossfs
```

---

### 5.5 OOM kill

**症状**：服务突然停止，systemd 日志 `Main process exited, code=killed, status=9/KILL`，`dmesg` 显示 `Out of memory: Killed process ... heypickler`。

**诊断**：

```bash
# 1. 确认 OOM
sudo dmesg -T | grep -i 'killed process\|out of memory' | tail -10

# 2. 是否有 heap dump
sudo ls -lh /var/log/heypickler/*.hprof 2>/dev/null

# 3. 当前 JVM 内存配置
ps -ef | grep '[h]ey-pickler-server' | head -1 | tr ' ' '\n' | grep -E '\-Xm'
```

**修复**：

```bash
# 短期：降低 -Xmx
sudo vi /etc/systemd/system/hey-pickler.service
# 改 -Xmx2g 为 -Xmx1g（如 ECS 是 4GB，留更多给 OS）
sudo systemctl daemon-reload
sudo systemctl restart hey-pickler

# 中期：升级 ECS 内存
# 阿里云控制台 → ECS → 实例 → 升降配

# 长期：分析 heap dump（如有）
# 下载到本地用 jvisualvm / Eclipse MAT 分析
sudo sz /var/log/heypickler/java_pid*.hprof  # 或 scp 拉走

# 添加 swap（如未配置）—— 仅作应急，不应作为长期方案
sudo fallocate -l 2G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

---

### 5.6 SSL 证书过期

**症状**：浏览器访问 admin 提示证书错误，小程序请求失败。

**诊断**：

```bash
# 1. 查证书过期时间
echo | openssl s_client -connect admin.heypickler.com:443 -servername admin.heypickler.com 2>/dev/null \
  | openssl x509 -noout -dates

# 2. 定时检查脚本（建议加 cron）
#!/usr/bin/env bash
# /usr/local/bin/check-ssl-expiry
for domain in admin.heypickler.com api.heypickler.com; do
  expiry=$(echo | openssl s_client -connect $domain:443 -servername $domain 2>/dev/null \
    | openssl x509 -noout -enddate | cut -d= -f2)
  days=$(( ($(date -d "$expiry" +%s) - $(date +%s)) / 86400 ))
  [[ $days -lt 30 ]] && echo "WARN: $domain expires in $days days"
done
```

**修复**：

```bash
# 1. 阿里云控制台 → 数字证书 → 续签（DV 免费证书可免费续）
# 2. 下载新的 Nginx 格式证书
# 3. 上传替换
sudo cp admin.heypickler.com.new.pem /etc/ssl/heypickler.pem
sudo cp admin.heypickler.com.new.key /etc/ssl/heypickler.key
sudo chmod 644 /etc/ssl/heypickler.pem
sudo chmod 600 /etc/ssl/heypickler.key
# 4. nginx reload（不需要重启后端）
sudo nginx -t && sudo systemctl reload nginx
```

#### 自动续期（可选）

如使用阿里云免费 DV 证书，目前不支持 ACME 自动续期。建议：

- 加 cron 提前 30 天告警（上面脚本）
- 或改用 Let's Encrypt + certbot（需 DNS API 配置）

---

### 5.7 升级回滚

详见 §2.5「升级失败回滚」段落。核心步骤：

1. 停服 `systemctl stop hey-pickler`
2. 数据库回滚（从备份恢复，§3.4）
3. 还原旧 jar
4. 修改 systemd unit jar 路径
5. 启服 + 验证

---

## 附录

### A. 关键文件位置速查

| 文件 | 路径 |
|------|------|
| systemd unit | `/etc/systemd/system/hey-pickler.service` |
| env 配置 | `/etc/heypickler/heypickler.env` |
| jar 包 | `/opt/heypickler/hey-pickler-server-1.0.0.jar` |
| 应用日志 | `/var/log/heypickler/app.log` 与 `app.err.log` |
| admin 静态资源 | `/var/www/hey-pickler-admin/dist/` |
| Nginx 配置 | `/etc/nginx/sites-enabled/heypickler.conf` |
| SSL 证书 | `/etc/ssl/heypickler.{pem,key}` |
| MySQL data | `/var/lib/mysql/` |
| Redis data | `/var/lib/redis/` |
| 备份本地 | `/var/backups/heypickler/mysql/` |
| logrotate | `/etc/logrotate.d/heypickler` |
| ossutil 配置 | `/home/heypickler/.ossutilconfig` |

### B. 相关文档

- `docs/DEPLOYMENT-REQUIREMENTS.md` —— 部署前硬件软件清单
- `docs/CREDENTIALS.md` —— 密钥管理（轮换、应急）
- `docs/DELIVERABLES.md` —— 交付物清单与验收
- `README.md` —— 项目总体说明
- `CLAUDE.md` —— 开发指南

### C. 紧急联系

| 场景 | 联系方 |
|------|--------|
| 应用 bug / 异常堆栈 | 开发团队（提供 `/var/log/heypickler/app.err.log` 片段）|
| 阿里云 ECS 宕机 | 阿里云工单 |
| 域名解析异常 | 域名服务商 |
| SSL 证书问题 | 阿里云数字证书服务 |
| 数据恢复求助 | 开发团队（提供 binlog 坐标）|

### D. Phase 2 待办（不在本变更范围）

- [ ] actuator 健康检查 + Prometheus metrics（变更 #2 可观测性）
- [ ] CI/CD pipeline（变更 #5）
- [ ] 核心业务单元测试覆盖（变更 #3）
- [ ] Docker 容器化（独立变更）
- [ ] K8s Helm chart（独立变更）
- [ ] API `/api/v1/` 版本前缀（变更 #5）
- [ ] Sentry 错误监控（变更 #2）
