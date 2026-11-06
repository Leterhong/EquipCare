package com.equipcare;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 巡维云 EquipCare 后端启动类。
 *
 * <p>Maven 多模块结构（依赖自底向上）：
 * <ol>
 *   <li>equipcare-common —— 通用基础：统一响应/异常/工具、基础实体、@AuditLog 注解、登录上下文</li>
 *   <li>equipcare-system —— 系统管理：用户/角色/部门/文件</li>
 *   <li>equipcare-framework —— 框架核心：安全(JWT)、基础设施配置、审计切面</li>
 *   <li>equipcare-business —— 业务模块：asset/workorder/inspection/inventory/analytics/export/notification</li>
 *   <li>equipcare-admin —— 本模块：启动类、bootstrap 引导、配置与静态前端、集成测试</li>
 * </ol>
 *
 * <p>scanBasePackages 显式指定根包，保证各模块 jar 中的 Bean 均被扫描。
 */
@SpringBootApplication(scanBasePackages = "com.equipcare")
@MapperScan({"com.equipcare.**.mapper", "com.equipcare.outbox"})
@EnableAsync
@EnableScheduling
public class EquipCareApplication {

    public static void main(String[] args) {
        SpringApplication.run(EquipCareApplication.class, args);
    }
}
