package com.management.software.interfaces.facade;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.management.software.domain.aggregate.User;
import com.management.software.domain.aggregate.entity.RoleAuthority;
import com.management.software.infrastructure.enums.UserStatusEnum;
import com.management.software.infrastructure.repository.mapper.RoleAuthorityMapper;
import com.management.software.infrastructure.repository.mapper.UserMapper;
import com.management.software.support.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 角色管理接口集成测试。
 *
 * <p>依赖 seed 权限字典（{@code docs/db/seed-authority.sql}：5 个模块分组 + 19 个功能页），
 * 只读取不修改；自建的角色与挂载关系随事务回滚。
 */
class RoleApiTest extends ApiTestSupport {

    private static final String PREFIX = "IT-角色-";

    /** 10000 系统设置分组及其功能页（seed 固定值）。 */
    private static final int GROUP_SYSTEM = 10000;
    private static final int PAGE_SYSTEM_USER = 10001;
    private static final int PAGE_AUTHORITY = 10002;
    private static final int PAGE_ROLE = 10003;
    private static final int GROUP_HR = 20000;
    private static final int PAGE_EMPLOYEE = 20001;

    @Autowired
    private RoleAuthorityMapper roleAuthorityMapper;

    @Autowired
    private UserMapper userMapper;

    @Test
    @DisplayName("未登录不能访问角色接口")
    void requiresLogin() throws Exception {
        mockMvc.perform(get("/api/roles"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("角色增删改查全流程")
    void crudFlow() throws Exception {
        MockHttpSession session = login();

        Integer id = create(session, PREFIX + "运营");
        mockMvc.perform(get("/api/roles").session(session))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[?(@.id == " + id + ")].name", hasItem(PREFIX + "运营")));

        mockMvc.perform(put("/api/roles/" + id).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", PREFIX + "运营中心",
                                "description", "负责日常运营",
                                "status", 2))))
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/roles/" + id).session(session))
                .andExpect(jsonPath("$.data.name").value(PREFIX + "运营中心"))
                .andExpect(jsonPath("$.data.description").value("负责日常运营"))
                .andExpect(jsonPath("$.data.statusText").value("停用"))
                .andExpect(jsonPath("$.data.userCount").value(0))
                .andExpect(jsonPath("$.data.authorityCount").value(0));

        mockMvc.perform(delete("/api/roles/" + id).session(session))
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(get("/api/roles/" + id).session(session))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("角色不存在或已被删除"));
    }

    @Test
    @DisplayName("角色名称重复被拒绝")
    void duplicateNameRejected() throws Exception {
        MockHttpSession session = login();
        String name = PREFIX + "重名";
        create(session, name);

        mockMvc.perform(post("/api/roles").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", name,
                                "status", 1))))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("角色名称已存在：" + name));
    }

    @Test
    @DisplayName("角色名称为空被拒绝")
    void blankNameRejected() throws Exception {
        MockHttpSession session = login();

        mockMvc.perform(post("/api/roles").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "   ",
                                "status", 1))))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("name 不能为空"));
    }

    @Test
    @DisplayName("仍有用户引用时不允许删除角色")
    void deleteWithUserRejected() throws Exception {
        MockHttpSession session = login();
        Integer roleId = create(session, PREFIX + "在用角色");

        User user = new User();
        user.setUsername(PREFIX + "占用用户");
        user.setStatus(UserStatusEnum.NORMAL.getValue());
        user.setRoleId(roleId);
        userMapper.insert(user);

        mockMvc.perform(delete("/api/roles/" + roleId).session(session))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("该角色下还有 1 个用户，请先调整这些用户的角色"));
    }

    @Test
    @DisplayName("挂载单个功能页会自动补上所属分组，且不扩大兄弟页权限")
    void saveSinglePageKeepsGranularity() throws Exception {
        MockHttpSession session = login();
        Integer roleId = create(session, PREFIX + "细粒度");

        saveAuthorities(session, roleId, List.of(PAGE_SYSTEM_USER));

        List<Integer> saved = authorities(roleId);
        assertEquals(2, saved.size(), "应只保存「系统设置」分组 + 「系统用户」功能页");
        assertTrue(saved.contains(GROUP_SYSTEM), "功能页应自动补上所属分组");
        assertTrue(saved.contains(PAGE_SYSTEM_USER));
        assertTrue(!saved.contains(PAGE_AUTHORITY), "不应自动扩大兄弟页（权限管理）");
        assertTrue(!saved.contains(PAGE_ROLE), "不应自动扩大兄弟页（角色管理）");
    }

    @Test
    @DisplayName("只勾分组等于整组授权，自动补全其下全部功能页")
    void saveGroupExpandsToAllPages() throws Exception {
        MockHttpSession session = login();
        Integer roleId = create(session, PREFIX + "整组");

        saveAuthorities(session, roleId, List.of(GROUP_HR));

        List<Integer> saved = authorities(roleId);
        assertEquals(4, saved.size(), "人力资源分组含 3 个功能页，加分组自身共 4 项");
        assertTrue(saved.containsAll(List.of(GROUP_HR, 20001, 20002, 20003)));
    }

    @Test
    @DisplayName("全量覆盖：再次保存会清掉上一次的挂载")
    void saveReplacesPrevious() throws Exception {
        MockHttpSession session = login();
        Integer roleId = create(session, PREFIX + "覆盖");

        saveAuthorities(session, roleId, List.of(GROUP_SYSTEM));
        assertEquals(6, authorities(roleId).size(), "系统设置分组 + 5 个功能页");

        saveAuthorities(session, roleId, List.of(PAGE_EMPLOYEE));
        List<Integer> saved = authorities(roleId);
        assertEquals(2, saved.size());
        assertTrue(saved.containsAll(List.of(GROUP_HR, PAGE_EMPLOYEE)));
        assertTrue(!saved.contains(GROUP_SYSTEM), "上一次的分组挂载应已被清除");
    }

    @Test
    @DisplayName("清空权限：传空集合等于取消全部挂载")
    void saveEmptyClearsAll() throws Exception {
        MockHttpSession session = login();
        Integer roleId = create(session, PREFIX + "清空");

        saveAuthorities(session, roleId, List.of(GROUP_SYSTEM));
        assertTrue(!authorities(roleId).isEmpty());

        saveAuthorities(session, roleId, List.of());
        assertTrue(authorities(roleId).isEmpty());

        mockMvc.perform(get("/api/roles/" + roleId).session(session))
                .andExpect(jsonPath("$.data.authorityCount").value(0));
    }

    @Test
    @DisplayName("不存在的权限助记符被拒绝")
    void unknownMnemonicRejected() throws Exception {
        MockHttpSession session = login();
        Integer roleId = create(session, PREFIX + "非法");

        mockMvc.perform(put("/api/roles/" + roleId + "/authorities").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("mnemonics", List.of(99999)))))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("权限不存在，请刷新页面后重试：99999"));
    }

    @Test
    @DisplayName("权限列表缺失 mnemonics 字段被拒绝")
    void missingMnemonicsRejected() throws Exception {
        MockHttpSession session = login();
        Integer roleId = create(session, PREFIX + "缺参");

        mockMvc.perform(put("/api/roles/" + roleId + "/authorities").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("mnemonics 不能为空"));
    }

    @Test
    @DisplayName("删除角色时连带清理权限挂载记录")
    void deleteClearsAuthorityBindings() throws Exception {
        MockHttpSession session = login();
        Integer roleId = create(session, PREFIX + "删挂载");
        saveAuthorities(session, roleId, List.of(GROUP_SYSTEM));

        Long before = roleAuthorityMapper.selectCount(new LambdaQueryWrapper<RoleAuthority>()
                .eq(RoleAuthority::getRoleId, roleId));
        assertTrue(before != null && before > 0, "删除前应存在挂载记录");

        mockMvc.perform(delete("/api/roles/" + roleId).session(session))
                .andExpect(jsonPath("$.code").value(0));

        Long after = roleAuthorityMapper.selectCount(new LambdaQueryWrapper<RoleAuthority>()
                .eq(RoleAuthority::getRoleId, roleId));
        assertEquals(0L, after == null ? 0L : after, "删除角色后挂载记录应一并清除");
    }

    @Test
    @DisplayName("角色列表带用户数与权限数统计")
    void listCarriesCounters() throws Exception {
        MockHttpSession session = login();
        Integer roleId = create(session, PREFIX + "统计");
        saveAuthorities(session, roleId, List.of(PAGE_EMPLOYEE));

        User user = new User();
        user.setUsername(PREFIX + "统计用户");
        user.setStatus(UserStatusEnum.NORMAL.getValue());
        user.setRoleId(roleId);
        userMapper.insert(user);

        MvcResult result = mockMvc.perform(get("/api/roles").session(session).param("keyword", PREFIX + "统计"))
                .andExpect(status().isOk())
                .andReturn();
        var records = readJson(result).path("data");
        assertEquals(1, records.size());
        assertEquals(1, records.get(0).path("userCount").asInt());
        assertEquals(2, records.get(0).path("authorityCount").asInt());
    }

    /* ------------------------------------------------------------------ 页面 */

    @Test
    @DisplayName("角色管理页可渲染，含权限树容器")
    void pageRenders() throws Exception {
        MockHttpSession session = login();

        mockMvc.perform(get("/roles").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("角色管理")))
                .andExpect(content().string(containsString("/js/roles.js")))
                .andExpect(content().string(containsString("id=\"authTree\"")))
                .andExpect(content().string(containsString("id=\"btnSavePerm\"")))
                .andExpect(content().string(containsString("id=\"btnCheckAll\"")));
    }

    /* ------------------------------------------------------------------ 工具 */

    private Integer create(MockHttpSession session, String name) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("status", 1);

        MvcResult result = mockMvc.perform(post("/api/roles").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return readJson(result).path("data").asInt();
    }

    private void saveAuthorities(MockHttpSession session, Integer roleId, List<Integer> mnemonics)
            throws Exception {
        mockMvc.perform(put("/api/roles/" + roleId + "/authorities").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("mnemonics", mnemonics))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    private List<Integer> authorities(Integer roleId) throws Exception {
        return roleAuthorityMapper.selectList(new LambdaQueryWrapper<RoleAuthority>()
                        .eq(RoleAuthority::getRoleId, roleId)
                        .orderByAsc(RoleAuthority::getMnemonic))
                .stream()
                .map(RoleAuthority::getMnemonic)
                .toList();
    }

}
