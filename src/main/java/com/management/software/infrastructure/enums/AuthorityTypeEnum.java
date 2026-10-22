package com.management.software.infrastructure.enums;

import lombok.Getter;

/**
 * 权限类型，对应 authority.type。
 *
 * <p>权限按两级组织：模块分组（菜单）下挂功能页；助记符的万位区分分组
 * （10000 系统设置 / 20000 人力资源 / 30000 财务管理 / 40000 生产制造 / 50000 战略决策）。
 */
@Getter
public enum AuthorityTypeEnum {

    /** 模块分组（菜单），助记符形如 10000、20000 */
    GROUP(1, "模块分组"),
    /** 功能页，助记符形如 10001、20001 */
    PAGE(2, "功能页");

    private final int value;
    private final String description;

    AuthorityTypeEnum(int value, String description) {
        this.value = value;
        this.description = description;
    }

    /** 码值转描述，未匹配时原样返回码值。 */
    public static String descriptionOf(Integer value) {
        if (value == null) {
            return null;
        }
        for (AuthorityTypeEnum item : values()) {
            if (item.value == value) {
                return item.description;
            }
        }
        return String.valueOf(value);
    }

    /** 是否模块分组（菜单）。 */
    public static boolean isGroup(Integer value) {
        return value != null && value == GROUP.value;
    }

    /** 是否功能页。 */
    public static boolean isPage(Integer value) {
        return value != null && value == PAGE.value;
    }

}
