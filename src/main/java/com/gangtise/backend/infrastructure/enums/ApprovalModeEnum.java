package com.gangtise.backend.infrastructure.enums;

import lombok.Getter;

/**
 * 审批模式，对应 {@code workflow_node.approval_mode}（仅审批节点有值）。
 *
 * <p>只在审批人不止一个时才有区别：或签（任意一人通过即可）与会签（全部通过才生效）。
 * 单人的审批节点两种模式等价，但这里不强制归一，避免把用户显式选过的配置悄悄改掉。
 */
@Getter
public enum ApprovalModeEnum {

    /** 或签：一人通过即生效 */
    ANY(1, "或签（一人通过即生效）"),
    /** 会签：全员通过才生效 */
    ALL(2, "会签（全员通过才生效）");

    private final int value;
    private final String description;

    ApprovalModeEnum(int value, String description) {
        this.value = value;
        this.description = description;
    }

    /** 码值转描述，未匹配时原样返回码值。 */
    public static String descriptionOf(Integer value) {
        if (value == null) {
            return null;
        }
        for (ApprovalModeEnum item : values()) {
            if (item.value == value) {
                return item.description;
            }
        }
        return String.valueOf(value);
    }

    /** 码值是否合法。 */
    public static boolean isValid(Integer value) {
        if (value == null) {
            return false;
        }
        for (ApprovalModeEnum item : values()) {
            if (item.value == value) {
                return true;
            }
        }
        return false;
    }

}
