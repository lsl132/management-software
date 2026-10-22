package com.management.software.application.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 打卡结果。
 *
 * <p>{@code punchTime} 回成 {@code LocalDateTime} 而不是库里的 epoch 毫秒：
 * 全局 Jackson 已按 {@code yyyy-MM-dd HH:mm:ss} 输出，前端拿到即可直接展示，
 * 不必各自再写一遍毫秒转字符串（放原始毫秒出去，很容易被前端当成秒用）。
 */
@Data
public class AttendancePunchVO {

    /** 打卡记录ID（{@code attendance_record.id}）。 */
    private Long id;

    /** 员工工号。 */
    private String workNo;

    /** 员工姓名，打卡时顺带回填，省得前端再查一次。 */
    private String employeeName;

    /** 打卡时间（服务端时钟）。 */
    private LocalDateTime punchTime;

    /** 打卡位置。 */
    private String location;

}
