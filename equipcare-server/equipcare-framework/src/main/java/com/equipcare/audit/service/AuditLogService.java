package com.equipcare.audit.service;

import com.equipcare.audit.dto.AuditLogQuery;
import com.equipcare.audit.entity.SysAuditLog;
import com.equipcare.common.result.PageResult;

/**
 * 审计日志服务。
 */
public interface AuditLogService {

    /** 异步保存审计日志，失败不影响主流程 */
    void saveAsync(SysAuditLog log);

    /** 分页查询 */
    PageResult<SysAuditLog> page(AuditLogQuery query);
}
