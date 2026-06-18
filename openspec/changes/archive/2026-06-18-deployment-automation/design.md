# Design: deployment-automation

## Context

阿里云 ECS 部署场景，目标系统 Ubuntu 22.04+ / Alibaba Cloud Linux 3，备份走 OSS。本设计文档锁定 5 个新增工件的具体内容、关键决策的实现细节、复用现有模式，以及可测试性策略。

## 工件清单与文件树

```
deploy/
├── systemd/
│   └── hey-pickler.service          # systemd unit
├── nginx/
│   └── heypickler.conf               # Nginx 双 vhost 参考配置
└── scripts/
    ├── install.sh                    # 首次部署最小集
    ├── backup-mysql.sh               # MySQL 备份脚本（cron 调用）
    └── restore-mysql.sh              # MySQL 恢复脚本（runbook 引用）
docs/
├── RUNBOOK.md                        # 运维手册（5 章节）
└── DELIVERABLES.md                   # 交付物验收清单
```

## 实现细节

### 1. `deploy/systemd/hey-pickler.service`

```ini
[Unit]
Description=Hey Pickler Server
Documentation=https://github.com/tlbfpx/pickler/blob/master/docs/RUNBOOK.md
After=network.target mysql.service redis.service
Wants=mysql.service redis.service

[Service]
Type=simple
User=heypickler
Group=heypickler
WorkingDirectory=/opt/heypickler
EnvironmentFile=/etc/heypickler/heypickler.env
ExecStart=/usr/bin/java \
  -Xms512m -Xmx2g \
  -XX:+UseG1GC \
  -XX:+HeapDumpOnOutOfMemoryError \
  -XX:HeapDumpPath=/var/log/heypickler/ \
  -Dfile.encoding=UTF-8 \
  -jar /opt/heypickler/hey-pickler-server-1.0.0.jar
Restart=on-failure
RestartSec=10
StartLimitInterval=60
StartLimitBurst=3
StandardOutput=append:/var/log/heypickler/app.log
StandardError=append:/var/log/heypickler/app.err.log
LimitNOFILE=65536

[Install]
WantedBy=multi-user.target
```

关键设计：

- **`EnvironmentFile`**：所有 env var（JWT_SECRET / AES_KEY / DB_* / INITIAL_ADMIN_* / PROD_GUARD 等）放 `/etc/heypickler/heypickler.env`，文件权限 `0600` `heypickler:heypickler`。这与 secure-credentials-parameterization 变更的 env var 架构一致。
- **`Restart=on-failure` + `StartLimitBurst=3`**：进程崩了自动重启，但 60s 内连续崩 3 次停止重启（防止 brain-dead loop）。需要运维介入。
- **`HeapDumpOnOutOfMemoryError`**：OOM 时自动 dump 到 `/var/log/heypickler/`，便于事后分析。
- **JVM 参数**：`-Xms512m -Xmx2g -XX:+UseG1GC` —— 配合 4GB ECS（DEPLOYMENT-REQUIREMENTS 推荐配置），给 MySQL/Redis 留 ~2GB。
- **`LimitNOFILE=65536`**：HikariCP + Tomcat NIO 默认 1024 不够。
- **`StandardOutput=append:`**：直接落盘，不用 journalctl（运维更熟悉 tail -f）。

### 2. `deploy/nginx/heypickler.conf`

两个 server block：admin（HTTPS + SPA + API 代理）+ api（HTTPS + API 代理给小程序）。共用 SSL 证书。

