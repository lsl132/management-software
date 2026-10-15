package com.gangtise.backend.infrastructure.exception;

import com.gangtise.backend.application.dto.ApiResult;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 接口层统一异常处理。
 *
 * <p>仅拦截 {@code interfaces.facade} 下的 JSON 接口，页面渲染异常仍交给 Spring Boot 的错误页机制，
 * 避免把 HTML 请求也返回成 JSON。
 */
@RestControllerAdvice(basePackages = "com.gangtise.backend.interfaces.facade")
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public ApiResult<Void> handleBizException(BizException e) {
        log.warn("[API] 业务异常 -> code={}, message={}", e.getCode(), e.getMessage());
        return ApiResult.fail(e.getCode(), e.getMessage());
    }

    /** {@code @Valid} 校验失败，取第一条字段错误提示。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResult<Void> handleValidation(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = fieldError == null
                ? "请求参数校验失败"
                : fieldError.getField() + " " + fieldError.getDefaultMessage();
        log.warn("[API] 参数校验失败 -> {}", message);
        return ApiResult.fail(ApiResult.CODE_BAD_REQUEST, message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ApiResult<Void> handleConstraintViolation(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .findFirst()
                .map(ConstraintViolation::getMessage)
                .orElse("请求参数校验失败");
        log.warn("[API] 参数校验失败 -> {}", message);
        return ApiResult.fail(ApiResult.CODE_BAD_REQUEST, message);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ApiResult<Void> handleMissingParam(MissingServletRequestParameterException e) {
        return ApiResult.fail(ApiResult.CODE_BAD_REQUEST, "缺少必要参数：" + e.getParameterName());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ApiResult<Void> handleUnreadableBody(HttpMessageNotReadableException e) {
        log.warn("[API] 请求体解析失败 -> {}", e.getMessage());
        return ApiResult.fail(ApiResult.CODE_BAD_REQUEST, "请求体格式不正确");
    }

    /** 唯一约束冲突（工号、用户名等业务键重复）。 */
    @ExceptionHandler(DuplicateKeyException.class)
    public ApiResult<Void> handleDuplicateKey(DuplicateKeyException e) {
        log.warn("[API] 唯一约束冲突 -> {}", e.getMessage());
        return ApiResult.fail(ApiResult.CODE_BAD_REQUEST, "数据已存在，请检查唯一字段（如工号）是否重复");
    }

    @ExceptionHandler(Exception.class)
    public ApiResult<Void> handleUnexpected(Exception e) {
        log.error("[API] 未预期异常", e);
        return ApiResult.fail(ApiResult.CODE_SERVER_ERROR, "系统繁忙，请稍后重试");
    }

}
