package com.gangtise.backend.application.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gangtise.backend.application.dto.AuthoritySaveDTO;
import com.gangtise.backend.application.dto.AuthorityTreeVO;
import com.gangtise.backend.application.dto.AuthorityVO;
import com.gangtise.backend.domain.aggregate.Authority;
import com.gangtise.backend.domain.aggregate.entity.RoleAuthority;
import com.gangtise.backend.infrastructure.enums.AuthorityTypeEnum;
import com.gangtise.backend.infrastructure.exception.BizException;
import com.gangtise.backend.infrastructure.repository.mapper.AuthorityMapper;
import com.gangtise.backend.infrastructure.repository.mapper.RoleAuthorityMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 权限应用服务：权限字典的增删改查 + 「模块分组 → 功能页」两级树组装。
 *
 * <p>权限表**不存父级 ID**：功能页的助记符万位就是它所属分组的助记符
 * （10000 系统设置 / 20000 人力资源 / 30000 财务管理 / 40000 生产制造 / 50000 战略决策），
 * 因此这里按万位归组，遇到找不到分组的孤儿功能页只记 warn 日志并跳过（不下发给前端，
 * 避免授权了却操作不了的数据）。
 *
 * <p>因为助记符既是唯一业务键、又被 {@code role_authority} 按值引用，写操作有三条硬约束：
 * <ol>
 *   <li><b>格式自洽</b>：模块分组的助记符必须是整万位；功能页不能是整万位，且其万位分组必须已存在
 *       ——否则新增出来的就是一棵挂不上去的孤儿，授权弹窗里根本看不到；</li>
 *   <li><b>改助记符要级联</b>：修改成功时在同一事务里把 {@code role_authority} 中引用旧助记符的记录
 *       一并改成新值，角色已有的授权不会断链（用户拍板：允许改，且自动同步）；</li>
 *   <li><b>删除要有保护</b>：被任意角色引用、或（分组）其下还有功能页时拒绝删除，
 *       与部门/职级/角色的删除保护保持一致。</li>
 * </ol>
 */
@Service
public class AuthorityService {

    private static final Logger log = LoggerFactory.getLogger(AuthorityService.class);

    /** 助记符的模块分组步长：10001 属于 10000 分组。 */
    public static final int GROUP_UNIT = 10000;

    private final AuthorityMapper authorityMapper;
    private final RoleAuthorityMapper roleAuthorityMapper;

    public AuthorityService(AuthorityMapper authorityMapper, RoleAuthorityMapper roleAuthorityMapper) {
        this.authorityMapper = authorityMapper;
        this.roleAuthorityMapper = roleAuthorityMapper;
    }

    /** 权限助记符所属的模块分组助记符（10001 → 10000）。 */
    public static int groupMnemonicOf(int mnemonic) {
        return mnemonic / GROUP_UNIT * GROUP_UNIT;
    }

    /** 是否整万位（模块分组专用）。 */
    public static boolean isGroupMnemonic(int mnemonic) {
        return mnemonic > 0 && mnemonic % GROUP_UNIT == 0;
    }

    /* ------------------------------------------------------------------ 查询 */

    /** 全部权限（分组 + 功能页），按助记符升序。 */
    public List<Authority> listAll() {
        return authorityMapper.selectList(new LambdaQueryWrapper<Authority>()
                .orderByAsc(Authority::getMnemonic));
    }

    /** 权限助记符 → 权限，供角色授权时的存在性校验与父子归一化使用。 */
    public Map<Integer, Authority> allByMnemonic() {
        return listAll().stream()
                .collect(Collectors.toMap(Authority::getMnemonic, Function.identity(),
                        (a, b) -> a, LinkedHashMap::new));
    }

