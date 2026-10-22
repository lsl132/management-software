package com.management.software.application.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 权限新增/修改入参。
 *
 * <p>助记符是权限的业务键（{@code uk_authority_mnemonic}），且被 {@code role_authority}
 * 按值引用，因此仅做**格式**校验（正整数、与 type 自洽），
 * 占用检测和引用级联由服务层负责，见 {@code AuthorityService}。
 */
@Data
public class AuthoritySaveDTO {

    /** 权限助记符：模块分组为整万位（10000），功能页在其万位分组下递增（10001）。 */
    @NotNull(message = "不能为空")
    @Min(value = 1, message = "必须是正整数")
    @Max(value = 99999999, message = "长度不能超过 8 位")
    private Integer mnemonic;

    @NotBlank(message = "不能为空")
    @Size(max = 64, message = "长度不能超过 64")
    private String authName;

    @Size(max = 255, message = "长度不能超过 255")
    private String description;

    /** 权限类型，见 AuthorityTypeEnum：1 模块分组（菜单） / 2 功能页。 */
    @NotNull(message = "不能为空")
    @Min(value = 1, message = "取值只能是 1 模块分组 / 2 功能页")
    @Max(value = 2, message = "取值只能是 1 模块分组 / 2 功能页")
    private Integer type;

    /**
     * 功能页路由（如 {@code /employees}），二级导航的跳转地址；可空，为空表示该功能页尚未实现。
     *
     * <p>这里只校验**格式**：必须以 {@code /} 开头、不含空白。空串与 null 等价（服务层统一归一成 null），
     * 因此正则里显式放行空串，避免前端提交 {@code ""} 时收到一个莫名其妙的 400。
     *
     * <p>模块分组是下拉容器，不设路由——服务层会把分组的路由强制置空，不依赖调用方自觉。
     */
    @Size(max = 128, message = "长度不能超过 128")
    @Pattern(regexp = "^$|^/\\S*$", message = "必须以 / 开头且不能包含空白字符，如 /employees")
    private String path;

}
