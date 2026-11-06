package com.equipcare.common.exception;

import com.equipcare.common.result.ResultCode;

/**
 * 业务异常。由全局异常处理器统一转换为标准响应，不打印堆栈（属于预期内的业务分支）。
 */
public class BizException extends RuntimeException {

    private final ResultCode resultCode;
    private final transient Object[] args;

    public BizException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.resultCode = resultCode;
        this.args = null;
    }

    public BizException(ResultCode resultCode, String message) {
        super(message);
        this.resultCode = resultCode;
        this.args = null;
    }

    public BizException(ResultCode resultCode, String message, Object... args) {
        super(message);
        this.resultCode = resultCode;
        this.args = args;
    }

    public ResultCode getResultCode() {
        return resultCode;
    }

    public Object[] getArgs() {
        return args;
    }

    public static BizException of(ResultCode resultCode) {
        return new BizException(resultCode);
    }

    public static BizException notFound(String what) {
        return new BizException(ResultCode.DATA_NOT_FOUND, what + "不存在");
    }

    public static BizException duplicate(String what) {
        return new BizException(ResultCode.DATA_DUPLICATE, what + "已存在");
    }

    public static BizException inUse(String message) {
        return new BizException(ResultCode.DATA_IN_USE, message);
    }

    public static BizException forbidden(String message) {
        return new BizException(ResultCode.OPERATION_FORBIDDEN, message);
    }
}
