package com.equipcare.common.file;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

import java.util.List;

/**
 * 文件存储配置。
 */
@Component
@ConfigurationProperties(prefix = "equipcare.file")
public class FileStorageProperties {

    /** 本地存储根目录，支持相对路径与绝对路径 */
    private String uploadDir = "./data/upload";

    /** 单文件大小上限 */
    private DataSize maxSize = DataSize.ofMegabytes(10);

    /** 允许的扩展名（小写，不带点） */
    private List<String> allowedExtensions = List.of("jpg", "jpeg", "png", "pdf", "docx", "xlsx");

    /** 对外访问前缀，与 WebMvcConfig 的资源映射保持一致 */
    private String urlPrefix = "/upload";

    public String getUploadDir() {
        return uploadDir;
    }

    public void setUploadDir(String uploadDir) {
        this.uploadDir = uploadDir;
    }

    public DataSize getMaxSize() {
        return maxSize;
    }

    public void setMaxSize(DataSize maxSize) {
        this.maxSize = maxSize;
    }

    public List<String> getAllowedExtensions() {
        return allowedExtensions;
    }

    public void setAllowedExtensions(List<String> allowedExtensions) {
        this.allowedExtensions = allowedExtensions;
    }

    public String getUrlPrefix() {
        return urlPrefix;
    }

    public void setUrlPrefix(String urlPrefix) {
        this.urlPrefix = urlPrefix;
    }
}
