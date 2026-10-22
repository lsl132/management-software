package com.management.software.infrastructure.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.management.software.application.dto.ApiResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * 后台登录态拦截器。
 *
 * <p>未登录时：{@code /api/**} 返回 401 JSON（前端统一跳登录页），其余请求 302 到登录页。
 */
public class LoginInterceptor implements HandlerInterceptor {

    /** 会话中保存当前登录用户的 key。 */
    public static final String SESSION_USER = "LOGIN_USER";

    private static final Logger log = LoggerFactory.getLogger(LoginInterceptor.class);

    private static final String LOGIN_PAGE = "/login";
    private static final String API_PREFIX = "/api/";

    private final ObjectMapper objectMapper;

    public LoginInterceptor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute(SESSION_USER) != null) {
            return true;
        }

        String uri = request.getRequestURI();
        log.debug("[AUTH] 未登录访问 -> {}", uri);
        if (uri.startsWith(API_PREFIX)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(objectMapper.writeValueAsString(
                    ApiResult.fail(ApiResult.CODE_UNAUTHORIZED, "未登录或登录已过期")));
        } else {
            response.sendRedirect(request.getContextPath() + LOGIN_PAGE);
        }
        return false;
    }

}
