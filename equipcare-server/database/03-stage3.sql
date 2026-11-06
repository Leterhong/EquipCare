-- =====================================================================
-- 巡维云 EquipCare · 第三阶段增量脚本
-- 文件：database/03-stage3.sql
--
-- 适用场景：已经在跑第二阶段的库，只执行本文件补齐第三阶段所需结构。
-- 全新部署直接执行 schema.sql + seed.sql 即可（schema.sql 已包含本表）。
--
-- 本脚本只做加法：
--   1. eq_equipment_file —— 设备图片与附件关联表
-- 不创建任何账号、不改动任何业务数据。
--
-- 说明：设备维修/巡检历史按 equipment_id 过滤，schema.sql 中已有的
--       idx_wo_equipment_status(equipment_id, status) 与
--       idx_insp_task_equipment_date(equipment_id, plan_date)
--       已经能命中设备维度过滤，无需重复加索引。
-- =====================================================================

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS eq_equipment_file (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    equipment_id BIGINT UNSIGNED NOT NULL COMMENT '设备ID',
    file_id      BIGINT UNSIGNED NOT NULL COMMENT '文件ID（sys_file.id）',
    file_type    VARCHAR(20)     NOT NULL DEFAULT 'ATTACHMENT' COMMENT '用途：IMAGE 设备图片 / ATTACHMENT 附件',
    sort_no      INT             NOT NULL DEFAULT 0 COMMENT '排序号，升序展示，首张图片作为设备封面',
    create_by    BIGINT UNSIGNED NULL COMMENT '上传人',
    create_time  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '绑定时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_eq_equipment_file (equipment_id, file_id),
    KEY idx_eq_equipment_file_type (equipment_id, file_type, sort_no),
    CONSTRAINT fk_eq_equipment_file_equipment FOREIGN KEY (equipment_id) REFERENCES eq_equipment (id) ON DELETE CASCADE,
    CONSTRAINT fk_eq_equipment_file_file FOREIGN KEY (file_id) REFERENCES sys_file (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '设备图片与附件关联';
