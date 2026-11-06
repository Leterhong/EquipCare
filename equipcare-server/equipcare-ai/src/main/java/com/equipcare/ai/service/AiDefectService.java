package com.equipcare.ai.service;

import com.equipcare.ai.client.OpenAiCompatClient;
import com.equipcare.ai.config.AiProperties;
import com.equipcare.common.exception.BizException;
import com.equipcare.common.result.ResultCode;
import com.equipcare.common.file.FileStorageProperties;
import com.equipcare.system.entity.SysFile;
import com.equipcare.system.mapper.SysFileMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * 巡检/报修拍照缺陷识别（多模态视觉）。
 *
 * <p>把现场照片（sys_file 元数据 → 上传目录磁盘文件 → base64 data URL）
 * 交给视觉模型，输出结构化缺陷判定：是否缺陷 / 缺陷类型 / 严重程度 / 依据 / 处置建议。
 */
@Service
@ConditionalOnProperty(name = "equipcare.ai.enabled", havingValue = "true")
public class AiDefectService {

    private static final Logger log = LoggerFactory.getLogger(AiDefectService.class);

    /** 单次最多分析张数（防止 token 失控） */
    private static final int MAX_IMAGES = 4;
    /** 允许送入视觉模型的图片类型 */
    private static final List<String> IMAGE_TYPES = List.of("image/jpeg", "image/png", "image/webp", "image/gif");

    private static final String SYSTEM_PROMPT = """
            你是资深设备巡检专家。根据现场照片与文字线索，输出缺陷判定报告，要求：
            1. 简体中文 Markdown，结构固定为：## 缺陷判定（明确：是缺陷/疑似缺陷/无异常）、## 缺陷类型与严重程度（严重度：高/中/低）、## 依据（照片中可见的具体证据）、## 处置建议（1~3 条，可执行）；
            2. 只描述照片中真实可见的内容，看不清或无法判断时明确写「照片分辨率不足以判断」，不得脑补；
            3. 总长不超过 400 字。""";

    private final AiProperties props;
    private final OpenAiCompatClient client;
    private final SysFileMapper sysFileMapper;
    private final FileStorageProperties storageProperties;

    public AiDefectService(AiProperties props, OpenAiCompatClient client,
                           SysFileMapper sysFileMapper, FileStorageProperties storageProperties) {
        this.props = props;
        this.client = client;
        this.sysFileMapper = sysFileMapper;
        this.storageProperties = storageProperties;
    }

    /**
     * 分析现场照片。
     *
     * @param fileIds 附件 id（sys_file，最多 4 张）
     * @param hint    文字线索（巡检项描述 / 故障描述，可空）
     * @return Markdown 缺陷判定报告
     */
    public String analyze(List<Long> fileIds, String hint) {
        if (fileIds == null || fileIds.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "请至少选择一张现场照片");
        }
        String model = props.getVisionModel() == null || props.getVisionModel().isBlank()
                ? props.getModel() : props.getVisionModel();
        if (model == null || model.isBlank()) {
            throw new BizException(ResultCode.BAD_REQUEST, "未配置视觉模型（equipcare.ai.vision-model）");
        }
        List<String> dataUrls = new ArrayList<>();
        List<Long> used = fileIds.stream().limit(MAX_IMAGES).toList();
        for (Long fileId : used) {
            dataUrls.add(toDataUrl(fileId));
        }
        String userText = (hint == null || hint.isBlank()
                ? "请判定这些设备现场照片中是否存在缺陷。"
                : "文字线索：" + hint + "\n请结合照片判定是否存在缺陷。")
                + "\n（共 " + dataUrls.size() + " 张照片）";
        log.info("AI 缺陷识别开始 fileIds={} visionModel={}", used, model);
        return client.chatVision(model, SYSTEM_PROMPT, userText, dataUrls);
    }

    /** sys_file 元数据 → 上传目录磁盘文件 → base64 data URL（仅图片类型放行） */
    private String toDataUrl(Long fileId) {
        SysFile file = sysFileMapper.selectById(fileId);
        if (file == null) {
            throw BizException.notFound("附件 " + fileId);
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        if (!IMAGE_TYPES.contains(contentType)) {
            throw new BizException(ResultCode.BAD_REQUEST, "附件 " + fileId + " 不是图片（" + contentType + "）");
        }
        Path root = Paths.get(storageProperties.getUploadDir()).toAbsolutePath().normalize();
        Path target = root.resolve(file.getFilePath()).normalize();
        if (!target.startsWith(root)) {
            throw new BizException(ResultCode.BAD_REQUEST, "非法的文件路径");
        }
        try {
            byte[] bytes = Files.readAllBytes(target);
            String mime = contentType;
            return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);
        } catch (Exception e) {
            log.error("缺陷识别读取附件失败 fileId={}", fileId, e);
            throw new BizException(ResultCode.BAD_REQUEST, "读取附件 " + fileId + " 失败，文件可能已被移动或删除");
        }
    }
}
