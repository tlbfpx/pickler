#!/usr/bin/env bash
# =============================================================================
# Hey Pickler MySQL 恢复脚本
#
# 用法：
#   从本地备份恢复：sudo bash deploy/scripts/restore-mysql.sh /var/backups/heypickler/mysql/hey_pickler-20260101-020000.sql.gz
#   从 OSS 恢复：    sudo bash deploy/scripts/restore-mysql.sh oss://heypickler-backup/mysql/hey_pickler-20260101-020000.sql.gz
#
# 流程：
#   1. 校验备份文件存在（本地或 OSS）
#   2. 提取 dump 头部的 binlog 坐标（CHANGE MASTER TO MASTER_LOG_FILE/POS）
#   3. 提示目标 DB 是否清空（默认不清空，需人工 drop & create）
#   4. gunzip + mysql restore
#   5. 打印 binlog 坐标供 PITR
#
# 警告：本脚本不自动 DROP DATABASE，避免误操作。请先在 RUNBOOK §3.4
#       演练后再生产使用。
# =============================================================================
set -euo pipefail

# -----------------------------------------------------------------------------
# 参数校验
# -----------------------------------------------------------------------------
[[ $# -eq 1 ]] || { echo "Usage: $0 <local-file.sql.gz | oss://...>"; exit 1; }
SOURCE="$1"

ENV_FILE="${ENV_FILE:-/etc/heypickler/heypickler.env}"
[[ -f "$ENV_FILE" ]] || { echo "[FATAL] $ENV_FILE not found"; exit 1; }
set -a
# shellcheck source=/dev/null
. "$ENV_FILE"
set +a

[[ -n "${DB_USERNAME:-}" ]] || { echo "[FATAL] DB_USERNAME empty"; exit 1; }
[[ -n "${DB_PASSWORD:-}" ]] || { echo "[FATAL] DB_PASSWORD empty"; exit 1; }

# -----------------------------------------------------------------------------
# 准备临时 SQL 文件
# -----------------------------------------------------------------------------
TMP_SQL="$(mktemp /tmp/heypickler-restore-XXXXXX.sql)"
trap 'rm -f "$TMP_SQL"' EXIT

echo "[$(date -Iseconds)] preparing restore from: $SOURCE"

if [[ "$SOURCE" == oss://* ]]; then
  command -v ossutil >/dev/null 2>&1 || { echo "[FATAL] ossutil not installed"; exit 1; }
  ossutil cp "$SOURCE" - 2>/dev/null | gunzip > "$TMP_SQL"
elif [[ -f "$SOURCE" ]]; then
  gunzip -c "$SOURCE" > "$TMP_SQL"
else
  echo "[FATAL] source not found: $SOURCE"
  exit 1
fi

[[ $(stat -c%s "$TMP_SQL" 2>/dev/null || stat -f%z "$TMP_SQL") -gt 1024 ]] || {
  echo "[FATAL] restored SQL too small, possibly corrupt"
  exit 1
}

# -----------------------------------------------------------------------------
# 提取 binlog 坐标（CHANGE MASTER TO MASTER_LOG_FILE='...', MASTER_LOG_POS=...）
# -----------------------------------------------------------------------------
echo ""
echo "=== Binlog 坐标（用于 PITR，如需时间点恢复请记录）==="
BINLOG_LINE=$(grep -m1 "CHANGE MASTER TO MASTER_LOG_FILE" "$TMP_SQL" || true)
if [[ -n "$BINLOG_LINE" ]]; then
  echo "  $BINLOG_LINE"
else
  echo "  (未找到 binlog 坐标 — 备份可能未用 --master-data=2 参数)"
fi
echo ""

# -----------------------------------------------------------------------------
# 二次确认
# -----------------------------------------------------------------------------
read -r -p "即将恢复到数据库 hey_pickler。目标库现有数据不会自动清除，确认继续？[y/N] " confirm
[[ "$confirm" =~ ^[Yy]$ ]] || { echo "aborted"; exit 0; }

# -----------------------------------------------------------------------------
# 恢复
# -----------------------------------------------------------------------------
echo "[$(date -Iseconds)] restoring..."
mysql \
  -u"$DB_USERNAME" \
  -p"$DB_PASSWORD" \
  hey_pickler < "$TMP_SQL" 2> >(grep -v "Using a password on the command line")

echo "[$(date -Iseconds)] restore complete"
echo ""
echo "下一步："
echo "  1. 验证数据：mysql -u$DB_USERNAME -p hey_pickler -e 'SELECT COUNT(*) FROM admin_user, event, registration, point_record, ranking, banner, ban_record, operation_log'"
echo "  2. 如需 PITR（基于上面的 binlog 坐标），用 mysqlbinlog 重放"
echo "  3. 重启服务：sudo systemctl restart hey-pickler"
