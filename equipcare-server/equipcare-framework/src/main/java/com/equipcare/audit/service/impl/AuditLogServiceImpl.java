package com.equipcare.audit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.equipcare.audit.dto.AuditLogQuery;
import com.equipcare.audit.entity.SysAuditLog;
import com.equipcare.audit.mapper.SysAuditLogMapper;
import com.equipcare.audit.service.AuditLogService;
import com.equipcare.common.result.PageResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 审计日志实现。写入走独立线程池，避免影响接口响应时间。
 */
@Service
public class AuditLogServiceImpl implements AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogServiceImpl.class);

    private final SysAuditLogMapper auditLogMapper;

    public AuditLogServiceImpl(SysAuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    @Override
    @Async("auditExecutor")
    public void saveAsync(SysAuditLog entity) {
        try {
            auditLogMapper.insert(entity);
        } catch (Exception ex) {
            // 审计失败不得影响业务，记录错误日志由运维排查
            log.error("审计日志写入失败 module={} action={}", entity.getModule(), entity.getAction(), ex);
        }
    }

    @Override
    public PageResult<SysAuditLog> page(AuditLogQuery query) {
        LambdaQueryWrapper<SysAuditLog> wrapper = Wrappers.<SysAuditLog>lambdaQuery()
                .like(query.getUsername() != null && !query.getUsername().isBlank(),
                        SysAuditLog::getUsername, query.getUsername())
                .eq(query.getModule() != null && !query.getModule().isBlank(),
                        SysAuditLog::getModule, query.getModule())
                .eq(query.getResultStatus() != null && !query.getResultStatus().isBlank(),
                        SysAuditLog::getResultStatus, query.getResultStatus())
                .ge(query.getStartTime() != null, SysAuditLog::getCreateTime, query.getStartTime())
                .le(query.getEndTime() != null, SysAuditLog::getCreateTime, query.getEndTime())
                .orderByDesc(SysAuditLog::getId);
        IPage<SysAuditLog> page = auditLogMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        List<SysAuditLog> records = page.getRecords();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }
}
