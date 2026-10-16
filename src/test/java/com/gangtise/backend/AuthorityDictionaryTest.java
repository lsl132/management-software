package com.gangtise.backend;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gangtise.backend.domain.aggregate.Authority;
import com.gangtise.backend.infrastructure.enums.AuthorityTypeEnum;
import com.gangtise.backend.infrastructure.repository.mapper.AuthorityMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 权限字典（authority）数据校验，依赖 {@code docs/db/seed-authority.sql} 已执行。
 *
 * <p>校验点：总条数、模块分组与功能页的数量、type 取值合法、
 * 助记符的万位归属（每个功能页都能找到同万位的模块分组）、
 * 路由与实现状态的对应（分组不设路由；有路由的功能页一定是站内绝对路径）。
 */
@SpringBootTest
class AuthorityDictionaryTest {

    /** 助记符的模块步长：功能页助记符整除该值即为其所属分组的助记符 */
    private static final int GROUP_UNIT = 10000;

    @Autowired
    private AuthorityMapper authorityMapper;

    private List<Authority> loadAll() {
        List<Authority> all = authorityMapper.selectList(
                new LambdaQueryWrapper<Authority>().orderByAsc(Authority::getMnemonic));
        assertTrue(all.size() >= 24,
                "权限字典缺失，请先执行 docs/db/seed-authority.sql，当前仅 " + all.size() + " 条");
        return all;
    }

    /**
     * seed 的 24 条（5 组 + 19 页）必须完整存在。
     *
     * <p>权限在「权限管理」里是可增删改的，因此这里用**包含式**断言：只保证 seed 基线没被破坏，
     * 不限制运维后来新增的权限；唯一仍然严格的是 type 只能取 1 / 2。
     */
    @Test
    @DisplayName("seed 权限字典完整：5 个模块分组 + 19 个功能页均已就位")
    void dictionarySizeIsExpected() {
        List<Authority> all = loadAll();
        Map<Integer, Authority> byMnemonic = all.stream()
                .collect(Collectors.toMap(Authority::getMnemonic, Function.identity(), (a, b) -> a));

        int[] seedGroups = {10000, 20000, 30000, 40000, 50000};
        int[] seedPages = {10001, 10002, 10003, 10004, 10005,
                20001, 20002, 20003,
                30001, 30002, 30003, 30004, 30005,
                40001, 40002, 40003, 40004,
                50001, 50002};

        for (int mnemonic : seedGroups) {
            Authority item = byMnemonic.get(mnemonic);
            assertNotNull(item, "缺少模块分组助记符：" + mnemonic);
            assertEquals(AuthorityTypeEnum.GROUP.getValue(), item.getType(),
                    "助记符 " + mnemonic + " 应为模块分组");
        }
        for (int mnemonic : seedPages) {
            Authority item = byMnemonic.get(mnemonic);
            assertNotNull(item, "缺少功能页助记符：" + mnemonic);
            assertEquals(AuthorityTypeEnum.PAGE.getValue(), item.getType(),
                    "助记符 " + mnemonic + " 应为功能页");
        }

        List<Authority> groups = all.stream()
                .filter(item -> AuthorityTypeEnum.isGroup(item.getType()))
                .toList();
        List<Authority> pages = all.stream()
                .filter(item -> AuthorityTypeEnum.isPage(item.getType()))
                .toList();
        assertTrue(groups.size() >= 5, "模块分组不应少于 seed 的 5 个，实际：" + names(groups));
        assertTrue(pages.size() >= 19, "功能页不应少于 seed 的 19 个，实际：" + names(pages));
        assertEquals(all.size(), groups.size() + pages.size(), "不允许出现 1 / 2 之外的 type 取值");
        System.out.println("[AUTHORITY] 共 " + all.size() + " 条 → 分组 " + groups.size()
                + " + 功能页 " + pages.size() + "，seed 基线 24 条完整");
    }

    @Test
    @DisplayName("功能页均可归属到同万位的模块分组")
    void everyPageBelongsToAnExistingGroup() {
        List<Authority> all = loadAll();
        Set<Integer> groupMnemonics = all.stream()
                .filter(item -> AuthorityTypeEnum.isGroup(item.getType()))
                .map(Authority::getMnemonic)
                .collect(Collectors.toSet());
        List<Authority> pages = all.stream()
                .filter(item -> AuthorityTypeEnum.isPage(item.getType()))
                .toList();

        for (Authority page : pages) {
            int groupMnemonic = page.getMnemonic() / GROUP_UNIT * GROUP_UNIT;
            assertTrue(groupMnemonics.contains(groupMnemonic),
                    "功能页「" + page.getAuthName() + "(" + page.getMnemonic() + ")」找不到归属分组 "
                            + groupMnemonic);
        }
        System.out.println("[AUTHORITY] " + pages.size() + " 个功能页的万位归属校验通过，分组助记符："
                + groupMnemonics);
    }

