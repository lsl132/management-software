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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 权限字典（authority）数据校验，依赖 {@code docs/db/seed-authority.sql} 已执行。
 *
 * <p>校验点：总条数、模块分组与功能页的数量、type 取值合法、
 * 助记符的万位归属（每个功能页都能找到同万位的模块分组）。
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
        assertTrue(all.size() >= 23,
                "权限字典缺失，请先执行 docs/db/seed-authority.sql，当前仅 " + all.size() + " 条");
        return all;
    }

    /**
     * seed 的 23 条（5 组 + 18 页）必须完整存在。
     *
     * <p>权限在「权限管理」里是可增删改的，因此这里用**包含式**断言：只保证 seed 基线没被破坏，
     * 不限制运维后来新增的权限；唯一仍然严格的是 type 只能取 1 / 2。
     */
    @Test
    @DisplayName("seed 权限字典完整：5 个模块分组 + 18 个功能页均已就位")
    void dictionarySizeIsExpected() {
        List<Authority> all = loadAll();
        Map<Integer, Authority> byMnemonic = all.stream()
                .collect(Collectors.toMap(Authority::getMnemonic, Function.identity(), (a, b) -> a));

        int[] seedGroups = {10000, 20000, 30000, 40000, 50000};
        int[] seedPages = {10001, 10002, 10003, 10004,
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
        assertTrue(pages.size() >= 18, "功能页不应少于 seed 的 18 个，实际：" + names(pages));
        assertEquals(all.size(), groups.size() + pages.size(), "不允许出现 1 / 2 之外的 type 取值");
        System.out.println("[AUTHORITY] 共 " + all.size() + " 条 → 分组 " + groups.size()
                + " + 功能页 " + pages.size() + "，seed 基线 23 条完整");
    }

    @Test
    @DisplayName("功能页均可归属到同万位的模块分组")
    void everyPageBelongsToAnExistingGroup() {
        List<Authority> all = loadAll();
        Set<Integer> groupMnemonics = all.stream()
                .filter(item -> AuthorityTypeEnum.isGroup(item.getType()))
                .map(Authority::getMnemonic)
                .collect(Collectors.toSet());

        for (Authority page : all) {
            if (!AuthorityTypeEnum.isPage(page.getType())) {
                continue;
            }
            int groupMnemonic = page.getMnemonic() / GROUP_UNIT * GROUP_UNIT;
            assertTrue(groupMnemonics.contains(groupMnemonic),
                    "功能页「" + page.getAuthName() + "(" + page.getMnemonic() + ")」找不到归属分组 "
                            + groupMnemonic);
        }
        System.out.println("[AUTHORITY] 18 个功能页的万位归属校验通过，分组助记符：" + groupMnemonics);
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
        System.out.println("[AUTHORITY] 抽样核对 20003 / 30005 / 40004 / 50000 全部匹配");
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
