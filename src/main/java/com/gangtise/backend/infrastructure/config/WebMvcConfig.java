package com.gangtise.backend.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gangtise.backend.infrastructure.interceptor.LoginInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置：注册登录态拦截器。
 *
 * <p>放行登录页、登录接口与静态资源；错误页必须放行，否则未登录时会陷入重定向循环。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private static final String[] EXCLUDE_PATTERNS = {
            "/login",
            "/api/auth/**",
            "/css/**",
            "/js/**",
            "/img/**",
            "/favicon.ico",
            "/error",
            "/error/**"
    };

    private final ObjectMapper objectMapper;

    public WebMvcConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LoginInterceptor(objectMapper))
                .addPathPatterns("/**")
                .excludePathPatterns(EXCLUDE_PATTERNS);
    }

}