    /**
     * 权限树：模块分组节点（含 children 功能页），按助记符升序。
     *
     * @param keyword 可选关键字，命中名称或助记符；**命中分组时展开其全部功能页**，
     *                命中功能页时保留该页并带出其所属分组（保证树结构完整，父节点不丢）
     */
    public List<AuthorityTreeVO> tree(String keyword) {
        List<Authority> all = listAll();
        Map<Integer, Long> roleCounts = roleCountByMnemonic();
        Set<Integer> fullGroups = new LinkedHashSet<>();
        Set<Integer> matchedPages = new LinkedHashSet<>();
        Set<Integer> groupsOfMatchedPages = new LinkedHashSet<>();
        boolean filtered = filter(keyword, all, fullGroups, matchedPages, groupsOfMatchedPages);

        Map<Integer, AuthorityTreeVO> groups = new LinkedHashMap<>();
        for (Authority authority : all) {
            if (!AuthorityTypeEnum.isGroup(authority.getType())) {
                continue;
            }
            // 分组留下：要么自己命中，要么有子页命中（保留父节点才不至于让子页变成孤儿）
            if (filtered && !fullGroups.contains(authority.getMnemonic())
                    && !groupsOfMatchedPages.contains(authority.getMnemonic())) {
                continue;
            }
            groups.put(authority.getMnemonic(), toTreeVO(authority, roleCounts));
        }

        for (Authority authority : all) {
            if (!AuthorityTypeEnum.isPage(authority.getType())) {
                continue;
            }
            int groupMnemonic = groupMnemonicOf(authority.getMnemonic());
            AuthorityTreeVO group = groups.get(groupMnemonic);
            if (group == null) {
                if (!filtered) {
                    log.warn("[AUTHORITY] 功能页找不到所属模块分组，已跳过 -> mnemonic={}, authName={}",
                            authority.getMnemonic(), authority.getAuthName());
                }
                continue;
            }
            // 分组直接命中 → 展开其全部功能页；只有子页命中 → 只保留命中的那个
            if (filtered && !fullGroups.contains(groupMnemonic)
                    && !matchedPages.contains(authority.getMnemonic())) {
                continue;
            }
            group.getChildren().add(toTreeVO(authority, roleCounts));
        }

        return new ArrayList<>(groups.values());
    }

    /** 助记符 → 引用它的角色数（{@code role_authority} 上 (role_id, mnemonic) 唯一，行数即角色数）。 */
    private Map<Integer, Long> roleCountByMnemonic() {
        return roleAuthorityMapper.selectList(null).stream()
                .filter(record -> record.getMnemonic() != null)
                .collect(Collectors.groupingBy(RoleAuthority::getMnemonic, Collectors.counting()));
    }

    /**
     * 关键字过滤：把命中的权限摊到三个集合里，供 {@link #tree} 决定保留哪些节点。
     *
     * <p>刻意区分「命中分组」与「命中功能页」，否则会出现「搜某个功能页却把它所有兄弟页都带出来」：
     * <ul>
     *   <li>{@code fullGroups} —— 名称 / 助记符**直接命中分组**，该分组要展开全部功能页；</li>
     *   <li>{@code matchedPages} —— 命中功能页，只保留它自己；</li>
     *   <li>{@code groupsOfMatchedPages} —— 命中功能页所属的分组，只为保住父节点，不展开子页。</li>
     * </ul>
     *
     * @return 未按关键字过滤时返回 false，表示「全部下发」（此时孤儿功能页要记 warn）
     */
    private boolean filter(String keyword, List<Authority> all,
                           Set<Integer> fullGroups, Set<Integer> matchedPages,
                           Set<Integer> groupsOfMatchedPages) {
        if (!StringUtils.hasText(keyword)) {
            return false;
        }
        String key = keyword.trim().toLowerCase();
        for (Authority authority : all) {
            String name = authority.getAuthName() == null ? "" : authority.getAuthName().toLowerCase();
            String mnemonic = String.valueOf(authority.getMnemonic());
            if (!name.contains(key) && !mnemonic.contains(key)) {
                continue;
            }
            if (AuthorityTypeEnum.isGroup(authority.getType())) {
                fullGroups.add(authority.getMnemonic());
            } else {
                matchedPages.add(authority.getMnemonic());
                groupsOfMatchedPages.add(groupMnemonicOf(authority.getMnemonic()));
            }
        }
        return true;
    }

