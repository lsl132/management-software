package com.gangtise.backend.application.dto;

import lombok.Data;

import java.util.List;

/**
 * 权限树节点：模块分组（{@code authority.type = 1}）+ 其下功能页（{@code type = 2}）。
 *
 * <p>分组与其子页面的助记符都作为可勾选项下发，角色授权时两者都会写入 {@code role_authority}。
 */
@Data
public class AuthorityTreeVO {

    /** 主键，权限管理页的编辑/删除按其定位（助记符可被修改，不适合作路径标识）。 */
    private Integer id;

    private Integer mnemonic;
    private String authName;
    private String description;
    private Integer type;
    private String typeText;

    /** 该分组下的功能页，按助记符升序。 */
    private List<AuthorityTreeVO> children;

    /** 引用该权限的角色数量；大于 0 时列表提示「已被引用」，且删除会被拒绝。 */
    private Integer roleCount;

}
