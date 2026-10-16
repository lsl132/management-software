package com.gangtise.backend.interfaces.facade;

import com.gangtise.backend.support.ApiTestSupport;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 顶部二级导航的渲染校验（真实渲染整页后用 jsoup 解析，不做字符串包含式断言）。
 *
 * <p>字符串包含只能证明「页面上出现过这几个字」，证明不了「它在正确的位置、是否可点、是否被点亮」。
 * 这里直接按 DOM 结构与 class 断言，模板改坏了能立刻发现。
 *
 * <p>依赖 seed 权限字典（{@code docs/db/seed-authority.sql}）。
 */
class NavigationPageTest extends ApiTestSupport {

    private static final List<String> SEED_GROUPS = List.of(
            "系统设置", "人力资源", "财务管理", "生产制造", "战略决策");

    @Test
    @DisplayName("顶部导航渲染成两级：一级是模块分组，二级是其下功能页")
    void headerRendersTwoLevelMenu() throws Exception {
        Document doc = load("/employees");

        Elements groups = doc.select(".nav .nav__item");
        assertTrue(groups.size() >= 5, "一级应有 5 个模块分组，实际：" + groups.size());

        List<String> groupNames = new ArrayList<>();
        for (Element group : groups) {
            Element trigger = group.selectFirst(".nav__link--group");
            assertNotNull(trigger, "一级项应是分组触发器（button），而不是直接跳转的链接");
            Element label = trigger.selectFirst(".nav__label");
            assertNotNull(label, "一级项要有稳定的名称节点");
            groupNames.add(label.text());

            assertFalse(group.select(".nav__submenu-link").isEmpty(),
                    "分组「" + label.text() + "」下应有功能页，空浮层不该渲染");
        }
        assertTrue(groupNames.containsAll(SEED_GROUPS),
                "一级菜单应来自权限字典的模块分组，实际：" + groupNames);

        // 二级项挂在正确的一级下：公告管理与流程管理都在「系统设置」
        Element system = groupByName(doc, "系统设置");
        assertNotNull(system);
        List<String> systemItems = texts(system.select(".nav__submenu-link"));
        assertTrue(systemItems.contains("公告管理"), "公告管理应挂在系统设置下，实际：" + systemItems);
        assertTrue(systemItems.contains("系统用户"), "系统用户应挂在系统设置下，实际：" + systemItems);
        assertTrue(systemItems.contains("流程管理"), "流程管理应挂在系统设置下，实际：" + systemItems);
    }

    @Test
    @DisplayName("二级项的可点性来自路由：已实现的渲染成链接，未实现的置灰且不带 href")
    void submenuLinksFollowPath() throws Exception {
        Document doc = load("/employees");
        Element system = groupByName(doc, "系统设置");

        Element announcement = menuItem(system, "公告管理");
        assertNotNull(announcement);
        assertEquals("a", announcement.tagName(), "已实现的功能页应是链接");
        assertEquals("/announcements", announcement.attr("href"));
        assertFalse(announcement.hasClass("is-off"), "已接入的页面不该置灰");

        Element workflow = menuItem(system, "流程管理");
        assertNotNull(workflow);
        assertEquals("a", workflow.tagName(), "流程管理已接入页面，应是链接");
        assertEquals("/workflows", workflow.attr("href"));
        assertFalse(workflow.hasClass("is-off"), "已接入的页面不该置灰");

        // 仍未实现的功能页：薪酬绩效（20002）
        Element salary = menuItem(groupByName(doc, "人力资源"), "薪酬绩效");
        assertNotNull(salary, "尚未实现的功能页也要列出来——菜单要体现系统整体规划");
        assertTrue(salary.hasClass("is-off"), "未实现的功能页应置灰");
        // 关键：置灰项必须是 span 而非 a。若带 href，点了就会 404，
        // 「置灰」只是视觉约定，拦不住点击
        assertEquals("span", salary.tagName(), "置灰项不能是可点的链接");
        assertFalse(salary.hasAttr("href"), "置灰项不该带 href");
    }

    @Test
    @DisplayName("当前页对应的二级项与所属一级分组都带选中态，且只点亮一个")
    void currentPageAndItsGroupAreHighlighted() throws Exception {
        Document doc = load("/employees");

        Elements active = doc.select(".nav__submenu-link.is-active");
        assertEquals(1, active.size(), "当前页只应点亮一个二级项，实际：" + texts(active));
        assertEquals("员工管理", active.first().text());

        Element hr = groupByName(doc, "人力资源");
        assertNotNull(hr);
        assertTrue(hr.selectFirst(".nav__link--group").hasClass("is-active"),
                "二级项所属的一级分组也要高亮，否则看不出自己在哪个模块下");
        assertFalse(groupByName(doc, "系统设置").selectFirst(".nav__link--group").hasClass("is-active"),
                "其它分组不应被点亮");
    }

    @Test
    @DisplayName("分组在子页面也能点亮：active 取路由首段")
    void groupStaysHighlightedAcrossItsPages() throws Exception {
        Document doc = load("/authorities");

        assertEquals("权限管理", doc.select(".nav__submenu-link.is-active").first().text());
        assertTrue(groupByName(doc, "系统设置").selectFirst(".nav__link--group").hasClass("is-active"));
        assertFalse(groupByName(doc, "人力资源").selectFirst(".nav__link--group").hasClass("is-active"));
    }

    @Test
    @DisplayName("固定基础项（欢迎页 / 我的日程 / 关于）不在权限字典里，但每个页面都要有")
    void fixedItemsAlwaysPresent() throws Exception {
        for (String route : List.of("/home", "/employees", "/authorities", "/schedules", "/about")) {
            Document doc = load(route);
            assertNotNull(doc.selectFirst(".nav a[href='/home']"), route + " 缺少欢迎页入口");
            assertNotNull(doc.selectFirst(".nav a[href='/schedules']"), route + " 缺少我的日程入口");
            assertNotNull(doc.selectFirst(".nav a[href='/about']"), route + " 缺少关于入口");
        }
    }

    @Test
    @DisplayName("关于页也挂了公共脚本，否则退出登录与导航浮层都失效")
    void aboutPageLoadsSharedScript() throws Exception {
        Document doc = load("/about");
        assertFalse(doc.select("script[src='/js/app.js']").isEmpty(),
                "关于页应加载 /js/app.js（退出登录按钮与二级导航的点击展开都靠它）");
    }

    /* ------------------------------------------------------------------ 工具 */

    private Document load(String route) throws Exception {
        MockHttpSession session = login();
        String html = mockMvc.perform(get(route).session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return Jsoup.parse(html);
    }

    /** 按一级菜单的名称节点定位到那个 .nav__item。 */
    private Element groupByName(Document doc, String name) {
        for (Element group : doc.select(".nav .nav__item")) {
            Element label = group.selectFirst(".nav__label");
            if (label != null && name.equals(label.text())) {
                return group;
            }
        }
        return null;
    }

    private Element menuItem(Element group, String name) {
        for (Element item : group.select(".nav__submenu-link")) {
            if (name.equals(item.text())) {
                return item;
            }
        }
        return null;
    }

    private List<String> texts(Elements elements) {
        List<String> list = new ArrayList<>();
        for (Element element : elements) {
            list.add(element.text());
        }
        return list;
    }

}