    /** 权限详情（含子页数与引用角色数，供页面提示与编辑回填）。 */
    public AuthorityVO detail(Integer id) {
        Authority authority = require(id);
        AuthorityVO vo = new AuthorityVO();
        vo.setId(authority.getId());
        vo.setMnemonic(authority.getMnemonic());
        vo.setAuthName(authority.getAuthName());
        vo.setDescription(authority.getDescription());
        vo.setType(authority.getType());
        vo.setTypeText(AuthorityTypeEnum.descriptionOf(authority.getType()));
        vo.setGroupMnemonic(groupMnemonicOf(authority.getMnemonic()));
        vo.setChildCount(AuthorityTypeEnum.isGroup(authority.getType())
                ? (int) countChildren(authority.getMnemonic()) : 0);
        vo.setRoleCount((int) countRoles(authority.getMnemonic()));
        vo.setCreateTime(authority.getCreateTime());
        return vo;
    }

    /* ------------------------------------------------------------------ 写入 */

    /** 新增权限：助记符唯一且与类型自洽；功能页必须已有归属分组。 */
    @Transactional(rollbackFor = Exception.class)
    public Integer create(AuthoritySaveDTO dto) {
        int mnemonic = dto.getMnemonic();
        int type = dto.getType();

        ensureMnemonicUnique(mnemonic, null);
        ensureMnemonicMatchesType(mnemonic, type);
        if (AuthorityTypeEnum.isPage(type)) {
            ensureGroupExists(mnemonic);
        }

        Authority authority = new Authority();
        authority.setMnemonic(mnemonic);
        authority.setAuthName(dto.getAuthName().trim());
        authority.setDescription(trimToNull(dto.getDescription()));
        authority.setType(type);
        authorityMapper.insert(authority);
        log.info("[AUTHORITY] 新增权限 -> id={}, mnemonic={}, type={}, name={}",
                authority.getId(), mnemonic, type, authority.getAuthName());
        return authority.getId();
    }

    /**
     * 修改权限。助记符允许修改：改动时**同事务级联更新 {@code role_authority}**，
     * 已授权角色不会因为这次改动而失去权限。
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(Integer id, AuthoritySaveDTO dto) {
        Authority existing = require(id);
        int oldMnemonic = existing.getMnemonic();
        int newMnemonic = dto.getMnemonic();
        int newType = dto.getType();

        ensureMnemonicUnique(newMnemonic, id);
        ensureMnemonicMatchesType(newMnemonic, newType);

        boolean mnemonicChanged = oldMnemonic != newMnemonic;

        // 分组改助记符 → 其下功能页的万位就对不上了，会变成树里看不到的孤儿；有子页时必须先处理子页
        if (mnemonicChanged && AuthorityTypeEnum.isGroup(existing.getType())) {
            long children = countChildren(oldMnemonic);
            if (children > 0) {
                throw new BizException("该模块分组下还有 " + children
                        + " 个功能页，修改助记符会让它们失去归属；请先调整这些功能页的助记符后再改");
            }
        }
        // 功能页改完仍需保证归属分组存在（可能被移动到了尚未创建的万位上）
        if (AuthorityTypeEnum.isPage(newType)) {
            ensureGroupExists(newMnemonic);
        }

        // description 是 ALWAYS 更新策略，这里用显式 set 保证「留空即清空」，不受 null 跳过影响
        authorityMapper.update(null, Wrappers.<Authority>lambdaUpdate()
                .eq(Authority::getId, id)
                .set(Authority::getMnemonic, newMnemonic)
                .set(Authority::getAuthName, dto.getAuthName().trim())
                .set(Authority::getDescription, trimToNull(dto.getDescription()))
                .set(Authority::getType, newType));

        if (mnemonicChanged) {
            int moved = roleAuthorityMapper.update(null, Wrappers.<RoleAuthority>lambdaUpdate()
                    .eq(RoleAuthority::getMnemonic, oldMnemonic)
                    .set(RoleAuthority::getMnemonic, newMnemonic));
            log.info("[AUTHORITY] 修改权限助记符 -> id={}, {} -> {}，已同步 {} 条角色授权",
                    id, oldMnemonic, newMnemonic, moved);
        } else {
            log.info("[AUTHORITY] 修改权限 -> id={}, mnemonic={}, name={}", id, newMnemonic,
                    dto.getAuthName().trim());
        }
    }

    /** 删除权限：被角色引用、或分组下还有功能页时拒绝，避免静默收回授权或制造孤儿。 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Integer id) {
        Authority existing = require(id);
        int mnemonic = existing.getMnemonic();

        if (AuthorityTypeEnum.isGroup(existing.getType())) {
            long children = countChildren(mnemonic);
            if (children > 0) {
                throw new BizException("该模块分组下还有 " + children
                        + " 个功能页，请先删除或调整这些功能页后再删除分组");
            }
        }

        long roles = countRoles(mnemonic);
        if (roles > 0) {
            throw new BizException("该权限已被 " + roles + " 个角色引用，请先在角色管理中解除授权");
        }

        authorityMapper.deleteById(id);
        log.info("[AUTHORITY] 删除权限 -> id={}, mnemonic={}, name={}", id, mnemonic, existing.getAuthName());
    }

    /* ------------------------------------------------------------------ 校验 */

