package com.equipcare.notification.dto;

import com.equipcare.common.query.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 我的消息查询条件。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "消息查询条件")
public class MessageQuery extends PageQuery {

    @Schema(description = "消息类型：TASK / WORKORDER / SLA / SYSTEM")
    private String msgType;

    @Schema(description = "是否已读：0 未读 1 已读，不传为全部")
    private Integer readFlag;
}
