package com.gangtise.backend.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 流程新增/修改入参（流程头 + 完整节点链，一次提交）。
 *
 * <p><b>为什么节点跟着流程一起提交</b>：审批规则只有在「节点串成的链路」完整时才有意义，
 * 分开保存会出现「流程已改、节点还是老的」这种半截状态。整体提交 + 单事务落库，
 * 要么整条规则生效，要么一点都不变。
 *
 * <p>新增与修改共用同一份结构：修改按全量覆盖处理，留空的可选字段会被清空。
 */
@Data
public class WorkflowSaveDTO {

    /** 流程名称，全公司唯一（库侧 {@code uk_workflow_name}）。 */
    @NotBlank(message = "不能为空")
    @Size(max = 128, message = "长度不能超过 128")
    private String workflowName;

    /** 图标：图标名或图片地址，可空。 */
    @Size(max = 255, message = "长度不能超过 255")
    private String icon;

    @Size(max = 255, message = "长度不能超过 255")
    private String description;

    /** 状态，见 WorkflowStatusEnum：1 启用 / 2 禁用。 */
    @NotNull(message = "不能为空")
    private Integer status;

    /** 节点链，**顺序即流程顺序**；至少要有起始与结束两个节点。 */
    @NotEmpty(message = "不能为空")
    @Valid
    private List<WorkflowNodeSaveDTO> nodes;

}
