package com.equipcare.audit.dto;

import com.equipcare.common.query.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 审计日志查询条件。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "审计日志查询条件")
public class AuditLogQuery extends PageQuery {

    @Schema(description = "操作人账号（模糊匹配）")
    private String username;

    @Schema(description = "模块")
    private String module;

    @Schema(description = "结果：SUCCESS / FAIL")
    private String resultStatus;

    @Schema(description = "开始时间")
    private LocalDateTime startTime;

    @Schema(description = "结束时间")
    private LocalDateTime endTime;
}
