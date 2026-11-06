package com.equipcare.inspection.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 附件（现场照片）元数据。
 *
 * <p>只返回文件ID与名称，图片本身通过 {@code GET /api/files/{id}} 带令牌读取，
 * 不把可公开猜测的静态地址暴露出去。
 */
@Data
@Schema(description = "附件")
public class AttachmentVO {

    private Long id;

    private String fileName;

    private Long fileSize;

    private String contentType;
}
