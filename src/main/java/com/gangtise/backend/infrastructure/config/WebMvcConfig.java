package com.gangtise.backend.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gangtise.backend.infrastructure.interceptor.AttendancePunchSourceInterceptor;
import com.gangtise.backend.infrastructure.interceptor.LoginInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置：注册登录态拦截器与打卡接口的来源网段拦截器。
 *
 * <p>放行登录页、登录接口与静态资源；错误页必须放行，否则未登录时会陷入重定向循环。
 *
 * <p><b>打卡接口是唯一的例外</b>：它由打卡机这类无会话设备调用，登录拦截对它没有意义，
 * 因此从登录拦截里摘出，改由 {@link AttendancePunchSourceInterceptor} 按来源 IP 管控。
 * 两个拦截器的管辖范围互不重叠——登录拦截排除该路径，网段拦截只挂该路径。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /** 打卡接口路径。设备调用，无会话，见类注释。 */
    private static final String ATTENDANCE_PUNCH_PATH = "/api/attendance/punch";

    private static final String[] EXCLUDE_PATTERNS = {
            "/login",
            "/api/auth/**",
            "/css/**",
            "/js/**",
            "/img/**",
            // 第三方前端库（Quill / DOMPurify）本地内置目录，按静态资源处理
            "/vendor/**",
            "/favicon.ico",
            "/error",
            "/error/**",
            // 打卡机等设备调用，没有会话；访问控制由 AttendancePunchSourceInterceptor 按来源网段承担
            ATTENDANCE_PUNCH_PATH
    };

    private final ObjectMapper objectMapper;

    private final AttendanceProperties attendanceProperties;

    public WebMvcConfig(ObjectMapper objectMapper, AttendanceProperties attendanceProperties) {
        this.objectMapper = objectMapper;
        this.attendanceProperties = attendanceProperties;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LoginInterceptor(objectMapper))
                .addPathPatterns("/**")
                .excludePathPatterns(EXCLUDE_PATTERNS);

        registry.addInterceptor(new AttendancePunchSourceInterceptor(attendanceProperties, objectMapper))
                .addPathPatterns(ATTENDANCE_PUNCH_PATH);
    }

}
