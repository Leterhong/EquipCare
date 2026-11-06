package com.equipcare.asset.service.impl;

import com.equipcare.asset.service.QrCodeService;
import com.equipcare.common.exception.BizException;
import com.equipcare.common.result.ResultCode;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;

/**
 * ZXing 二维码实现。
 *
 * <p>采用 M 级纠错（约 15% 容错），适合打印后贴在设备上被油污、折角部分遮挡的场景；
 * 输出 PNG 无损格式，便于打印。
 */
@Service
public class QrCodeServiceImpl implements QrCodeService {

    private static final Logger log = LoggerFactory.getLogger(QrCodeServiceImpl.class);

    private static final int MIN_SIZE = 120;
    private static final int MAX_SIZE = 1024;

    @Override
    public byte[] png(String content, int size, int margin) {
        if (!StringUtils.hasText(content)) {
            throw new BizException(ResultCode.BAD_REQUEST, "二维码内容不能为空");
        }
        if (size < MIN_SIZE || size > MAX_SIZE) {
            throw new BizException(ResultCode.BAD_REQUEST, "二维码尺寸需在 " + MIN_SIZE + "-" + MAX_SIZE + " 之间");
        }
        if (margin < 0 || margin > 8) {
            throw new BizException(ResultCode.BAD_REQUEST, "二维码静区需在 0-8 之间");
        }

        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.MARGIN, margin);

        try {
            BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints);
            BufferedImage image = MatrixToImageWriter.toBufferedImage(matrix);
            ByteArrayOutputStream out = new ByteArrayOutputStream(4096);
            ImageIO.write(image, "PNG", out);
            return out.toByteArray();
        } catch (WriterException ex) {
            log.error("二维码编码失败 contentLength={}", content.length(), ex);
            throw new BizException(ResultCode.INTERNAL_ERROR, "二维码生成失败，请检查扫码地址配置");
        } catch (IOException ex) {
            log.error("二维码图片输出失败", ex);
            throw new BizException(ResultCode.INTERNAL_ERROR, "二维码图片输出失败");
        }
    }
}
