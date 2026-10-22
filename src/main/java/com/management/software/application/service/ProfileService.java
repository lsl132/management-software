package com.management.software.application.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.management.software.application.dto.PasswordChangeDTO;
import com.management.software.application.dto.ProfileSaveDTO;
import com.management.software.application.dto.ProfileVO;
import com.management.software.domain.aggregate.Role;
import com.management.software.domain.aggregate.User;
import com.management.software.domain.aggregate.entity.UserAccount;
import com.management.software.domain.aggregate.entity.UserLoginRecord;
import com.management.software.infrastructure.enums.UserStatusEnum;
import com.management.software.infrastructure.exception.BizException;
import com.management.software.infrastructure.repository.mapper.RoleMapper;
import com.management.software.infrastructure.repository.mapper.UserAccountMapper;
import com.management.software.infrastructure.repository.mapper.UserLoginRecordMapper;
import com.management.software.infrastructure.repository.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 个人信息应用服务：本人查看/维护自己的资料，以及自助修改密码。
 *
 * <p><b>与用户管理（{@code UserService}）的分工</b>：那边是管理员视角，能改登录标识、角色、状态、
 * 重置密码；这里是本人视角，只能改显示名 / 手机 / 邮箱，以及用「原密码」换新密码。
 * 两条链路的入参刻意不共用：如果复用 {@code UserSaveDTO}，本人就能传 {@code roleId} 给自己提权。
 */
@Service
public class ProfileService {

    private static final Logger log = LoggerFactory.getLogger(ProfileService.class);

    /** 新密码长度区间（含端点）。 */
    private static final int PASSWORD_MIN_LENGTH = 8;
    private static final int PASSWORD_MAX_LENGTH = 32;

    private final UserMapper userMapper;
    private final UserAccountMapper userAccountMapper;
    private final RoleMapper roleMapper;
    private final UserLoginRecordMapper userLoginRecordMapper;
    private final CryptoService cryptoService;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;

    public ProfileService(UserMapper userMapper,
                          UserAccountMapper userAccountMapper,
                          RoleMapper roleMapper,
                          UserLoginRecordMapper userLoginRecordMapper,
                          CryptoService cryptoService,
                          PasswordEncoder passwordEncoder,
                          ObjectMapper objectMapper) {
        this.userMapper = userMapper;
        this.userAccountMapper = userAccountMapper;
        this.roleMapper = roleMapper;
        this.userLoginRecordMapper = userLoginRecordMapper;
        this.cryptoService = cryptoService;
        this.passwordEncoder = passwordEncoder;
        this.objectMapper = objectMapper;
    }

    /** 当前登录人的资料。 */
    public ProfileVO detail(Integer userId) {
        User user = requireUser(userId);
        UserAccount account = userAccountMapper.selectOne(new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getUserId, userId)
                .last("limit 1"));

        ProfileVO vo = new ProfileVO();
        vo.setUserId(user.getId());
        vo.setIdentifier(account == null ? null : account.getIdentifier());
        vo.setUsername(user.getUsername());
        vo.setMobile(user.getMobile());
        vo.setEmail(user.getEmail());
        vo.setRoleName(roleNameOf(user.getRoleId()));
        vo.setStatus(user.getStatus());
        vo.setStatusText(UserStatusEnum.descriptionOf(user.getStatus()));
        vo.setCreateTime(user.getCreateTime());
        vo.setUpdateTime(user.getUpdateTime());

