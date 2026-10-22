package com.management.software.infrastructure.enums;

import lombok.Getter;

@Getter
public enum GenderEnum {

    MALE(1, "男"),
    FEMALE(2, "女");

    private int value;
    private String description;

    GenderEnum(int value, String description) {
        this.value = value;
        this.description = description;
    }

    public static String descriptionOf(Integer value) {
        if (value == null) {
            return null;
        }
        for (GenderEnum item : values()) {
            if (item.value == value) {
                return item.description;
            }
        }
        return String.valueOf(value);
    }

}
