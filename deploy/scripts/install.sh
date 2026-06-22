#!/usr/bin/env bash
# =============================================================================
# Hey Pickler 首次部署脚本（阿里云 ECS，systemd 路线）
#
# 用法：sudo bash deploy/scripts/install.sh
#
# 前置条件：
#   - 操作系统：Ubuntu 22.04+ / Alibaba Cloud Linux 3
#   - 已安装：JDK 17+ / MySQL 8+ / Redis 6+ / Nginx 1.20+
#   - 当前用户有 sudo 权限
#   - 工作目录为项目根（含 deploy/ 和 docs/）
#
# 本脚本做：
#   1. 创建 heypickler 系统用户与目录
#   2. 拷贝 systemd unit + 启用
#   3. 生成 env 文件模板（如不存在）
#   4. 安装 logrotate 配置
#   5. 打印下一步指引
#
# 本脚本不做：
#   - 不拷贝 jar 包（运维自行 scp）
#   - 不编辑 env 文件内容（运维自行填）
#   - 不 systemctl start（需 env 填完才启动）
#   - 不配置 Nginx / SSL / 防火墙（见 RUNBOOK §1.5）
#
# 幂等：重跑不会覆盖 env、不会重建用户、不会重复 enable unit
# =============================================================================
set -euo pipefail

# 颜色输出
red()    { printf '\033[0;31m%s\033[0m\n' "$*"; }
green()  { printf '\033[0;32m%s\033[0m\n' "$*"; }
yellow() { printf '\033[0;33m%s\033[0m\n' "$*"; }
info()   { printf '[INFO] %s\n' "$*"; }
warn()   { printf '[WARN] %s\n' "$*"; }
fatal()  { red "[FATAL] $*"; exit 1; }

# 必须 root
[[ $EUID -eq 0 ]] || fatal "must run as root (use sudo)"

# 工作目录校验（必须在项目根）
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
[[ -f "$PROJECT_ROOT/deploy/systemd/hey-pickler.service" ]] || \
  fatal "deploy/systemd/hey-pickler.service not found under $PROJECT_ROOT"

