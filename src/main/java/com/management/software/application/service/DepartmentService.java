package com.management.software.application.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.management.software.application.dto.DepartmentSaveDTO;
import com.management.software.application.dto.DepartmentVO;
import com.management.software.domain.aggregate.Department;
import com.management.software.domain.aggregate.Employee;
import com.management.software.domain.aggregate.entity.DepartmentRank;
import com.management.software.infrastructure.exception.BizException;
import com.management.software.infrastructure.repository.mapper.DepartmentMapper;
import com.management.software.infrastructure.repository.mapper.DepartmentRankMapper;
import com.management.software.infrastructure.repository.mapper.EmployeeMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 部门应用服务：部门增删改查。
 *
 * <p>两个关键约束：
 * <ul>
 *   <li>部门名称全公司唯一（库侧有 {@code uk_department_depart_name} 唯一索引，服务侧做前置校验给出友好提示）；</li>
 *   <li>删除部门前必须保证没有下游归属（子部门 / 职级 / 员工），否则直接拒绝，避免留下悬挂数据。</li>
 * </ul>
 */
@Service
public class DepartmentService {

    /** 父级链最大追溯层数，防止历史脏数据形成环导致死循环。 */
    private static final int MAX_PARENT_LEVEL = 32;

    private final DepartmentMapper departmentMapper;
    private final DepartmentRankMapper departmentRankMapper;
    private final EmployeeMapper employeeMapper;

    public DepartmentService(DepartmentMapper departmentMapper,
                             DepartmentRankMapper departmentRankMapper,
                             EmployeeMapper employeeMapper) {
        this.departmentMapper = departmentMapper;
        this.departmentRankMapper = departmentRankMapper;
        this.employeeMapper = employeeMapper;
    }

    /** 部门列表，支持按名称模糊过滤。 */
    public List<DepartmentVO> list(String keyword) {
        List<Department> all = departmentMapper.selectList(new LambdaQueryWrapper<Department>()
                .orderByAsc(Department::getId));

        Map<Integer, String> names = all.stream()
                .collect(Collectors.toMap(Department::getId, Department::getDepartName, (a, b) -> a));
        Map<Integer, Long> rankCounts = rankCountByDepartment();
        Map<Integer, Long> employeeCounts = employeeCountByDepartment();

        String key = StringUtils.hasText(keyword) ? keyword.trim() : null;
        return all.stream()
                .filter(department -> key == null || department.getDepartName().contains(key))
                .map(department -> toVO(department, names, rankCounts, employeeCounts))
                .toList();
    }

    public DepartmentVO detail(Integer id) {
        Department department = departmentMapper.selectById(id);
        if (department == null) {
            throw new BizException("部门不存在或已被删除");
        }
        return toVO(department, departmentNameMap(), rankCountByDepartment(), employeeCountByDepartment());
    }

    @Transactional(rollbackFor = Exception.class)
    public Integer create(DepartmentSaveDTO dto) {
        String departName = dto.getDepartName().trim();
        ensureNameUnique(departName, null);
        Integer parentId = dto.getParentId();
        ensureParentExists(parentId);

        Department department = new Department();
        department.setDepartName(departName);
        department.setParentId(parentId);
        department.setDescription(trimToNull(dto.getDescription()));
        departmentMapper.insert(department);
        return department.getId();
    }

