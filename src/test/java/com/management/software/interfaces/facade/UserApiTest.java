package com.management.software.interfaces.facade;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.management.software.domain.aggregate.Role;
import com.management.software.domain.aggregate.User;
import com.management.software.domain.aggregate.entity.UserAccount;
import com.management.software.infrastructure.enums.UserStatusEnum;
import com.management.software.infrastructure.repository.mapper.RoleMapper;
import com.management.software.infrastructure.repository.mapper.UserAccountMapper;
import com.management.software.infrastructure.repository.mapper.UserMapper;
import com.management.software.support.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 用户管理接口集成测试。
 *
 * <p>覆盖：统一初始密码可登录、展示名/登录标识唯一、设置角色、重置密码、冻结与解冻、
 * 禁止冻结当前登录账号、关键字命中登录标识。全部自建数据且事务回滚。
 */
class UserApiTest extends ApiTestSupport {

    private static final String PREFIX = "IT-用户-";

    /** 与配置 app.user.default-password 保持一致（测试里读配置，避免改配置后断言失效）。 */
    @Value("${app.user.default-password}")
    private String defaultPassword;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserAccountMapper userAccountMapper;

    @Autowired
    private RoleMapper roleMapper;

    @Test
    @DisplayName("未登录不能访问用户接口")
    void requiresLogin() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("新增用户后可用统一初始密码登录，且列表能查到")
    void createThenLoginWithDefaultPassword() throws Exception {
        MockHttpSession session = login();
        String identifier = PREFIX + "login";
        Integer id = create(session, PREFIX + "张三", identifier, null, null, UserStatusEnum.NORMAL.getValue());
        assertNotNull(userMapper.selectById(id), "新增后应能查到用户");

        mockMvc.perform(get("/api/users").session(session)
                        .param("keyword", identifier))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].username").value(PREFIX + "张三"))
                .andExpect(jsonPath("$.data.records[0].identifier").value(identifier))
                .andExpect(jsonPath("$.data.records[0].accountReady").value(true));

