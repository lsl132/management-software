package com.gangtise.backend.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 流程节点保存入参。
 *
 * <p><b>顺序不由这里传</b>：节点次序直接取它在 {@code nodes} 数组里的下标，
 * 保存时统一重排。让客户端同时传顺序和数组，两者不一致时就得靠约定去猜谁对——
 * 数组本身就是顺序，不必再有一份可以与它矛盾的字段。
 *
 * <p><b>{@code id} 有值表示改已有节点、无值表示新增</b>：这是差量同步的判据，
 * 也是「编辑流程时不把老节点整批删掉重建」的前提（见 {@code WorkflowService#syncNodes}）。
 */
@Data
public class WorkflowNodeSaveDTO {

    /** 已有节点ID；新增节点留空。 */
    private Integer id;

    /** 节点名称，如「部门主管审批」；起始/结束节点也允许自定义（默认「发起申请」「审批完成」）。 */
    @NotBlank(message = "不能为空")
    @Size(max = 128, message = "长度不能超过 128")
    private String nodeName;

    /** 节点类型，见 WorkflowNodeTypeEnum：0 起始 / 1 审批 / 2 结束。 */
    @NotNull(message = "不能为空")
    private Integer type;

    /** 审批方式，见 ApprovalTypeEnum；仅审批节点需要。 */
    private Integer approvalType;

    /**
     * 审批配置值：{@code approvalType=1} 时是 {@code "user".id} 列表，
     * {@code approvalType=3} 时是 {@code role.id} 列表。落库前拼成逗号分隔的字符串。
     */
    private List<Integer> approvalValues;

    /** 审批模式，见 ApprovalModeEnum：1 或签 / 2 会签；仅审批节点需要。 */
    private Integer approvalMode;

}
