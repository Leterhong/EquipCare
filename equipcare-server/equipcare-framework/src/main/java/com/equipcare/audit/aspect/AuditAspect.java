package com.equipcare.audit.aspect;

import com.equipcare.common.annotation.AuditLog;
import com.equipcare.audit.entity.SysAuditLog;
import com.equipcare.audit.service.AuditLogService;
import com.equipcare.common.constant.CommonConstants;
import com.equipcare.common.util.IpUtil;
import com.equipcare.common.security.LoginUser;
import com.equipcare.common.security.SecurityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 审计切面：记录谁在什么时间、通过什么接口、做了什么、结果如何。
 *
 * <p>安全约束：
 * <ul>
 *   <li>参数中的密码类字段写入前脱敏；</li>
 *   <li>文件与 Servlet 对象不序列化，避免大对象与循环引用；</li>
 *   <li>写库异步执行，审计失败不影响业务返回。</li>
 * </ul>
 */
@Aspect
@Component
public class AuditAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);

    /** 敏感字段脱敏规则 */
    private static final Pattern SENSITIVE_FIELD = Pattern.compile(
            "(\"(?:password|oldPassword|newPassword|refreshToken)\"\\s*:\\s*\")[^\"]*(\")");
    private static final int MAX_PARAM_LENGTH = 4000;
    private static final int MAX_ERROR_LENGTH = 900;

    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    public AuditAspect(AuditLogService auditLogService, ObjectMapper objectMapper) {
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
    }

    @Around("@annotation(auditLog)")
    public Object around(ProceedingJoinPoint joinPoint, AuditLog auditLog) throws Throwable {
        long start = System.currentTimeMillis();
        SysAuditLog entity = new SysAuditLog();
        entity.setModule(auditLog.module());
        entity.setAction(auditLog.action());
        entity.setTraceId(MDC.get(CommonConstants.MDC_TRACE_ID));
        entity.setCreateTime(LocalDateTime.now());

        LoginUser loginUser = SecurityUtils.getLoginUserOrNull();
        if (loginUser != null) {
            entity.setUserId(loginUser.getUserId());
            entity.setUsername(loginUser.getUsername());
        }

        HttpServletRequest request = currentRequest();
        if (request != null) {
            entity.setRequestMethod(request.getMethod());
            entity.setRequestUri(request.getRequestURI());
            entity.setIp(IpUtil.getClientIp(request));
            entity.setUserAgent(truncate(request.getHeader("User-Agent"), 300));
        }

        if (auditLog.saveParam()) {
            entity.setRequestParam(serializeArgs(joinPoint.getArgs()));
        }
        // 登录/登出等无认证上下文的动作：从请求参数回填操作者用户名
        if (entity.getUsername() == null) {
            entity.setUsername(extractUsername(joinPoint.getArgs()));
        }

        try {
            Object result = joinPoint.proceed();
            entity.setResultStatus("SUCCESS");
            return result;
        } catch (Throwable ex) {
            entity.setResultStatus("FAIL");
            entity.setErrorMsg(truncate(ex.getMessage(), MAX_ERROR_LENGTH));
            throw ex;
        } finally {
            entity.setCostMs((int) (System.currentTimeMillis() - start));
            try {
                auditLogService.saveAsync(entity);
            } catch (Exception ex) {
                log.warn("提交审计日志失败: {}", ex.getMessage());
            }
        }
    }

    /** 从请求参数中提取 username 字段（用于登录前无认证上下文的场景） */
    private String extractUsername(Object[] args) {
        if (args == null) {
            return null;
        }
        for (Object arg : args) {
            if (arg == null || arg instanceof HttpServletRequest || arg instanceof HttpServletResponse
                    || arg instanceof MultipartFile || arg instanceof MultipartFile[]) {
                continue;
            }
            try {
                com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(
                        objectMapper.writeValueAsString(arg));
                if (node.hasNonNull("username")) {
                    return truncate(node.get("username").asText(), 64);
                }
            } catch (Exception ignored) {
                // 非对象参数或不可序列化：跳过
            }
        }
        return null;
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private String serializeArgs(Object[] args) {
        if (args == null || args.length == 0) {
            return null;
        }
        List<Object> loggable = new ArrayList<>();
        for (Object arg : args) {
            if (arg == null) {
                continue;
            }
            if (arg instanceof HttpServletRequest || arg instanceof HttpServletResponse
                    || arg instanceof MultipartFile || arg instanceof MultipartFile[]) {
                continue;
            }
            loggable.add(arg);
        }
        if (loggable.isEmpty()) {
            return null;
        }
        try {
            String json = objectMapper.writeValueAsString(loggable.size() == 1 ? loggable.get(0) : loggable);
            return truncate(SENSITIVE_FIELD.matcher(json).replaceAll("$1***$2"), MAX_PARAM_LENGTH);
        } catch (Exception ex) {
            return truncate(loggable.toString(), MAX_PARAM_LENGTH);
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
