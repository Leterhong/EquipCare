package com.equipcare.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 文档配置。访问 /swagger-ui.html 在线调试，/v3/api-docs 获取 JSON。
 */
@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI equipCareOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("巡维云 EquipCare 后端接口")
                        .description("企业设备巡检与维修管理平台 · 模块化单体后端。"
                                + "登录接口获取 accessToken 后，点击右上角 Authorize 填入即可调试受保护接口。")
                        .version("0.1.0")
                        .contact(new Contact().name("EquipCare Team"))
                        .license(new License().name("Proprietary")))
                .components(new Components().addSecuritySchemes(SECURITY_SCHEME_NAME,
                        new SecurityScheme()
                                .name(SECURITY_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME));
    }
}
