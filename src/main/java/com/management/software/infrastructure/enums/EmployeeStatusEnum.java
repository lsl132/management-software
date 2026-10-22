package com.management.software.infrastructure.enums;

import lombok.Getter;

@Getter
public enum EmployeeStatusEnum {

    TRIAL(0,"试用期"),
    FORMAL(1,"正式"),
    DISMISS(2,"辞退"),
    RESIGN(3,"离职");

    private int status;
    private String description;

    EmployeeStatusEnum(int status, String description) {
        this.status = status;
        this.description = description;
    }

    /**
     * 是否在职。
     *
     * <p>「在职」= 试用期 + 正式，辞退与离职都属于已离开公司。判定统一收口在这里：
     * 打卡接口据此拒绝离职员工打卡，考勤统计据此决定统计范围，两边口径必须一致，
     * 各写一份 {@code status == 0 || status == 1} 早晚会改岔。
     */
    public static boolean isActive(Integer status) {
        return status != null && (status == TRIAL.status || status == FORMAL.status);
    }

    /** 码值转描述，未匹配时原样返回码值。 */
    public static String descriptionOf(Integer status) {
        if (status == null) {
            return null;
        }
        for (EmployeeStatusEnum item : values()) {
            if (item.status == status) {
                return item.description;
            }
        }
        return String.valueOf(status);
    }

}
