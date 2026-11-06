-- =====================================================================
-- 巡维云 EquipCare · 数据库与账号初始化（DBA 手工执行一次）
-- 文件：database/00-create-database.sql
--
-- 执行方式（在 MySQL 服务器上）：
--   mysql -u root -p < database/00-create-database.sql
--
-- 说明：
--   1. 本脚本只创建数据库与应用账号，不创建任何业务表与数据；
--   2. 业务表由 schema.sql 创建，演示数据由 seed.sql 写入；
--   3. 应用账号密码请通过环境变量注入，生产环境禁止使用示例密码。
-- =====================================================================

-- 生产库
CREATE DATABASE IF NOT EXISTS `equipcare`
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

-- 测试库（集成测试使用，与生产库隔离）
CREATE DATABASE IF NOT EXISTS `equipcare_test`
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

-- 应用账号：仅授予业务库的增删改查权限，不授予 DROP / GRANT
-- 请把 'ChangeMe_2026' 替换为实际密码，并与 application.yml 中的 DB_PASSWORD 保持一致
CREATE USER IF NOT EXISTS 'equipcare_app'@'%' IDENTIFIED BY 'ChangeMe_2026';

GRANT SELECT, INSERT, UPDATE, DELETE, EXECUTE, SHOW VIEW ON `equipcare`.* TO 'equipcare_app'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE, EXECUTE, SHOW VIEW ON `equipcare_test`.* TO 'equipcare_app'@'%';

-- 审计日志、状态流转日志、库存流水「只允许插入」的约束
-- 说明：MySQL 无法在库级授权之上按表回收权限（ERROR 1147），
--       因此这三张表的不可篡改由 schema.sql 末尾的 BEFORE UPDATE / BEFORE DELETE 触发器强制，
--       触发器对所有账号（含 root）生效，比权限回收更彻底。
FLUSH PRIVILEGES;

-- 提示：首次部署若使用 root 连接，可跳过本脚本，直接由应用自动建表。
-- 但正式环境应以 equipcare_app 账号运行应用。
SELECT 'EquipCare database initialized.' AS message;
