package com.management.software.interfaces.facade;

import com.management.software.application.dto.CurrentUserVO;
import com.management.software.domain.aggregate.User;
import com.management.software.infrastructure.interceptor.LoginInterceptor;
import com.management.software.support.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.ResultActions;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 个人信息与自助修改密码接口测试（导航「我的」下的两项）。
 */
class ProfileApiTest extends ApiTestSupport {

    private static final String JSON_TYPE = MediaType.APPLICATION_JSON_VALUE;

    /** 取会话里那个人的库中记录。 */
    private User userOf(MockHttpSession session) {
        CurrentUserVO current = (CurrentUserVO) session.getAttribute(LoginInterceptor.SESSION_USER);
        return userMapper.selectById(current.getUserId());
    }

    private ResultActions saveProfile(MockHttpSession session, String username, String mobile, String email)
            throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("username", username);
        body.put("mobile", mobile);
        body.put("email", email);
        return mockMvc.perform(put("/api/profile")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    /** 走完整链路改密码：明文 JSON -> 一次性公钥加密 -> 提交密文。 */
    private ResultActions changePassword(MockHttpSession session, String oldPassword, String newPassword)
            throws Exception {
        SecretKey key = requestPublicKey();
        Map<String, String> plain = new HashMap<>();
        plain.put("oldPassword", oldPassword);
        plain.put("newPassword", newPassword);
        String cipher = encrypt(objectMapper.writeValueAsString(plain), key.publicKey());
        return mockMvc.perform(put("/api/profile/password")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("keyId", key.keyId(), "cipher", cipher))));
    }

    private ResultActions loginWith(String password) throws Exception {
        SecretKey key = requestPublicKey();
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "username", TEST_USERNAME,
                        "keyId", key.keyId(),
                        "cipher", encrypt(password, key.publicKey())))));
    }

    /* ------------------------------------------------------------ 读取 */

    @Test
    @DisplayName("读取本人资料：账号/角色/状态齐全，且不回传密码散列")
    void detailReturnsOwnProfile() throws Exception {
        MockHttpSession session = login();

        mockMvc.perform(get("/api/profile").session(session))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.identifier").value(TEST_USERNAME))
                .andExpect(jsonPath("$.data.username").value(TEST_USERNAME))
                .andExpect(jsonPath("$.data.statusText").value("正常"))
                .andExpect(jsonPath("$.data.credential").doesNotExist());
    }

    /* ------------------------------------------------------------ 保存 */

    @Test
    @DisplayName("保存只动显示名/手机/邮箱：角色不会被顺带清空，会话里的显示名同步刷新")
    void saveOnlyTouchesEditableColumns() throws Exception {
        MockHttpSession session = login();
        User before = userOf(session);

        // 先挂一个角色：User 的 role_id 是 ALWAYS 策略，一旦用 updateById 做局部更新就会被写成 NULL，
        // 这条断言就是用来钉住「必须按列更新」这个实现的
        User withRole = new User();
        withRole.setId(before.getId());
        withRole.setRoleId(4242);
        userMapper.updateById(withRole);

        Map<String, Object> body = new HashMap<>();
        body.put("username", "资料改名");
        body.put("mobile", "13900001111");
        body.put("email", "profile@example.com");
        // 入参里没有 roleId，即使硬塞也不会被采纳（Jackson 忽略未知字段）
        body.put("roleId", 7777);

        mockMvc.perform(put("/api/profile")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.username").value("资料改名"))
                .andExpect(jsonPath("$.data.mobile").value("13900001111"));

        User after = userMapper.selectById(before.getId());
        assertEquals("资料改名", after.getUsername());
        assertEquals("13900001111", after.getMobile());
        assertEquals("profile@example.com", after.getEmail());
        assertEquals(4242, after.getRoleId(), "只改三个字段，角色不该被清空");
        assertEquals(Integer.valueOf(1), after.getStatus(), "状态也不该被牵连");
        assertNotNull(after.getUpdateTime(), "按列更新不经过自动填充，update_time 必须显式写");

        // 顶栏显示名读的是会话里那份 CurrentUserVO，保存后要一起变
        CurrentUserVO current = (CurrentUserVO) session.getAttribute(LoginInterceptor.SESSION_USER);
        assertEquals("资料改名", current.getUsername());
    }

    @Test
    @DisplayName("手机号与邮箱留空即清空，不会存成空字符串")
    void blankMobileAndEmailCleared() throws Exception {
        MockHttpSession session = login();

        saveProfile(session, TEST_USERNAME, "13900001111", "keep@example.com")
                .andExpect(jsonPath("$.code").value(0));

        saveProfile(session, TEST_USERNAME, "", "")
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.mobile").doesNotExist())
                .andExpect(jsonPath("$.data.email").doesNotExist());

        User after = userOf(session);
        assertEquals(null, after.getMobile());
        assertEquals(null, after.getEmail());
    }

    @Test
    @DisplayName("显示名为空、邮箱格式非法时返回字段级提示")
    void saveValidatesInput() throws Exception {
        MockHttpSession session = login();

        saveProfile(session, "   ", "13900001111", null)
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("username")));

        saveProfile(session, TEST_USERNAME, null, "not-an-email")
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("email")));
    }

    /* ---------------------------------------------------------- 改密码 */

    @Test
    @DisplayName("原密码不正确时拒绝修改，密码不变")
    void wrongOldPasswordRejected() throws Exception {
        MockHttpSession session = login();

        changePassword(session, "NotMyPassword1", "NewPass2026")
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("原密码不正确"));

        // 原密码仍然可用
        loginWith(TEST_PASSWORD).andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("新密码规则：与原密码相同 / 太短 / 纯数字 都拒绝")
    void newPasswordRulesEnforced() throws Exception {
        MockHttpSession session = login();

        changePassword(session, TEST_PASSWORD, TEST_PASSWORD)
                .andExpect(jsonPath("$.message").value("新密码不能与原密码相同"));

        changePassword(session, TEST_PASSWORD, "Ab1")
                .andExpect(jsonPath("$.message").value("新密码长度需为 8~32 位"));

        changePassword(session, TEST_PASSWORD, "12345678")
                .andExpect(jsonPath("$.message").value("新密码需同时包含字母与数字"));
    }

    @Test
    @DisplayName("修改成功：换新散列、旧密码失效、当前会话作废")
    void changePasswordSucceeds() throws Exception {
        MockHttpSession session = login();
        String newPassword = "NewPass2026";

        changePassword(session, TEST_PASSWORD, newPassword)
                .andExpect(jsonPath("$.code").value(0));

        assertTrue(session.isInvalid(), "改密码成功后旧会话必须作废，否则旧凭证继续有效");

        // 浏览器会丢弃失效会话，这里用不带会话的请求模拟后续访问
        mockMvc.perform(get("/api/profile")).andExpect(status().isUnauthorized());

        loginWith(newPassword).andExpect(jsonPath("$.code").value(0));
        loginWith(TEST_PASSWORD)
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("用户名或密码错误"));
    }

    @Test
    @DisplayName("未登录不能读资料或改密码")
    void requiresLogin() throws Exception {
        mockMvc.perform(get("/api/profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        mockMvc.perform(put("/api/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(put("/api/profile/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

}
