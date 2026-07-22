#!/usr/bin/env bash
# Hey Pickler — 本机开发一键启动编排
#
# 起依赖（docker compose: MySQL + Redis）→ 等 MySQL 健康 → 打印 server / admin
# 启动命令（带预填的 INITIAL_ADMIN_PASSWORD，避免首启 exit(1)）。
#
# Usage:
#   bash scripts/dev-up.sh           # 起依赖 + 打印后续命令（server 本地跑）
#   bash scripts/dev-up.sh --full    # 额外起容器化 server（docker compose --profile full）
#
# 前置：Docker Desktop / Docker Engine 已装并运行（`docker --version` 验证）。
# 不想用 Docker？手动装 MySQL 8 + Redis 6，然后跳过本脚本，直接看 README 第七节。

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

INITIAL_ADMIN_PASSWORD="${INITIAL_ADMIN_PASSWORD:-DevAdmin123!}"
INITIAL_ADMIN_USERNAME="${INITIAL_ADMIN_USERNAME:-admin}"
START_SERVER=0
[[ "${1:-}" == "--full" ]] && START_SERVER=1

# ---- helpers ----
log()  { printf '\033[0;36m==>\033[0m %s\n' "$*"; }
ok()   { printf '\033[0;32m  ✓\033[0m %s\n' "$*"; }
warn() { printf '\033[0;33m  !\033[0m %s\n' "$*"; }

# ---- 1. docker 可用性 ----
if ! command -v docker >/dev/null 2>&1; then
  warn "docker 未安装。本机开发需要 Docker 起依赖；或手动装 MySQL 8 + Redis 6。"
  warn "安装 Docker Desktop: https://www.docker.com/products/docker-desktop"
  exit 1
fi
if ! docker info >/dev/null 2>&1; then
  warn "docker daemon 未运行，请先启动 Docker Desktop。"
  exit 1
fi
ok "docker 可用"

# ---- 2. 起依赖 ----
# 检测本机是否已有 MySQL/Redis（端口被占），若是则复用本机、跳过 compose（避免端口冲突）
mysql_in_use=0; redis_in_use=0
lsof -nP -iTCP:3306 -sTCP:LISTEN >/dev/null 2>&1 && mysql_in_use=1
lsof -nP -iTCP:6379 -sTCP:LISTEN >/dev/null 2>&1 && redis_in_use=1

if [[ $mysql_in_use -eq 1 || $redis_in_use -eq 1 ]]; then
  log "检测到本机已有依赖运行（MySQL:3306=$([ $mysql_in_use -eq 1 ] && echo yes || echo no)  Redis:6379=$([ $redis_in_use -eq 1 ] && echo yes || echo no)）"
  warn "复用本机 MySQL/Redis，跳过 docker compose（避免端口冲突）"
  COMPOSE_USED=0
else
  log "启动依赖（MySQL + Redis）via docker compose"
  if [[ "$START_SERVER" -eq 1 ]]; then
    docker compose --profile full up -d --build
  else
    docker compose up -d
  fi
  ok "依赖已启动（docker compose）"
  COMPOSE_USED=1
fi

# ---- 3. 等 MySQL 健康 / 测本机可达 ----
if [[ "${COMPOSE_USED:-0}" -eq 1 ]]; then
  log "等待 MySQL 就绪（最多 60s）"
  for i in $(seq 1 30); do
    mysql_cid=$(docker compose ps -q mysql 2>/dev/null)
    status=$(docker inspect --format '{{.State.Health.Status}}' "$mysql_cid" 2>/dev/null || echo "none")
    if [[ "$status" == "healthy" ]]; then
      ok "MySQL 健康"
      break
    fi
    sleep 2
    if [[ $i -eq 30 ]]; then
      warn "MySQL 60s 内未健康，用 'docker compose logs mysql' 排查"
      exit 1
    fi
  done
else
  log "检测本机 MySQL 可达性（root/root）"
  if mysql -uroot -proot -e "SELECT 1" >/dev/null 2>&1; then
    ok "本机 MySQL 可达"
  else
    warn "本机 MySQL root/root 连不上 —— 检查密码或权限（dev 默认 DB_PASSWORD=root）"
  fi
fi

echo
log "依赖就绪"
docker compose ps

# ---- 4. 后续步骤 ----
if [[ "$START_SERVER" -eq 1 ]]; then
  echo
  log "server 已随 --full 启动（容器 hey-pickler-server）"
  echo "  日志:    docker logs -f hey-pickler-server"
  echo "  Swagger: http://localhost:8080/doc.html"
else
  echo
  log "下一步：在两个终端分别启动 server 与 admin"
  echo
  printf '\033[0;36m[终端 1 — 后端]\033[0m\n'
  echo "  cd hey-pickler-server"
  echo "  INITIAL_ADMIN_PASSWORD='$INITIAL_ADMIN_PASSWORD' mvn spring-boot:run"
  echo "  # Swagger: http://localhost:8080/doc.html"
  echo
  printf '\033[0;36m[终端 2 — 管理后台]\033[0m\n'
  echo "  cd hey-pickler-admin"
  echo "  npm install   # 首次"
  echo "  npm run dev   # http://localhost:5173"
  echo
  printf '\033[0;36m[微信小程序]\033[0m\n'
  echo "  微信开发者工具导入 hey-pickler-wxapp/（AppID 填 touristappid）"
  echo "  开发工具自动用 localhost:8080（app.js resolveBaseUrl 按 envVersion 切换）"
fi

echo
log "admin 登录：$INITIAL_ADMIN_USERNAME / $INITIAL_ADMIN_PASSWORD"
echo
log "停止依赖：docker compose down    （清数据：docker compose down -v）"
