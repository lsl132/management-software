package com.management.software.infrastructure.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.management.software.application.dto.ApiResult;
import com.management.software.infrastructure.config.AttendanceProperties;
import com.management.software.infrastructure.utils.IpUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.List;

/**
 * 打卡接口的来源网段拦截器。
 *
 * <p><b>为什么打卡接口不吃登录态</b>：登录用户是这个管理软件的管理员，不是员工；打卡是打卡机
 * 替员工发起的设备行为，设备没有、也不该有管理员会话。所以该接口从 {@code LoginInterceptor}
 * 的管辖里摘出来，改由本拦截器按**来源 IP** 收敛——这是它唯一的门禁。
 *
 * <p><b>只看 {@code getRemoteAddr()}，绝不读 {@code X-Forwarded-For}</b>：透传头由客户端完全可控，
 * 读它等于把白名单交给调用方自己填。服务如果部署在 nginx 之后，请用
 * {@code server.forward-headers-strategy} 让容器在信任直连代理的前提下改写 remoteAddr。
 *
 * <p>白名单为空时**拒绝一切来源**（fail closed）：配置漏了的后果是打卡立刻全失败、日志里原因明确，
 * 而不是接口对全网敞开且无人察觉。
 */
public class AttendancePunchSourceInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AttendancePunchSourceInterceptor.class);

    private final List<IpUtils.Network> allowedNetworks;
    private final ObjectMapper objectMapper;

    public AttendancePunchSourceInterceptor(AttendanceProperties properties, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.allowedNetworks = IpUtils.parseNetworks(properties.getAllowedNetworks());

        // 启动即把生效范围打进日志：白名单配错最难发现的形态是「配了但不生效」
        if (this.allowedNetworks.isEmpty()) {
            log.warn("[考勤] app.attendance.allowed-networks 为空，打卡接口将拒绝一切来源；"
                    + "配置示例：192.168.1.0/24,127.0.0.1/32");
        } else {
            log.info("[考勤] 打卡接口允许的来源网段：{}", this.allowedNetworks);
            for (IpUtils.Network network : this.allowedNetworks) {
                if (network.allowsEverything()) {
                    log.warn("[考勤] 来源网段 {} 的前缀长度为 0，等于放行该协议族的全部地址，请确认这是有意为之",
                            network);
                }
            }
        }
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        String remoteAddr = request.getRemoteAddr();

        if (allowedNetworks.isEmpty()) {
            log.warn("[考勤] 拒绝打卡：未配置来源白名单 -> remoteAddr={}", remoteAddr);
            reject(response, "考勤打卡接口未配置来源网段（app.attendance.allowed-networks），已拒绝该请求");
            return false;
        }

        for (IpUtils.Network network : allowedNetworks) {
            if (network.contains(remoteAddr)) {
                log.debug("[考勤] 放行打卡：remoteAddr={} 命中 {}", remoteAddr, network);
                return true;
            }
        }

        log.warn("[考勤] 拒绝打卡：remoteAddr={} 不在允许网段 {} 内", remoteAddr, allowedNetworks);
        reject(response, "来源地址不在允许网段内，无法打卡");
        return false;
    }

    private void reject(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(
                ApiResult.fail(ApiResult.CODE_FORBIDDEN, message)));
    }

}
