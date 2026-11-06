package com.equipcare.common.exception;

import com.equipcare.common.result.Result;
import com.equipcare.common.result.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理。所有异常统一转换为 {@link Result} 结构，并保留 HTTP 语义状态码。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 业务异常：预期内的分支，仅记录 warn，不打印堆栈 */
    @ExceptionHandler(BizException.class)
    public ResponseEntity<Result<Void>> handleBiz(BizException ex, HttpServletRequest request) {
        ResultCode code = ex.getResultCode();
        log.warn("业务异常 uri={} code={} message={}", request.getRequestURI(), code.getCode(), ex.getMessage());
        return ResponseEntity.status(code.getHttpStatus())
                .body(Result.fail(code.getCode(), ex.getMessage()));
    }

    /** 请求体参数校验失败（@RequestBody + @Valid） */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleInvalidBody(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));
        return ResponseEntity.status(ResultCode.VALIDATION_FAILED.getHttpStatus())
                .body(Result.fail(ResultCode.VALIDATION_FAILED.getCode(), detail));
    }

    /** 表单/查询参数绑定校验失败 */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<Result<Void>> handleBind(BindException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));
        return ResponseEntity.status(ResultCode.VALIDATION_FAILED.getHttpStatus())
                .body(Result.fail(ResultCode.VALIDATION_FAILED.getCode(), detail));
    }

    /** 方法参数上的约束校验失败（@Validated + @RequestParam） */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleConstraint(ConstraintViolationException ex) {
        String detail = ex.getConstraintViolations().stream()
                .map(this::formatViolation)
                .collect(Collectors.joining("; "));
        return ResponseEntity.status(ResultCode.VALIDATION_FAILED.getHttpStatus())
                .body(Result.fail(ResultCode.VALIDATION_FAILED.getCode(), detail));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            IllegalArgumentException.class})
    public ResponseEntity<Result<Void>> handleBadRequest(Exception ex) {
        log.warn("请求参数错误: {}", ex.getMessage());
        return ResponseEntity.status(ResultCode.BAD_REQUEST.getHttpStatus())
                .body(Result.fail(ResultCode.BAD_REQUEST.getCode(), "请求参数格式不正确"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(ResultCode.METHOD_NOT_ALLOWED.getHttpStatus())
                .body(Result.fail(ResultCode.METHOD_NOT_ALLOWED.getCode(),
                        "不支持的请求方法：" + ex.getMethod()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Void>> handleNoResource(NoResourceFoundException ex) {
        return ResponseEntity.status(ResultCode.NOT_FOUND.getHttpStatus())
                .body(Result.fail(ResultCode.NOT_FOUND.getCode(), "接口不存在：" + ex.getResourcePath()));
    }

    /**
     * 内容协商失败：例如请求头 {@code Accept: application/json} 却访问只输出 PNG 的二维码接口。
     * 正确的语义是 406，而不是落到兜底 500。
     */
    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<Result<Void>> handleNotAcceptable(HttpMediaTypeNotAcceptableException ex) {
        return ResponseEntity.status(ResultCode.NOT_ACCEPTABLE.getHttpStatus())
                .body(Result.fail(ResultCode.NOT_ACCEPTABLE.getCode(),
                        "接口不支持的响应格式，请调整 Accept 请求头"));
    }

    /** 方法级权限校验失败（@PreAuthorize） */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result<Void>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(ResultCode.FORBIDDEN.getHttpStatus())
                .body(Result.fail(ResultCode.FORBIDDEN));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Result<Void>> handleAuthentication(AuthenticationException ex) {
        return ResponseEntity.status(ResultCode.UNAUTHORIZED.getHttpStatus())
                .body(Result.fail(ResultCode.UNAUTHORIZED));
    }

    /** 唯一索引冲突：兜底转换为可读提示 */
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<Result<Void>> handleDuplicateKey(DuplicateKeyException ex) {
        log.warn("唯一约束冲突: {}", ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(ResultCode.DATA_DUPLICATE.getHttpStatus())
                .body(Result.fail(ResultCode.DATA_DUPLICATE.getCode(), "数据已存在，请检查唯一字段"));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Result<Void>> handleUploadSize(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(ResultCode.FILE_TOO_LARGE.getHttpStatus())
                .body(Result.fail(ResultCode.FILE_TOO_LARGE));
    }

    /**
     * Redis 连接异常兜底：统一转为 503 可理解的提示，不泄漏堆栈 / 连接串 / 密码。
     * 多数场景已由 TokenStore 内部降级处理，此handler 拦截其余直连 Redis 的路径。
     */
    @ExceptionHandler(org.springframework.data.redis.RedisConnectionFailureException.class)
    public ResponseEntity<Result<Void>> handleRedisDown(org.springframework.data.redis.RedisConnectionFailureException ex) {
        log.error("Redis 连接异常: {}", ex.getClass().getSimpleName());
        return ResponseEntity.status(ResultCode.SERVICE_UNAVAILABLE.getHttpStatus())
                .body(Result.fail(ResultCode.SERVICE_UNAVAILABLE));
    }

    /** 兜底：未预期异常。打印完整堆栈并返回 traceId，便于对日志排查 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleUnexpected(Exception ex, HttpServletRequest request) {
        String traceId = MDC.get("traceId");
        log.error("系统异常 uri={} traceId={}", request.getRequestURI(), traceId, ex);
        String message = "系统繁忙，请稍后重试（traceId=" + traceId + "）";
        return ResponseEntity.status(ResultCode.INTERNAL_ERROR.getHttpStatus())
                .body(Result.fail(ResultCode.INTERNAL_ERROR.getCode(), message));
    }

    private String formatFieldError(FieldError error) {
        return error.getField() + ": " + error.getDefaultMessage();
    }

    private String formatViolation(ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath().toString();
        int idx = path.lastIndexOf('.');
        String field = idx >= 0 ? path.substring(idx + 1) : path;
        return field + ": " + violation.getMessage();
    }
}
