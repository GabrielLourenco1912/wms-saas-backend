#!/bin/sh
set -eu
umask 077
: "${DB_NAME:?Set DB_NAME}"
: "${DB_USERNAME:?Set DB_USERNAME}"
: "${DB_PASSWORD:?Set DB_PASSWORD}"
case "$DB_NAME" in *[!A-Za-z0-9_]*|'') echo 'DB_NAME must contain only letters, digits and underscore' >&2; exit 2;; esac
backup_dir=${BACKUP_DIR:-./backups}
retention=${BACKUP_RETENTION_DAYS:-30}
case "$retention" in *[!0-9]*|'') echo 'Invalid retention' >&2; exit 2;; esac
[ "$retention" -ge 30 ] || { echo 'Retention must be at least 30 days' >&2; exit 2; }
mkdir -p "$backup_dir"
lock_dir="$backup_dir/.wms-backup-lock"
mkdir "$lock_dir" 2>/dev/null || { echo 'A backup is already running' >&2; exit 1; }
stamp=$(date -u +%Y%m%dT%H%M%SZ)
dump="$backup_dir/wms-${DB_NAME}-${stamp}.sql.partial"
archive="$backup_dir/wms-${DB_NAME}-${stamp}.sql.gz"
cleanup() { rm -f "$dump" "$archive.partial"; rmdir "$lock_dir" 2>/dev/null || true; }
trap cleanup EXIT HUP INT TERM
export MYSQL_PWD="$DB_PASSWORD"
# First save the dump: a failing mysqldump must never become a successful empty gzip.
mysqldump --host="${DB_HOST:-localhost}" --port="${DB_PORT:-3306}" --user="$DB_USERNAME" \
    --single-transaction --routines --events --triggers --hex-blob --no-tablespaces --set-gtid-purged=OFF \
    "$DB_NAME" > "$dump"
gzip -c "$dump" > "$archive.partial"
gzip -t "$archive.partial"
mv "$archive.partial" "$archive"
find "$backup_dir" -maxdepth 1 -type f -name "wms-${DB_NAME}-*.sql.gz" -mtime "+$retention" -delete
printf 'Backup completed: %s\n' "$archive"
