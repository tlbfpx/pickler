#!/usr/bin/env bash
# =============================================================================
# Hey Pickler MySQL 备份脚本（阿里云 OSS 后端）
#
# 用法：
#   手动：sudo -u heypickler bash deploy/scripts/backup-mysql.sh
#   自动：crontab -u heypickler -e
#         0 2 * * * /opt/heypickler/scripts/backup-mysql.sh >> /var/log/heypickler/backup.log 2>&1
#
# 流程：
#   1. 从 /etc/heypickler/heypickler.env 加载 DB 凭据
#   2. mysqldump --single-transaction --master-data=2 全量 + gzip
#   3. ossutil 上传到 OSS（如已配置）— 否则降级仅本地
#   4. 清理本地 7 天以上的旧备份
#
# 失败处理：
#   - mysqldump 失败 → exit 1（cron 会通过 mail 告警）
#   - ossutil 失败 → warn 但 exit 0（不阻塞 cron，本地有备份）
# =============================================================================
set -euo pipefail

# -----------------------------------------------------------------------------
# 配置（可通过 env 覆盖）
# -----------------------------------------------------------------------------
BACKUP_DIR="${BACKUP_DIR:-/var/backups/heypickler/mysql}"
RETAIN_LOCAL_DAYS="${RETAIN_LOCAL_DAYS:-7}"
OSS_BUCKET="${OSS_BUCKET:-}"  # 客户在 /etc/heypickler/heypickler.env 设置，如 oss://heypickler-backup/mysql
TIMESTAMP="$(date +%Y%m%d-%H%M%S)"
DUMP_FILE="$BACKUP_DIR/hey_pickler-$TIMESTAMP.sql.gz"

# 加载应用 env（DB_USERNAME / DB_PASSWORD / OSS_BUCKET 等）
ENV_FILE="${ENV_FILE:-/etc/heypickler/heypickler.env}"
[[ -f "$ENV_FILE" ]] || { echo "[FATAL] $ENV_FILE not found"; exit 1; }
set -a
# shellcheck source=/dev/null
. "$ENV_FILE"
set +a

# -----------------------------------------------------------------------------
# Step 1: 校验
# -----------------------------------------------------------------------------
[[ -n "${DB_USERNAME:-}" ]] || { echo "[FATAL] DB_USERNAME empty in $ENV_FILE"; exit 1; }
[[ -n "${DB_PASSWORD:-}" ]] || { echo "[FATAL] DB_PASSWORD empty in $ENV_FILE"; exit 1; }

mkdir -p "$BACKUP_DIR"

# -----------------------------------------------------------------------------
# Step 2: mysqldump
# -----------------------------------------------------------------------------
echo "[$(date -Iseconds)] starting mysqldump → $DUMP_FILE"

mysqldump \
  --single-transaction \
  --master-data=2 \
  --routines \
  --triggers \
  --routines \
  --default-character-set=utf8mb4 \
  -u"$DB_USERNAME" \
  -p"$DB_PASSWORD" \
  hey_pickler 2> >(grep -v "Using a password on the command line") \
  | gzip > "$DUMP_FILE"

# 校验非空（gzip 后至少几 KB）
DUMP_SIZE=$(stat -c%s "$DUMP_FILE" 2>/dev/null || stat -f%z "$DUMP_FILE")
[[ "$DUMP_SIZE" -gt 1024 ]] || { echo "[FATAL] dump too small: $DUMP_SIZE bytes"; exit 1; }

echo "[$(date -Iseconds)] mysqldump OK ($DUMP_SIZE bytes)"

# -----------------------------------------------------------------------------
# Step 3: OSS 上传（可选）
# -----------------------------------------------------------------------------
if [[ -n "$OSS_BUCKET" ]]; then
  if command -v ossutil >/dev/null 2>&1; then
    echo "[$(date -Iseconds)] uploading to OSS bucket=$OSS_BUCKET"
    if ossutil cp "$DUMP_FILE" "$OSS_BUCKET/$(basename "$DUMP_FILE")" \
         --acl private \
         --meta "backup-date=$TIMESTAMP" >/dev/null 2>&1; then
      echo "[$(date -Iseconds)] OSS upload OK"
    else
      echo "[WARN] ossutil upload failed, local backup still complete: $DUMP_FILE"
    fi
  else
    echo "[WARN] OSS_BUCKET set but ossutil not installed; local backup only"
    echo "       install: see docs/RUNBOOK.md §3.1"
  fi
else
  echo "[INFO] OSS_BUCKET not set; local backup only"
fi

# -----------------------------------------------------------------------------
# Step 4: 清理本地旧备份
# -----------------------------------------------------------------------------
find "$BACKUP_DIR" -name "hey_pickler-*.sql.gz" -mtime +"$RETAIN_LOCAL_DAYS" -print -delete \
  | sed 's/^/[INFO] cleaned up: /' || true

echo "[$(date -Iseconds)] backup complete"
