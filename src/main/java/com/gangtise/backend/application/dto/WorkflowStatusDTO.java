package com.gangtise.backend.application.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 流程启停入参。
 */
@Data
public class WorkflowStatusDTO {

    /** 目标状态，见 WorkflowStatusEnum：1 启用 / 2 禁用。 */
    @NotNull(message = "不能为空")
    private Integer status;

}
