package com.gangtise.backend.application.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 角色挂载权限入参（全量覆盖）。
 *
 * <p>提交的是**权限助记符**集合（{@code authority.mnemonic}），不是主键：
 * {@code role_authority} 按助记符引用权限，与主键解耦。
 *
 * <p>服务端会做一次父子归一化（见 {@code RoleService#saveAuthorities}）：
 * 勾了分组但一个子页都没勾 → 整组授权（补全其下全部功能页）；勾了任意功能页 → 自动补上所属分组，
 * 保证不会出现「有菜单但打不开页面」；而「勾了分组又只留部分子页」不会被补回兄弟页面，
 * 细粒度授权不会丢失。
 */
@Data
public class RoleAuthoritySaveDTO {

    @NotNull(message = "不能为空")
    private List<Integer> mnemonics;

}
