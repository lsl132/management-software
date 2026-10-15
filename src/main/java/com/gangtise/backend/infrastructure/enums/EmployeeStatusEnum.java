package com.gangtise.backend.infrastructure.enums;

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
