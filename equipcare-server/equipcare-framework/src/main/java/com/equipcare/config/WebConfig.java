package com.equipcare.config;

import com.equipcare.common.constant.CommonConstants;
import com.equipcare.common.file.FileStorageProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Web 层配置：跨域与上传目录静态映射。
 *
 * <p>生产环境建议由 Nginx 直接托管上传目录，此处配置保证本地开发可直接预览。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final FileStorageProperties fileStorageProperties;

    public WebConfig(FileStorageProperties fileStorageProperties) {
        this.fileStorageProperties = fileStorageProperties;
    }

    /**
     * CORS 策略（第九期安全整改）：认证走 Authorization 头（非 Cookie），
     * 因此关闭 allowCredentials，消除「任意来源 + 携带凭据」的组合风险；
     * 来源默认放开（*），生产环境经 EQUIPCARE_CORS_ALLOWED_ORIGINS 收敛为白名单。
     */
    @org.springframework.beans.factory.annotation.Value("${equipcare.cors.allowed-origins:*}")
    private String corsAllowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns(java.util.Arrays.stream(corsAllowedOrigins.split(","))
                        .map(String::trim).toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders(CommonConstants.HEADER_TRACE_ID, "Content-Disposition")
                .allowCredentials(false)
                .maxAge(3600);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path uploadPath = Paths.get(fileStorageProperties.getUploadDir()).toAbsolutePath().normalize();
        registry.addResourceHandler(fileStorageProperties.getUrlPrefix() + "/**")
                .addResourceLocations(uploadPath.toUri().toString());
    }
}
