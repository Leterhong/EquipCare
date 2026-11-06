package com.equipcare.workorder.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 巡检现场照片元数据。图片本身通过带令牌的 {@code GET /api/files/{id}} 读取。
 */
@Data
@Schema(description = "巡检现场照片")
public class SourceAttachmentVO {

    private Long id;

    private String fileName;

    private Long fileSize;

    private String contentType;
}