        UserLoginRecord lastLogin = userLoginRecordMapper.selectOne(new LambdaQueryWrapper<UserLoginRecord>()
                .eq(UserLoginRecord::getUserId, userId)
                .orderByDesc(UserLoginRecord::getId)
                .last("limit 1"));
        if (lastLogin != null) {
            vo.setLastLoginTime(lastLogin.getCreateTime());
            vo.setLastLoginIp(lastLogin.getLoginIp());
        }
        return vo;
    }

    /**
     * 保存本人可维护的三项。
     *
     * <p><b>这里不能用 {@code updateById}</b>：{@link User} 的手机号 / 邮箱 / 角色用的是
     * {@code FieldStrategy.ALWAYS}，只带三个字段的实体走 {@code updateById} 会把没填的
     * {@code role_id} 一并写成 NULL——用户只是改了个手机号，角色就被悄悄清空了。
     * 用 {@code LambdaUpdateWrapper} 显式指定列，只动这三列。
     */
    @Transactional(rollbackFor = Exception.class)
    public ProfileVO update(Integer userId, ProfileSaveDTO dto) {
        requireUser(userId);

        userMapper.update(null, Wrappers.<User>lambdaUpdate()
                .eq(User::getId, userId)
                .set(User::getUsername, dto.getUsername().trim())
                .set(User::getMobile, trimToNull(dto.getMobile()))
                .set(User::getEmail, trimToNull(dto.getEmail()))
                // update(null, wrapper) 不经过 MyMetaObjectHandler，审计列得自己写
                .set(User::getUpdateTime, LocalDateTime.now()));

        log.info("[PROFILE] 个人资料已更新 -> userId={}, displayName={}", userId, dto.getUsername().trim());
        return detail(userId);
    }

    /**
     * 自助修改密码：SM2 解密 → 校验原密码 → 规则校验 → 写入新散列。
     *
     * <p>明文只在这一步存在于内存里：解密即销毁密钥，散列后明文不落库、不进日志。
     */
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(Integer userId, PasswordChangeDTO dto) {
        requireUser(userId);

        String plain = cryptoService.decryptOnce(dto.getKeyId(), dto.getCipher());
        PasswordPair pair = parsePair(plain);
        if (!StringUtils.hasText(pair.oldPassword()) || !StringUtils.hasText(pair.newPassword())) {
            throw new BizException("请完整填写原密码与新密码");
        }

        UserAccount account = userAccountMapper.selectOne(new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getUserId, userId)
                .last("limit 1"));
        if (account == null) {
            throw new BizException("账号不存在，请联系管理员");
        }
        // 原密码必须校验：否则拿到他人登录态的人可以直接把密码改成自己的
        if (!StringUtils.hasText(account.getCredential())
                || !passwordEncoder.matches(pair.oldPassword(), account.getCredential())) {
            log.warn("[PROFILE] 修改密码失败，原密码不正确 -> userId={}", userId);
            throw new BizException("原密码不正确");
        }

        validateNewPassword(pair.newPassword(), pair.oldPassword());

        UserAccount update = new UserAccount();
        update.setId(account.getId());
        update.setCredential(passwordEncoder.encode(pair.newPassword()));
        userAccountMapper.updateById(update);

        log.info("[PROFILE] 密码修改成功 -> userId={}", userId);
    }

    private void validateNewPassword(String newPassword, String oldPassword) {
        if (newPassword.equals(oldPassword)) {
            throw new BizException("新密码不能与原密码相同");
        }
        int length = newPassword.length();
        if (length < PASSWORD_MIN_LENGTH || length > PASSWORD_MAX_LENGTH) {
            throw new BizException("新密码长度需为 " + PASSWORD_MIN_LENGTH + "~" + PASSWORD_MAX_LENGTH + " 位");
        }
        boolean hasLetter = false;
        boolean hasDigit = false;
        for (int i = 0; i < length; i++) {
            char c = newPassword.charAt(i);
            if (Character.isLetter(c)) {
                hasLetter = true;
            } else if (Character.isDigit(c)) {
                hasDigit = true;
            }
        }
        if (!hasLetter || !hasDigit) {
            throw new BizException("新密码需同时包含字母与数字");
        }
    }

    /**
     * 解析解密后的密码对。
     *
     * <p>字段缺失（比如前端换了格式但没同步）按「请完整填写」处理，不透出解析细节——
     * 解析异常里可能带明文片段。
     */
    private PasswordPair parsePair(String plain) {
        try {
            PasswordPair pair = objectMapper.readValue(plain, PasswordPair.class);
            if (pair == null) {
                throw new BizException("密码信息解析失败，请刷新页面后重试");
            }
            return pair;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[PROFILE] 密码密文内容无法解析 -> reason={}", e.getMessage());
            throw new BizException("密码信息解析失败，请刷新页面后重试");
        }
    }

    private User requireUser(Integer userId) {
        User user = userId == null ? null : userMapper.selectById(userId);
        if (user == null) {
            throw new BizException("用户不存在，请联系管理员");
        }
        return user;
    }

    private String roleNameOf(Integer roleId) {
        if (roleId == null) {
            return null;
        }
        Role role = roleMapper.selectById(roleId);
        return role == null ? null : role.getName();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** 解密后的密码对（前端把两段密码合成一份 JSON 一起加密，见 {@link PasswordChangeDTO}）。 */
    public record PasswordPair(String oldPassword, String newPassword) {
    }

}
