package com.management.software.application.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 流程视图。
 *
 * <p>{@code nodes} 只在详情接口里填充：分页列表用 {@code nodeSummary} 一行带过即可，
 * 把每条流程的节点全量塞进列表响应，翻一页就要多传几十个节点对象，
 * 而列表上一个字都用不到——需要细节时再点开详情。
 */
@Data
public class WorkflowVO {

    private Integer id;

    private String icon;

    private String workflowName;

    private String description;

    /** 状态，见 WorkflowStatusEnum。 */
    private Integer status;

    private String statusText;

    /** 全部节点数（含起始与结束）。 */
    private Integer nodeCount;

    /** 审批节点数（不含起始与结束），列表里用来说明「要过几道审批」。 */
    private Integer approvalNodeCount;

    /** 节点链路摘要，如「发起申请 → 部门主管审批 → 审批完成」。 */
    private String nodeSummary;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /** 节点明细，仅详情接口返回。 */
    private List<WorkflowNodeVO> nodes;

}
