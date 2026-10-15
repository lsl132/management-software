package com.gangtise.backend.infrastructure.exception;

import com.gangtise.backend.application.dto.ApiResult;

/**
 * 业务异常：可预期的业务/参数错误，由 {@link GlobalExceptionHandler} 转成统一返回体。
 */
public class BizException extends RuntimeException {

    private final int code;

    public BizException(String message) {
        this(ApiResult.CODE_BAD_REQUEST, message);
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BizException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static BizException unauthorized(String message) {
        return new BizException(ApiResult.CODE_UNAUTHORIZED, message);
    }

}
