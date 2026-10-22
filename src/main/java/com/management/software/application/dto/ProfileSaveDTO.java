package com.management.software.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 个人信息保存入参。
 *
 * <p><b>只有三项可改</b>：显示名、手机号、电子邮箱。登录账号 / 角色 / 状态不在入参里，
 * 即使前端多传也不会被采纳——免得有人拿这个接口给自己改角色。
 */
@Data
public class ProfileSaveDTO {

    /** 显示名，允许中文。 */
    @NotBlank(message = "不能为空")
    @Size(max = 64, message = "长度不能超过 64")
    private String username;

    @Size(max = 32, message = "长度不能超过 32")
    private String mobile;

    @Email(message = "格式不正确")
    @Size(max = 128, message = "长度不能超过 128")
    private String email;

}
