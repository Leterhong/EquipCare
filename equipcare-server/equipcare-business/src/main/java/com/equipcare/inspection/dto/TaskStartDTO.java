package com.equipcare.inspection.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 开始巡检（可选扫码核验）。
 */
@Data
@Schema(description = "开始巡检请求")
public class TaskStartDTO {

    @Schema(description = "设备二维码令牌；传入则校验与任务设备一致，用于防止代签")
    private String qrToken;
}
