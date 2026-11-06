package com.equipcare.asset.service;

/**
 * 二维码图片生成（ZXing）。
 *
 * <p>只负责把文本编码成 PNG，不关心业务含义；内容安全由调用方保证。
 */
public interface QrCodeService {

    /**
     * 生成 PNG 字节。
     *
     * @param content 二维码内容（设备扫码地址，不含密码 / JWT / 手机号等敏感信息）
     * @param size    边长像素，120-1024
     * @param margin  静区格数，0-8
     */
    byte[] png(String content, int size, int margin);
}
