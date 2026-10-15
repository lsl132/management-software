package com.gangtise.backend.application.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色列表/详情返回结构。
 */
@Data
public class RoleVO {

    private Integer id;

    private String name;
    private String description;

    private Integer status;
    /** 状态描述（启用 / 停用），由 RoleStatusEnum 转换。 */
    private String statusText;

    /** 已分配该角色的用户数；大于 0 时不允许删除该角色。 */
    private Integer userCount;
    /** 已挂载的权限数量（模块分组 + 功能页）。 */
    private Integer authorityCount;

    private LocalDateTime createTime;

}