```nginx
# 通用 ssl / 安全 header / gzip / log_format
# 在 http {} 块引入本文件后，nginx.conf 主配置需提供 ssl_certificate 路径

# Admin 后台
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
    ssl_protocols       TLSv1.2 TLSv1.3;
    ssl_ciphers         HIGH:!aNULL:!MD5;
    ssl_prefer_server_ciphers on;
    ssl_session_cache   shared:SSL:10m;
    ssl_session_timeout 1d;

    # 安全 header
    add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
    add_header X-Frame-Options "SAMEORIGIN" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header Referrer-Policy "strict-origin-when-cross-origin" always;

    client_max_body_size 10m;
    proxy_read_timeout   60s;
    proxy_send_timeout   60s;

    gzip on;
    gzip_types text/plain text/css application/json application/javascript;
    gzip_min_length 1024;

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

    access_log /var/log/nginx/heypickler-admin-access.log;
    error_log  /var/log/nginx/heypickler-admin-error.log;
}

# 小程序 API
server {
    listen 443 ssl http2;
    server_name api.heypickler.com;

    ssl_certificate     /etc/ssl/heypickler.pem;
    ssl_certificate_key /etc/ssl/heypickler.key;
    ssl_protocols       TLSv1.2 TLSv1.3;
    # (其他 ssl 与安全 header 同上，用 include 复用)

    client_max_body_size 10m;

    location /api/app/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }

    access_log /var/log/nginx/heypickler-api-access.log;
    error_log  /var/log/nginx/heypickler-api-error.log;
}
```

关键设计：

- **`ssl_protocols TLSv1.2 TLSv1.3`**：禁用 TLSv1.0/1.1（POODLE / BEAST）
- **`client_max_body_size 10m`**：banner / event 图片上传需 10MB 余量
- **HSTS**：强制浏览器 HTTPS，max-age 一年
- **`add_header ... always`**：确保 4xx/5xx 响应也带安全 header
- **access_log 分文件**：admin 和 api 流量分开，便于按子系统定位异常
- **不重复 SSL 配置**：runbook 提示用 `include /etc/nginx/snippets/heypickler-ssl.conf;` 提取通用段

### 3. `deploy/scripts/install.sh`

最小化首次部署脚本，**幂等**（可重跑）：

```bash
#!/usr/bin/env bash
# 首次部署 hey-pickler 到阿里云 ECS
# 用法: sudo bash install.sh
# 前置: JDK 17+ / MySQL 8 / Redis 6 / Nginx 已安装
set -euo pipefail

# 1. 检查前置条件
check_prereqs() {
  for cmd in java mysql redis-cli nginx; do
    command -v $cmd >/dev/null 2>&1 || { echo "ERROR: $cmd not found"; exit 1; }
  done
  java -version 2>&1 | grep -q '"17"\|"18"\|"19"\|"20"\|"21"' || {
    echo "ERROR: JDK 17+ required"; exit 1; }
}

# 2. 创建系统用户与目录
setup_user_and_dirs() {
  id -u heypickler &>/dev/null || useradd --system --no-create-home --shell /usr/sbin/nologin heypickler
  for dir in /opt/heypickler /var/log/heypickler /etc/heypickler; do
    mkdir -p "$dir"
    chown heypickler:heypickler "$dir"
  done
  chmod 0750 /etc/heypickler
}

# 3. 拷贝 systemd unit
install_unit() {
  cp deploy/systemd/hey-pickler.service /etc/systemd/system/
  systemctl daemon-reload
  systemctl enable hey-pickler
}

# 4. 生成 env 模板（如果不存在）
write_env_template() {
  if [[ ! -f /etc/heypickler/heypickler.env ]]; then
    cat > /etc/heypickler/heypickler.env <<'TEMPLATE'
# 编辑此文件填入真实值后，systemctl start hey-pickler
JWT_SECRET=
AES_KEY=
INITIAL_ADMIN_USERNAME=admin
INITIAL_ADMIN_PASSWORD=
PROD_GUARD=true
SPRING_PROFILES_ACTIVE=prod
DB_URL=jdbc:mysql://localhost:3306/hey_pickler?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai
DB_USERNAME=
DB_PASSWORD=
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=
WX_APPID=
WX_SECRET=
CORS_ADMIN_ORIGINS=https://admin.heypickler.com
TEMPLATE
    chmod 0600 /etc/heypickler/heypickler.env
    chown heypickler:heypickler /etc/heypickler/heypickler.env
    echo "INFO: /etc/heypickler/heypickler.env created. Edit it, then run:"
    echo "  sudo systemctl start hey-pickler"
  fi
}

# 5. logrotate 配置
install_logrotate() {
  cat > /etc/logrotate.d/heypickler <<'EOF'
/var/log/heypickler/*.log {
    daily
    rotate 30
    compress
    missingok
    notifempty
    copytruncate
}
EOF
}

# Main
check_prereqs
setup_user_and_dirs
install_unit
write_env_template
install_logrotate

echo ""
echo "=== 部署就绪 ==="
echo "1. 拷贝 jar:    sudo cp hey-pickler-server-1.0.0.jar /opt/heypickler/"
echo "2. 编辑 env:    sudo vi /etc/heypickler/heypickler.env"
echo "3. 启动服务:    sudo systemctl start hey-pickler"
echo "4. 验证健康:    curl http://localhost:8080/actuator/health"
echo ""
echo "详见 docs/RUNBOOK.md"
```

