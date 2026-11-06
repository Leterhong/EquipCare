package com.equipcare.asset.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设备图片 / 附件。
 */
@Data
@Schema(description = "设备图片或附件")
public class EquipmentFileVO {

    @Schema(description = "关联ID，解绑时使用")
    private Long id;

    private Long equipmentId;

    @Schema(description = "文件ID（sys_file.id）")
    private Long fileId;

    @Schema(description = "用途：IMAGE 图片 / ATTACHMENT 附件")
    private String fileType;

    private Integer sortNo;

    private String fileName;

    @Schema(description = "字节数")
    private Long fileSize;

    @Schema(description = "MIME 类型")
    private String contentType;

    @Schema(description = "访问地址，需携带 Authorization 请求头下载")
    private String fileUrl;

    private LocalDateTime createTime;
}
