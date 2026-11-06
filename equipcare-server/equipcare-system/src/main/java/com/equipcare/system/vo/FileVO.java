package com.equipcare.system.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 上传结果。
 */
@Data
@Schema(description = "文件上传结果")
public class FileVO {

    @Schema(description = "文件ID，业务表存该ID")
    private Long fileId;

    @Schema(description = "原始文件名")
    private String fileName;

    @Schema(description = "访问地址")
    private String url;

    @Schema(description = "文件大小（字节）")
    private Long size;

    @Schema(description = "MIME 类型")
    private String contentType;
}
