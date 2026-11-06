package com.equipcare.workorder.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 验收记录。
 */
@Data
@Schema(description = "验收记录")
public class VerifyRecordVO {

    private Long id;

    private Long verifyUserId;

    private String verifyUserName;

    @Schema(description = "PASS / REJECT")
    private String verifyResult;

    private Integer qualityScore;

    private String remark;

    private String attachmentIds;

    private LocalDateTime verifyTime;
}
