package com.gangtise.backend.application.dto;

import lombok.Data;

/**
 * 用户分页查询条件（GET 查询参数绑定）。
 */
@Data
public class UserQueryDTO {

    /** 页码，从 1 开始。 */
    private Integer current = 1;

    /** 每页条数，服务端会限制在 1~100。 */
    private Integer size = 10;

    /** 关键字，模糊匹配展示名 / 登录标识 / 手机号 / 邮箱。 */
    private String keyword;

    /** 角色ID，为空表示全部角色。 */
    private Integer roleId;

    /** 状态，见 UserStatusEnum；为空表示全部。 */
    private Integer status;

}
