package com.management.software.interfaces.facade;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.management.software.domain.aggregate.User;
import com.management.software.domain.aggregate.entity.UserLoginRecord;
import com.management.software.infrastructure.interceptor.LoginInterceptor;
import com.management.software.infrastructure.repository.mapper.UserLoginRecordMapper;
import com.management.software.support.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 登录链路集成测试：SM2 加密传输、密钥一次性、登录态与页面渲染。
 */
class AuthApiTest extends ApiTestSupport {

    @Autowired
    private UserLoginRecordMapper userLoginRecordMapper;

    @Test
    @DisplayName("登录成功写入登录记录：用户ID + X-Forwarded-For 首个地址 + 时间")
    void loginWritesLoginRecord() throws Exception {
        SecretKey key = requestPublicKey();
        mockMvc.perform(post("/api/auth/login")
                        .header("X-Forwarded-For", "203.0.113.9, 10.0.0.2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", TEST_USERNAME,
                                "keyId", key.keyId(),
                                "cipher", encrypt(TEST_PASSWORD, key.publicKey())))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        User user = requireTestUser();
        UserLoginRecord record = latestRecord(user.getId());
        assertNotNull(record, "登录成功应写入 user_login_record");
        assertEquals("203.0.113.9", record.getLoginIp(), "应取 X-Forwarded-For 中的首个地址");
        assertNotNull(record.getCreateTime(), "登录时间应由 MyMetaObjectHandler 自动填充");
        System.out.println("[AUTH] 登录记录 -> userId=" + record.getUserId()
                + ", ip=" + record.getLoginIp() + ", time=" + record.getCreateTime());
    }

    @Test
    @DisplayName("无代理头时登录记录落到 remoteAddr")
    void loginRecordUsesRemoteAddrWithoutProxyHeader() throws Exception {
        login();

        UserLoginRecord record = latestRecord(requireTestUser().getId());
        assertNotNull(record, "登录成功应写入 user_login_record");
        assertEquals("127.0.0.1", record.getLoginIp());
    }

