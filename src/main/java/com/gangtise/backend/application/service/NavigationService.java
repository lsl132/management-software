package com.gangtise.backend.application.service;

import com.gangtise.backend.application.dto.NavGroupVO;
import com.gangtise.backend.application.dto.NavItemVO;
import com.gangtise.backend.domain.aggregate.Authority;
import com.gangtise.backend.infrastructure.enums.AuthorityTypeEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 顶部二级导航的菜单装配：把权限字典翻译成「模块分组 → 功能页」的两级菜单。
 *
 * <p><b>为什么菜单由权限字典驱动</b>：菜单结构（有哪些模块、每个模块下有哪些功能页）
 * 在权限管理页里本来就要维护一份，硬编码在模板里就等于维护两份，新增功能页时要记得改两处、
 * 漏改也不会报错。这里直接读 {@code authority}：一级是 {@code type=1} 的模块分组，
 * 二级是其下 {@code type=2} 的功能页。
 *
 * <p><b>路由为空的功能页置灰</b>：字典里的功能页是「规划中」的完整清单，其中不少还没有对应页面。
 * 这类菜单项仍要显示（否则看不出系统整体规划），但不能点——点了只会 404。
 * 由 {@code authority.path} 是否为空决定，可点性完全来自数据。
 *
 * <p><b>没有做缓存</b>：每次页面渲染查一次 {@code authority}（单表、走唯一索引顺序扫描，二十来行），
 * 对后台这种量级可以忽略。加缓存反而要处理「权限管理页改完菜单要立刻生效」的一致性问题，
 * 为省一次微不足道的查询引入一类难查的脏数据故障，不划算。
 */
@Service
public class NavigationService {

    private static final Logger log = LoggerFactory.getLogger(NavigationService.class);

    private final AuthorityService authorityService;

    public NavigationService(AuthorityService authorityService) {
        this.authorityService = authorityService;
    }

    /**
     * 装配二级导航菜单，按分组助记符升序、分组内按功能页助记符升序。
     *
     * <p>两类数据被跳过并记 warn：找不到所属分组的功能页、一个功能页都没有的分组。
     * 两者都是「结构不对」的中间状态（前者是孤儿数据，后者点开是空浮层），
     * 跳过比渲染出来更不容易误导。
     */
    public List<NavGroupVO> menus() {
        List<Authority> all = authorityService.listAll();

        Map<Integer, NavGroupVO> groups = new LinkedHashMap<>();
        for (Authority authority : all) {
            if (!AuthorityTypeEnum.isGroup(authority.getType())) {
                continue;
            }
            NavGroupVO group = new NavGroupVO();
            group.setMnemonic(authority.getMnemonic());
            group.setName(authority.getAuthName());
            groups.put(authority.getMnemonic(), group);
        }

        for (Authority authority : all) {
            if (!AuthorityTypeEnum.isPage(authority.getType())) {
                continue;
            }
            int groupMnemonic = AuthorityService.groupMnemonicOf(authority.getMnemonic());
            NavGroupVO group = groups.get(groupMnemonic);
            if (group == null) {
                log.warn("[NAV] 功能页找不到所属模块分组，菜单里已跳过 -> mnemonic={}, authName={}",
                        authority.getMnemonic(), authority.getAuthName());
                continue;
            }
            group.getChildren().add(toItem(authority));
        }

        List<NavGroupVO> menus = new ArrayList<>();
        for (NavGroupVO group : groups.values()) {
            if (group.getChildren().isEmpty()) {
                log.warn("[NAV] 模块分组下没有任何功能页，菜单里已跳过 -> mnemonic={}, name={}",
                        group.getMnemonic(), group.getName());
                continue;
            }
            menus.add(group);
        }
        return menus;
    }

    private NavItemVO toItem(Authority authority) {
        String path = trimToNull(authority.getPath());

        NavItemVO item = new NavItemVO();
        item.setMnemonic(authority.getMnemonic());
        item.setName(authority.getAuthName());
        item.setPath(path);
        // 判空只看路由：路径非空才可点，菜单的可点性完全由数据决定，不额外维护一份白名单
        item.setEnabled(path != null);
        item.setKey(keyOf(path));
        return item;
    }

    /**
     * 路由 → 高亮 key，取**第一段**：{@code /employees} → {@code employees}。
     *
     * <p>取第一段而不是整串，是为了让 {@code /employees/detail} 这类子路由也能把
     * 「员工管理」整条菜单点亮；各页面控制器传给 header 的 {@code active} 也正是段值。
     */
    private static String keyOf(String path) {
        if (path == null) {
            return null;
        }
        for (String segment : path.split("/")) {
            if (!segment.isEmpty()) {
                return segment;
            }
        }
        return null;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

}
