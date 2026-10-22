package com.management.software.application.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.management.software.application.dto.SelectOption;
import com.management.software.domain.aggregate.Role;
import com.management.software.domain.aggregate.User;
import com.management.software.domain.aggregate.entity.DepartmentRank;
import com.management.software.infrastructure.enums.AnnouncementStatusEnum;
import com.management.software.infrastructure.enums.AuthorityTypeEnum;
import com.management.software.infrastructure.enums.CurrencyEnum;
import com.management.software.infrastructure.enums.EmployeeEducationEnum;
import com.management.software.infrastructure.enums.EmployeeStatusEnum;
import com.management.software.infrastructure.enums.GenderEnum;
import com.management.software.infrastructure.enums.RankTypeEnum;
import com.management.software.infrastructure.enums.RoleStatusEnum;
import com.management.software.infrastructure.enums.UserStatusEnum;
import com.management.software.infrastructure.enums.WorkflowStatusEnum;
import com.management.software.infrastructure.repository.mapper.DepartmentMapper;
import com.management.software.infrastructure.repository.mapper.DepartmentRankMapper;
import com.management.software.infrastructure.repository.mapper.RoleMapper;
import com.management.software.infrastructure.repository.mapper.UserMapper;
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
    private final UserMapper userMapper;

    public OptionService(DepartmentMapper departmentMapper,
                         DepartmentRankMapper departmentRankMapper,
                         RoleMapper roleMapper,
                         UserMapper userMapper) {
        this.departmentMapper = departmentMapper;
        this.departmentRankMapper = departmentRankMapper;
        this.roleMapper = roleMapper;
        this.userMapper = userMapper;
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

    /**
     * 审批人选项（系统账号），供流程管理的审批节点选人使用。
     *
     * <p><b>只列正常状态的账号</b>：冻结的账号连登录都过不去，把它选成审批人等于把这条流程卡死
     * ——规则配好了却没有人能点通过。已经配在规则里的历史 id 不会被这里过滤掉，
     * 展示层会原样显示（必要时保留 {@code #id}），改名或冻结不会让规则内容凭空消失。
     */
    public List<SelectOption> users() {
        return userMapper.selectList(Wrappers.<User>lambdaQuery()
                        .eq(User::getStatus, UserStatusEnum.NORMAL.getValue())
                        .orderByAsc(User::getId)).stream()
                .map(user -> SelectOption.of(user.getId(), user.getUsername()))
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

    /** 币种选项（人民币/美元/…），供员工薪酬弹窗使用。 */
    public List<SelectOption> currencies() {
        return Arrays.stream(CurrencyEnum.values())
                .map(item -> SelectOption.of(item.getValue(), item.getDescription()))
                .toList();
    }

    /** 流程状态选项（启用/禁用），供流程管理页筛选与表单使用。 */
    public List<SelectOption> workflowStatuses() {
        return Arrays.stream(WorkflowStatusEnum.values())
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
