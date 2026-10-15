package com.gangtise.backend.application.dto;

/**
 * 登录成功后的跳转信息。
 *
 * @param redirect 登录成功后前端应跳转的地址
 */
public record LoginResultDTO(String redirect) {
}
