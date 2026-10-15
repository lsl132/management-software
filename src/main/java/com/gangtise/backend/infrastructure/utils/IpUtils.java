package com.gangtise.backend.infrastructure.utils;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

/**
 * 客户端 IP 获取工具。
 *
 * <p>服务部署在 nginx / 网关之后时，{@code request.getRemoteAddr()} 拿到的是代理地址，
 * 因此按「代理透传头优先、remoteAddr 兜底」的顺序解析。
 *
 * <p><b>安全说明</b>：{@code X-Forwarded-For} 等请求头由客户端可控，仅可作为审计参考，
 * <b>不得用于鉴权或风控判定</b>。因此这里做的是**白名单校验**而不是字符过滤——
 * 只有能解析成 IPv4 / IPv6 的内容才会被采用，其余一律视为解析失败，
 * 避免伪造内容（SQL 片段、超长串等）以任何形式落到库里。
 */
public final class IpUtils {

    /** 常见反向代理透传头，按优先级依次尝试 */
    private static final String[] PROXY_HEADERS = {
            "X-Forwarded-For",
            "X-Real-IP",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_CLIENT_IP",
            "HTTP_X_FORWARDED_FOR"
    };

    private static final String UNKNOWN = "unknown";

    /** 严格 IPv4：四段点分十进制，每段 0~255 */
    private static final Pattern IPV4 = Pattern.compile(
            "^(?:25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)(?:\\.(?:25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)){3}$");

    /**
     * 宽松 IPv6：必须含冒号，只允许十六进制字符、冒号与点（兼容 {@code ::ffff:192.168.1.1} 这类
     * IPv4 映射写法），长度不超过 IPv6 上限 45。审计字段不追求严格的段数校验。
     */
    private static final Pattern IPV6 = Pattern.compile("^(?=.*:)[0-9a-fA-F:.]{2,45}$");

    private IpUtils() {
    }

    /**
     * 解析客户端 IP，解析不到时返回 {@code null}。
     *
     * @param request 当前请求，可为 null
     */
    public static String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        for (String header : PROXY_HEADERS) {
            String candidate = firstAddress(request.getHeader(header));
            if (candidate != null) {
                return candidate;
            }
        }
        return parse(request.getRemoteAddr());
    }

    /**
     * 取代理头中的第一个地址：{@code X-Forwarded-For} 形如
     * {@code client, proxy1, proxy2}，第一个才是客户端地址。
     */
    private static String firstAddress(String headerValue) {
        if (!StringUtils.hasText(headerValue)) {
            return null;
        }
        String value = headerValue.trim();
        if (UNKNOWN.equalsIgnoreCase(value)) {
            return null;
        }
        int comma = value.indexOf(',');
        if (comma > 0) {
            value = value.substring(0, comma).trim();
        }
        if (UNKNOWN.equalsIgnoreCase(value)) {
            return null;
        }
        return parse(value);
    }

    /** 必须是合法 IPv4 / IPv6 才采用，否则返回 null */
    private static String parse(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String ip = value.trim();
        if (IPV4.matcher(ip).matches() || IPV6.matcher(ip).matches()) {
            return ip;
        }
        return null;
    }

}
