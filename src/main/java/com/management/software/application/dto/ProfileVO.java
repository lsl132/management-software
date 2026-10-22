package com.management.software.application.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 个人信息（当前登录人视角）。
 *
 * <p>与 {@code UserVO} 的区别：那份是管理员在用户管理里看的（含登录标识、角色、状态，可改密码）；
 * 这份是本人看的——登录账号、角色、状态在这里一律**只读**，只有显示名 / 手机 / 邮箱可自行维护。
 */
@Data
public class ProfileVO {

    private Integer userId;

    /** 登录账号（{@code user_account.identifier}），只读：它是登录凭据，改名等于换账号。 */
    private String identifier;

    /** 显示名（{@code user.username}），可改：顶栏头像旁显示的就是它。 */
    private String username;

    private String mobile;
    private String email;

    /** 角色名称，只读：由管理员在用户管理里调整。 */
    private String roleName;

    /** 账号状态，只读：冻结/解冻由管理员操作。 */
    private Integer status;
    private String statusText;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /** 最近一次登录时间与来源 IP，取 {@code user_login_record} 最新一条；从未登录则为空。 */
    private LocalDateTime lastLoginTime;
    private String lastLoginIp;

}
