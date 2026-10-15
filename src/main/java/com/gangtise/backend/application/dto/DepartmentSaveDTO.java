package com.gangtise.backend.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 部门新增/修改入参。
 *
 * <p>新增与修改共用同一份结构：修改按全量覆盖处理，留空的可选字段会被清空。
 */
@Data
public class DepartmentSaveDTO {

    /** 部门名称，全公司唯一。 */
    @NotBlank(message = "不能为空")
    @Size(max = 64, message = "长度不能超过 64")
    private String departName;

    /** 上级部门ID，顶级部门传 null。 */
    private Integer parentId;

    @Size(max = 255, message = "长度不能超过 255")
    private String description;

}
