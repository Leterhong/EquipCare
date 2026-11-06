#!/bin/bash
# =====================================================================
# 巡维云 EquipCare · MySQL 容器首次初始化脚本
# 由 mysql:8.4 官方镜像的 /docker-entrypoint-initdb.d 机制自动执行（仅首次建库时运行一次）。
# 依赖环境变量（由 docker-compose.yml 注入）：
#   MYSQL_ROOT_PASSWORD     root 口令
#   EQUIPCARE_APP_PASSWORD  应用账号 equipcare_app 口令（禁止弱密码）
# 数据来源：/sql-src 挂载的 database/ 目录（schema.sql 为全量 33 表 + 触发器，seed.sql 为演示数据）。
# =====================================================================
set -euo pipefail

echo "[equipcare-init] 创建数据库与应用账号 ..."

mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" <<'SQL'
CREATE DATABASE IF NOT EXISTS `equipcare`
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;
SQL

# 应用账号：密码来自环境变量，不用脚本内写死的示例密码
mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" <<SQL
CREATE USER IF NOT EXISTS 'equipcare_app'@'%' IDENTIFIED BY '${EQUIPCARE_APP_PASSWORD}';
GRANT SELECT, INSERT, UPDATE, DELETE, EXECUTE, SHOW VIEW ON \`equipcare\`.* TO 'equipcare_app'@'%';
FLUSH PRIVILEGES;
SQL

echo "[equipcare-init] 应用全量表结构 schema.sql ..."
mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" --default-character-set=utf8mb4 equipcare < /sql-src/schema.sql

echo "[equipcare-init] 写入演示数据 seed.sql ..."
mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" --default-character-set=utf8mb4 equipcare < /sql-src/seed.sql

echo "[equipcare-init] 完成：equipcare 库 33 表 + 演示数据就绪。"
