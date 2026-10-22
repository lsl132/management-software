package com.management.software.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * 考勤相关配置，对应 {@code app.attendance.*}。
 *
 * <p>把上下班时间做成配置而不是常量：迟到/早退的判定基准因公司、季节而变，
 * 写死在代码里改一次就要发一次版。生产环境可用环境变量覆盖（见 application.yml）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.attendance")
public class AttendanceProperties {

    /** 考勤统计定时任务开关，关闭后 {@code AttendanceStatisticsTask} 只记日志不写库。 */
    private boolean enabled = true;

    /** 上班时间：当天最早打卡晚于它记「迟到」，等于它不算迟到。 */
    private LocalTime workStart = LocalTime.of(9, 0);

    /** 下班时间：最晚打卡早于它记「早退」，晚于它的部分计入加班。 */
    private LocalTime workEnd = LocalTime.of(18, 0);

    /**
     * 判定「属于哪一天」所用的时区（如 {@code Asia/Shanghai}）。
     *
     * <p>必须显式配置而不能用 JVM 默认时区：容器镜像默认多半是 UTC，会把 08:00 的打卡
     * 算到前一天，迟到早退全判错。
     */
    private String zone = "Asia/Shanghai";

    /** 解析后的时区。 */
    public ZoneId zoneId() {
        return ZoneId.of(zone);
    }

    /**
     * 允许调用打卡接口的来源网段（CIDR，逗号分隔，元素也允许写成裸 IP）。
     *
     * <p>打卡接口面向打卡机这类**无会话设备**，不校验登录，来源网段就是它唯一的门禁；
     * 默认只放行本机回环，部署时必须把打卡机所在网段显式加进来（如 {@code 192.168.1.0/24}）。
     * 留空表示拒绝一切来源——宁可打不上卡后立刻发现，也不要默认敞开。
     */
    private List<String> allowedNetworks = new ArrayList<>(List.of("127.0.0.1/32", "::1/128"));

}
