package com.gangtise.backend.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 员工新增/修改入参。
 *
 * <p>新增与修改共用同一份结构：修改时按全量覆盖处理，未填写的可选项会被清空。
 */
@Data
public class EmployeeSaveDTO {

    @NotBlank(message = "不能为空")
    @Size(max = 32, message = "长度不能超过 32")
    private String workNo;

    @NotBlank(message = "不能为空")
    @Size(max = 64, message = "长度不能超过 64")
    private String name;

    @Size(max = 64, message = "长度不能超过 64")
    private String alias;

    /** 性别 1 男 2 女，见 GenderEnum。 */
    @Min(value = 1, message = "取值不合法")
    @Max(value = 2, message = "取值不合法")
    private Integer gender;

    @Size(max = 32, message = "长度不能超过 32")
    private String identityCard;

    @Size(max = 32, message = "长度不能超过 32")
    private String telephone;

    @Email(message = "格式不正确")
    @Size(max = 128, message = "长度不能超过 128")
    private String email;

    @Size(max = 255, message = "长度不能超过 255")
    private String address;

    /** 状态 见 EmployeeStatusEnum。 */
    @Min(value = 0, message = "取值不合法")
    @Max(value = 9, message = "取值不合法")
    private Integer status;

    /** 生日 yyyyMMdd。 */
    @Min(value = 19000101, message = "格式应为 yyyyMMdd")
    @Max(value = 29991231, message = "格式应为 yyyyMMdd")
    private Integer birthday;

    /** 学历 见 EmployeeEducationEnum。 */
    @Min(value = 0, message = "取值不合法")
    @Max(value = 5, message = "取值不合法")
    private Integer education;

    /** 职级，关联 department_rank.id。 */
    private Integer rank;

    /** 所属部门，关联 department.id。 */
    private Integer department;

}
