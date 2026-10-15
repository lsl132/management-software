package com.gangtise.backend.application.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gangtise.backend.application.dto.SelectOption;
import com.gangtise.backend.domain.aggregate.Role;
import com.gangtise.backend.domain.aggregate.entity.DepartmentRank;
import com.gangtise.backend.infrastructure.enums.AnnouncementStatusEnum;
import com.gangtise.backend.infrastructure.enums.AuthorityTypeEnum;
import com.gangtise.backend.infrastructure.enums.EmployeeEducationEnum;
import com.gangtise.backend.infrastructure.enums.EmployeeStatusEnum;
import com.gangtise.backend.infrastructure.enums.GenderEnum;
import com.gangtise.backend.infrastructure.enums.RankTypeEnum;
import com.gangtise.backend.infrastructure.enums.RoleStatusEnum;
import com.gangtise.backend.infrastructure.enums.UserStatusEnum;
import com.gangtise.backend.infrastructure.repository.mapper.DepartmentMapper;
import com.gangtise.backend.infrastructure.repository.mapper.DepartmentRankMapper;
import com.gangtise.backend.infrastructure.repository.mapper.RoleMapper;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * 下拉框选项服务：把枚举与字典表统一收敛成模板可直接渲染的 {@link SelectOption}。
 */
@Service
public class OptionService {

    private final DepartmentMapper departmentMapper;
    private final DepartmentRankMapper departmentRankMapper;
    private final RoleMapper roleMapper;

    public OptionService(DepartmentMapper departmentMapper,
                         DepartmentRankMapper departmentRankMapper,
                         RoleMapper roleMapper) {
        this.departmentMapper = departmentMapper;
        this.departmentRankMapper = departmentRankMapper;
        this.roleMapper = roleMapper;
    }

    public List<SelectOption> departments() {
        return departmentMapper.selectList(null).stream()
                .map(department -> SelectOption.of(department.getId(), department.getDepartName()))
                .toList();
    }

    /**
     * 部门下的职级下拉选项。
     *
     * <p>职级隶属于部门，因此必须指定部门；未指定部门时返回空集合（前端要求先选部门）。
     */
    public List<SelectOption> ranks(Integer department) {
        if (department == null) {
            return List.of();
        }
        // 按序列（O/T/F/M/S）+ 职级编码排序，保证下拉顺序与职级体系一致
        return departmentRankMapper.selectList(
                        Wrappers.<DepartmentRank>lambdaQuery()
                                .eq(DepartmentRank::getDepartment, department)
                                .orderByAsc(DepartmentRank::getType)
                                .orderByAsc(DepartmentRank::getCode))
                .stream()
                .map(this::rankOption)
                .toList();
    }

    public List<SelectOption> genders() {
        return Arrays.stream(GenderEnum.values())
                .map(item -> SelectOption.of(item.getValue(), item.getDescription()))
                .toList();
    }

    public List<SelectOption> educations() {
        return Arrays.stream(EmployeeEducationEnum.values())
                .map(item -> SelectOption.of(item.getStatus(), item.getDescription()))
                .toList();
    }

    public List<SelectOption> employeeStatuses() {
        return Arrays.stream(EmployeeStatusEnum.values())
                .map(item -> SelectOption.of(item.getStatus(), item.getDescription()))
                .toList();
    }

    /** 职级序列选项（O/T/F/M/S），供部门职级维护表单使用。 */
    public List<SelectOption> rankTypes() {
        return Arrays.stream(RankTypeEnum.values())
                .map(item -> SelectOption.of(item.getValue(), RankTypeEnum.labelOf(item.getValue())))
                .toList();
    }

    /** 公告状态选项（草稿/已发布/已下线），供公告管理页筛选使用。 */
    public List<SelectOption> announcementStatuses() {
        return Arrays.stream(AnnouncementStatusEnum.values())
                .map(item -> SelectOption.of(item.getValue(), item.getDescription()))
                .toList();
    }

    /** 角色选项，供用户管理页的角色下拉与筛选使用（按主键升序）。 */
    public List<SelectOption> roles() {
        return roleMapper.selectList(Wrappers.<Role>lambdaQuery().orderByAsc(Role::getId)).stream()
                .map(role -> SelectOption.of(role.getId(), role.getName()))
                .toList();
    }

    /** 用户状态选项（正常/冻结），供用户管理页筛选使用。 */
    public List<SelectOption> userStatuses() {
        return Arrays.stream(UserStatusEnum.values())
                .map(item -> SelectOption.of(item.getValue(), item.getDescription()))
                .toList();
    }

    /** 角色状态选项（启用/停用），供角色管理页筛选与表单使用。 */
    public List<SelectOption> roleStatuses() {
        return Arrays.stream(RoleStatusEnum.values())
                .map(item -> SelectOption.of(item.getValue(), item.getDescription()))
                .toList();
    }

    /** 权限类型选项（模块分组/功能页），供权限管理页筛选与表单使用。 */
    public List<SelectOption> authorityTypes() {
        return Arrays.stream(AuthorityTypeEnum.values())
                .map(item -> SelectOption.of(item.getValue(), item.getDescription()))
                .toList();
    }

    private SelectOption rankOption(DepartmentRank rank) {
        String label = rank.getIntro() == null || rank.getIntro().isBlank()
                ? rank.getCode()
                : rank.getCode() + "（" + rank.getIntro() + "）";
        return SelectOption.of(rank.getId(), label);
    }

}
