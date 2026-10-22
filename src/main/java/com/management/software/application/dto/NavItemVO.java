package com.management.software.application.dto;

import lombok.Data;

/**
 * 二级导航的二级节点：一个功能页（{@code authority.type = 2}）。
 *
 * <p>由 {@code NavigationService} 从权限字典装配，只在服务端渲染，不经任何接口下发。
 * 用普通类而非 record：模板（Thymeleaf/SpEL）只识别 {@code getXxx()} / {@code isXxx()} 形式的属性访问器。
 */
@Data
public class NavItemVO {

    /** 权限助记符，便于排查「菜单项对应哪条权限」。 */
    private Integer mnemonic;

    /** 菜单显示文本，取权限名称。 */
    private String name;

    /** 路由（如 {@code /employees}），来自 {@code authority.path}；为空表示该功能页尚未实现。 */
    private String path;

    /**
     * 高亮用的 key：路由去掉前导斜杠（{@code /employees} → {@code employees}）。
     *
     * <p>各页面控制器传给 header 片段的 {@code active} 正是这个取值（{@code active='employees'}），
     * 模板靠它把当前页对应的菜单项标成选中态。
     */
    private String key;

    /** 是否可点：路由非空即可点；为空时菜单置灰不可点（功能页还没实现，点进去只会 404）。 */
    private boolean enabled;

}
