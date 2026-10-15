package com.gangtise.backend.infrastructure.tasks;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gangtise.backend.domain.aggregate.User;
import com.gangtise.backend.domain.aggregate.entity.UserAccount;
import com.gangtise.backend.infrastructure.enums.UserStatusEnum;
import com.gangtise.backend.infrastructure.repository.mapper.RoleMapper;
import com.gangtise.backend.infrastructure.repository.mapper.UserAccountMapper;
import com.gangtise.backend.infrastructure.repository.mapper.UserMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 管理员账号初始化器回归测试。
 *
 * <p>核心保护点：初始化器是 {@code ApplicationRunner}，一旦抛异常会连带
 * {@code SpringApplication.run} 失败、**整个应用启动不起来**，所以幂等判据必须稳固。
 */
@SpringBootTest
@Transactional
class AdminAccountInitializerTest {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserAccountMapper userAccountMapper;

    @Autowired
    private RoleMapper roleMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private AdminAccountInitializer initializer(String loginName, String password) {
        return new AdminAccountInitializer(userMapper, userAccountMapper, roleMapper,
                passwordEncoder, true, loginName, password, "超级管理员");
    }

    @Test
    @DisplayName("展示名与登录标识不一致时按登录标识判重，不再重复开户")
    void skipsWhenDisplayNameDiffersFromLoginIdentifier() {
        // 复现真实故障：运维把 user.username 改成中文展示名，登录标识仍是 admin
        User user = new User();
        user.setUsername("init_test_display_name");
        user.setStatus(UserStatusEnum.NORMAL.getValue());
        userMapper.insert(user);

        UserAccount account = new UserAccount();
        account.setUserId(user.getId());
        account.setIdentifier("init_test_login_identifier");
        account.setCredential(passwordEncoder.encode("origin-password"));
        userAccountMapper.insert(account);

        long usersBefore = userMapper.selectCount(new LambdaQueryWrapper<>());
        long accountsBefore = userAccountMapper.selectCount(new LambdaQueryWrapper<>());

        assertDoesNotThrow(() -> initializer("init_test_login_identifier", "admin123")
                        .run(new DefaultApplicationArguments(new String[0])),
                "登录标识已存在时必须跳过初始化，而不是抛 DuplicateKeyException 让应用启动失败");

        assertEquals(usersBefore, userMapper.selectCount(new LambdaQueryWrapper<>()), "不应重复创建用户行");
        assertEquals(accountsBefore, userAccountMapper.selectCount(new LambdaQueryWrapper<>()), "不应重复创建账号行");

        UserAccount kept = userAccountMapper.selectOne(new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getIdentifier, "init_test_login_identifier")
                .last("limit 1"));
        assertNotNull(kept);
        assertTrue(passwordEncoder.matches("origin-password", kept.getCredential()),
                "既有凭证不得被初始密码覆盖");
    }

    @Test
    @DisplayName("登录标识缺失时正常开户：用户已存在则只补凭证，不重复建用户")
    void createsAccountReusingExistingUser() {
        User user = new User();
        user.setUsername("init_test_half_done");
        user.setStatus(UserStatusEnum.NORMAL.getValue());
        userMapper.insert(user);

        long usersBefore = userMapper.selectCount(new LambdaQueryWrapper<>());

        assertDoesNotThrow(() -> initializer("init_test_half_done", "pwd@123456")
                .run(new DefaultApplicationArguments(new String[0])));

        assertEquals(usersBefore, userMapper.selectCount(new LambdaQueryWrapper<>()),
                "用户已存在时应复用，不得再插入一条同名用户");

        UserAccount created = userAccountMapper.selectOne(new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getIdentifier, "init_test_half_done")
                .last("limit 1"));
        assertNotNull(created, "应补建账号凭证");
        assertEquals(user.getId(), created.getUserId());
        assertTrue(passwordEncoder.matches("pwd@123456", created.getCredential()),
                "密码必须以 BCrypt 散列形式入库");
    }

    @Test
    @DisplayName("完全不存在时新建用户 + 凭证")
    void createsBothUserAndAccount() {
        assertDoesNotThrow(() -> initializer("init_test_brand_new", "pwd@123456")
                .run(new DefaultApplicationArguments(new String[0])));

        UserAccount created = userAccountMapper.selectOne(new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getIdentifier, "init_test_brand_new")
                .last("limit 1"));
        assertNotNull(created);

        User createdUser = userMapper.selectById(created.getUserId());
        assertNotNull(createdUser, "账号必须挂在真实存在的用户上");
        assertEquals("init_test_brand_new", createdUser.getUsername());
        assertNotNull(createdUser.getRoleId(), "应绑定管理员角色");
    }

    @Test
    @DisplayName("关闭开关时不做任何写入")
    void skipsWhenDisabled() {
        long accountsBefore = userAccountMapper.selectCount(new LambdaQueryWrapper<>());

        new AdminAccountInitializer(userMapper, userAccountMapper, roleMapper, passwordEncoder,
                false, "init_test_disabled", "pwd@123456", "超级管理员")
                .run(new DefaultApplicationArguments(new String[0]));

        assertEquals(accountsBefore, userAccountMapper.selectCount(new LambdaQueryWrapper<>()));
        assertNull(userAccountMapper.selectOne(new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getIdentifier, "init_test_disabled")
                .last("limit 1")));
    }

}
