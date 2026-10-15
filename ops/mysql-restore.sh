#!/bin/sh
set -eu
: "${DB_NAME:?Set source DB_NAME}"
: "${RESTORE_DB_NAME:?Set the empty destination RESTORE_DB_NAME}"
: "${DB_USERNAME:?Set DB_USERNAME}"
: "${DB_PASSWORD:?Set DB_PASSWORD}"
case "$RESTORE_DB_NAME" in *[!A-Za-z0-9_]*|'') echo 'Invalid RESTORE_DB_NAME' >&2; exit 2;; esac
[ "$RESTORE_DB_NAME" != "$DB_NAME" ] || { echo 'Restore destination must differ from the operational database' >&2; exit 2; }
[ "$#" -eq 1 ] && [ -f "$1" ] || { echo 'Usage: mysql-restore.sh backup.sql.gz' >&2; exit 2; }
export MYSQL_PWD="$DB_PASSWORD"
count=$(mysql --host="${DB_HOST:-localhost}" --port="${DB_PORT:-3306}" --user="$DB_USERNAME" --batch --skip-column-names \
    --execute="SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '$RESTORE_DB_NAME'")
[ "$count" -eq 0 ] || { echo 'Restore destination must be empty' >&2; exit 2; }
gzip -t "$1"
dump=$(mktemp)
cleanup() { rm -f "$dump"; }
trap cleanup EXIT HUP INT TERM
gzip -dc "$1" > "$dump"
mysql --host="${DB_HOST:-localhost}" --port="${DB_PORT:-3306}" --user="$DB_USERNAME" --database="$RESTORE_DB_NAME" < "$dump"
printf 'Restore completed into %s\n' "$RESTORE_DB_NAME"
