package com.management.software.application.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 日程返回结构。
 */
@Data
public class ScheduleVO {

    private Long id;
    /** 归属用户ID，关联 "user".id。 */
    private Integer userId;

    /** 预约时间。 */
    private LocalDateTime scheduleTime;
    /** 提醒时间，为空表示不提醒。 */
    private LocalDateTime remindTime;

    private String title;
    private String intro;
    private LocalDateTime createTime;

}
