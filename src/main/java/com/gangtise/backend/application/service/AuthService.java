package com.gangtise.backend.application.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gangtise.backend.application.dto.CurrentUserVO;
import com.gangtise.backend.application.dto.LoginRequest;
import com.gangtise.backend.domain.aggregate.Role;
import com.gangtise.backend.domain.aggregate.User;
import com.gangtise.backend.domain.aggregate.entity.UserAccount;
import com.gangtise.backend.domain.aggregate.entity.UserLoginRecord;
import com.gangtise.backend.infrastructure.enums.UserStatusEnum;
import com.gangtise.backend.infrastructure.exception.BizException;
import com.gangtise.backend.infrastructure.repository.mapper.RoleMapper;
import com.gangtise.backend.infrastructure.repository.mapper.UserAccountMapper;
import com.gangtise.backend.infrastructure.repository.mapper.UserLoginRecordMapper;
import com.gangtise.backend.infrastructure.repository.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 认证应用服务：解密登录凭证、校验 BCrypt 密码、记录登录成功。
 *
 * <p>会话（HttpSession）由控制层维护，本服务只负责「验明身份」。
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserMapper userMapper;
    private final UserAccountMapper userAccountMapper;
    private final RoleMapper roleMapper;
    private final UserLoginRecordMapper userLoginRecordMapper;
    private final CryptoService cryptoService;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserMapper userMapper,
                       UserAccountMapper userAccountMapper,
                       RoleMapper roleMapper,
                       UserLoginRecordMapper userLoginRecordMapper,
                       CryptoService cryptoService,
                       PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.userAccountMapper = userAccountMapper;
        this.roleMapper = roleMapper;
        this.userLoginRecordMapper = userLoginRecordMapper;
        this.cryptoService = cryptoService;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 登录校验：SM2 解密 → 按登录标识查账号 → 校验用户状态 → BCrypt 比对密码 → 写入登录记录。
     *
     * <p><b>登录名以 {@code user_account.identifier} 为准</b>（唯一索引 {@code uk_user_account_identifier}，
     * 一个登录名只绑一个账号）；{@code user.username} 只是展示名，允许随时改成中文姓名，
     * 不得作为身份判定依据，否则改名会同时锁死登录。
     *
     * @param request 登录请求（密文密码）
     * @param loginIp 客户端 IP，由控制层从请求头解析，解析不到传 null
     * @return 登录用户信息
     */
    public CurrentUserVO login(LoginRequest request, String loginIp) {
        String plainPassword = cryptoService.decryptOnce(request.getKeyId(), request.getCipher());
        if (!StringUtils.hasText(plainPassword)) {
            throw BizException.unauthorized("用户名或密码错误");
        }

        String loginName = request.getUsername().trim();
        UserAccount account = userAccountMapper.selectOne(new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getIdentifier, loginName)
                .last("limit 1"));
        if (account == null) {
            log.warn("[AUTH] 登录失败，账号不存在 -> identifier={}", loginName);
            throw BizException.unauthorized("用户名或密码错误");
        }

        User user = userMapper.selectById(account.getUserId());
        if (user == null) {
            log.warn("[AUTH] 登录失败，账号缺少用户信息 -> identifier={}, userId={}", loginName, account.getUserId());
            throw BizException.unauthorized("用户名或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != UserStatusEnum.NORMAL.getValue()) {
            log.warn("[AUTH] 登录失败，账号已冻结 -> identifier={}", loginName);
            throw BizException.unauthorized("账号已被冻结，请联系管理员");
        }

        if (!StringUtils.hasText(account.getCredential())
                || !passwordEncoder.matches(plainPassword, account.getCredential())) {
            log.warn("[AUTH] 登录失败，密码错误 -> identifier={}", loginName);
            throw BizException.unauthorized("用户名或密码错误");
        }

        String roleName = null;
        if (user.getRoleId() != null) {
            Role role = roleMapper.selectById(user.getRoleId());
            roleName = role == null ? null : role.getName();
        }
        log.info("[AUTH] 登录成功 -> identifier={}, displayName={}, userId={}, role={}",
                loginName, user.getUsername(), user.getId(), roleName);
        recordLogin(user.getId(), loginIp);
        return new CurrentUserVO(user.getId(), user.getUsername(), roleName);
    }

    /**
     * 写入登录成功记录（登录审计）。
     *
     * <p>刻意不让记录失败影响登录结果：审计表异常不应该把用户挡在系统外，
     * 但必须留下 error 日志，便于监控发现。因此这里也**不加 @Transactional**，
     * 让插入独立提交，catch 之后不会把外层事务标记为 rollback-only。
     */
    private void recordLogin(Integer userId, String loginIp) {
        try {
            UserLoginRecord record = new UserLoginRecord();
            record.setUserId(userId);
            record.setLoginIp(loginIp);
            userLoginRecordMapper.insert(record);
        } catch (Exception e) {
            log.error("[AUTH] 登录记录写入失败 -> userId={}, loginIp={}", userId, loginIp, e);
        }
    }

}
