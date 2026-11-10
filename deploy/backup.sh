#!/usr/bin/env bash
# 备份数据库和上传文件到 deploy/backups/，默认保留 14 天。
# 用法：./backup.sh            （在 deploy/ 目录下执行）
# 定时：crontab -e 加一行      30 3 * * * cd /opt/lemon/deploy && ./backup.sh >> backups/backup.log 2>&1
# 建议再把 backups/ 同步到对象存储（如 ossutil / coscmd），防止整机故障。
set -euo pipefail
cd "$(dirname "$0")"

KEEP_DAYS="${KEEP_DAYS:-14}"
TS="$(date +%Y%m%d-%H%M%S)"
mkdir -p backups

# 数据库：--single-transaction 不锁表；密码通过容器内的 MYSQL_PWD 传入，不出现在命令行参数里
docker compose exec -T mysql sh -c \
  'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump -uroot --single-transaction --routines --triggers --set-gtid-purged=OFF lemon' \
  | gzip > "backups/db-${TS}.sql.gz"

# 上传的图片
docker compose exec -T web tar -C /data -czf - uploads > "backups/uploads-${TS}.tar.gz"

# 清理过期备份
find backups -name 'db-*.sql.gz' -mtime +"${KEEP_DAYS}" -delete
find backups -name 'uploads-*.tar.gz' -mtime +"${KEEP_DAYS}" -delete

echo "[$(date '+%F %T')] 备份完成：db-${TS}.sql.gz $(du -h "backups/db-${TS}.sql.gz" | cut -f1)，uploads-${TS}.tar.gz $(du -h "backups/uploads-${TS}.tar.gz" | cut -f1)"
