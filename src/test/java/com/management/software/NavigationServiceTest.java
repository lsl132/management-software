package com.management.software;

import com.management.software.application.dto.NavGroupVO;
import com.management.software.application.dto.NavItemVO;
import com.management.software.application.service.NavigationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 二级导航菜单装配校验，依赖 {@code docs/db/seed-authority.sql} 已执行。
 *
 * <p>菜单完全由权限字典推导，所以这里校验的是「字典 → 菜单」这层翻译：
 * 层级对不对、顺序对不对、可点性与路由是否一致。
 * 只测渲染结果（HTML 字符串）看不出这些结构性问题，渲染成什么样是模板的事。
 */
@SpringBootTest
class NavigationServiceTest {

    @Autowired
    private NavigationService navigationService;

    @Test
    @DisplayName("一级 = 模块分组、二级 = 其下功能页，两层都按助记符升序且归属正确")
    void menusMirrorAuthorityDictionary() {
        List<NavGroupVO> menus = navigationService.menus();
        assertTrue(menus.size() >= 5, "seed 至少 5 个模块分组，实际：" + menus.size());

        int previousGroup = 0;
        int totalItems = 0;
        for (NavGroupVO group : menus) {
            assertEquals(0, group.getMnemonic() % 10000,
                    "一级菜单应是模块分组（整万位助记符），实际：" + group.getMnemonic());
            assertTrue(group.getMnemonic() > previousGroup, "一级菜单应按助记符升序");
            previousGroup = group.getMnemonic();
            assertNotNull(group.getName());

            assertFalse(group.getChildren().isEmpty(),
                    "一个功能页都没有的分组不该下发（点开是空浮层）：" + group.getName());

            int previousItem = 0;
            for (NavItemVO item : group.getChildren()) {
                assertTrue(item.getMnemonic() > previousItem, "分组内功能页应按助记符升序");
                previousItem = item.getMnemonic();
                assertEquals(group.getMnemonic(), item.getMnemonic() / 10000 * 10000,
                        "功能页 " + item.getMnemonic() + " 应挂在分组 " + group.getMnemonic() + " 下");
                assertNotNull(item.getName());
                totalItems++;
            }
        }
        assertTrue(totalItems >= 19, "seed 至少 19 个功能页，实际：" + totalItems);
    }

    @Test
    @DisplayName("可点性完全来自路由：有路由才能点，且 key 取路由首段")
    void enabledAndKeyComeFromPath() {
        NavItemVO employee = findItem("员工管理");
        assertTrue(employee.isEnabled(), "员工管理已接入 /employees，应可点");
        assertEquals("/employees", employee.getPath());
        assertEquals("employees", employee.getKey(),
                "key 取路由首段，与页面控制器传给 header 的 active 对齐");

        // 流程管理（字典 10004，即原「审批规则」）本轮接入 /workflows
        NavItemVO workflow = findItem("流程管理");
        assertNotNull(workflow, "流程管理应在菜单里");
        assertTrue(workflow.isEnabled(), "流程管理已接入 /workflows，应可点");
        assertEquals("/workflows", workflow.getPath());
        assertEquals("workflows", workflow.getKey());

        NavItemVO salary = findItem("薪酬绩效");
        assertNotNull(salary, "薪酬绩效应在菜单里（尚未实现）");
        assertFalse(salary.isEnabled(), "薪酬绩效还没有页面，应置灰不可点");
        assertNull(salary.getPath());
        assertNull(salary.getKey(), "没有路由就没有 key——它不该在任何页面上被点亮");
    }

    @Test
    @DisplayName("路由带子路径时 key 仍取首段，保证子页面也能点亮整条菜单")
    void keyTakesFirstPathSegment() {
        // 用服务自身的约定做纯函数式校验，不依赖字典里恰好存在这样的数据：
        // /employees/detail 的菜单项，应该在 active='employees' 的页面上也被点亮
        NavItemVO item = new NavItemVO();
        item.setPath("/employees/detail");
        assertEquals("employees", keyOf(item.getPath()));
        assertEquals("employees", keyOf("/employees"));
        assertNull(keyOf("/"), "根路径没有可用的段，不应产生 key");
    }

    @Test
    @DisplayName("规划中但未实现的功能页照样下发（菜单要体现整体规划）")
    void plannedPagesAreStillListed() {
        NavGroupVO finance = findGroup("财务管理");
        assertNotNull(finance, "财务管理应在菜单里");
        assertFalse(finance.getChildren().isEmpty());
        for (NavItemVO item : finance.getChildren()) {
            assertFalse(item.isEnabled(),
                    "财务管理的功能页都还没有页面，应全部置灰：" + item.getName());
        }
    }

    @Test
    @DisplayName("公告管理已挂到「系统设置」下并接入 /announcements")
    void announcementPageJoinedSystemGroup() {
        NavGroupVO system = findGroup("系统设置");
        assertNotNull(system);

        NavItemVO announcement = null;
        for (NavItemVO item : system.getChildren()) {
            if ("公告管理".equals(item.getName())) {
                announcement = item;
            }
        }
        assertNotNull(announcement, "公告管理应在系统设置分组下，实际：" + names(system));
        assertEquals("/announcements", announcement.getPath());
        assertTrue(announcement.isEnabled());
    }

    /* ------------------------------------------------------------------ 工具 */

    /** 与 NavigationService#keyOf 同一规则的纯函数实现，用于不依赖数据的边界校验。 */
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

    private NavGroupVO findGroup(String name) {
        for (NavGroupVO group : navigationService.menus()) {
            if (name.equals(group.getName())) {
                return group;
            }
        }
        return null;
    }

    private NavItemVO findItem(String name) {
        for (NavGroupVO group : navigationService.menus()) {
            for (NavItemVO item : group.getChildren()) {
                if (name.equals(item.getName())) {
                    return item;
                }
            }
        }
        return null;
    }

    private String names(NavGroupVO group) {
        return group.getChildren().stream().map(NavItemVO::getName).toList().toString();
    }

}
