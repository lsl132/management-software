package com.gangtise.backend.infrastructure.enums;

import lombok.Getter;

/**
 * 职级序列类型，对应 department_rank.type，取值与职级编码首字母（O/T/F/M/S）一一对应。
 */
@Getter
public enum RankTypeEnum {

    OPERATION(1, "O", "一线操作工种"),
    TECHNOLOGY(2, "T", "技术/研发"),
    LOGISTICS(3, "F", "后勤"),
    MANAGEMENT(4, "M", "管理"),
    SALES(5, "S", "销售");

    private final int value;
    /** 序列字母，与职级编码首字母一致 */
    private final String series;
    private final String description;

    RankTypeEnum(int value, String series, String description) {
        this.value = value;
        this.series = series;
        this.description = description;
    }

    /** 码值转描述，未匹配时原样返回码值。 */
    public static String descriptionOf(Integer value) {
        if (value == null) {
            return null;
        }
        for (RankTypeEnum item : values()) {
            if (item.value == value) {
                return item.description;
            }
        }
        return String.valueOf(value);
    }

    /** 序列标签，形如「O 一线操作工种」，用于下拉选项与列表展示。 */
    public static String labelOf(Integer value) {
        if (value == null) {
            return null;
        }
        for (RankTypeEnum item : values()) {
            if (item.value == value) {
                return item.series + " " + item.description;
            }
        }
        return String.valueOf(value);
    }

}
