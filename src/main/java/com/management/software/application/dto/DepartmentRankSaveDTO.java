package com.management.software.application.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 部门职级新增/修改入参。
 *
 * <p>归属部门由请求路径（{@code /api/departments/{departmentId}/ranks}）决定，请求体不接受部门参数，
 * 避免出现「路径部门」与「参数部门」不一致的越权写入。
 */
@Data
public class DepartmentRankSaveDTO {

    /** 职级编码，全公司唯一，如 O1 / T3。 */
    @NotBlank(message = "不能为空")
    @Size(max = 32, message = "长度不能超过 32")
    private String code;

    /** 职级序列，见 RankTypeEnum（1=O 2=T 3=F 4=M 5=S）。 */
    @NotNull(message = "不能为空")
    @Min(value = 1, message = "取值不合法")
    @Max(value = 5, message = "取值不合法")
    private Integer type;

    /** 职级说明/名称，如「中级工」。 */
    @Size(max = 255, message = "长度不能超过 255")
    private String intro;

}
