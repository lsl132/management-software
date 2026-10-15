package com.gangtise.backend.application.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gangtise.backend.application.dto.RoleSaveDTO;
import com.gangtise.backend.application.dto.RoleVO;
import com.gangtise.backend.domain.aggregate.Authority;
import com.gangtise.backend.domain.aggregate.Role;
import com.gangtise.backend.domain.aggregate.User;
import com.gangtise.backend.domain.aggregate.entity.RoleAuthority;
import com.gangtise.backend.infrastructure.enums.AuthorityTypeEnum;
import com.gangtise.backend.infrastructure.enums.RoleStatusEnum;
import com.gangtise.backend.infrastructure.exception.BizException;
import com.gangtise.backend.infrastructure.repository.mapper.RoleAuthorityMapper;
import com.gangtise.backend.infrastructure.repository.mapper.RoleMapper;
import com.gangtise.backend.infrastructure.repository.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 角色应用服务：角色增删改查 + 角色挂载权限。
 *
 * <p>两条关键约束：
 * <ul>
 *   <li>角色名称全公司唯一（库侧 {@code uk_role_name}，服务侧前置校验给友好提示）；</li>
 *   <li>删除角色前必须保证没有用户引用，否则拒绝——避免把在用的角色删掉导致这些用户失去权限；
 *       删除成功时会连带清理该角色的 {@code role_authority} 挂载记录。</li>
 * </ul>
 *
 * <p>权限挂载按**助记符**（{@code authority.mnemonic}）而不是主键：{@code role_authority}
 * 与权限主键解耦，权限字典重建后挂载关系依然有效。
 */
@Service
public class RoleService {

    private static final Logger log = LoggerFactory.getLogger(RoleService.class);

    private final RoleMapper roleMapper;
    private final RoleAuthorityMapper roleAuthorityMapper;
    private final UserMapper userMapper;
    private final AuthorityService authorityService;

    public RoleService(RoleMapper roleMapper,
                       RoleAuthorityMapper roleAuthorityMapper,
                       UserMapper userMapper,
                       AuthorityService authorityService) {
        this.roleMapper = roleMapper;
        this.roleAuthorityMapper = roleAuthorityMapper;
        this.userMapper = userMapper;
        this.authorityService = authorityService;
    }

    /* ------------------------------------------------------------------ 查询 */

    /** 角色列表（角色量级小，不分页），支持按名称模糊过滤，带用户数与权限数统计。 */
    public List<RoleVO> list(String keyword) {
        List<Role> all = roleMapper.selectList(new LambdaQueryWrapper<Role>()
                .orderByAsc(Role::getId));

        Map<Integer, Long> userCounts = userCountByRole();
        Map<Integer, Long> authorityCounts = authorityCountByRole();

        String key = StringUtils.hasText(keyword) ? keyword.trim() : null;
        return all.stream()
                .filter(role -> key == null || role.getName().contains(key))
                .map(role -> toVO(role, userCounts, authorityCounts))
                .toList();
    }

    public RoleVO detail(Integer id) {
        return toVO(requireRole(id), userCountByRole(), authorityCountByRole());
    }

    /* ------------------------------------------------------------------ 写入 */

    @Transactional(rollbackFor = Exception.class)
    public Integer create(RoleSaveDTO dto) {
        String name = dto.getName().trim();
        ensureNameUnique(name, null);

        Role role = new Role();
        role.setName(name);
        role.setDescription(trimToNull(dto.getDescription()));
        role.setStatus(dto.getStatus());
        roleMapper.insert(role);
        log.info("[ROLE] 新增角色 -> roleId={}, name={}", role.getId(), name);
        return role.getId();
    }

    /** 修改角色：备注按全量覆盖处理，留空即清空。 */
    @Transactional(rollbackFor = Exception.class)
    public void update(Integer id, RoleSaveDTO dto) {
        requireRole(id);
        String name = dto.getName().trim();
        ensureNameUnique(name, id);

        Role role = new Role();
        role.setId(id);
        role.setName(name);
        role.setDescription(trimToNull(dto.getDescription()));
        role.setStatus(dto.getStatus());
        roleMapper.updateById(role);
    }

    /** 删除角色：被用户引用时拒绝；成功时连带清理该角色的权限挂载记录。 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Integer id) {
        Role existing = requireRole(id);

        Long userCount = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getRoleId, id));
        if (userCount != null && userCount > 0) {
            throw new BizException("该角色下还有 " + userCount + " 个用户，请先调整这些用户的角色");
        }

        roleAuthorityMapper.delete(new LambdaQueryWrapper<RoleAuthority>()
                .eq(RoleAuthority::getRoleId, id));
        roleMapper.deleteById(id);
        log.info("[ROLE] 删除角色 -> roleId={}, name={}", id, existing.getName());
    }

    /* -------------------------------------------------------------- 权限挂载 */

    /** 该角色已挂载的权限助记符（升序）。 */
    public List<Integer> authorities(Integer roleId) {
        requireRole(roleId);
        return roleAuthorityMapper.selectList(new LambdaQueryWrapper<RoleAuthority>()
                        .eq(RoleAuthority::getRoleId, roleId)
                        .orderByAsc(RoleAuthority::getMnemonic))
                .stream()
                .map(RoleAuthority::getMnemonic)
                .toList();
    }

