package com.gangtise.backend.infrastructure.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 客户端 IP 解析单测：代理头优先级、多值取首个、unknown 忽略、remoteAddr 兜底，
 * 以及非法 / 伪造内容一律拒绝（白名单校验）。
 */
class IpUtilsTest {

    @Test
    @DisplayName("优先取 X-Forwarded-For 中的第一个地址（最靠近客户端）")
    void takesFirstAddressFromForwardedFor() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "203.0.113.9, 10.0.0.2, 10.0.0.3");
        assertEquals("203.0.113.9", IpUtils.getClientIp(request));
    }

    @Test
    @DisplayName("没有 X-Forwarded-For 时依次尝试 X-Real-IP 等代理头")
    void fallsBackToOtherProxyHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Real-IP", "198.51.100.7");
        assertEquals("198.51.100.7", IpUtils.getClientIp(request));
    }

    @Test
    @DisplayName("代理头为 unknown 时忽略，继续向下解析")
    void ignoresUnknownHeaderValue() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "unknown");
        request.addHeader("X-Real-IP", "198.51.100.8");
        assertEquals("198.51.100.8", IpUtils.getClientIp(request));
    }

    @Test
    @DisplayName("无任何代理头时使用 remoteAddr")
    void usesRemoteAddrByDefault() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.20");
        assertEquals("192.168.1.20", IpUtils.getClientIp(request));
    }

    @Test
    @DisplayName("IPv6 地址原样保留")
    void keepsIpv6Address() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("0:0:0:0:0:0:0:1");
        request.addHeader("X-Forwarded-For", "::ffff:192.168.1.1");
        assertEquals("::ffff:192.168.1.1", IpUtils.getClientIp(request));
    }

    @Test
    @DisplayName("伪造的非法内容（SQL 片段）被拒，回落到 remoteAddr")
    void ignoresMalformedHeaderValue() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "'; drop table employee; --");
        assertEquals("127.0.0.1", IpUtils.getClientIp(request));
    }

    @Test
    @DisplayName("超长代理头被拒（不是合法 IP），回落到 remoteAddr")
    void ignoresOverlongHeaderValue() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "1".repeat(120));
        assertEquals("127.0.0.1", IpUtils.getClientIp(request));
    }

    @Test
    @DisplayName("非法 octet（256.x）不算 IPv4，回落到 remoteAddr")
    void rejectsInvalidIpv4Octet() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.20");
        request.addHeader("X-Forwarded-For", "256.1.1.1");
        assertEquals("192.168.1.20", IpUtils.getClientIp(request));
    }

    @Test
    @DisplayName("请求为 null 或 IP 解析不到时返回 null")
    void returnsNullWhenUnavailable() {
        assertNull(IpUtils.getClientIp(null));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("");
        assertNull(IpUtils.getClientIp(request));
    }

}
