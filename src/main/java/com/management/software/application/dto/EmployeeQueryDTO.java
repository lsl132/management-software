package com.management.software.application.dto;

import lombok.Data;

/**
 * 员工分页查询条件（GET 查询参数绑定）。
 */
@Data
public class EmployeeQueryDTO {

    /** 页码，从 1 开始。 */
    private Integer current = 1;

    /** 每页条数，服务端会限制在 1~100。 */
    private Integer size = 10;

    /** 关键字，模糊匹配姓名 / 工号 / 电话 / 邮箱。 */
    private String keyword;

    /** 所属部门ID。 */
    private Integer department;

    /** 状态，见 EmployeeStatusEnum。 */
    private Integer status;

}
