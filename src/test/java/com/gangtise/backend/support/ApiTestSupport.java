package com.gangtise.backend.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gangtise.backend.domain.aggregate.User;
import com.gangtise.backend.domain.aggregate.entity.UserAccount;
import com.gangtise.backend.infrastructure.enums.UserStatusEnum;
import com.gangtise.backend.infrastructure.repository.mapper.UserAccountMapper;
import com.gangtise.backend.infrastructure.repository.mapper.UserMapper;
import com.gangtise.backend.infrastructure.utils.Sm2Utils;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 接口层集成测试基类。
 *
 * <p>统一准备一个密码已知的测试账号，并提供「走完 SM2 加密登录链路拿到会话」的工具方法。
 * 所有用例带事务并回滚，不会污染本地库。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public abstract class ApiTestSupport {

    protected static final String TEST_USERNAME = "api_test_user";
    protected static final String TEST_PASSWORD = "Test@123456";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected UserMapper userMapper;

    @Autowired
    protected UserAccountMapper userAccountMapper;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @BeforeEach
    void prepareTestUser() {
        Long exists = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, TEST_USERNAME));
        if (exists != null && exists > 0) {
            return;
        }

        User user = new User();
        user.setUsername(TEST_USERNAME);
        user.setStatus(UserStatusEnum.NORMAL.getValue());
        userMapper.insert(user);

        UserAccount account = new UserAccount();
        account.setUserId(user.getId());
        account.setIdentifier(TEST_USERNAME);
        account.setCredential(passwordEncoder.encode(TEST_PASSWORD));
        userAccountMapper.insert(account);
    }

    /** 申请一次性公钥。 */
    protected SecretKey requestPublicKey() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/public-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        JsonNode data = readJson(result).path("data");
        return new SecretKey(data.path("keyId").asText(), data.path("publicKey").asText());
    }

    /** 用服务端公钥加密明文，模拟前端 sm-crypto 的行为（C1C3C2，C1 不带 04 前缀）。 */
    protected String encrypt(String plainText, String publicKey) {
        return Sm2Utils.encrypt(plainText, publicKey);
    }

    /** 完整登录链路，返回登录后的会话。 */
    protected MockHttpSession login() throws Exception {
        SecretKey key = requestPublicKey();
        String body = objectMapper.writeValueAsString(Map.of(
                "username", TEST_USERNAME,
                "keyId", key.keyId(),
                "cipher", encrypt(TEST_PASSWORD, key.publicKey())));

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.redirect").value("/home"))
                .andReturn();

        return (MockHttpSession) result.getRequest().getSession(false);
    }

    protected JsonNode readJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    /** 服务端下发的一次性公钥。 */
    protected record SecretKey(String keyId, String publicKey) {
    }

}