    /**
     * 全量更新：可选项按传入值覆盖，传 null 即清空（依赖实体上的 {@code updateStrategy = ALWAYS}）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(Integer id, DepartmentSaveDTO dto) {
        Department existing = departmentMapper.selectById(id);
        if (existing == null) {
            throw new BizException("部门不存在或已被删除");
        }
        String departName = dto.getDepartName().trim();
        ensureNameUnique(departName, id);

        Integer parentId = dto.getParentId();
        if (parentId != null) {
            ensureParentExists(parentId);
            ensureNoCycle(id, parentId);
        }

        Department department = new Department();
        department.setId(id);
        department.setDepartName(departName);
        department.setParentId(parentId);
        department.setDescription(trimToNull(dto.getDescription()));
        departmentMapper.updateById(department);
    }

    /** 删除部门：存在子部门、职级或员工时拒绝。 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Integer id) {
        Department existing = departmentMapper.selectById(id);
        if (existing == null) {
            throw new BizException("部门不存在或已被删除");
        }

        Long childCount = departmentMapper.selectCount(new LambdaQueryWrapper<Department>()
                .eq(Department::getParentId, id));
        if (childCount != null && childCount > 0) {
            throw new BizException("该部门下还有 " + childCount + " 个子部门，请先处理子部门");
        }

        Long rankCount = departmentRankMapper.selectCount(new LambdaQueryWrapper<DepartmentRank>()
                .eq(DepartmentRank::getDepartment, id));
        if (rankCount != null && rankCount > 0) {
            throw new BizException("该部门下还有 " + rankCount + " 个职级，请先删除职级");
        }

        Long employeeCount = employeeMapper.selectCount(new LambdaQueryWrapper<Employee>()
                .eq(Employee::getDepartment, id));
        if (employeeCount != null && employeeCount > 0) {
            throw new BizException("该部门下还有 " + employeeCount + " 名员工，请先调整员工所属部门");
        }

        departmentMapper.deleteById(id);
    }

    /* ------------------------------------------------------------------ 校验 */

    private void ensureNameUnique(String departName, Integer excludeId) {
        Long count = departmentMapper.selectCount(new LambdaQueryWrapper<Department>()
                .eq(Department::getDepartName, departName)
                .ne(excludeId != null, Department::getId, excludeId));
        if (count != null && count > 0) {
            throw new BizException("部门名称已存在：" + departName);
        }
    }

    private void ensureParentExists(Integer parentId) {
        if (parentId != null && departmentMapper.selectById(parentId) == null) {
            throw new BizException("所选上级部门不存在，请重新选择");
        }
    }

    /** 沿父级链向上追溯，禁止把部门挂到自身或其子孙节点下。 */
    private void ensureNoCycle(Integer id, Integer parentId) {
        Integer cursor = parentId;
        int depth = 0;
        while (cursor != null && depth++ < MAX_PARENT_LEVEL) {
            if (cursor.equals(id)) {
                throw new BizException("上级部门不能选择本部门或其下级部门");
            }
            Department parent = departmentMapper.selectById(cursor);
            cursor = parent == null ? null : parent.getParentId();
        }
    }

    /* ------------------------------------------------------------------ 装配 */

    private Map<Integer, String> departmentNameMap() {
        return departmentMapper.selectList(null).stream()
                .collect(Collectors.toMap(Department::getId, Department::getDepartName, (a, b) -> a));
    }

    private Map<Integer, Long> rankCountByDepartment() {
        return departmentRankMapper.selectList(null).stream()
                .filter(rank -> rank.getDepartment() != null)
                .collect(Collectors.groupingBy(DepartmentRank::getDepartment, Collectors.counting()));
    }

    private Map<Integer, Long> employeeCountByDepartment() {
        List<Map<String, Object>> rows = employeeMapper.selectMaps(new QueryWrapper<Employee>()
                .select("department", "count(*) as cnt")
                .isNotNull("department")
                .groupBy("department"));

        Map<Integer, Long> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Object key = pick(row, "department");
            Object count = pick(row, "cnt");
            if (key instanceof Number departmentId && count instanceof Number value) {
                result.put(departmentId.intValue(), value.longValue());
            }
        }
        return result;
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

    private DepartmentVO toVO(Department department,
                              Map<Integer, String> names,
                              Map<Integer, Long> rankCounts,
                              Map<Integer, Long> employeeCounts) {
        DepartmentVO vo = new DepartmentVO();
        vo.setId(department.getId());
        vo.setParentId(department.getParentId());
        vo.setParentName(department.getParentId() == null ? null : names.get(department.getParentId()));
        vo.setDepartName(department.getDepartName());
        vo.setDescription(department.getDescription());
        vo.setRankCount(rankCounts.getOrDefault(department.getId(), 0L).intValue());
        vo.setEmployeeCount(employeeCounts.getOrDefault(department.getId(), 0L).intValue());
        vo.setCreateTime(department.getCreateTime());
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
