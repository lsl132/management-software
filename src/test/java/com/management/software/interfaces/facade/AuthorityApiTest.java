package com.management.software.interfaces.facade;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.management.software.domain.aggregate.entity.RoleAuthority;
import com.management.software.infrastructure.repository.mapper.RoleAuthorityMapper;
import com.management.software.support.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 权限接口集成测试：权限树的万位归组 + 权限字典的增删改查与三条硬约束。
 *
 * <p>依赖 seed 权限字典（{@code docs/db/seed-authority.sql}：5 个模块分组 + 19 个功能页），
 * 自建的权限与角色随事务回滚；测试用的助记符统一落在 60000 段，与 seed 的 10000~50000 不重叠。
 */
class AuthorityApiTest extends ApiTestSupport {

    /** 测试用模块分组及其功能页助记符（60000 段在 seed 中不存在）。 */
    private static final int GROUP_TEST = 60000;
    private static final int PAGE_TEST = 60001;
    private static final int PAGE_TEST_MOVED = 60005;

    @Autowired
    private RoleAuthorityMapper roleAuthorityMapper;

    @Test
    @DisplayName("未登录不能访问权限接口")
    void requiresLogin() throws Exception {
        mockMvc.perform(get("/api/authorities"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("权限树按万位归组，分组下挂功能页")
    void treeGroupsPagesByMnemonic() throws Exception {
        MockHttpSession session = login();

        MvcResult result = mockMvc.perform(get("/api/authorities").session(session)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        JsonNode groups = readJson(result).path("data");
        assertTrue(groups.size() >= 5, "seed 至少包含 5 个模块分组，实际：" + groups.size());

        int pageTotal = 0;
        for (JsonNode group : groups) {
            int groupMnemonic = group.path("mnemonic").asInt();
            assertEquals(0, groupMnemonic % 10000, "分组助记符应为整万位：" + groupMnemonic);
            assertEquals(1, group.path("type").asInt(), "分组节点 type 应为 1");
            assertEquals("模块分组", group.path("typeText").asText());

            JsonNode children = group.path("children");
            assertTrue(children.size() > 0, "分组 " + groupMnemonic + " 下应有功能页");

            int previous = 0;
            for (JsonNode page : children) {
                int mnemonic = page.path("mnemonic").asInt();
                assertEquals(groupMnemonic, mnemonic / 10000 * 10000,
                        "功能页 " + mnemonic + " 应归入分组 " + groupMnemonic);
                assertTrue(mnemonic > previous, "同一分组下功能页应按助记符升序");
                previous = mnemonic;
                assertEquals(2, page.path("type").asInt(), "功能页节点 type 应为 2");
                pageTotal++;
            }
        }

        assertTrue(pageTotal >= 19, "seed 至少包含 19 个功能页，实际：" + pageTotal);
    }

    @Test
    @DisplayName("分组与功能页助记符互不重复且与分组对齐")
    void mnemonicsAreUnique() throws Exception {
        MockHttpSession session = login();
        MvcResult result = mockMvc.perform(get("/api/authorities").session(session))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode groups = readJson(result).path("data");
        List<Integer> all = new ArrayList<>();
        for (JsonNode group : groups) {
            all.add(group.path("mnemonic").asInt());
            for (JsonNode page : group.path("children")) {
                all.add(page.path("mnemonic").asInt());
            }
        }
        assertEquals(all.size(), all.stream().distinct().count(), "助记符不应重复：" + all);
    }

    @Test
    @DisplayName("权限树节点带主键与授权角色数")
    void treeNodesCarryIdAndRoleCount() throws Exception {
        MockHttpSession session = login();
        MvcResult result = mockMvc.perform(get("/api/authorities").session(session))
                .andExpect(status().isOk())
                .andReturn();

        for (JsonNode group : readJson(result).path("data")) {
            assertTrue(group.path("id").asInt() > 0, "分组节点必须带主键 id（编辑/删除按 id 定位）");
            assertTrue(group.has("roleCount"), "分组节点必须带 roleCount");
            for (JsonNode page : group.path("children")) {
                assertTrue(page.path("id").asInt() > 0, "功能页节点必须带主键 id");
                assertTrue(page.has("roleCount"), "功能页节点必须带 roleCount");
            }
        }
    }

    @Test
    @DisplayName("权限树支持关键字过滤：命中分组展开全部子页，命中功能页带出所属分组")
    void treeSupportsKeyword() throws Exception {
        MockHttpSession session = login();

        // 命中功能页：保留该页 + 其所属分组
        MvcResult byPage = mockMvc.perform(get("/api/authorities").session(session)
                        .param("keyword", "组织架构"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode groups = readJson(byPage).path("data");
        assertEquals(1, groups.size(), "「组织架构」只属于人力资源分组");
        assertEquals(20000, groups.get(0).path("mnemonic").asInt());
        assertEquals(1, groups.get(0).path("children").size(), "只保留命中的功能页");
        assertEquals("组织架构", groups.get(0).path("children").get(0).path("authName").asText());

        // 命中分组：展开全部子页
        MvcResult byGroup = mockMvc.perform(get("/api/authorities").session(session)
                        .param("keyword", "系统设置"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode system = readJson(byGroup).path("data");
        assertEquals(1, system.size());
        assertEquals(10000, system.get(0).path("mnemonic").asInt());
        assertTrue(system.get(0).path("children").size() >= 4, "命中分组应展开其全部功能页");

        // 按助记符搜索
        MvcResult byMnemonic = mockMvc.perform(get("/api/authorities").session(session)
                        .param("keyword", "30003"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode tax = readJson(byMnemonic).path("data");
        assertEquals(1, tax.size());
        assertEquals("税务报表", tax.get(0).path("children").get(0).path("authName").asText());
    }

    @Test
    @DisplayName("权限增删改查全流程")
    void crudFlow() throws Exception {
        MockHttpSession session = login();

        Integer groupId = create(session, GROUP_TEST, "IT-权限-测试模块", 1, "临时分组");
        Integer pageId = create(session, PAGE_TEST, "IT-权限-测试功能页", 2, "临时功能页");

        // 新增后出现在权限树里，且功能页挂在分组下
        JsonNode groups = tree(session);
        JsonNode group = findGroup(groups, GROUP_TEST);
        assertNotNull(group, "新增的模块分组应出现在权限树中：" + groups);
        assertEquals("IT-权限-测试模块", group.path("authName").asText());
        assertEquals("模块分组", group.path("typeText").asText());
        assertEquals(1, group.path("children").size());
        assertEquals("IT-权限-测试功能页", group.path("children").get(0).path("authName").asText());

        // 详情：子页数与引用角色数
        mockMvc.perform(get("/api/authorities/" + groupId).session(session))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.authName").value("IT-权限-测试模块"))
                .andExpect(jsonPath("$.data.description").value("临时分组"))
                .andExpect(jsonPath("$.data.typeText").value("模块分组"))
                .andExpect(jsonPath("$.data.groupMnemonic").value(GROUP_TEST))
                .andExpect(jsonPath("$.data.childCount").value(1))
                .andExpect(jsonPath("$.data.roleCount").value(0));

        mockMvc.perform(get("/api/authorities/" + pageId).session(session))
                .andExpect(jsonPath("$.data.authName").value("IT-权限-测试功能页"))
                .andExpect(jsonPath("$.data.typeText").value("功能页"))
                .andExpect(jsonPath("$.data.groupMnemonic").value(GROUP_TEST))
                .andExpect(jsonPath("$.data.childCount").value(0));

        // 修改名称与说明
        mockMvc.perform(put("/api/authorities/" + pageId).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saveBody(PAGE_TEST, "IT-权限-功能页改名", 2, "改过了")))
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/authorities/" + pageId).session(session))
                .andExpect(jsonPath("$.data.authName").value("IT-权限-功能页改名"))
                .andExpect(jsonPath("$.data.description").value("改过了"));

        // 说明留空 → 清空（ALWAYS 更新策略）
        mockMvc.perform(put("/api/authorities/" + pageId).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saveBody(PAGE_TEST, "IT-权限-功能页改名", 2, null)))
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(get("/api/authorities/" + pageId).session(session))
                // non_null 序列化：清空后的字段不出现在返回体里
                .andExpect(jsonPath("$.data.description").doesNotExist());

        // 先删子页再删分组
        mockMvc.perform(delete("/api/authorities/" + pageId).session(session))
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(delete("/api/authorities/" + groupId).session(session))
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(get("/api/authorities/" + groupId).session(session))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message", containsString("权限不存在")));
    }

    @Test
    @DisplayName("助记符格式与归属校验")
    void mnemonicRulesAreEnforced() throws Exception {
        MockHttpSession session = login();

        // 模块分组的助记符必须是整万位
        expectCreateRejected(session, GROUP_TEST + 1, "IT-权限-非法分组", 1, "必须是整万位");
        // 功能页的助记符不能是整万位
        expectCreateRejected(session, GROUP_TEST, "IT-权限-非法功能页", 2, "不能是整万位");
        // 功能页必须已有归属分组（70000 段在 seed 中不存在）
        expectCreateRejected(session, 70001, "IT-权限-孤儿功能页", 2, "找不到该功能页所属的模块分组");
        // 助记符全局唯一
        expectCreateRejected(session, 10000, "IT-权限-重复助记符", 1, "权限助记符已存在");
        // 类型越界由 @Valid 拦下
        expectCreateRejected(session, 80000, "IT-权限-非法类型", 9, "type");
    }

    @Test
    @DisplayName("路由：功能页可存取、留空即清空，模块分组一律不存路由")
    void pathIsPersistedAndNormalized() throws Exception {
        MockHttpSession session = login();

        // 分组带上路由 → 服务端强制置空：分组是下拉容器、本身没有页面，
        // 留着路由会让二级导航渲染出一个「能点但打不开」的一级入口
        Integer groupId = createWithPath(session, GROUP_TEST, "IT-权限-路由模块", 1, "/should-be-dropped");
        mockMvc.perform(get("/api/authorities/" + groupId).session(session))
                .andExpect(jsonPath("$.data.path").doesNotExist());

        // 功能页带上路由 → 正常存取
        Integer pageId = createWithPath(session, PAGE_TEST, "IT-权限-路由功能页", 2, "/it-test-route");
        mockMvc.perform(get("/api/authorities/" + pageId).session(session))
                .andExpect(jsonPath("$.data.path").value("/it-test-route"));

        // 权限树要带上路由：二级导航与权限管理页都从这一份数据取
        JsonNode page = findGroup(tree(session), GROUP_TEST).path("children").get(0);
        assertEquals("/it-test-route", page.path("path").asText(), "权限树节点应带上路由");

        // 留空 → 清空（ALWAYS 更新策略：否则填错的路由永远删不掉，导航会一直指向错的地址）
        mockMvc.perform(put("/api/authorities/" + pageId).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saveBodyWithPath(PAGE_TEST, "IT-权限-路由功能页", 2, null)))
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(get("/api/authorities/" + pageId).session(session))
                .andExpect(jsonPath("$.data.path").doesNotExist());

        // 空串与留空等价：库里只允许 null 一种「没有路由」，否则判空到处都要写两遍
        mockMvc.perform(put("/api/authorities/" + pageId).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saveBodyWithPath(PAGE_TEST, "IT-权限-路由功能页", 2, "")))
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(get("/api/authorities/" + pageId).session(session))
                .andExpect(jsonPath("$.data.path").doesNotExist());

        mockMvc.perform(delete("/api/authorities/" + pageId).session(session))
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(delete("/api/authorities/" + groupId).session(session))
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("路由格式校验：必须 / 开头、不含空白、不超 128")
    void pathFormatIsValidated() throws Exception {
        MockHttpSession session = login();
        Integer groupId = create(session, GROUP_TEST, "IT-权限-路由校验模块", 1, null);

        // 相对路径：会跟着当前页 URL 变，同一个菜单项在不同页面点开跳到不同地方
        expectCreateRejected(session, PAGE_TEST, 2, "employees", "必须以 / 开头");
        // 含空白
        expectCreateRejected(session, PAGE_TEST, 2, "/a b", "不能包含空白");
        // 超长（1 个 / 加 128 个字符 = 129 > 128）
        expectCreateRejected(session, PAGE_TEST, 2, "/" + "a".repeat(128), "长度不能超过 128");

        // 合法路由可以建出来，并能从详情读回
        Integer pageId = createWithPath(session, PAGE_TEST, "IT-权限-路由合法", 2, "/it-ok");
        mockMvc.perform(get("/api/authorities/" + pageId).session(session))
                .andExpect(jsonPath("$.data.path").value("/it-ok"));

        mockMvc.perform(delete("/api/authorities/" + pageId).session(session))
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(delete("/api/authorities/" + groupId).session(session))
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("改助记符会级联同步已授权的角色")
    void renameMnemonicCascadesToRoleAuthorities() throws Exception {
        MockHttpSession session = login();

        Integer groupId = create(session, GROUP_TEST, "IT-权限-级联模块", 1, null);
        Integer pageId = create(session, PAGE_TEST, "IT-权限-级联功能页", 2, null);

        Integer roleId = createRole(session, "IT-权限-级联角色");
        mockMvc.perform(put("/api/roles/" + roleId + "/authorities").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("mnemonics", List.of(PAGE_TEST)))))
                .andExpect(jsonPath("$.code").value(0));

        List<Integer> before = authoritiesOf(roleId);
        assertTrue(before.contains(PAGE_TEST), "授权应写入功能页助记符：" + before);
        assertTrue(before.contains(GROUP_TEST), "授权功能页应自动补上所属分组：" + before);

        // 改助记符：60001 → 60005
        mockMvc.perform(put("/api/authorities/" + pageId).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saveBody(PAGE_TEST_MOVED, "IT-权限-级联功能页", 2, null)))
                .andExpect(jsonPath("$.code").value(0));

        List<Integer> after = authoritiesOf(roleId);
        assertTrue(after.contains(PAGE_TEST_MOVED),
                "角色的授权应随新助记符同步，而不是断链：" + after);
        assertFalse(after.contains(PAGE_TEST), "旧助记符不应再留在 role_authority：" + after);
        assertEquals(before.size(), after.size(), "同步前后授权条数应一致（只是助记符换了值）");

        // 新助记符出现在权限树中，且位置未变（仍在 60000 分组下）
        JsonNode group = findGroup(tree(session), GROUP_TEST);
        assertNotNull(group);
        assertEquals(PAGE_TEST_MOVED, group.path("children").get(0).path("mnemonic").asInt());

        // 改名没有把引用关系弄丢：此刻删除仍会因「被角色引用」被拒（说明 role_authority 已跟着新助记符）
        mockMvc.perform(delete("/api/authorities/" + pageId).session(session))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message", containsString("已被 1 个角色引用")));

        // 清理：先解除授权，再删子页与分组（分组还有子页时不允许删除）
        mockMvc.perform(put("/api/roles/" + roleId + "/authorities").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("mnemonics", List.of()))))
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(delete("/api/authorities/" + pageId).session(session))
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(delete("/api/authorities/" + groupId).session(session))
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("删除保护：被角色引用、分组仍有子页时拒绝删除")
    void deleteIsProtected() throws Exception {
        MockHttpSession session = login();

        Integer groupId = create(session, GROUP_TEST, "IT-权限-保护模块", 1, null);
        Integer pageId = create(session, PAGE_TEST, "IT-权限-保护功能页", 2, null);

        // 分组下还有功能页 → 拒绝
        mockMvc.perform(delete("/api/authorities/" + groupId).session(session))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message", containsString("还有 1 个功能页")));

        // 分组带子页时也不允许改助记符（否则子页会失去归属）
        mockMvc.perform(put("/api/authorities/" + groupId).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saveBody(90000, "IT-权限-保护模块", 1, null)))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message", containsString("修改助记符会让它们失去归属")));

        // 功能页被角色引用 → 拒绝
        Integer roleId = createRole(session, "IT-权限-保护角色");
        mockMvc.perform(put("/api/roles/" + roleId + "/authorities").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("mnemonics", List.of(PAGE_TEST)))))
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(delete("/api/authorities/" + pageId).session(session))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message", containsString("已被 1 个角色引用")));

        // 详情里也能看到引用数，供页面提前提示
        mockMvc.perform(get("/api/authorities/" + pageId).session(session))
                .andExpect(jsonPath("$.data.roleCount").value(1));

        // 解除授权后即可删除
        mockMvc.perform(put("/api/roles/" + roleId + "/authorities").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("mnemonics", List.of()))))
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(delete("/api/authorities/" + pageId).session(session))
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(delete("/api/authorities/" + groupId).session(session))
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("权限管理页正常渲染")
    void pageRenders() throws Exception {
        MockHttpSession session = login();

        mockMvc.perform(get("/authorities").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("权限管理")))
                .andExpect(content().string(containsString("/js/authorities.js")))
                .andExpect(content().string(containsString("id=\"authorityBody\"")))
                .andExpect(content().string(containsString("id=\"authorityModal\"")))
                .andExpect(content().string(containsString("id=\"groupPick\"")))
                .andExpect(content().string(containsString("模块分组")))
                .andExpect(content().string(containsString("功能页")));
    }

    /* ------------------------------------------------------------------ 工具 */

    private JsonNode tree(MockHttpSession session) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/authorities").session(session))
                .andExpect(status().isOk())
                .andReturn();
        return readJson(result).path("data");
    }

    private JsonNode findGroup(JsonNode groups, int mnemonic) {
        for (JsonNode group : groups) {
            if (group.path("mnemonic").asInt() == mnemonic) {
                return group;
            }
        }
        return null;
    }

    private String saveBody(int mnemonic, String authName, int type, String description) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("mnemonic", mnemonic);
        body.put("authName", authName);
        body.put("type", type);
        body.put("description", description);
        return objectMapper.writeValueAsString(body);
    }

    private Integer create(MockHttpSession session, int mnemonic, String name,
                           int type, String description) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/authorities").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saveBody(mnemonic, name, type, description)))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return readJson(result).path("data").asInt();
    }

    private void expectCreateRejected(MockHttpSession session, int mnemonic, String name,
                                      int type, String expectMessage) throws Exception {
        mockMvc.perform(post("/api/authorities").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saveBody(mnemonic, name, type, null)))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message", containsString(expectMessage)));
    }

    /** 带路由的请求体；说明固定留空，只关心路由这一个字段。 */
    private String saveBodyWithPath(int mnemonic, String authName, int type, String path) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("mnemonic", mnemonic);
        body.put("authName", authName);
        body.put("type", type);
        body.put("path", path);
        return objectMapper.writeValueAsString(body);
    }

    private Integer createWithPath(MockHttpSession session, int mnemonic, String name,
                                   int type, String path) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/authorities").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saveBodyWithPath(mnemonic, name, type, path)))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return readJson(result).path("data").asInt();
    }

    /** 路由格式非法时的统一断言入口（与按名称的重载区分：这里第 4 个参数是路由）。 */
    private void expectCreateRejected(MockHttpSession session, int mnemonic,
                                      int type, String path, String expectMessage) throws Exception {
        mockMvc.perform(post("/api/authorities").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saveBodyWithPath(mnemonic, "IT-权限-路由非法", type, path)))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message", containsString(expectMessage)));
    }

    private Integer createRole(MockHttpSession session, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/roles").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", name, "status", 1))))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return readJson(result).path("data").asInt();
    }

    private List<Integer> authoritiesOf(Integer roleId) {
        return roleAuthorityMapper.selectList(new LambdaQueryWrapper<RoleAuthority>()
                        .eq(RoleAuthority::getRoleId, roleId)
                        .orderByAsc(RoleAuthority::getMnemonic))
                .stream()
                .map(RoleAuthority::getMnemonic)
                .toList();
    }

}
