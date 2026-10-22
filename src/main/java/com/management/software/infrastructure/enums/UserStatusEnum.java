package com.management.software.infrastructure.enums;

import lombok.Getter;

/**
 * 用户状态，对应 {@code "user".status}。
 *
 * <p>系统不提供用户物理删除，废弃账号一律走「冻结」：冻结后 {@code AuthService} 拒绝登录，
 * 但登录记录、日程等历史数据完整保留。
 */
@Getter
public enum UserStatusEnum {

    /** 正常：可登录 */
    NORMAL(1, "正常"),
    /** 冻结：拒绝登录，数据保留 */
    FREEZE(2, "冻结");

    private final int value;
    private final String description;

    UserStatusEnum(int value, String description) {
        this.value = value;
        this.description = description;
    }

    /** 码值转描述，未匹配时原样返回码值。 */
    public static String descriptionOf(Integer value) {
        if (value == null) {
            return null;
        }
        for (UserStatusEnum item : values()) {
            if (item.value == value) {
                return item.description;
            }
        }
        return String.valueOf(value);
    }

    /** 是否正常状态。 */
    public static boolean isNormal(Integer value) {
        return value != null && value == NORMAL.value;
    }

    /** 是否冻结状态。 */
    public static boolean isFreeze(Integer value) {
        return value != null && value == FREEZE.value;
    }

}
