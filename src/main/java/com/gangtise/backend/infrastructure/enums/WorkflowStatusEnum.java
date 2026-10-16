package com.gangtise.backend.infrastructure.enums;

import lombok.Getter;

/**
 * 流程状态，对应 {@code workflow.status}。
 *
 * <p>禁用不是删除：停用的流程不再允许发起新的审批（工单），但已经跑在途中的单子要能继续走完，
 * 历史也留得住。删除是另一回事——见 {@code WorkflowService#delete}。
 */
@Getter
public enum WorkflowStatusEnum {

    /** 启用：可被发起审批 */
    ENABLED(1, "启用"),
    /** 禁用：不可发起新审批，历史数据保留 */
    DISABLED(2, "禁用");

    private final int value;
    private final String description;

    WorkflowStatusEnum(int value, String description) {
        this.value = value;
        this.description = description;
    }

    /** 码值转描述，未匹配时原样返回码值。 */
    public static String descriptionOf(Integer value) {
        if (value == null) {
            return null;
        }
        for (WorkflowStatusEnum item : values()) {
            if (item.value == value) {
                return item.description;
            }
        }
        return String.valueOf(value);
    }

    /** 码值是否合法（新增/修改时用它拦掉字典外的取值）。 */
    public static boolean isValid(Integer value) {
        if (value == null) {
            return false;
        }
        for (WorkflowStatusEnum item : values()) {
            if (item.value == value) {
                return true;
            }
        }
        return false;
    }

    public static boolean isEnabled(Integer value) {
        return value != null && value == ENABLED.value;
    }

}
