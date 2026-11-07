package com.equipcare.system.service.impl;

import com.equipcare.common.exception.BizException;
import com.equipcare.common.file.FileStorageProperties;
import com.equipcare.common.result.ResultCode;
import com.equipcare.common.security.SecurityUtils;
import com.equipcare.system.entity.SysFile;
import com.equipcare.system.mapper.SysFileMapper;
import com.equipcare.system.service.FileStorageService;
import com.equipcare.system.vo.FileVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/**
 * 本地磁盘存储实现。
 *
 * <p>安全控制：
 * <ul>
 *   <li>扩展名白名单 + 大小上限，双重校验；</li>
 *   <li>存储文件名使用 UUID，屏蔽原始文件名中的路径字符；</li>
 *   <li>上传目录按年月日分层，避免单目录文件过多。</li>
 * </ul>
 */
@Service
public class FileStorageServiceImpl implements FileStorageService {

    /** 扩展名 → 受控 Content-Type（渗透整改：不信任客户端提交的 MIME） */
    private static final java.util.Map<String, String> SAFE_CONTENT_TYPES = java.util.Map.ofEntries(
            java.util.Map.entry("jpg", "image/jpeg"),
            java.util.Map.entry("jpeg", "image/jpeg"),
            java.util.Map.entry("png", "image/png"),
            java.util.Map.entry("gif", "image/gif"),
            java.util.Map.entry("webp", "image/webp"),
            java.util.Map.entry("pdf", "application/pdf"),
            java.util.Map.entry("doc", "application/msword"),
            java.util.Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            java.util.Map.entry("xls", "application/vnd.ms-excel"),
            java.util.Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            java.util.Map.entry("mp4", "video/mp4"));
    private static final Logger log = LoggerFactory.getLogger(FileStorageServiceImpl.class);
    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final FileStorageProperties properties;
    private final SysFileMapper fileMapper;

    public FileStorageServiceImpl(FileStorageProperties properties, SysFileMapper fileMapper) {
        this.properties = properties;
        this.fileMapper = fileMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileVO store(MultipartFile file, String bizType, Long bizId) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ResultCode.FILE_EMPTY);
        }
        if (file.getSize() > properties.getMaxSize().toBytes()) {
            throw new BizException(ResultCode.FILE_TOO_LARGE,
                    "文件超过大小限制（" + properties.getMaxSize().toMegabytes() + "MB）");
        }

        String originalName = file.getOriginalFilename() == null ? "unknown" : file.getOriginalFilename();
        String extension = extractExtension(originalName);
        if (!properties.getAllowedExtensions().contains(extension)) {
            throw new BizException(ResultCode.FILE_TYPE_NOT_ALLOWED,
                    "不支持的文件类型：" + extension + "，允许：" + properties.getAllowedExtensions());
        }

        String dayPath = LocalDate.now().format(DAY_FORMATTER);
        String storedName = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        Path root = Paths.get(properties.getUploadDir()).toAbsolutePath().normalize();
        Path targetDir = root.resolve(dayPath).normalize();
        if (!targetDir.startsWith(root)) {
            throw new BizException(ResultCode.FILE_TYPE_NOT_ALLOWED, "非法的存储路径");
        }

        String md5;
        try {
            Files.createDirectories(targetDir);
            Path target = targetDir.resolve(storedName);
            try (InputStream in = file.getInputStream()) {
                md5 = DigestUtils.md5DigestAsHex(in);
            }
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            log.error("文件写入失败 name={}", originalName, ex);
            throw new BizException(ResultCode.INTERNAL_ERROR, "文件保存失败");
        }

        String relativePath = dayPath + "/" + storedName;

        // 安全加固（渗透整改 P3）：contentType 不信任客户端提交值，按扩展名服务端重映射，
        // 杜绝「jpg 扩展名 + text/html」组合在下载端被浏览器内联渲染的可能。
        String safeContentType = SAFE_CONTENT_TYPES.getOrDefault(extension,
                "application/octet-stream");
        SysFile entity = new SysFile();
        entity.setFileName(originalName);
        entity.setStoredName(storedName);
        entity.setFilePath(relativePath);
        entity.setFileUrl(properties.getUrlPrefix() + "/" + relativePath);
        entity.setFileSize(file.getSize());
        entity.setContentType(safeContentType);
        entity.setMd5(md5);
        entity.setBizType(bizType);
        entity.setBizId(bizId);
        entity.setUploadUserId(SecurityUtils.getCurrentUserId());
        entity.setCreateTime(LocalDateTime.now());
        fileMapper.insert(entity);

        FileVO vo = new FileVO();
        vo.setFileId(entity.getId());
        vo.setFileName(originalName);
        vo.setUrl(entity.getFileUrl());
        vo.setSize(entity.getFileSize());
        vo.setContentType(entity.getContentType());
        return vo;
    }

    @Override
    public SysFile getById(Long id) {
        SysFile file = fileMapper.selectById(id);
        if (file == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "文件不存在");
        }
        return file;
    }

    private String extractExtension(String filename) {
        int idx = filename.lastIndexOf('.');
        if (idx < 0 || idx == filename.length() - 1) {
            throw new BizException(ResultCode.FILE_TYPE_NOT_ALLOWED, "文件缺少扩展名");
        }
        return filename.substring(idx + 1).toLowerCase(Locale.ROOT);
    }
}