        // 真登录一次：证明凭证确实是「统一初始密码」的 BCrypt 散列
        loginAs(identifier, defaultPassword);
    }

    @Test
    @DisplayName("展示名重复被拒绝")
    void duplicateUsernameRejected() throws Exception {
        MockHttpSession session = login();
        create(session, PREFIX + "重名", PREFIX + "dup1", null, null, UserStatusEnum.NORMAL.getValue());

        mockMvc.perform(post("/api/users").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", PREFIX + "重名",
                                "identifier", PREFIX + "dup2",
                                "status", UserStatusEnum.NORMAL.getValue()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("展示名已存在：" + PREFIX + "重名"));
    }

    @Test
    @DisplayName("登录标识重复被拒绝")
    void duplicateIdentifierRejected() throws Exception {
        MockHttpSession session = login();
        String identifier = PREFIX + "dup3";
        create(session, PREFIX + "甲", identifier, null, null, UserStatusEnum.NORMAL.getValue());

        mockMvc.perform(post("/api/users").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", PREFIX + "乙",
                                "identifier", identifier,
                                "status", UserStatusEnum.NORMAL.getValue()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("登录标识已存在：" + identifier));
    }

    @Test
    @DisplayName("重置密码后旧密码失效，统一初始密码可用")
    void resetPasswordRestoresDefault() throws Exception {
        MockHttpSession session = login();
        String identifier = PREFIX + "reset";
        Integer id = create(session, PREFIX + "重置", identifier, null, null, UserStatusEnum.NORMAL.getValue());

        // 先把密码改成别的，模拟「用户自己改过密码」
        UserAccount account = accountOf(id);
        userAccountMapper.update(null, Wrappers.<UserAccount>lambdaUpdate()
                .eq(UserAccount::getId, account.getId())
                .set(UserAccount::getCredential, passwordEncoder.encode("UserChanged@1")));
        loginAs(identifier, "UserChanged@1");

        mockMvc.perform(post("/api/users/" + id + "/password/reset").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        loginAs(identifier, defaultPassword);
    }

    @Test
    @DisplayName("冻结后无法登录，解冻后恢复")
    void freezeBlocksLogin() throws Exception {
        MockHttpSession session = login();
        String identifier = PREFIX + "freeze";
        Integer id = create(session, PREFIX + "冻结", identifier, null, null, UserStatusEnum.NORMAL.getValue());

        mockMvc.perform(post("/api/users/" + id + "/freeze").session(session))
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(get("/api/users/" + id).session(session))
                .andExpect(jsonPath("$.data.status").value(UserStatusEnum.FREEZE.getValue()))
                .andExpect(jsonPath("$.data.statusText").value("冻结"));

        // 冻结后登录被拒（AuthService 校验状态）
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(identifier, defaultPassword)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("账号已被冻结，请联系管理员"));

        mockMvc.perform(post("/api/users/" + id + "/unfreeze").session(session))
                .andExpect(jsonPath("$.code").value(0));
        loginAs(identifier, defaultPassword);
    }

    @Test
    @DisplayName("不能冻结当前登录账号")
    void cannotFreezeSelf() throws Exception {
        MockHttpSession session = login();
        User self = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, TEST_USERNAME)
                .last("limit 1"));

        mockMvc.perform(post("/api/users/" + self.getId() + "/freeze").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("不能冻结当前登录的账号"));
    }

    @Test
    @DisplayName("设置角色与取消角色")
    void assignRole() throws Exception {
        MockHttpSession session = login();
        Integer id = create(session, PREFIX + "角色", PREFIX + "role", null, null,
                UserStatusEnum.NORMAL.getValue());

        Role role = new Role();
        role.setName(PREFIX + "测试角色");
        role.setStatus(1);
        roleMapper.insert(role);

        mockMvc.perform(post("/api/users/" + id + "/role").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("roleId", role.getId()))))
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(get("/api/users/" + id).session(session))
                .andExpect(jsonPath("$.data.roleId").value(role.getId()))
                .andExpect(jsonPath("$.data.roleName").value(PREFIX + "测试角色"));

        // roleId 传 null → 取消角色绑定（RoleVO 里 roleId/roleName 为 null，non_null 序列化下字段不出现）
        Map<String, Object> body = new HashMap<>();
        body.put("roleId", null);
        mockMvc.perform(post("/api/users/" + id + "/role").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(jsonPath("$.code").value(0));
        assertNull(userMapper.selectById(id).getRoleId(), "取消角色后 role_id 应为空");
    }

    @Test
    @DisplayName("修改用户：改展示名与登录标识、清空手机号")
    void updateUser() throws Exception {
        MockHttpSession session = login();
        String identifier = PREFIX + "upd1";
        Integer id = create(session, PREFIX + "原名", identifier, "13800000000", "a@b.com",
                UserStatusEnum.NORMAL.getValue());

        mockMvc.perform(put("/api/users/" + id).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", PREFIX + "新名",
                                "identifier", PREFIX + "upd2",
                                "email", "c@d.com",
                                "status", UserStatusEnum.NORMAL.getValue()))))
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/users/" + id).session(session))
                .andExpect(jsonPath("$.data.username").value(PREFIX + "新名"))
                .andExpect(jsonPath("$.data.identifier").value(PREFIX + "upd2"))
                .andExpect(jsonPath("$.data.email").value("c@d.com"))
                // 手机号留空 → 全量覆盖语义下被清空（non_null 序列化时字段不出现）
                .andExpect(jsonPath("$.data.mobile").doesNotExist());

        // 登录标识已改，旧标识登录失败、新标识登录成功
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(identifier, defaultPassword)))
                .andExpect(jsonPath("$.code").value(401));
        loginAs(PREFIX + "upd2", defaultPassword);
    }

    @Test
    @DisplayName("角色筛选与状态筛选生效")
    void filterByRoleAndStatus() throws Exception {
        MockHttpSession session = login();
        Role role = new Role();
        role.setName(PREFIX + "筛选角色");
        role.setStatus(1);
        roleMapper.insert(role);

        Integer matched = create(session, PREFIX + "筛选甲", PREFIX + "filter1", null, null,
                UserStatusEnum.FREEZE.getValue());
        mockMvc.perform(post("/api/users/" + matched + "/role").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("roleId", role.getId()))))
                .andExpect(jsonPath("$.code").value(0));
        create(session, PREFIX + "筛选乙", PREFIX + "filter2", null, null, UserStatusEnum.NORMAL.getValue());

        mockMvc.perform(get("/api/users").session(session)
                        .param("roleId", String.valueOf(role.getId()))
                        .param("status", String.valueOf(UserStatusEnum.FREEZE.getValue())))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].username").value(PREFIX + "筛选甲"));

        mockMvc.perform(get("/api/users").session(session).param("keyword", PREFIX + "筛选"))
                .andExpect(jsonPath("$.data.total").value(2));
    }

    @Test
    @DisplayName("展示名可以是中文，登录仍以标识为准")
    void chineseDisplayNameIsNotLoginName() throws Exception {
        MockHttpSession session = login();
        String identifier = PREFIX + "cn";
        Integer id = create(session, PREFIX + "李四", identifier, null, null, UserStatusEnum.NORMAL.getValue());

        // 用展示名登录应当失败（这是历史事故的回归断言）
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(PREFIX + "李四", defaultPassword)))
                .andExpect(jsonPath("$.code").value(401));

        MockHttpSession userSession = loginAs(identifier, defaultPassword);
        assertNotNull(userSession, "登录标识 + 初始密码应能登录");

        // 登录后欢迎页展示的是展示名
        mockMvc.perform(get("/api/users/" + id).session(session))
                .andExpect(jsonPath("$.data.username").value(PREFIX + "李四"));
    }

    /* ------------------------------------------------------------------ 页面 */

    @Test
    @DisplayName("用户管理页可渲染，导航含新入口")
    void pageRenders() throws Exception {
        MockHttpSession session = login();

        mockMvc.perform(get("/users").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("用户管理")))
                .andExpect(content().string(containsString("/js/users.js")))
                .andExpect(content().string(containsString("id=\"identifier\"")))
                .andExpect(content().string(containsString("id=\"assignRoleId\"")))
                // 密码不由页面填写：提示统一初始密码
                .andExpect(content().string(containsString("系统初始密码")))
                // 导航新增的两个入口
                .andExpect(content().string(containsString("href=\"/roles\"")));
    }

    /* ------------------------------------------------------------------ 工具 */

    private Integer create(MockHttpSession session,
                           String username,
                           String identifier,
                           String mobile,
                           String email,
                           Integer status) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("username", username);
        body.put("identifier", identifier);
        body.put("mobile", mobile);
        body.put("email", email);
        body.put("status", status);

        MvcResult result = mockMvc.perform(post("/api/users").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return readJson(result).path("data").asInt();
    }

    private UserAccount accountOf(Integer userId) {
        UserAccount account = userAccountMapper.selectOne(new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getUserId, userId)
                .last("limit 1"));
        assertNotNull(account, "新增用户应同时创建登录凭证");
        return account;
    }

    /** 登录请求体（走真实 SM2 加密链路，用于断言登录成功/失败）。 */
    private String loginBody(String identifier, String password) throws Exception {
        SecretKey key = requestPublicKey();
        return objectMapper.writeValueAsString(Map.of(
                "username", identifier,
                "keyId", key.keyId(),
                "cipher", encrypt(password, key.publicKey())));
    }

    /** 用指定登录标识 + 密码登录，成功返回会话（失败即断言失败）。 */
    private MockHttpSession loginAs(String identifier, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(identifier, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.redirect").value("/home"))
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    @Test
    @DisplayName("统一初始密码配置必须存在且足够长度")
    void defaultPasswordConfigured() {
        assertNotNull(defaultPassword, "app.user.default-password 未配置");
        assertTrue(defaultPassword != null && defaultPassword.trim().length() >= 8,
                "统一初始密码至少 8 位，当前：" + defaultPassword);
    }

}