    /**
     * 全量覆盖角色的权限挂载。
     *
     * <p>保存前做一次**父子归一化**，让授权结果永远是自洽的（与前端勾选框的联动规则一致）：
     * <ul>
     *   <li>勾了模块分组且其下**一个功能页都没勾** → 视为整组授权，自动补全该分组下全部功能页
     *       （「勾父 = 全选子」）；</li>
     *   <li>勾了任意功能页 → 自动补上所属模块分组，否则菜单不显示、页面等于进不去；</li>
     *   <li>勾了分组又只保留部分子页（「勾父后取消几个子项」）→ <b>不</b>补全兄弟页面，
     *       保留细粒度授权，避免把管理员刻意去掉的权限又加回来。</li>
     * </ul>
     * 前端勾选框同样联动，这里是有意做的第二道保障：绕过页面直接调接口也不会写出半吊子授权。
     *
     * @param roleId    角色ID
     * @param mnemonics 权限助记符集合，允许为空（等于清空该角色全部权限）
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveAuthorities(Integer roleId, List<Integer> mnemonics) {
        requireRole(roleId);

        Map<Integer, Authority> all = authorityService.allByMnemonic();
        Set<Integer> selected = new LinkedHashSet<>();
        for (Integer mnemonic : mnemonics) {
            if (mnemonic == null) {
                continue;
            }
            if (!all.containsKey(mnemonic)) {
                throw new BizException("权限不存在，请刷新页面后重试：" + mnemonic);
            }
            selected.add(mnemonic);
        }

        // 分组助记符 -> 其下功能页助记符
        Map<Integer, List<Integer>> childrenOfGroup = new HashMap<>();
        for (Authority authority : all.values()) {
            if (AuthorityTypeEnum.isPage(authority.getType())) {
                childrenOfGroup
                        .computeIfAbsent(AuthorityService.groupMnemonicOf(authority.getMnemonic()),
                                key -> new ArrayList<>())
                        .add(authority.getMnemonic());
            }
        }

        for (Map.Entry<Integer, List<Integer>> entry : childrenOfGroup.entrySet()) {
            Integer groupMnemonic = entry.getKey();
            List<Integer> pages = entry.getValue();
            if (!all.containsKey(groupMnemonic)) {
                // 功能页的万位找不到分组：不下发也不授权，避免写出「打不开的菜单」
                selected.removeAll(pages);
                log.warn("[ROLE] 权限数据结构异常，缺少模块分组 {}，相关功能页已忽略", groupMnemonic);
                continue;
            }
            boolean anyPageSelected = pages.stream().anyMatch(selected::contains);
            if (anyPageSelected) {
                // 有子页 → 父级必须一起授权（菜单可见）
                selected.add(groupMnemonic);
            } else if (selected.contains(groupMnemonic)) {
                // 只勾了父级 → 整组授权
                selected.addAll(pages);
            }
        }

        roleAuthorityMapper.delete(new LambdaQueryWrapper<RoleAuthority>()
                .eq(RoleAuthority::getRoleId, roleId));
        for (Integer mnemonic : selected) {
            RoleAuthority record = new RoleAuthority();
            record.setRoleId(roleId);
            record.setMnemonic(mnemonic);
            roleAuthorityMapper.insert(record);
        }
        log.info("[ROLE] 保存角色权限 -> roleId={}, 合计 {} 项（含分组与功能页）", roleId, selected.size());
    }

    /* ------------------------------------------------------------------ 校验 */

    private Role requireRole(Integer id) {
        Role role = id == null ? null : roleMapper.selectById(id);
        if (role == null) {
            throw new BizException("角色不存在或已被删除");
        }
        return role;
    }

    private void ensureNameUnique(String name, Integer excludeId) {
        Long count = roleMapper.selectCount(new LambdaQueryWrapper<Role>()
                .eq(Role::getName, name)
                .ne(excludeId != null, Role::getId, excludeId));
        if (count != null && count > 0) {
            throw new BizException("角色名称已存在：" + name);
        }
    }

    /* ------------------------------------------------------------------ 装配 */

    private Map<Integer, Long> userCountByRole() {
        List<Map<String, Object>> rows = userMapper.selectMaps(new QueryWrapper<User>()
                .select("role_id", "count(*) as cnt")
                .isNotNull("role_id")
                .groupBy("role_id"));

        Map<Integer, Long> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Object key = pick(row, "role_id");
            Object count = pick(row, "cnt");
            if (key instanceof Number roleId && count instanceof Number value) {
                result.put(roleId.intValue(), value.longValue());
            }
        }
        return result;
    }

    private Map<Integer, Long> authorityCountByRole() {
        return roleAuthorityMapper.selectList(null).stream()
                .filter(record -> record.getRoleId() != null)
                .collect(Collectors.groupingBy(RoleAuthority::getRoleId, Collectors.counting()));
    }

    /** 结果集列名大小写依赖驱动实现，这里做一次忽略大小写的取值。 */
    private static Object pick(Map<String, Object> row, String column) {
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (column.equalsIgnoreCase(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private RoleVO toVO(Role role, Map<Integer, Long> userCounts, Map<Integer, Long> authorityCounts) {
        RoleVO vo = new RoleVO();
        vo.setId(role.getId());
        vo.setName(role.getName());
        vo.setDescription(role.getDescription());
        vo.setStatus(role.getStatus());
        vo.setStatusText(RoleStatusEnum.descriptionOf(role.getStatus()));
        vo.setUserCount(userCounts.getOrDefault(role.getId(), 0L).intValue());
        vo.setAuthorityCount(authorityCounts.getOrDefault(role.getId(), 0L).intValue());
        vo.setCreateTime(role.getCreateTime());
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
