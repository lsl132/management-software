package com.gangtise.backend.application.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 角色新增/修改入参。
 */
@Data
public class RoleSaveDTO {

    /** 角色名称，全公司唯一（唯一索引 uk_role_name）。 */
    @NotBlank(message = "不能为空")
    @Size(max = 64, message = "长度不能超过 64")
    private String name;

    @Size(max = 255, message = "长度不能超过 255")
    private String description;

    /** 状态，见 RoleStatusEnum：1 启用 / 2 停用。 */
    @NotNull(message = "不能为空")
    @Min(value = 1, message = "取值只能是 1 启用 / 2 停用")
    @Max(value = 2, message = "取值只能是 1 启用 / 2 停用")
    private Integer status;

}
