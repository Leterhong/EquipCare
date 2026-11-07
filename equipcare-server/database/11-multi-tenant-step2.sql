-- =====================================================================
-- 巡维云 EquipCare · 多租户第二步（第十期）
-- 文件：database/11-multi-tenant-step2.sql
-- 说明：租户主表 + 租户化登录链路（JWT tid claim / 行级上下文随会话建立）。
--       本步骤仍为单租户运行（所有用户 tenant_id=0），零功能变化。
-- =====================================================================

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS sys_tenant (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '租户 id',
    tenant_code VARCHAR(64)     NOT NULL COMMENT '租户编码（子域名/登录路由标识）',
    tenant_name VARCHAR(128)    NOT NULL COMMENT '租户名称',
    plan        VARCHAR(32)     NOT NULL DEFAULT 'STANDARD' COMMENT '套餐：STANDARD/PRO/PRIVATE',
    status      TINYINT         NOT NULL DEFAULT 1 COMMENT '1 启用 / 0 停用（停用后该租户登录被拒）',
    quota_json  JSON            NULL COMMENT '配额（设备数/存储/月 AI token 上限）',
    created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_code (tenant_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '租户主表（SaaS 化第二步：登录路由与配额基础）';

-- 平台预置租户（id=0 与全部既有数据 tenant_id=0 对应；AUTO_INCREMENT 表允许显式 0）
INSERT IGNORE INTO sys_tenant (id, tenant_code, tenant_name, plan, status)
VALUES (0, 'platform', '平台预置租户', 'STANDARD', 1);

-- 登录查询索引（用户名在租户内唯一——多租户开放注册后约束迁移到 (tenant_id, username)）
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND INDEX_NAME = 'idx_user_tenant');
SET @ddl = IF(@idx_exists = 0,
  'ALTER TABLE sys_user ADD INDEX idx_user_tenant (tenant_id)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
