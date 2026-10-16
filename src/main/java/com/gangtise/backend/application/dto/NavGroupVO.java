package com.gangtise.backend.application.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 二级导航的一级节点：一个模块分组（{@code authority.type = 1}）及其下的功能页。
 *
 * <p>由 {@code NavigationService} 从权限字典装配，只在服务端渲染，不经任何接口下发。
 * 用普通类而非 record：模板（Thymeleaf/SpEL）只识别 {@code getXxx()} 形式的属性访问器。
 */
@Data
public class NavGroupVO {

    /** 权限助记符（整万位），便于排查「菜单项对应哪条权限」。 */
    private Integer mnemonic;

    /** 一级菜单显示文本，取分组权限名称（如「系统设置」）。 */
    private String name;

    /** 该分组下的功能页，按助记符升序。 */
    private List<NavItemVO> children = new ArrayList<>();

    /**
     * 子项 key 集合，供模板判断「当前页是否落在本分组内」，决定一级菜单要不要高亮。
     *
     * <p>模板里没法对 SpEL 集合直接写「有没有某一项」，所以先在这里摊平；
     * 派生属性，不落库、不序列化（导航不经接口下发）。
     */
    public List<String> getKeys() {
        return children.stream().map(NavItemVO::getKey).toList();
    }

}
