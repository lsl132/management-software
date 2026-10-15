package com.gangtise.backend.application.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gangtise.backend.application.dto.DepartmentRankSaveDTO;
import com.gangtise.backend.application.dto.DepartmentRankVO;
import com.gangtise.backend.domain.aggregate.Department;
import com.gangtise.backend.domain.aggregate.Employee;
import com.gangtise.backend.domain.aggregate.entity.DepartmentRank;
import com.gangtise.backend.infrastructure.enums.RankTypeEnum;
import com.gangtise.backend.infrastructure.exception.BizException;
import com.gangtise.backend.infrastructure.repository.mapper.DepartmentMapper;
import com.gangtise.backend.infrastructure.repository.mapper.DepartmentRankMapper;
import com.gangtise.backend.infrastructure.repository.mapper.EmployeeMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 部门职级应用服务：职级隶属于部门，所有操作都以「部门」为入口。
 *
 * <p>约束：
 * <ul>
 *   <li>职级编码 {@code code} 全公司唯一（与库侧 {@code uk_department_rank_code} 一致）；</li>
 *   <li>新增/修改/删除前都会校验职级的归属部门与路径部门一致，防止跨部门越权修改；</li>
 *   <li>删除前校验职级是否已被员工引用。</li>
 * </ul>
 */
@Service
public class DepartmentRankService {

    private final DepartmentMapper departmentMapper;
    private final DepartmentRankMapper departmentRankMapper;
    private final EmployeeMapper employeeMapper;

    public DepartmentRankService(DepartmentMapper departmentMapper,
                                 DepartmentRankMapper departmentRankMapper,
                                 EmployeeMapper employeeMapper) {
        this.departmentMapper = departmentMapper;
        this.departmentRankMapper = departmentRankMapper;
        this.employeeMapper = employeeMapper;
    }

    /** 某部门下的职级列表，按序列（O/T/F/M/S）+ 编码排序。 */
    public List<DepartmentRankVO> list(Integer departmentId) {
        Department department = departmentMapper.selectById(departmentId);
        if (department == null) {
            throw new BizException("部门不存在或已被删除");
        }
        return departmentRankMapper.selectList(new LambdaQueryWrapper<DepartmentRank>()
                        .eq(DepartmentRank::getDepartment, departmentId)
                        .orderByAsc(DepartmentRank::getType)
                        .orderByAsc(DepartmentRank::getCode))
                .stream()
                .map(rank -> toVO(rank, department))
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public Integer create(Integer departmentId, DepartmentRankSaveDTO dto) {
        Department department = requireDepartment(departmentId);
        String code = dto.getCode().trim();
        ensureCodeUnique(code, null);

        DepartmentRank rank = new DepartmentRank();
        rank.setCode(code);
        rank.setType(dto.getType());
        rank.setDepartment(department.getId());
        rank.setIntro(trimToNull(dto.getIntro()));
        departmentRankMapper.insert(rank);
        return rank.getId();
    }

    /**
     * 全量更新：{@code intro} 传 null 即清空（依赖实体上的 {@code updateStrategy = ALWAYS}）。
     * 不允许通过本接口把职级挪到别的部门，归属变更请走「删除 + 在目标部门新增」。
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(Integer departmentId, Integer rankId, DepartmentRankSaveDTO dto) {
        Department department = requireDepartment(departmentId);
        DepartmentRank existing = requireRankOfDepartment(departmentId, rankId);
        String code = dto.getCode().trim();
        ensureCodeUnique(code, existing.getId());

        DepartmentRank rank = new DepartmentRank();
        rank.setId(existing.getId());
        rank.setCode(code);
        rank.setType(dto.getType());
        rank.setDepartment(department.getId());
        rank.setIntro(trimToNull(dto.getIntro()));
        departmentRankMapper.updateById(rank);
    }

    /** 删除职级：已被员工引用时拒绝。 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Integer departmentId, Integer rankId) {
        requireDepartment(departmentId);
        DepartmentRank existing = requireRankOfDepartment(departmentId, rankId);

        Long used = employeeMapper.selectCount(new LambdaQueryWrapper<Employee>()
                .eq(Employee::getRank, existing.getId()));
        if (used != null && used > 0) {
            throw new BizException("该职级已被 " + used + " 名员工使用，请先调整员工职级");
        }

        departmentRankMapper.deleteById(existing.getId());
    }

    /* ------------------------------------------------------------------ 校验 */

    private Department requireDepartment(Integer departmentId) {
        Department department = departmentMapper.selectById(departmentId);
        if (department == null) {
            throw new BizException("部门不存在或已被删除");
        }
        return department;
    }

    private DepartmentRank requireRankOfDepartment(Integer departmentId, Integer rankId) {
        DepartmentRank rank = departmentRankMapper.selectById(rankId);
        if (rank == null) {
            throw new BizException("职级不存在或已被删除");
        }
        if (!departmentId.equals(rank.getDepartment())) {
            throw new BizException("该职级不属于当前部门，请刷新页面后重试");
        }
        return rank;
    }

    private void ensureCodeUnique(String code, Integer excludeId) {
        Long count = departmentRankMapper.selectCount(new LambdaQueryWrapper<DepartmentRank>()
                .eq(DepartmentRank::getCode, code)
                .ne(excludeId != null, DepartmentRank::getId, excludeId));
        if (count != null && count > 0) {
            throw new BizException("职级编码已存在：" + code);
        }
    }

    /* ------------------------------------------------------------------ 装配 */

    private DepartmentRankVO toVO(DepartmentRank rank, Department department) {
        DepartmentRankVO vo = new DepartmentRankVO();
        vo.setId(rank.getId());
        vo.setCode(rank.getCode());
        vo.setType(rank.getType());
        vo.setTypeText(RankTypeEnum.labelOf(rank.getType()));
        vo.setDepartment(rank.getDepartment());
        vo.setDepartmentName(department == null ? null : department.getDepartName());
        vo.setIntro(rank.getIntro());
        vo.setCreateTime(rank.getCreateTime());
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