# -----------------------------------------------------------------------------
# Step 1: 前置条件检查
# -----------------------------------------------------------------------------
check_prereqs() {
  info "Step 1/5: 检查前置条件"

  # 操作系统
  if [[ -f /etc/os-release ]]; then
    . /etc/os-release
    info "  OS: $PRETTY_NAME"
  fi

  # 必需命令
  local missing=()
  for cmd in java mysql redis-cli nginx systemctl useradd groupadd; do
    if ! command -v "$cmd" >/dev/null 2>&1; then
      missing+=("$cmd")
    fi
  done
  [[ ${#missing[@]} -eq 0 ]] || fatal "missing commands: ${missing[*]}"

  # JDK 版本（17+）
  local java_version
  java_version=$(java -version 2>&1 | awk -F\" '/version/ {print $2; exit}')
  local java_major=${java_version%%.*}
  [[ "$java_major" -ge 17 ]] || \
    fatal "JDK 17+ required, got $java_version (run: sdk use java 17.0.9-tem || apt install openjdk-17-jdk)"

  # systemd
  [[ -d /etc/systemd/system ]] || fatal "systemd not available (this script targets systemd-based Linux)"

  info "  ✓ all prereqs satisfied"
}

# -----------------------------------------------------------------------------
# Step 2: 创建用户与目录
# -----------------------------------------------------------------------------
setup_user_and_dirs() {
  info "Step 2/5: 创建 heypickler 用户与目录"

  # 用户
  if id -u heypickler &>/dev/null; then
    info "  user heypickler already exists, skipping creation"
  else
    useradd --system --no-create-home --shell /usr/sbin/nologin heypickler
    info "  ✓ created user heypickler"
  fi

  # 目录
  install -d -o heypickler -g heypickler -m 0755 /opt/heypickler
  install -d -o heypickler -g heypickler -m 0755 /var/log/heypickler
  install -d -o heypickler -g heypickler -m 0750 /etc/heypickler
  install -d -o heypickler -g heypickler -m 0755 /var/backups/heypickler/mysql

  info "  ✓ /opt/heypickler /var/log/heypickler /etc/heypickler ready"
}

# -----------------------------------------------------------------------------
# Step 3: 安装 systemd unit
# -----------------------------------------------------------------------------
install_unit() {
  info "Step 3/5: 安装 systemd unit"

  install -m 0644 "$PROJECT_ROOT/deploy/systemd/hey-pickler.service" \
                  /etc/systemd/system/hey-pickler.service

  systemctl daemon-reload
  systemctl enable hey-pickler >/dev/null 2>&1

  info "  ✓ unit installed and enabled (will not start until env configured)"
}

# -----------------------------------------------------------------------------
# Step 4: 生成 env 模板（幂等：已存在则跳过）
# -----------------------------------------------------------------------------
write_env_template() {
  info "Step 4/5: 生成 env 文件"

  local env_file=/etc/heypickler/heypickler.env

  if [[ -f $env_file ]]; then
    warn "  $env_file already exists, NOT overwriting"
    return
  fi

  cat > "$env_file" <<'TEMPLATE'
# =============================================================================
# Hey Pickler 环境变量
# 编辑此文件填入真实值后：sudo systemctl start hey-pickler
# 字段含义与生成命令详见 docs/CREDENTIALS.md
# =============================================================================

# 必填：JWT 签名密钥（≥32 字符；生成：openssl rand -base64 48）
JWT_SECRET=

# 必填：AES 数据加密密钥（精确 16/24/32 字节；生成：openssl rand -base64 32）
AES_KEY=

# 必填（首次启动，admin_user 表为空时）：初始管理员账号
INITIAL_ADMIN_USERNAME=admin
INITIAL_ADMIN_PASSWORD=

# 推荐：生产环境深度防御
PROD_GUARD=true
SPRING_PROFILES_ACTIVE=prod

# 必填：MySQL 连接
DB_URL=jdbc:mysql://localhost:3306/hey_pickler?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai
DB_USERNAME=
DB_PASSWORD=

# 必填：Redis 连接（无密码留空）
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=

# 必填：微信小程序凭据（开发模式可填占位符）
WX_APPID=
WX_SECRET=

# 必填：admin 后台 CORS 域名（按实际域名改）
CORS_ADMIN_ORIGINS=https://admin.heypickler.com
# 小程序原生请求不走 CORS，留空
CORS_APP_ORIGINS=
TEMPLATE

  chown heypickler:heypickler "$env_file"
  chmod 0600 "$env_file"
  info "  ✓ $env_file created (mode 0600, owner heypickler:heypickler)"
}

# -----------------------------------------------------------------------------
# Step 5: 安装 logrotate
# -----------------------------------------------------------------------------
install_logrotate() {
  info "Step 5/5: 安装 logrotate"

  install -m 0644 "$PROJECT_ROOT/deploy/logrotate/heypickler" \
                  /etc/logrotate.d/heypickler

  info "  ✓ /etc/logrotate.d/heypickler installed"
}

# -----------------------------------------------------------------------------
# Main
# -----------------------------------------------------------------------------
main() {
  green ""
  green "============================================================"
  green "  Hey Pickler 首次部署脚本"
  green "============================================================"
  echo ""

  check_prereqs
  setup_user_and_dirs
  install_unit
  write_env_template
  install_logrotate

  echo ""
  green "============================================================"
  green "  ✓ 部署工件就绪"
  green "============================================================"
  echo ""
  echo "下一步操作（详见 docs/RUNBOOK.md §1.3-1.5）："
  echo ""
  echo "  1. 上传 jar 包："
  echo "     scp hey-pickler-server-1.0.0.jar root@<ecs>:/opt/heypickler/"
  echo "     chown heypickler:heypickler /opt/heypickler/hey-pickler-server-1.0.0.jar"
  echo ""
  echo "  2. 编辑 env 文件："
  echo "     sudo vi /etc/heypickler/heypickler.env"
  echo ""
  echo "  3. 启动服务："
  echo "     sudo systemctl start hey-pickler"
  echo "     sudo systemctl status hey-pickler"
  echo ""
  echo "  4. 验证健康（变更 #2 actuator 接入前用 API 探测）："
  echo "     curl -sf http://localhost:8080/api/admin/auth/login -I | head -1"
  echo ""
  echo "  5. 配置 Nginx + SSL（详见 RUNBOOK §1.5）："
  echo "     sudo cp deploy/nginx/heypickler.conf /etc/nginx/sites-available/"
  echo "     sudo ln -s /etc/nginx/sites-available/heypickler.conf /etc/nginx/sites-enabled/"
  echo "     sudo nginx -t && sudo systemctl reload nginx"
  echo ""
}

main "$@"
