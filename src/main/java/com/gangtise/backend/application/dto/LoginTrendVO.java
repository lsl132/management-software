package com.gangtise.backend.application.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 欢迎页「我的登录记录」数据。
 *
 * <p>趋势按自然日聚合，**区间内没有登录的日期补 0**，保证前端折线图横轴连续。
 */
@Data
public class LoginTrendVO {

    /** 统计天数（含今天）。 */
    private Integer days;
    /** 区间内登录总次数。 */
    private Integer total;
    /** 单日最大次数，图表纵轴上限（至少为 1，避免除零）。 */
    private Integer max;

    /** 最近一次登录时间，无记录为 null。 */
    private LocalDateTime lastLoginTime;
    /** 最近一次登录IP，无记录或解析不到为 null。 */
    private String lastLoginIp;

    /** 趋势点，按日期升序，每天一个。 */
    private List<Point> points;

    /** 趋势上的单个数据点。 */
    @Data
    @AllArgsConstructor
    public static class Point {

        /** 完整日期 yyyy-MM-dd。 */
        private String date;
        /** 横轴短标签 MM-dd。 */
        private String label;
        /** 当天登录次数。 */
        private Integer count;

    }

}