    /**
     * 路由（{@code authority.path}）驱动二级导航，格式错了菜单就点不动，因此在这里锁住两条约定：
     * <ol>
     *   <li><b>模块分组一律没有路由</b>：它是下拉容器、本身没有页面，
     *       留一个路由上去会让菜单渲染出「能点但打不开」的入口；</li>
     *   <li><b>功能页的路由必须是站内绝对路径</b>（以 {@code /} 开头）。
     *       相对路径会跟着当前页 URL 变，同一个菜单项在不同页面点开跳到不同地方。
     *       路由为空是**允许**的，代表该功能页尚未实现（导航里置灰），
     *       所以这里只校验非空值的格式，不要求每个功能页都有路由。</li>
     * </ol>
     */
    @Test
    @DisplayName("路由约定：分组不设路由，功能页路由必须是站内绝对路径")
    void pathConventions() {
        List<Authority> all = loadAll();
        long pagesWithPath = 0;

        for (Authority item : all) {
            String path = item.getPath();
            if (AuthorityTypeEnum.isGroup(item.getType())) {
                assertTrue(path == null || path.isBlank(),
                        "模块分组「" + item.getAuthName() + "」不应有路由，实际：" + path);
                continue;
            }
            if (path == null) {
                continue;
            }
            pagesWithPath++;
            assertTrue(path.startsWith("/"),
                    "功能页「" + item.getAuthName() + "」的路由必须以 / 开头，实际：" + path);
            assertFalse(path.contains(" "), "路由不应包含空格：" + path);
            assertTrue(path.length() <= 128, "路由长度不应超过 128：" + path);
        }

        assertTrue(pagesWithPath >= 6,
                "seed 里至少应有 6 个功能页已接入页面并填好路由，实际：" + pagesWithPath);
        System.out.println("[AUTHORITY] 路由校验通过：已实现 " + pagesWithPath
                + " 个功能页，其余为规划中（导航置灰）");
    }

    @Test
    @DisplayName("模块分组的助记符为整万位，且名称与助记符对应正确")
    void groupsAndNamesAreConsistent() {
        List<Authority> all = loadAll();

        for (Authority item : all) {
            if (AuthorityTypeEnum.isGroup(item.getType())) {
                assertEquals(0, item.getMnemonic() % GROUP_UNIT,
                        "模块分组「" + item.getAuthName() + "」助记符应为整万位：" + item.getMnemonic());
            }
        }

        Map<Integer, Authority> byMnemonic = all.stream()
                .collect(Collectors.toMap(Authority::getMnemonic, Function.identity(), (a, b) -> a));

        assertItem(byMnemonic, 20003, "组织架构", AuthorityTypeEnum.PAGE.getValue());
        assertItem(byMnemonic, 30005, "财务计划", AuthorityTypeEnum.PAGE.getValue());
        assertItem(byMnemonic, 40004, "物料需求", AuthorityTypeEnum.PAGE.getValue());
        assertItem(byMnemonic, 50000, "战略决策", AuthorityTypeEnum.GROUP.getValue());
        // 10004 原名「审批规则」，随流程管理页落地更名为「流程管理」并指向 /workflows
        assertItem(byMnemonic, 10004, "流程管理", AuthorityTypeEnum.PAGE.getValue());
        assertEquals("/workflows", byMnemonic.get(10004).getPath(),
                "流程管理应指向流程页面，否则导航里还是置灰的");
        System.out.println("[AUTHORITY] 抽样核对 10004 / 20003 / 30005 / 40004 / 50000 全部匹配");
    }

    private void assertItem(Map<Integer, Authority> byMnemonic, int mnemonic,
                            String expectName, int expectType) {
        Authority item = byMnemonic.get(mnemonic);
        assertNotNull(item, "缺少权限助记符：" + mnemonic);
        assertEquals(expectName, item.getAuthName(), "助记符 " + mnemonic + " 的权限名称不符");
        assertEquals(expectType, item.getType(), "助记符 " + mnemonic + " 的权限类型不符");
    }

    private String names(List<Authority> items) {
        return items.stream().map(Authority::getAuthName).collect(Collectors.joining(" / "));
    }

}
