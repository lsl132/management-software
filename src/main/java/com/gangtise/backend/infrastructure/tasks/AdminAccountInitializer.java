package com.gangtise.backend.infrastructure.tasks;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gangtise.backend.domain.aggregate.Role;
import com.gangtise.backend.domain.aggregate.User;
import com.gangtise.backend.domain.aggregate.entity.UserAccount;
import com.gangtise.backend.infrastructure.enums.UserStatusEnum;
import com.gangtise.backend.infrastructure.repository.mapper.RoleMapper;
import com.gangtise.backend.infrastructure.repository.mapper.UserAccountMapper;
import com.gangtise.backend.infrastructure.repository.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 管理员账号初始化。
 *
 * <p>启动时检查管理员是否已存在，不存在则创建角色 + 用户 + 账号凭证（密码 BCrypt 散列后写入
 * {@code user_account.credential}，任何位置都不保存明文）。幂等，可重复启动。
 *
 * <p><b>幂等判据是登录标识 {@code user_account.identifier}</b>（对应配置 {@code app.admin.username}），
 * 而非 {@code user.username}。后者只是展示名，可能被改成中文姓名而与登录标识不一致；若按它判断，
 * 会误判为「账号不存在」而重新开户，撞上 {@code uk_user_account_identifier} 唯一索引后抛
 * {@code DuplicateKeyException}，导致整个应用启动失败。
 */
@Component
public class AdminAccountInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountInitializer.class);

    private final UserMapper userMapper;
    private final UserAccountMapper userAccountMapper;
    private final RoleMapper roleMapper;
    private final PasswordEncoder passwordEncoder;

    private final boolean enabled;
    private final String username;
    private final String password;
    private final String roleName;

    public AdminAccountInitializer(UserMapper userMapper,
                                   UserAccountMapper userAccountMapper,
                                   RoleMapper roleMapper,
                                   PasswordEncoder passwordEncoder,
                                   @Value("${app.admin.enabled:true}") boolean enabled,
                                   @Value("${app.admin.username:admin}") String username,
                                   @Value("${app.admin.password:admin123}") String password,
                                   @Value("${app.admin.role-name:超级管理员}") String roleName) {
        this.userMapper = userMapper;
        this.userAccountMapper = userAccountMapper;
        this.roleMapper = roleMapper;
        this.passwordEncoder = passwordEncoder;
        this.enabled = enabled;
        this.username = username;
        this.password = password;
        this.roleName = roleName;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.info("[INIT] 管理员初始化已关闭（app.admin.enabled=false）");
            return;
        }

        // 幂等判据必须用「登录标识」user_account.identifier，而不是 user.username：
        // username 是展示名，运维改中文名后两者不再一致，若按 username 判断会误判为「账号不存在」，
        // 重新开户时撞上 uk_user_account_identifier 唯一索引，DuplicateKeyException 会让整个应用启动失败。
        UserAccount existingAccount = userAccountMapper.selectOne(new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getIdentifier, username)
                .last("limit 1"));
        if (existingAccount != null) {
            log.info("[INIT] 管理员账号已存在，跳过初始化 -> identifier={}, userId={}",
                    username, existingAccount.getUserId());
            return;
        }

        Integer roleId = ensureAdminRole();

        // 用户行按展示名复用：可能存在「用户已建、凭证未建」的半初始化状态，直接补凭证而不是重复建用户
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username)
                .last("limit 1"));
        if (user == null) {
            user = new User();
            user.setUsername(username);
            user.setStatus(UserStatusEnum.NORMAL.getValue());
            user.setRoleId(roleId);
            userMapper.insert(user);
        }

        UserAccount account = new UserAccount();
        account.setUserId(user.getId());
        account.setIdentifier(username);
        account.setCredential(passwordEncoder.encode(password));
        userAccountMapper.insert(account);

        log.info("[INIT] 管理员账号初始化完成 -> identifier={}, role={}, userId={}；"
                        + "初始密码取自配置 app.admin.password（默认 admin123），请及时修改",
                username, roleName, user.getId());
    }

    private Integer ensureAdminRole() {
        Role role = roleMapper.selectOne(new LambdaQueryWrapper<Role>()
                .eq(Role::getName, roleName)
                .last("limit 1"));
        if (role != null) {
            return role.getId();
        }
        Role created = new Role();
        created.setName(roleName);
        created.setDescription("系统初始化创建，拥有全部权限");
        created.setStatus(1);
        roleMapper.insert(created);
        return created.getId();
    }

}
