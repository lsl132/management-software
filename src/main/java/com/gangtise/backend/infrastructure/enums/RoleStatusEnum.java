package com.gangtise.backend.infrastructure.enums;

import lombok.Getter;

/**
 * 角色状态，对应 {@code role.status}。
 *
 * <p>角色不做删除保护之外的「软删除」：被用户引用的角色不允许删除，停用仅表示该角色不再适合新分配。
 */
@Getter
public enum RoleStatusEnum {

    ENABLED(1, "启用"),
    DISABLED(2, "停用");

    private final int value;
    private final String description;

    RoleStatusEnum(int value, String description) {
        this.value = value;
        this.description = description;
    }

    /** 码值转描述，未匹配时原样返回码值。 */
    public static String descriptionOf(Integer value) {
        if (value == null) {
            return null;
        }
        for (RoleStatusEnum item : values()) {
            if (item.value == value) {
                return item.description;
            }
        }
        return String.valueOf(value);
    }

}
