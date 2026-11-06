#!/bin/bash
# =====================================================================
# 巡维云 EquipCare · 数据库备份脚本（在部署机上以 crontab 定时执行）
#   crontab -e
#   30 2 * * * /opt/equipcare/equipcare-server/deploy/backup.sh >> /opt/equipcare/backup/backup.log 2>&1
# 依赖：部署机安装 mysql 客户端（或用 `docker compose exec -T mysql mysqldump` 方式，本脚本采用后者，无需宿主机客户端）。
# =====================================================================
set -euo pipefail

# compose 项目目录（按实际部署路径修改）
COMPOSE_DIR="$(cd "$(dirname "$0")/.." && pwd)"
BACKUP_DIR="${BACKUP_DIR:-$COMPOSE_DIR/../backup}"
KEEP_DAYS="${KEEP_DAYS:-7}"
STAMP="$(date +%Y%m%d_%H%M%S)"

mkdir -p "$BACKUP_DIR"

# 从 .env 读取 root 口令（不回显、不落日志）
# shellcheck disable=SC1091
set -a; source "$COMPOSE_DIR/.env"; set +a

echo "[$(date '+%F %T')] 开始备份 equipcare ..."
docker compose -f "$COMPOSE_DIR/docker-compose.yml" exec -T mysql \
    mysqldump -uroot -p"${MYSQL_ROOT_PASSWORD}" \
    --single-transaction --routines --triggers --events \
    --default-character-set=utf8mb4 \
    equipcare | gzip > "$BACKUP_DIR/equipcare_$STAMP.sql.gz"

SIZE=$(du -h "$BACKUP_DIR/equipcare_$STAMP.sql.gz" | cut -f1)
echo "[$(date '+%F %T')] 备份完成：equipcare_$STAMP.sql.gz ($SIZE)"

# 清理过期备份
find "$BACKUP_DIR" -name "equipcare_*.sql.gz" -mtime "+$KEEP_DAYS" -delete
echo "[$(date '+%F %T')] 已清理 $KEEP_DAYS 天前的旧备份。"
