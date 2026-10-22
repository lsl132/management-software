package com.management.software.infrastructure.enums;

import lombok.Getter;

/**
 * 流程节点类型，对应 {@code workflow_node.type}。
 *
 * <p>一条流程就是「起始 → 若干审批 → 结束」的链，所以起始与结束节点恰好各一个、
 * 且分列首尾，中间才是可增删的审批节点。这条约束由 {@code WorkflowService} 在保存时校验。
 */
@Getter
public enum WorkflowNodeTypeEnum {

    /** 起始节点：发起人提交，不需要审批配置 */
    START(0, "起始节点"),
    /** 审批节点：需要审批人、审批方式与审批模式 */
    APPROVAL(1, "审批节点"),
    /** 结束节点：流程归档，不需要审批配置 */
    END(2, "结束节点");

    private final int value;
    private final String description;

    WorkflowNodeTypeEnum(int value, String description) {
        this.value = value;
        this.description = description;
    }

    /** 码值转描述，未匹配时原样返回码值。 */
    public static String descriptionOf(Integer value) {
        if (value == null) {
            return null;
        }
        for (WorkflowNodeTypeEnum item : values()) {
            if (item.value == value) {
                return item.description;
            }
        }
        return String.valueOf(value);
    }

    public static boolean isStart(Integer value) {
        return value != null && value == START.value;
    }

    public static boolean isApproval(Integer value) {
        return value != null && value == APPROVAL.value;
    }

    public static boolean isEnd(Integer value) {
        return value != null && value == END.value;
    }

}
