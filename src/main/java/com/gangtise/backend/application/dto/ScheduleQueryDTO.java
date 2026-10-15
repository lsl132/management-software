package com.gangtise.backend.application.dto;

import lombok.Data;

/**
 * 日程查询条件（GET 查询参数绑定）。
 *
 * <p>一律按「当前登录用户 + 时间区间」查询：{@code month} 为空时取当前月份，
 * 欢迎页日历直接复用同一接口。
 */
@Data
public class ScheduleQueryDTO {

    /** 月份，格式 yyyy-MM；为空取当前月。 */
    private String month;

    /** 关键字，模糊匹配标题。 */
    private String keyword;

}
