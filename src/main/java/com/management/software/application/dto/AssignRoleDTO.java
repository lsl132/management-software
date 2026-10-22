package com.management.software.application.dto;

import lombok.Data;

/**
 * 设置用户角色的入参。
 *
 * <p>{@code roleId} 允许为空：表示把用户恢复为「未分配角色」。
 */
@Data
public class AssignRoleDTO {

    private Integer roleId;

}
