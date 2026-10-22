package com.management.software.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 日程新增/修改入参。
 *
 * <p>用户ID不在入参里：日程归属当前登录用户，由服务端写入并做归属校验。
 */
@Data
public class ScheduleSaveDTO {

    /** 预约时间（日程开始时间）。 */
    @NotNull(message = "不能为空")
    private LocalDateTime scheduleTime;

    /** 提醒时间，可为空表示不提醒；不为空时不能晚于预约时间。 */
    private LocalDateTime remindTime;

    @NotBlank(message = "不能为空")
    @Size(max = 128, message = "长度不能超过 128")
    private String title;

    @Size(max = 255, message = "长度不能超过 255")
    private String intro;

}
