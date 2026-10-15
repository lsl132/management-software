package com.gangtise.backend.application.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gangtise.backend.application.dto.EmployeeQueryDTO;
import com.gangtise.backend.application.dto.EmployeeSaveDTO;
import com.gangtise.backend.application.dto.EmployeeVO;
import com.gangtise.backend.application.dto.PageResult;
import com.gangtise.backend.domain.aggregate.Department;
import com.gangtise.backend.domain.aggregate.Employee;
import com.gangtise.backend.domain.aggregate.entity.DepartmentRank;
import com.gangtise.backend.infrastructure.enums.EmployeeEducationEnum;
import com.gangtise.backend.infrastructure.enums.EmployeeStatusEnum;
import com.gangtise.backend.infrastructure.enums.GenderEnum;
import com.gangtise.backend.infrastructure.exception.BizException;
import com.gangtise.backend.infrastructure.repository.mapper.DepartmentMapper;
import com.gangtise.backend.infrastructure.repository.mapper.EmployeeMapper;
import com.gangtise.backend.infrastructure.repository.mapper.DepartmentRankMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 员工应用服务：承载员工增删改查用例，负责 DTO/VO 转换与事务边界。
 */
@Service
public class EmployeeService {

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 100;

    private final EmployeeMapper employeeMapper;
    private final DepartmentMapper departmentMapper;
    private final DepartmentRankMapper departmentRankMapper;

    public EmployeeService(EmployeeMapper employeeMapper,
                           DepartmentMapper departmentMapper,
                           DepartmentRankMapper departmentRankMapper) {
        this.employeeMapper = employeeMapper;
        this.departmentMapper = departmentMapper;
        this.departmentRankMapper = departmentRankMapper;
    }

    /**
     * 分页查询，支持关键字（姓名/工号/电话/邮箱）与部门、状态过滤。
     */
    public PageResult<EmployeeVO> page(EmployeeQueryDTO query) {
        long current = query.getCurrent() == null || query.getCurrent() < 1 ? 1L : query.getCurrent();
        long size = query.getSize() == null ? DEFAULT_PAGE_SIZE : Math.min(Math.max(query.getSize(), 1), MAX_PAGE_SIZE);

        LambdaQueryWrapper<Employee> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(w -> w.like(Employee::getName, keyword)
                    .or().like(Employee::getWorkNo, keyword)
                    .or().like(Employee::getTelephone, keyword)
                    .or().like(Employee::getEmail, keyword));
        }
        if (query.getDepartment() != null) {
            wrapper.eq(Employee::getDepartment, query.getDepartment());
        }
        if (query.getStatus() != null) {
            wrapper.eq(Employee::getStatus, query.getStatus());
        }
        wrapper.orderByDesc(Employee::getId);

        Page<Employee> page = employeeMapper.selectPage(new Page<>(current, size), wrapper);

