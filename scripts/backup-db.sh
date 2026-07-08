#!/usr/bin/env bash
# Loop-v19 — Parameterized MySQL backup for Hey Pickler.
# Designed to run via cron. No infrastructure assumptions: DB creds, retention,
# and optional object-storage hand-off are all env-driven.
#
# Usage (cron example — daily 02:30):
#   30 2 * * *  DB_HOST=... DB_USERNAME=... DB_PASSWORD=... DB_NAME=hey_pickler \
#              BACKUP_DIR=/var/backups/hey_pickler RETENTION_DAYS=30 \
#              UPLOAD_HOOK=https://oss-upload.example.com/invoke \
#              /opt/hey-pickler/scripts/backup-db.sh >> /var/log/hey_pickler_backup.log 2>&1
#
# Required env:
#   DB_HOST, DB_USERNAME, DB_PASSWORD
# Optional:
#   DB_PORT          (default: 3306)
#   DB_NAME          (default: hey_pickler)
#   BACKUP_DIR       (default: ./backups)
#   RETENTION_DAYS   (default: 30)  — prune older backups
#   UPLOAD_HOOK      (default: none) — URL POSTed with the .gz as multipart file;
#                                       wire to an OSS/S3 upload lambda. Local copy retained on failure.
#   GZIP             (default: 1)    — set 0 to skip compression
#
# Exit codes: 0 = success (dump non-empty), 1 = failure (empty dump / mysqldump error).

set -euo pipefail

: "${DB_HOST:?DB_HOST is required}"
: "${DB_PORT:=3306}"
: "${DB_USERNAME:?DB_USERNAME is required}"
: "${DB_PASSWORD:?DB_PASSWORD is required}"
: "${DB_NAME:=hey_pickler}"
BACKUP_DIR="${BACKUP_DIR:-./backups}"
RETENTION_DAYS="${RETENTION_DAYS:-30}"
UPLOAD_HOOK="${UPLOAD_HOOK:-}"
GZIP="${GZIP:-1}"

mkdir -p "$BACKUP_DIR"
TS="$(date -u +%Y%m%dT%H%M%SZ)"
RAW="$BACKUP_DIR/${DB_NAME}-${TS}.sql"

echo "== Hey Pickler DB backup =="
echo "  host: $DB_HOST:$DB_PORT  db: $DB_NAME"
echo "  dest: $BACKUP_DIR/"

# MYSQL_PWD avoids leaking the password via `ps` / shell history (mysql client reads it from env).
export MYSQL_PWD="$DB_PASSWORD"

# --single-transaction: InnoDB consistent snapshot, does NOT lock writes.
# --quick: stream large tables without buffering (keeps memory flat).
# --routines/--triggers: include stored procedures and triggers.
# --set-gtid-purged=AUTO: safe default for managed MySQL (RDS / PolarDB / etc.).
mysqldump \
  --host="$DB_HOST" --port="$DB_PORT" \
  --user="$DB_USERNAME" \
  --single-transaction --quick --routines --triggers \
  --set-gtid-purged=AUTO \
  "$DB_NAME" > "$RAW"
unset MYSQL_PWD

if [ ! -s "$RAW" ]; then
  echo "FAILED — dump is empty (mysqldump likely errored). See stderr above." >&2
  rm -f "$RAW"
  exit 1
fi

FILE="$RAW"
if [ "$GZIP" = "1" ]; then
  gzip -f "$RAW"
  FILE="$RAW.gz"
fi
SIZE="$(du -h "$FILE" | cut -f1)"
echo "  file: $(basename "$FILE")  ($SIZE)"

# Optional hand-off to object storage. Local copy is always retained as a fallback.
if [ -n "$UPLOAD_HOOK" ]; then
  echo "  uploading via hook: $UPLOAD_HOOK"
  if curl -fsS -X POST "$UPLOAD_HOOK" -F "file=@$FILE"; then
    echo "  upload: ok"
  else
    echo "  ::warning:: upload hook failed — local backup retained at $FILE" >&2
  fi
fi

# Prune backups older than RETENTION_DAYS (only files matching this db's pattern).
find "$BACKUP_DIR" -maxdepth 1 -name "${DB_NAME}-*.sql*" -type f -mtime +"$RETENTION_DAYS" -delete
echo "OK — $(basename "$FILE") ($SIZE), retained ${RETENTION_DAYS}d"
