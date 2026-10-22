package com.management.software.infrastructure.enums;

import lombok.Getter;

@Getter
public enum EmployeeEducationEnum {

    JHS(0,"初中或以下"),
    SHS(1,"高中/技校/职业学校"),
    AD(2,"大专"),
    BD(3,"本科"),
    MST(4,"硕士"),
    PhD(5,"博士");

    private int status;
    private String description;

    EmployeeEducationEnum(int status, String description) {
        this.status = status;
        this.description = description;
    }

    /** 码值转描述，未匹配时原样返回码值。 */
    public static String descriptionOf(Integer status) {
        if (status == null) {
            return null;
        }
        for (EmployeeEducationEnum item : values()) {
            if (item.status == status) {
                return item.description;
            }
        }
        return String.valueOf(status);
    }

}
