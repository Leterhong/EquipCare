package com.equipcare.asset.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 设备二维码内容。
 *
 * <p>二维码里编码的是 {@link #content}：形如
 * {@code http://host/m/scan.html?token=<32位随机令牌>}。
 * 令牌与设备一一对应、可重置，不含密码、JWT、工号、手机号等任何敏感信息；
 * 换到设备信息必须先登录并通过 {@code asset:equipment:view} 权限校验。
 */
@Data
@Schema(description = "设备二维码内容")
public class QrCodeVO {

    private Long equipmentId;

    private String assetNo;

    private String equipmentName;

    @Schema(description = "二维码令牌，扫码后回传该值")
    private String qrToken;

    @Schema(description = "扫码后跳转地址（相对或绝对，取决于 equipcare.asset.scan-base-url）")
    private String scanUrl;

    @Schema(description = "实际编码进二维码的文本，前端可直接使用")
    private String content;

    @Schema(description = "服务端渲染的 PNG 地址，需携带 Authorization 请求头获取")
    private String imageUrl;
}
