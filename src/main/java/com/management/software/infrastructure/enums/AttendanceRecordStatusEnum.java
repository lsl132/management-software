package com.management.software.infrastructure.enums;

import lombok.Getter;

/**
 * 打卡记录有效性，对应 {@code attendance_record.status}。
 *
 * <p>为什么要有「无效」而不是直接删掉：打卡记录是**原始凭证**，设备误报、重复刷卡、
 * 补录纠错等场景需要把一条记录作废，但作废本身也要留痕（谁在什么时候判的无效）。
 * 因此作废走改状态而不是物理删除，考勤统计只取 {@link #VALID} 的记录。
 */
@Getter
public enum AttendanceRecordStatusEnum {

    /** 有效：参与考勤统计 */
    VALID(1, "有效"),
    /** 无效：不参与统计，记录保留 */
    INVALID(2, "无效");

    private final int value;
    private final String description;

    AttendanceRecordStatusEnum(int value, String description) {
        this.value = value;
        this.description = description;
    }

    /** 码值转描述，未匹配时原样返回码值。 */
    public static String descriptionOf(Integer value) {
        if (value == null) {
            return null;
        }
        for (AttendanceRecordStatusEnum item : values()) {
            if (item.value == value) {
                return item.description;
            }
        }
        return String.valueOf(value);
    }

}
