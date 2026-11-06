package com.equipcare.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作审计注解。标注在 Controller 方法上，由 {@code AuditAspect} 异步落库。
 *
 * <pre>
 * &#64;AuditLog(module = "工单", action = "派单")
 * </pre>
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditLog {

    /** 模块名 */
    String module();

    /** 动作名 */
    String action();

    /** 是否记录请求参数（默认记录，敏感字段会被脱敏） */
    boolean saveParam() default true;
}