设计原则：

- **不自动启动**：env 文件需人工填，install.sh 只生成模板
- **幂等**：重跑不会覆盖已有 env 文件、不会重建用户
- **前置检查**：缺少 JDK / MySQL / Redis 直接退出
- **logrotate 一并安装**：避免运维忘记导致磁盘爆

### 4. `deploy/scripts/backup-mysql.sh`

```bash
#!/usr/bin/env bash
# MySQL 全量备份 → gzip → OSS
# cron: 0 2 * * * heypickler /opt/heypickler/scripts/backup-mysql.sh
set -euo pipefail

BACKUP_DIR=/var/backups/heypickler/mysql
OSS_BUCKET=${OSS_BUCKET:-oss://heypickler-backup/mysql}
RETAIN_LOCAL_DAYS=7
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
DUMP_FILE=$BACKUP_DIR/hey_pickler-$TIMESTAMP.sql.gz

# 引用与 systemd unit 共享的 env
source /etc/heypickler/heypickler.env

mkdir -p "$BACKUP_DIR"

# 1. mysqldump 全量（含 binlog 位置）
mysqldump \
  --single-transaction \
  --master-data=2 \
  --routines --triggers \
  -u"$DB_USERNAME" -p"$DB_PASSWORD" \
  hey_pickler | gzip > "$DUMP_FILE"

# 2. 上传 OSS（如果 ossutil 配置好）
if command -v ossutil >/dev/null 2>&1; then
  ossutil cp "$DUMP_FILE" "$OSS_BUCKET/$(basename "$DUMP_FILE")" \
    --acl private \
    --meta "backup-date=$TIMESTAMP"
fi

# 3. 清理本地老备份
find "$BACKUP_DIR" -name "*.sql.gz" -mtime +$RETAIN_LOCAL_DAYS -delete

echo "[$(date)] backup OK: $DUMP_FILE"
```

设计要点：

- **`--single-transaction`**：InnoDB 一致性快照，不锁表
- **`--master-data=2`**：记录 binlog 位置，支持 PITR
- **本地 7 天 + OSS 30 天滚动**：双重保护
- **OSS bucket name 通过 env 注入**：客户可改
- **不依赖 ossutil 必须存在**：没装就只做本地备份（降级容错）

### 5. `docs/RUNBOOK.md`

5 章节，每章节末尾加「故障时联系谁」+「相关文档」。

```
# Hey Pickler 运维手册

## 1. 首次部署
   - 1.1 前置条件
   - 1.2 一键安装
   - 1.3 配置环境变量
   - 1.4 启动与验证
   - 1.5 Nginx 接入

## 2. 日常运营
   - 2.1 服务管理（start/stop/restart/status）
   - 2.2 日志查看（app.log / app.err.log / nginx-access）
   - 2.3 健康检查（actuator/health）
   - 2.4 配置变更与重启
   - 2.5 升级流程

## 3. 备份与恢复
   - 3.1 OSS bucket 与 RAM 子账号准备
   - 3.2 自动备份配置（cron）
   - 3.3 手动备份
   - 3.4 恢复流程（全量 + PITR）
   - 3.5 恢复演练（季度执行）

## 4. 限流调优
   - 4.1 限流维度说明
   - 4.2 调整方法（application.yml + systemctl restart）
   - 4.3 触发限流后的客户端表现

## 5. 常见故障响应
   - 5.1 服务 5xx 飙升
   - 5.2 数据库连接耗尽
   - 5.3 Redis 故障
   - 5.4 磁盘满
   - 5.5 OOM kill
   - 5.6 SSL 证书过期
   - 5.7 升级回滚
```

