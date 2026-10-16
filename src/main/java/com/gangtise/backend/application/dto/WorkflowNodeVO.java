package com.gangtise.backend.application.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 流程节点视图（流程详情与列表的节点摘要都用它）。
 */
@Data
public class WorkflowNodeVO {

    private Integer id;

    /** 节点在流程中的次序，从 0 开始。 */
    private Integer sort;

    private String nodeName;

    /** 节点类型，见 WorkflowNodeTypeEnum。 */
    private Integer type;

    private String typeText;

    /** 审批方式，见 ApprovalTypeEnum；起始/结束节点为空。 */
    private Integer approvalType;

    private String approvalTypeText;

    /** 审批配置值的原始ID列表（指定人员 → 用户ID，指定角色 → 角色ID）。 */
    private List<Integer> approvalValues;

    /** 审批配置值的可读文案（人名 / 角色名，逗号分隔），供列表与详情直接展示。 */
    private String approvalValueText;

    /** 审批模式，见 ApprovalModeEnum；起始/结束节点为空。 */
    private Integer approvalMode;

    private String approvalModeText;

    private LocalDateTime updateTime;

}
