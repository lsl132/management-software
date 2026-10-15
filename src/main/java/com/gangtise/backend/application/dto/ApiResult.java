package com.gangtise.backend.application.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 统一接口返回体。
 *
 * <p>{@code code} 为 0 表示成功，非 0 表示失败；前端统一按 {@code code} 判断。
 *
 * @param <T> 业务数据类型
 */
@Data
@AllArgsConstructor
public class ApiResult<T> {

    /** 成功。 */
    public static final int CODE_SUCCESS = 0;
    /** 参数或业务校验失败。 */
    public static final int CODE_BAD_REQUEST = 400;
    /** 未登录或登录态已失效。 */
    public static final int CODE_UNAUTHORIZED = 401;
    /** 服务端异常。 */
    public static final int CODE_SERVER_ERROR = 500;

    private int code;
    private String message;
    private T data;

    public static <T> ApiResult<T> ok() {
        return new ApiResult<>(CODE_SUCCESS, null, null);
    }

    public static <T> ApiResult<T> ok(T data) {
        return new ApiResult<>(CODE_SUCCESS, null, data);
    }

    public static <T> ApiResult<T> fail(int code, String message) {
        return new ApiResult<>(code, message, null);
    }

}