        Map<Integer, String> departmentNames = departmentNameMap();
        Map<Integer, String> rankNames = rankNameMap();
        List<EmployeeVO> records = page.getRecords().stream()
                .map(employee -> toVO(employee, departmentNames, rankNames))
                .toList();

        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), records);
    }

    public EmployeeVO detail(Integer id) {
        Employee employee = employeeMapper.selectById(id);
        if (employee == null) {
            throw new BizException("员工不存在或已被删除");
        }
        return toVO(employee, departmentNameMap(), rankNameMap());
    }

    @Transactional(rollbackFor = Exception.class)
    public Integer create(EmployeeSaveDTO dto) {
        String workNo = dto.getWorkNo().trim();
        ensureWorkNoUnique(workNo, null);
        ensureRankBelongsToDepartment(dto);

        Employee employee = new Employee();
        apply(employee, dto);
        employee.setWorkNo(workNo);
        employeeMapper.insert(employee);
        return employee.getId();
    }

    /**
     * 全量更新：可选项按传入值覆盖，传 null 即清空（依赖实体上的 {@code updateStrategy = ALWAYS}）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(Integer id, EmployeeSaveDTO dto) {
        Employee existing = employeeMapper.selectById(id);
        if (existing == null) {
            throw new BizException("员工不存在或已被删除");
        }
        String workNo = dto.getWorkNo().trim();
        ensureWorkNoUnique(workNo, id);
        ensureRankBelongsToDepartment(dto);

        Employee employee = new Employee();
        employee.setId(id);
        apply(employee, dto);
        employee.setWorkNo(workNo);
        employeeMapper.updateById(employee);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Integer id) {
        Employee existing = employeeMapper.selectById(id);
        if (existing == null) {
            throw new BizException("员工不存在或已被删除");
        }
        employeeMapper.deleteById(id);
    }

    private void ensureWorkNoUnique(String workNo, Integer excludeId) {
        Long count = employeeMapper.selectCount(new LambdaQueryWrapper<Employee>()
                .eq(Employee::getWorkNo, workNo)
                .ne(excludeId != null, Employee::getId, excludeId));
        if (count != null && count > 0) {
            throw new BizException("员工工号已存在：" + workNo);
        }
    }

    /**
     * 校验职级隶属部门：职级隶属于部门，员工填了职级就必须选择部门，且所选职级必须属于该部门。
     */
    private void ensureRankBelongsToDepartment(EmployeeSaveDTO dto) {
        Integer department = dto.getDepartment();
        if (department != null && departmentMapper.selectById(department) == null) {
            throw new BizException("所选部门不存在，请重新选择");
        }
        Integer rankId = dto.getRank();
        if (rankId == null) {
            return;
        }
        if (department == null) {
            throw new BizException("请先选择所属部门，再选择该部门下的职级");
        }
        DepartmentRank rank = departmentRankMapper.selectById(rankId);
        if (rank == null) {
            throw new BizException("所选职级不存在，请重新选择");
        }
        if (!department.equals(rank.getDepartment())) {
            throw new BizException("所选职级不属于该部门，请重新选择职级");
        }
    }

    private void apply(Employee employee, EmployeeSaveDTO dto) {
        employee.setName(dto.getName().trim());
        employee.setAlias(trimToNull(dto.getAlias()));
        employee.setGender(dto.getGender());
        employee.setIdentityCard(trimToNull(dto.getIdentityCard()));
        employee.setTelephone(trimToNull(dto.getTelephone()));
        employee.setEmail(trimToNull(dto.getEmail()));
        employee.setAddress(trimToNull(dto.getAddress()));
        employee.setStatus(dto.getStatus());
        employee.setBirthday(dto.getBirthday());
        employee.setEducation(dto.getEducation());
        employee.setRank(dto.getRank());
        employee.setDepartment(dto.getDepartment());
    }

    private EmployeeVO toVO(Employee employee, Map<Integer, String> departmentNames, Map<Integer, String> rankNames) {
        EmployeeVO vo = new EmployeeVO();
        vo.setId(employee.getId());
        vo.setWorkNo(employee.getWorkNo());
        vo.setName(employee.getName());
        vo.setAlias(employee.getAlias());
        vo.setGender(employee.getGender());
        vo.setGenderText(GenderEnum.descriptionOf(employee.getGender()));
        vo.setIdentityCard(employee.getIdentityCard());
        vo.setTelephone(employee.getTelephone());
        vo.setEmail(employee.getEmail());
        vo.setAddress(employee.getAddress());
        vo.setStatus(employee.getStatus());
        vo.setStatusText(EmployeeStatusEnum.descriptionOf(employee.getStatus()));
        vo.setBirthday(employee.getBirthday());
        vo.setEducation(employee.getEducation());
        vo.setEducationText(EmployeeEducationEnum.descriptionOf(employee.getEducation()));
        vo.setRank(employee.getRank());
        vo.setRankName(employee.getRank() == null ? null : rankNames.get(employee.getRank()));
        vo.setDepartment(employee.getDepartment());
        vo.setDepartmentName(employee.getDepartment() == null ? null : departmentNames.get(employee.getDepartment()));
        vo.setCreateTime(employee.getCreateTime());
        vo.setUpdateTime(employee.getUpdateTime());
        return vo;
    }

    private Map<Integer, String> departmentNameMap() {
        return departmentMapper.selectList(null).stream()
                .collect(Collectors.toMap(Department::getId, Department::getDepartName, (a, b) -> a));
    }

    private Map<Integer, String> rankNameMap() {
        return departmentRankMapper.selectList(null).stream()
                .collect(Collectors.toMap(DepartmentRank::getId, EmployeeService::rankLabel, (a, b) -> a));
    }

    private static String rankLabel(DepartmentRank rank) {
        return StringUtils.hasText(rank.getIntro()) ? rank.getCode() + "（" + rank.getIntro() + "）" : rank.getCode();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

}
