package com.gangtise.backend.application.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户列表/详情返回结构。
 *
 * <p>刻意不返回密码或散列值：凭证只存在于 {@code user_account.credential}，任何接口都不下发。
 */
@Data
public class UserVO {

    private Integer id;

    /** 展示名。 */
    private String username;
    /** 登录标识（唯一）。 */
    private String identifier;

    private String mobile;
    private String email;

    private Integer roleId;
    /** 角色名称，未分配角色为 null。 */
    private String roleName;

    private Integer status;
    /** 状态描述（正常 / 冻结），由 UserStatusEnum 转换。 */
    private String statusText;

    /** 是否有登录凭证；为 false 时无法登录，重置密码会自动补建凭证。 */
    private Boolean accountReady;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

}
