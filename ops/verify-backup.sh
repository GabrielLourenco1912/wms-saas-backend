#!/bin/sh
# Disposable integration check. Never connects to the configured application database.
set -eu
ops_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
project_dir=$(dirname "$ops_dir")
container="wms-backup-check-$$-$(date +%s)"
password=wms-verification-only
cleanup() { docker rm -f "$container" >/dev/null 2>&1 || true; }
trap cleanup EXIT HUP INT TERM
docker run --detach --rm --network none --name "$container" \
    --env MYSQL_ROOT_PASSWORD="$password" mysql:8.4.0 >/dev/null
attempt=0
until docker exec --env MYSQL_PWD="$password" "$container" mysql --user=root --execute='SELECT 1' >/dev/null 2>&1; do
    attempt=$((attempt + 1))
    [ "$attempt" -lt 60 ] || { docker logs "$container"; echo 'MySQL did not become ready' >&2; exit 1; }
    sleep 1
done
sql() { docker exec --env MYSQL_PWD="$password" "$container" mysql --user=root --batch --skip-column-names --execute="$1"; }
sql 'CREATE DATABASE wms_backup_verify; CREATE DATABASE wms_backup_restore;'
for migration in "$project_dir"/src/main/resources/db/migration/V*.sql; do
    docker exec -i --env MYSQL_PWD="$password" "$container" mysql --user=root wms_backup_verify < "$migration"
done
sql "INSERT INTO wms_backup_verify.armazens (id,nome,criado_em,atualizado_em) VALUES ('01AAAAAAAAAAAAAAAAAAAAAAAA','Backup verification',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6));
CREATE TRIGGER wms_backup_verify.backup_integrity_probe BEFORE DELETE ON wms_backup_verify.armazens FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Backup trigger restored';"
docker cp "$ops_dir/mysql-backup.sh" "$container:/tmp/mysql-backup.sh" >/dev/null
docker cp "$ops_dir/mysql-restore.sh" "$container:/tmp/mysql-restore.sh" >/dev/null
# Exercise retention at the same time: remove a 32-day archive, retain a 29-day one.
docker exec "$container" sh -c 'mkdir /tmp/backups; touch -d "32 days ago" /tmp/backups/wms-wms_backup_verify-old.sql.gz; touch -d "29 days ago" /tmp/backups/wms-wms_backup_verify-recent.sql.gz'
docker exec --env DB_NAME=wms_backup_verify --env DB_USERNAME=root --env DB_PASSWORD="$password" \
    --env BACKUP_DIR=/tmp/backups "$container" sh /tmp/mysql-backup.sh
archive=$(docker exec "$container" sh -c 'ls /tmp/backups/wms-wms_backup_verify-2*.sql.gz')
docker exec "$container" sh -c 'test ! -e /tmp/backups/wms-wms_backup_verify-old.sql.gz; test -f /tmp/backups/wms-wms_backup_verify-recent.sql.gz'
restore() {
    docker exec --env DB_NAME=wms_backup_verify --env RESTORE_DB_NAME="$1" --env DB_USERNAME=root \
        --env DB_PASSWORD="$password" "$container" sh /tmp/mysql-restore.sh "$archive"
}
restore wms_backup_restore
[ "$(sql "SELECT nome FROM wms_backup_restore.armazens WHERE id='01AAAAAAAAAAAAAAAAAAAAAAAA'")" = 'Backup verification' ]
[ "$(sql 'SELECT COUNT(*) FROM wms_backup_restore.access_profiles')" -eq 2 ]
[ "$(sql "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='wms_backup_verify'")" = "$(sql "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='wms_backup_restore'")" ]
[ "$(sql "SELECT COUNT(*) FROM information_schema.triggers WHERE trigger_schema='wms_backup_restore' AND trigger_name='backup_integrity_probe'")" -eq 1 ]
if sql 'DELETE FROM wms_backup_restore.armazens' >/dev/null 2>&1; then echo 'Restored trigger was not enforced' >&2; exit 1; fi
if restore wms_backup_restore >/dev/null 2>&1; then echo 'A populated destination was accepted' >&2; exit 1; fi
if restore wms_backup_verify >/dev/null 2>&1; then echo 'The operational database was accepted as restore destination' >&2; exit 1; fi
printf 'PASS: MySQL 8.4 schema/data/triggers restored; retention and destination protections verified.\n'