    private Authority require(Integer id) {
        Authority authority = id == null ? null : authorityMapper.selectById(id);
        if (authority == null) {
            throw new BizException("权限不存在或已被删除");
        }
        return authority;
    }

    private void ensureMnemonicUnique(int mnemonic, Integer excludeId) {
        Long count = authorityMapper.selectCount(new LambdaQueryWrapper<Authority>()
                .eq(Authority::getMnemonic, mnemonic)
                .ne(excludeId != null, Authority::getId, excludeId));
        if (count != null && count > 0) {
            throw new BizException("权限助记符已存在：" + mnemonic);
        }
    }

    /** 助记符必须与类型自洽：分组整万位、功能页非整万位。 */
    private void ensureMnemonicMatchesType(int mnemonic, int type) {
        if (AuthorityTypeEnum.isGroup(type) && !isGroupMnemonic(mnemonic)) {
            throw new BizException("模块分组的助记符必须是整万位（如 10000、20000），当前：" + mnemonic);
        }
        if (AuthorityTypeEnum.isPage(type) && isGroupMnemonic(mnemonic)) {
            throw new BizException("功能页的助记符不能是整万位（如 10001、20001），当前：" + mnemonic);
        }
    }

    /** 功能页的万位分组必须已存在，否则它会成为树里看不到、授权不了的数据。 */
    private void ensureGroupExists(int mnemonic) {
        int groupMnemonic = groupMnemonicOf(mnemonic);
        Long count = authorityMapper.selectCount(new LambdaQueryWrapper<Authority>()
                .eq(Authority::getMnemonic, groupMnemonic)
                .eq(Authority::getType, AuthorityTypeEnum.GROUP.getValue()));
        if (count == null || count == 0) {
            throw new BizException("找不到该功能页所属的模块分组（助记符 " + groupMnemonic
                    + "），请先创建这个模块分组");
        }
    }

    /** 分组下的功能页数量（助记符落在 groupMnemonic+1 ~ groupMnemonic+9999 区间内）。 */
    private long countChildren(int groupMnemonic) {
        return authorityMapper.selectCount(new LambdaQueryWrapper<Authority>()
                .eq(Authority::getType, AuthorityTypeEnum.PAGE.getValue())
                .ge(Authority::getMnemonic, groupMnemonic + 1)
                .le(Authority::getMnemonic, groupMnemonic + GROUP_UNIT - 1));
    }

    private long countRoles(int mnemonic) {
        Long count = roleAuthorityMapper.selectCount(new LambdaQueryWrapper<RoleAuthority>()
                .eq(RoleAuthority::getMnemonic, mnemonic));
        return count == null ? 0 : count;
    }

    /* ------------------------------------------------------------------ 装配 */

    private AuthorityTreeVO toTreeVO(Authority authority, Map<Integer, Long> roleCounts) {
        AuthorityTreeVO vo = new AuthorityTreeVO();
        vo.setId(authority.getId());
        vo.setMnemonic(authority.getMnemonic());
        vo.setAuthName(authority.getAuthName());
        vo.setDescription(authority.getDescription());
        vo.setType(authority.getType());
        vo.setTypeText(AuthorityTypeEnum.descriptionOf(authority.getType()));
        vo.setRoleCount(roleCounts.getOrDefault(authority.getMnemonic(), 0L).intValue());
        vo.setChildren(new ArrayList<>());
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
