package com.management.software.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户新增/修改入参。
 *
 * <p><b>不含密码</b>：新用户的登录凭证统一使用系统初始密码（配置 {@code app.user.default-password}），
 * 由服务端 BCrypt 散列后写入 {@code user_account.credential}，界面不回显、日志不输出明文。
 *
 * <p>修改按全量覆盖处理：手机号 / 邮箱 / 角色留空即清空。
 */
@Data
public class UserSaveDTO {

    /** 展示名（{@code user.username}），允许中文，可随时修改，不影响登录。 */
    @NotBlank(message = "不能为空")
    @Size(max = 64, message = "长度不能超过 64")
    private String username;

    /** 登录标识（{@code user_account.identifier}），全公司唯一，登录时按它查账号。 */
    @NotBlank(message = "不能为空")
    @Size(max = 128, message = "长度不能超过 128")
    private String identifier;

    @Size(max = 32, message = "长度不能超过 32")
    private String mobile;

    @Email(message = "格式不正确")
    @Size(max = 128, message = "长度不能超过 128")
    private String email;

    /** 角色ID，关联 role.id；为空表示未分配角色。 */
    private Integer roleId;

    /** 状态，见 UserStatusEnum：1 正常 / 2 冻结。 */
    @NotNull(message = "不能为空")
    @Min(value = 1, message = "取值只能是 1 正常 / 2 冻结")
    @Max(value = 2, message = "取值只能是 1 正常 / 2 冻结")
    private Integer status;

}
