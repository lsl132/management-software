package com.management.software.application.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.management.software.application.dto.PageResult;
import com.management.software.application.dto.UserQueryDTO;
import com.management.software.application.dto.UserSaveDTO;
import com.management.software.application.dto.UserVO;
import com.management.software.domain.aggregate.Role;
import com.management.software.domain.aggregate.User;
import com.management.software.domain.aggregate.entity.UserAccount;
import com.management.software.infrastructure.enums.UserStatusEnum;
import com.management.software.infrastructure.exception.BizException;
import com.management.software.infrastructure.repository.mapper.RoleMapper;
import com.management.software.infrastructure.repository.mapper.UserAccountMapper;
import com.management.software.infrastructure.repository.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 用户应用服务：系统账号的增删改查（删除以「冻结」替代）、设置角色、重置密码。
 *
 * <p>几条贯穿全流程的约定：
 * <ul>
 *   <li><b>登录名 = {@code user_account.identifier}</b>（唯一索引），{@code user.username} 只是展示名；</li>
 *   <li><b>密码不由管理员指定</b>：新用户与重置密码统一使用系统初始密码
 *       （{@code app.user.default-password}），BCrypt 散列入库，接口与日志都不出现明文；</li>
 *   <li><b>不提供物理删除</b>：删除会连带丢失审计留痕（登录记录按 user_id 关联），
 *       因此只做冻结/解冻；冻结后 {@code AuthService} 会拒绝登录；</li>
 *   <li>局部更新（设置角色 / 重置密码 / 冻结）一律用 {@code LambdaUpdateWrapper} 显式指定列：
 *       实体上手机号 / 邮箱 / 角色是 {@code FieldStrategy.ALWAYS}，直接用
 *       {@code updateById} 会把未传字段置空。</li>
 * </ul>
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 100;

    private final UserMapper userMapper;
    private final UserAccountMapper userAccountMapper;
    private final RoleMapper roleMapper;
    private final PasswordEncoder passwordEncoder;
    /** 新用户与重置密码后的统一初始密码（明文只在此处用于散列，不落库、不出接口）。 */
    private final String defaultPassword;

    public UserService(UserMapper userMapper,
                       UserAccountMapper userAccountMapper,
                       RoleMapper roleMapper,
                       PasswordEncoder passwordEncoder,
                       @Value("${app.user.default-password:Init@123456}") String defaultPassword) {
        this.userMapper = userMapper;
        this.userAccountMapper = userAccountMapper;
        this.roleMapper = roleMapper;
        this.passwordEncoder = passwordEncoder;
        this.defaultPassword = defaultPassword;
    }

    /* ------------------------------------------------------------------ 查询 */

    /** 分页查询，支持关键字（展示名/登录标识/手机号/邮箱）、角色与状态过滤。 */
    public PageResult<UserVO> page(UserQueryDTO query) {
        long current = query.getCurrent() == null || query.getCurrent() < 1 ? 1L : query.getCurrent();
        long size = query.getSize() == null
                ? DEFAULT_PAGE_SIZE
                : Math.min(Math.max(query.getSize(), 1), MAX_PAGE_SIZE);

        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        String keyword = StringUtils.hasText(query.getKeyword()) ? query.getKeyword().trim() : null;
        if (keyword != null) {
            // 登录标识在 user_account 表，先查出命中的 userId 再用 in 过滤：
            // 不用 inSql 拼字符串，避免关键字带来的 SQL 注入面
            List<Integer> matchedIds = userAccountMapper.selectList(new LambdaQueryWrapper<UserAccount>()
                            .select(UserAccount::getUserId)
                            .like(UserAccount::getIdentifier, keyword))
                    .stream()
                    .map(UserAccount::getUserId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();

            wrapper.and(w -> {
                w.like(User::getUsername, keyword)
                        .or().like(User::getMobile, keyword)
                        .or().like(User::getEmail, keyword);
                if (!matchedIds.isEmpty()) {
                    w.or().in(User::getId, matchedIds);
                }
            });
        }
        if (query.getRoleId() != null) {
            wrapper.eq(User::getRoleId, query.getRoleId());
        }
        if (query.getStatus() != null) {
            wrapper.eq(User::getStatus, query.getStatus());
        }
        wrapper.orderByDesc(User::getId);

        Page<User> page = userMapper.selectPage(new Page<>(current, size), wrapper);

        Map<Integer, String> roleNames = roleNameMap();
        Map<Integer, String> identifiers = identifierMap(
                page.getRecords().stream().map(User::getId).toList());
        List<UserVO> records = page.getRecords().stream()
                .map(user -> toVO(user, roleNames, identifiers.get(user.getId())))
                .toList();

        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), records);
    }

    public UserVO detail(Integer id) {
        User user = requireUser(id);
        String identifier = identifierOf(user.getId());
        return toVO(user, roleNameMap(), identifier);
    }

    /* ------------------------------------------------------------------ 写入 */

    /** 新增用户：同时创建 {@code user} 与登录凭证 {@code user_account}（统一初始密码）。 */
    @Transactional(rollbackFor = Exception.class)
    public Integer create(UserSaveDTO dto) {
        String username = dto.getUsername().trim();
        String identifier = dto.getIdentifier().trim();
        ensureUsernameUnique(username, null);
        ensureIdentifierUnique(identifier, null);
        Integer roleId = ensureRoleExists(dto.getRoleId());

        User user = new User();
        user.setUsername(username);
        user.setMobile(trimToNull(dto.getMobile()));
        user.setEmail(trimToNull(dto.getEmail()));
        user.setRoleId(roleId);
        user.setStatus(dto.getStatus());
        userMapper.insert(user);

        UserAccount account = new UserAccount();
        account.setUserId(user.getId());
        account.setIdentifier(identifier);
        account.setCredential(passwordEncoder.encode(defaultPassword));
        userAccountMapper.insert(account);

        log.info("[USER] 新增用户 -> userId={}, username={}, identifier={}, roleId={}",
                user.getId(), username, identifier, roleId);
        return user.getId();
    }

    /**
     * 修改用户：展示名 / 手机号 / 邮箱 / 角色 / 状态按全量覆盖处理，登录标识可改（需保持唯一）。
     *
     * <p>改登录标识只影响下次登录使用的名字，不会使已登录会话失效（会话里存的是 userId 与展示名）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(Integer id, UserSaveDTO dto) {
        User existing = requireUser(id);
        String username = dto.getUsername().trim();
        String identifier = dto.getIdentifier().trim();
        ensureUsernameUnique(username, id);
        ensureIdentifierUnique(identifier, id);
        Integer roleId = ensureRoleExists(dto.getRoleId());

        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setMobile(trimToNull(dto.getMobile()));
        user.setEmail(trimToNull(dto.getEmail()));
        user.setRoleId(roleId);
        user.setStatus(dto.getStatus());
        userMapper.updateById(user);

        UserAccount account = findAccountByUserId(id);
        if (account == null) {
            // 半初始化状态（有用户无凭证）：顺带补建，避免该用户永远无法登录
            UserAccount created = new UserAccount();
            created.setUserId(id);
            created.setIdentifier(identifier);
            created.setCredential(passwordEncoder.encode(defaultPassword));
            userAccountMapper.insert(created);
            log.info("[USER] 修改用户时补建登录凭证 -> userId={}, identifier={}", id, identifier);
        } else if (!identifier.equals(account.getIdentifier())) {
            userAccountMapper.update(null, Wrappers.<UserAccount>lambdaUpdate()
                    .eq(UserAccount::getId, account.getId())
                    .set(UserAccount::getIdentifier, identifier)
                    .set(UserAccount::getUpdateTime, LocalDateTime.now()));
            log.info("[USER] 登录标识变更 -> userId={}, {} -> {}",
                    id, existing.getUsername(), identifier);
        }
    }

    /** 设置角色；{@code roleId} 传 null 表示取消角色绑定。 */
    @Transactional(rollbackFor = Exception.class)
    public void assignRole(Integer id, Integer roleId) {
        requireUser(id);
        Integer verified = ensureRoleExists(roleId);
        userMapper.update(null, Wrappers.<User>lambdaUpdate()
                .eq(User::getId, id)
                .set(User::getRoleId, verified)
                .set(User::getUpdateTime, LocalDateTime.now()));
        log.info("[USER] 设置角色 -> userId={}, roleId={}", id, verified);
    }

    /**
     * 重置密码：恢复为系统统一初始密码。
     *
     * <p>没有登录凭证时顺带补建（登录标识取展示名），把半初始化账号一并修好。
     */
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Integer id) {
        User user = requireUser(id);
        String encoded = passwordEncoder.encode(defaultPassword);
        UserAccount account = findAccountByUserId(id);

        if (account == null) {
            String identifier = user.getUsername();
            ensureIdentifierUnique(identifier, id);
            UserAccount created = new UserAccount();
            created.setUserId(id);
            created.setIdentifier(identifier);
            created.setCredential(encoded);
            userAccountMapper.insert(created);
            log.info("[USER] 重置密码时补建登录凭证 -> userId={}, identifier={}", id, identifier);
            return;
        }

        userAccountMapper.update(null, Wrappers.<UserAccount>lambdaUpdate()
                .eq(UserAccount::getId, account.getId())
                .set(UserAccount::getCredential, encoded)
                .set(UserAccount::getUpdateTime, LocalDateTime.now()));
        log.info("[USER] 重置密码 -> userId={}, identifier={}", id, account.getIdentifier());
    }

    /**
     * 冻结账号：{@code AuthService} 在登录时会校验状态并拒绝。
     *
     * <p>禁止冻结当前登录账号——否则管理员会把自己关在系统外。
     * 冻结自**下次登录**起生效，已建立的会话不受影响。
     */
    @Transactional(rollbackFor = Exception.class)
    public void freeze(Integer id, Integer currentUserId) {
        User existing = requireUser(id);
        if (existing.getId().equals(currentUserId)) {
            throw new BizException("不能冻结当前登录的账号");
        }
        if (Objects.equals(existing.getStatus(), UserStatusEnum.FREEZE.getValue())) {
            throw new BizException("该账号已是冻结状态，无需重复操作");
        }
        updateStatus(id, UserStatusEnum.FREEZE.getValue());
        log.info("[USER] 冻结账号 -> userId={}, username={}", id, existing.getUsername());
    }

    /** 解冻账号，恢复正常登录。 */
    @Transactional(rollbackFor = Exception.class)
    public void unfreeze(Integer id) {
        User existing = requireUser(id);
        if (Objects.equals(existing.getStatus(), UserStatusEnum.NORMAL.getValue())) {
            throw new BizException("该账号已是正常状态，无需重复操作");
        }
        updateStatus(id, UserStatusEnum.NORMAL.getValue());
        log.info("[USER] 解冻账号 -> userId={}, username={}", id, existing.getUsername());
    }

    /* ------------------------------------------------------------------ 校验 */

    private void updateStatus(Integer id, Integer status) {
        userMapper.update(null, Wrappers.<User>lambdaUpdate()
                .eq(User::getId, id)
                .set(User::getStatus, status)
                .set(User::getUpdateTime, LocalDateTime.now()));
    }

    private User requireUser(Integer id) {
        User user = id == null ? null : userMapper.selectById(id);
        if (user == null) {
            throw new BizException("用户不存在或已被删除");
        }
        return user;
    }

    private void ensureUsernameUnique(String username, Integer excludeId) {
        Long count = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username)
                .ne(excludeId != null, User::getId, excludeId));
        if (count != null && count > 0) {
            throw new BizException("展示名已存在：" + username);
        }
    }

    private void ensureIdentifierUnique(String identifier, Integer excludeUserId) {
        UserAccount account = userAccountMapper.selectOne(new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getIdentifier, identifier)
                .last("limit 1"));
        if (account != null && !Objects.equals(account.getUserId(), excludeUserId)) {
            throw new BizException("登录标识已存在：" + identifier);
        }
    }

    private Integer ensureRoleExists(Integer roleId) {
        if (roleId == null) {
            return null;
        }
        if (roleMapper.selectById(roleId) == null) {
            throw new BizException("所选角色不存在，请重新选择");
        }
        return roleId;
    }

    /* ------------------------------------------------------------------ 装配 */

    private UserAccount findAccountByUserId(Integer userId) {
        return userAccountMapper.selectOne(new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getUserId, userId)
                .last("limit 1"));
    }

    private String identifierOf(Integer userId) {
        UserAccount account = findAccountByUserId(userId);
        return account == null ? null : account.getIdentifier();
    }

    private Map<Integer, String> identifierMap(List<Integer> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return userAccountMapper.selectList(new LambdaQueryWrapper<UserAccount>()
                        .in(UserAccount::getUserId, userIds))
                .stream()
                .filter(account -> account.getUserId() != null)
                .collect(Collectors.toMap(UserAccount::getUserId, UserAccount::getIdentifier, (a, b) -> a));
    }

    private Map<Integer, String> roleNameMap() {
        return roleMapper.selectList(null).stream()
                .collect(Collectors.toMap(Role::getId, Role::getName, (a, b) -> a));
    }

    private UserVO toVO(User user, Map<Integer, String> roleNames, String identifier) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setIdentifier(identifier);
        vo.setMobile(user.getMobile());
        vo.setEmail(user.getEmail());
        vo.setRoleId(user.getRoleId());
        vo.setRoleName(user.getRoleId() == null ? null : roleNames.get(user.getRoleId()));
        vo.setStatus(user.getStatus());
        vo.setStatusText(UserStatusEnum.descriptionOf(user.getStatus()));
        vo.setAccountReady(identifier != null);
        vo.setCreateTime(user.getCreateTime());
        vo.setUpdateTime(user.getUpdateTime());
        return vo;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

}
