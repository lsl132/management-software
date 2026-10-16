package com.gangtise.backend.interfaces.facade;

import com.gangtise.backend.infrastructure.config.AttendanceProperties;
import com.gangtise.backend.support.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 来源白名单**可配置**的验证。
 *
 * <p>单独一个测试类只为一件事：证明放行范围确实来自 {@code app.attendance.allowed-networks}，
 * 而不是被硬编码成回环。这类「配置项写了但不生效」的问题单元测试抓不到——它要么看着像
 * 「功能正常」（内网一律打不上卡），要么像「环境问题」。所以这里把配置整体换成一个内网网段，
 * 再断言该网段放行、网段外拒绝、回环仍然放行。
 *
 * <p>用「工号不存在」的 400 作为「已通过拦截器」的证据：能走到业务校验，说明没被 403 拦下。
 */
@SpringBootTest(properties = "app.attendance.allowed-networks=10.1.0.0/16,127.0.0.1")
class AttendancePunchSourceTest extends ApiTestSupport {

    @Autowired
    private AttendanceProperties attendanceProperties;

    @Test
    @DisplayName("配置项按 CIDR 生效：逗号串被解析成网段列表")
    void bindsConfiguredNetworks() {
        assertEquals(List.of("10.1.0.0/16", "127.0.0.1"), attendanceProperties.getAllowedNetworks());
    }

    @Test
    @DisplayName("配置的内网网段放行：请求能走到业务校验（400 而非 403）")
    void allowsConfiguredNetwork() throws Exception {
        mockMvc.perform(post("/api/attendance/punch")
                        .with(remoteAddr("10.1.2.3"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workNo\":\"IT-SRC-UNKNOWN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("工号 IT-SRC-UNKNOWN 不存在，无法打卡"));
    }

    @Test
    @DisplayName("网段外拒绝：10.2.0.1 不在 10.1.0.0/16 内")
    void rejectsNetworkOutsideConfiguredRange() throws Exception {
        mockMvc.perform(post("/api/attendance/punch")
                        .with(remoteAddr("10.2.0.1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workNo\":\"IT-SRC-UNKNOWN\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("自定义配置不会挤掉回环：本机自测仍然可用")
    void keepsLoopbackUsable() throws Exception {
        mockMvc.perform(post("/api/attendance/punch")
                        .with(remoteAddr("127.0.0.1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workNo\":\"IT-SRC-UNKNOWN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    private RequestPostProcessor remoteAddr(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

}
