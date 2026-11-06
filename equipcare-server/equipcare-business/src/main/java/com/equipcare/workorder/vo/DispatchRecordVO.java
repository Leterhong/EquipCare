package com.equipcare.workorder.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 派单轨迹节点。
 */
@Data
@Schema(description = "派单轨迹节点")
public class DispatchRecordVO {

    private Long id;

    @Schema(description = "ASSIGN / TRANSFER / REASSIGN / REVOKE")
    private String dispatchType;

    private Long fromUserId;

    private String fromUserName;

    private Long toUserId;

    private String toUserName;

    private Long dispatcherId;

    private String dispatcherName;

    private LocalDateTime dispatchTime;

    private LocalDateTime acceptTime;

    private Integer rejectFlag;

    private String rejectReason;

    private String remark;
}