    @Test
    @DisplayName("登录失败不写登录记录")
    void failedLoginWritesNoRecord() throws Exception {
        SecretKey key = requestPublicKey();
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", TEST_USERNAME,
                                "keyId", key.keyId(),
                                "cipher", encrypt("wrong-password", key.publicKey())))))
                .andExpect(jsonPath("$.code").value(401));

        assertEquals(0, recordsOf(requireTestUser().getId()).size(), "登录失败不应写登录记录");
    }

    private User requireTestUser() {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, TEST_USERNAME)
                .last("limit 1"));
        assertNotNull(user, "测试账号应已由 ApiTestSupport 准备好");
        return user;
    }

    private List<UserLoginRecord> recordsOf(Integer userId) {
        return userLoginRecordMapper.selectList(new LambdaQueryWrapper<UserLoginRecord>()
                .eq(UserLoginRecord::getUserId, userId)
                .orderByDesc(UserLoginRecord::getId));
    }

    private UserLoginRecord latestRecord(Integer userId) {
        List<UserLoginRecord> records = recordsOf(userId);
        return records.isEmpty() ? null : records.get(0);
    }

    @Test
    @DisplayName("SM2 加密密码登录成功，并写入会话")
    void loginSucceeds() throws Exception {
        MockHttpSession session = login();
        assertNotNull(session.getAttribute(LoginInterceptor.SESSION_USER));
    }

    @Test
    @DisplayName("密码错误返回 401，且不建立登录态")
    void loginFailsWithWrongPassword() throws Exception {
        SecretKey key = requestPublicKey();
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", TEST_USERNAME,
                                "keyId", key.keyId(),
                                "cipher", encrypt("wrong-password", key.publicKey())))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("用户名或密码错误"));
    }

    @Test
    @DisplayName("账号不存在也返回同样的提示，不泄露账号是否存在")
    void loginFailsWithUnknownUser() throws Exception {
        SecretKey key = requestPublicKey();
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "not-exist-user",
                                "keyId", key.keyId(),
                                "cipher", encrypt(TEST_PASSWORD, key.publicKey())))))
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("用户名或密码错误"));
    }

    @Test
    @DisplayName("同一密钥不能重复使用（防重放）")
    void publicKeyIsOneTimeOnly() throws Exception {
        SecretKey key = requestPublicKey();
        String cipher = encrypt(TEST_PASSWORD, key.publicKey());
        String body = objectMapper.writeValueAsString(Map.of(
                "username", TEST_USERNAME, "keyId", key.keyId(), "cipher", cipher));

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("加密凭证已失效，请刷新页面后重试"));
    }

    @Test
    @DisplayName("伪造的 keyId 无法登录")
    void unknownKeyIdRejected() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", TEST_USERNAME,
                                "keyId", "0123456789abcdef0123456789abcdef",
                                "cipher", encrypt(TEST_PASSWORD, requestPublicKey().publicKey())))))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("加密凭证已失效，请刷新页面后重试"));
    }

    @Test
    @DisplayName("密文被篡改无法登录")
    void tamperedCipherRejected() throws Exception {
        SecretKey key = requestPublicKey();
        String cipher = encrypt(TEST_PASSWORD, key.publicKey());
        String tampered = "ff" + cipher.substring(2);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", TEST_USERNAME, "keyId", key.keyId(), "cipher", tampered))))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("加密凭证解析失败，请刷新页面后重试"));
    }

    @Test
    @DisplayName("缺少请求参数时返回参数校验错误")
    void loginValidatesRequest() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"keyId\":\"\",\"cipher\":\"\"}"))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("未登录访问受保护接口返回 401 JSON")
    void protectedApiRequiresLogin() throws Exception {
        mockMvc.perform(get("/api/employees"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("未登录访问后台页面重定向到登录页")
    void protectedPageRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/home"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("/login")));

        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("/login")));
    }

    @Test
    @DisplayName("登录页渲染：含「记住登录账号」勾选框与脚本，且不展示技术实现细节")
    void loginPageRenders() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"rememberMe\"")))
                .andExpect(content().string(containsString("记住登录账号")))
                .andExpect(content().string(containsString("/js/sm2.js")))
                .andExpect(content().string(containsString("/js/login.js")))
                // 登录页面向最终用户，不应暴露国密 / 加密算法 / 密码存储等实现细节
                .andExpect(content().string(not(containsString("国密"))))
                .andExpect(content().string(not(containsString("SM2"))))
                .andExpect(content().string(not(containsString("BCrypt"))));
    }

    @Test
    @DisplayName("登录后欢迎页、员工管理页、关于页均正常渲染")
    void pagesRenderAfterLogin() throws Exception {
        MockHttpSession session = login();

        mockMvc.perform(get("/home").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("欢迎回来")))
                .andExpect(content().string(containsString(TEST_USERNAME)));

        mockMvc.perform(get("/employees").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("员工管理")))
                .andExpect(content().string(containsString("/js/employees.js")));

        mockMvc.perform(get("/about").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("运行信息")));
    }

    @Test
    @DisplayName("已登录访问登录页会跳回欢迎页")
    void loginPageRedirectsWhenAuthenticated() throws Exception {
        MockHttpSession session = login();

        mockMvc.perform(get("/login").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("/home")));
    }

    @Test
    @DisplayName("登录名以 user_account.identifier 为准：展示名与登录标识不一致时仍可登录")
    void loginResolvesByAccountIdentifierNotDisplayName() throws Exception {
        // 复现真实故障：运维把 user.username 改成中文展示名，登录标识保持 admin。
        // 若登录按 user.username 解析，改名会直接锁死登录。
        String displayName = "display_name_测试姓名";
        User renamed = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, TEST_USERNAME)
                .last("limit 1"));
        renamed.setUsername(displayName);
        userMapper.updateById(renamed);

        MockHttpSession session = login();

        assertNotNull(session.getAttribute(LoginInterceptor.SESSION_USER), "应能用登录标识正常登录");
        // 会话里的名字取展示名（欢迎页显示中文姓名），登录凭据仍走 identifier + credential
        mockMvc.perform(get("/home").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(displayName)));
    }

    @Test
    @DisplayName("退出登录后会话失效")
    void logoutInvalidatesSession() throws Exception {
        MockHttpSession session = login();

        mockMvc.perform(post("/api/auth/logout").session(session))
                .andExpect(jsonPath("$.code").value(0));

        assertTrue(session.isInvalid(), "退出后会话应当失效");

        // 浏览器会丢弃失效会话，这里用不带会话的请求模拟后续访问
        mockMvc.perform(get("/api/employees"))
                .andExpect(status().isUnauthorized());
    }

}
