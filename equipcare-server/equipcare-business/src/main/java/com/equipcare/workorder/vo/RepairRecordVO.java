package com.equipcare.workorder.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 维修过程记录。
 */
@Data
@Schema(description = "维修过程记录")
public class RepairRecordVO {

    private Long id;

    private String recordType;

    private String recordTypeLabel;

    private String content;

    @Schema(hidden = true)
    private String beforeImageIds;

    @Schema(hidden = true)
    private String afterImageIds;

    @Schema(description = "维修前照片")
    private List<SourceAttachmentVO> beforeImages = new ArrayList<>();

    @Schema(description = "维修后照片")
    private List<SourceAttachmentVO> afterImages = new ArrayList<>();

    private Integer costMinutes;

    private String attachmentIds;

    private Long operatorId;

    private String operatorName;

    private LocalDateTime createTime;
}
