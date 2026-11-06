package com.equipcare.common.result;

/**
 * 统一业务返回码。
 *
 * <p>编码约定：
 * <ul>
 *   <li>2xx：成功</li>
 *   <li>4xx / 5xx：与 HTTP 语义对齐的基础错误</li>
 *   <li>1xxx：认证与授权</li>
 *   <li>2xxx：通用数据错误</li>
 *   <li>3xxx：工单领域</li>
 *   <li>4xxx：库存领域</li>
 *   <li>5xxx：文件领域</li>
 * </ul>
 */
public enum ResultCode {

    SUCCESS(200, 200, "success"),

    BAD_REQUEST(400, 400, "请求参数错误"),
    UNAUTHORIZED(401, 401, "未登录或登录已过期"),
    FORBIDDEN(403, 403, "无访问权限"),
    NOT_FOUND(404, 404, "资源不存在"),
    METHOD_NOT_ALLOWED(405, 405, "请求方法不支持"),
    NOT_ACCEPTABLE(406, 406, "响应格式不支持"),
    CONFLICT(409, 409, "数据状态冲突"),
    PAYLOAD_TOO_LARGE(413, 413, "请求内容过大"),
    VALIDATION_FAILED(422, 422, "参数校验失败"),
    LOCKED(423, 423, "资源已被锁定"),
    INTERNAL_ERROR(500, 500, "系统内部错误"),

    AUTH_BAD_CREDENTIALS(1001, 401, "用户名或密码错误"),
    AUTH_ACCOUNT_DISABLED(1002, 403, "账号已被禁用"),
    AUTH_ACCOUNT_LOCKED(1003, 423, "账号已锁定，请稍后再试"),
    AUTH_TOKEN_INVALID(1004, 401, "令牌无效"),
    AUTH_TOKEN_EXPIRED(1005, 401, "令牌已过期"),
    AUTH_REFRESH_INVALID(1006, 401, "刷新令牌无效"),
    AUTH_OLD_PASSWORD_WRONG(1007, 400, "原密码不正确"),

    DATA_NOT_FOUND(2001, 404, "数据不存在"),
    DATA_DUPLICATE(2002, 409, "数据已存在"),
    DATA_IN_USE(2003, 409, "数据被引用，无法删除"),
    OPERATION_FORBIDDEN(2004, 409, "当前状态不允许该操作"),

    WO_ILLEGAL_TRANSITION(3001, 409, "非法的工单状态流转"),
    WO_STATE_CHANGED(3002, 409, "工单状态已变更，请刷新后重试"),
    WO_SELF_VERIFY_FORBIDDEN(3003, 403, "维修人不得验收本人工单"),

    STOCK_INSUFFICIENT(4001, 422, "库存不足"),

    FILE_EMPTY(5001, 400, "上传文件为空"),
    FILE_TYPE_NOT_ALLOWED(5002, 400, "不支持的文件类型"),
    FILE_TOO_LARGE(5003, 413, "文件超过大小限制"),

    /** 安全关键操作在依赖组件（Redis 等）不可用时安全失败，绝不降级为无验证模式 */
    SERVICE_UNAVAILABLE(6001, 503, "服务暂不可用，请稍后重试");

    private final int code;
    private final int httpStatus;
    private final String message;

    ResultCode(int code, int httpStatus, String message) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String getMessage() {
        return message;
    }
}