### 6. `docs/DELIVERABLES.md`

6 大类，每类列出工件 + 验收标准（「客户运营能完成的动作」描述）：

```markdown
# 交付物清单

| 类别 | 工件 | 验收标准 |
|------|------|---------|
| 1. 源代码 | git tag v1.0.0 | 客户运营可从 tag 检出，按 README 重建二进制 |
| 2. 二进制 | hey-pickler-server-1.0.0.jar / admin dist.zip | 在 JDK 17+ 环境 `java -jar` 启动成功 |
| 3. 部署工件 | deploy/ 目录 | install.sh 跑通后 systemctl status hey-pickler active |
| 4. 文档 | README + DEPLOYMENT + CREDENTIALS + RUNBOOK + DELIVERABLES | 客户运营无开发协助读懂并能操作 |
| 5. 测试报告 | unit + integration + e2e 通过率 | 详见各测试套件输出 |
| 6. 数据库 | db/migration/V1-V8 | 全新 schema 可重建，已有 schema 可升级 |
```

每类附「验收动作」段落（详细 step-by-step）。

## 复用现有模式

- **env var 架构**：复用 secure-credentials-parameterization 变更定义的 env var 名（JWT_SECRET / AES_KEY / INITIAL_ADMIN_* / PROD_GUARD / DB_* 等），systemd `EnvironmentFile` 与之天然对齐
- **OpenSpec change 结构**：复刻 secure-credentials-parameterization 的 proposal/design/specs/tasks 四件套结构
- **commit message 风格**：`feat(deploy):` / `docs:` / `chore(openspec):` 前缀，与现有 git log 一致
- **`deploy/scripts/install.sh` 风格**：参考 `scripts/cleanup-e2e-data.sql` 的「self-contained shell + 顶部注释说明用法」风格
- **运维文档语言**：中文为主（与 README / CREDENTIALS / DEPLOYMENT-REQUIREMENTS 一致），命令与配置英文

## 测试策略

| 测试 | 类型 | 覆盖 |
|------|------|------|
| install.sh dry-run | shellcheck + `bash -n` | 语法 + 静态检查 |
| install.sh 幂等性 | 本地 systemd-nspawn 容器（或客户 ECS 测试机） | 跑 2 次不出错 |
| systemd unit syntax | `systemd-analyze verify` | unit 文件语法 |
| nginx config syntax | `nginx -t` | 配置语法 |
| backup-mysql.sh dry-run | shellcheck + 测试库 | 备份能跑出非空文件 |
| RUNBOOK walkthrough | 人工对照执行 | 客户运营 30 分钟内完成首次部署 |

不写自动化集成测试——这些工件是配置/脚本，传统 shellcheck + 静态检查 + 文档化的人工验收足够。如果客户要求自动化部署测试（如 ansible playbook test），是 Phase 2 范围。

## 风险与缓解

- **客户 ECS 不在已测试发行版**：Ubuntu 22.04+ / Alibaba Cloud Linux 3 主流，runbook 第 1 章「前置条件」明确写出
- **ossutil 未配置导致备份链路半通**：脚本降级容错（仅本地备份），runbook 第 3.1 节给 ossutil 安装与配置 step-by-step
- **systemd unit 在非 systemd 系统（如 Alpine）失效**：明确目标发行版，不支持的不在范围
- **nginx 配置客户既有 nginx 版本 syntax 不兼容**：要求 nginx 1.20+，runbook 注明
