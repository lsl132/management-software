package com.management.software.application.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 权限详情返回结构（平铺字段，供权限管理页编辑回填与依赖提示使用）。
 */
@Data
public class AuthorityVO {

    private Integer id;
    private Integer mnemonic;
    private String authName;
    private String description;

    private Integer type;
    /** 类型描述（模块分组 / 功能页），由 AuthorityTypeEnum 转换。 */
    private String typeText;

    /** 功能页路由（如 /employees）；为空表示尚未实现，二级导航里置灰。模块分组恒为空。 */
    private String path;

    /** 该权限所属模块分组的助记符（分组自身即返回自己），由助记符万位推导。 */
    private Integer groupMnemonic;

    /** 分组下的功能页数量；非分组为 0。大于 0 时不允许删除该分组。 */
    private Integer childCount;
    /** 引用该权限的角色数量；大于 0 时不允许删除。 */
    private Integer roleCount;

    private LocalDateTime createTime;

}
