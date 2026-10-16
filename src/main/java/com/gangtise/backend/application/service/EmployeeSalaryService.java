package com.gangtise.backend.application.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gangtise.backend.application.dto.CurrentUserVO;
import com.gangtise.backend.application.dto.EmployeeSalarySaveDTO;
import com.gangtise.backend.application.dto.EmployeeSalaryVO;
import com.gangtise.backend.domain.aggregate.Employee;
import com.gangtise.backend.domain.aggregate.entity.EmployeeSalary;
import com.gangtise.backend.infrastructure.enums.CurrencyEnum;
import com.gangtise.backend.infrastructure.exception.BizException;
import com.gangtise.backend.infrastructure.repository.mapper.EmployeeMapper;
import com.gangtise.backend.infrastructure.repository.mapper.EmployeeSalaryMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/**
 * 员工薪酬服务。
 *
 * <p><b>关联键是工号而不是员工主键</b>：{@code employee_salary.work_no} 才是这张表的业务键
 * （唯一索引 {@code uk_employee_salary_work_no}），员工主键只出现在接口路径上，
 * 由这里换算成工号。这样「换主键」不会影响薪酬数据，也让接口层不必关心关联细节。
 *
 * <p><b>一人一行、改动即覆盖</b>：见 {@link EmployeeSalaryMapper#upsert}。
 */
@Service
public class EmployeeSalaryService {

    private static final Logger log = LoggerFactory.getLogger(EmployeeSalaryService.class);

    /** 金额小数位，与列类型 {@code numeric(18,2)} 对齐。 */
    private static final int AMOUNT_SCALE = 2;

    private final EmployeeMapper employeeMapper;

    private final EmployeeSalaryMapper employeeSalaryMapper;

    public EmployeeSalaryService(EmployeeMapper employeeMapper, EmployeeSalaryMapper employeeSalaryMapper) {
        this.employeeMapper = employeeMapper;
        this.employeeSalaryMapper = employeeSalaryMapper;
    }

    /**
     * 员工薪酬明细。
     *
     * @return 尚未设置过薪酬时返回 {@code null}（前端据此提示「尚未设置」并按 0 预填），
     *         而不是伪造一条全 0 的记录——「没设过」和「设成 0」是两件事
     */
    public EmployeeSalaryVO detail(Integer employeeId) {
        Employee employee = requireEmployee(employeeId);
        EmployeeSalary salary = findByWorkNo(employee.getWorkNo());
        return salary == null ? null : toVO(employee, salary);
    }

    /**
     * 保存员工薪酬（不存在则新建，存在则整体覆盖）。
     *
     * @param operator 操作人，用于审计字段 {@code update_by}；取不到时为 null（不影响保存）
     */
    @Transactional(rollbackFor = Exception.class)
    public EmployeeSalaryVO save(Integer employeeId, EmployeeSalarySaveDTO dto, CurrentUserVO operator) {
        Employee employee = requireEmployee(employeeId);

        String currency = dto.getCurrency().trim().toUpperCase(Locale.ROOT);
        if (!CurrencyEnum.isSupported(currency)) {
            throw new BizException("不支持的币种：" + dto.getCurrency());
        }

        EmployeeSalary salary = new EmployeeSalary();
        salary.setWorkNo(employee.getWorkNo());
        salary.setCurrency(currency);
        salary.setMonthSalary(toAmount(dto.getMonthSalary()));
        salary.setYearSalary(toAmount(dto.getYearSalary()));
        salary.setDaySalary(toAmount(dto.getDaySalary()));
        salary.setUpdateBy(operator == null ? null : operator.getIdentifier());

        employeeSalaryMapper.upsert(salary);
        log.info("[薪酬] 保存 -> workNo={}, currency={}, month={}, year={}, day={}, operator={}",
                salary.getWorkNo(), currency, salary.getMonthSalary(), salary.getYearSalary(),
                salary.getDaySalary(), salary.getUpdateBy());

        // 回读而不是拼装：create_time/update_time 由 SQL 里的 current_timestamp 生成，
        // 只有从库里读回来才是真正落库的那两个时刻
        return toVO(employee, findByWorkNo(employee.getWorkNo()));
    }

    /**
     * 按工号删除薪酬，供「删除员工」时级联清理。
     *
     * <p>不清理的话会留下指向已不存在工号的孤儿行：工号一旦被再次启用，新员工就静默继承了
     * 前任的薪酬——这种错不会报错，只会体现在某天 HR 发现新人的薪资是别人的。
     *
     * @return 删除行数
     */
    @Transactional(rollbackFor = Exception.class)
    public int deleteByWorkNo(String workNo) {
        if (workNo == null || workNo.isBlank()) {
            return 0;
        }
        int removed = employeeSalaryMapper.delete(Wrappers.<EmployeeSalary>lambdaQuery()
                .eq(EmployeeSalary::getWorkNo, workNo));
        if (removed > 0) {
            log.info("[薪酬] 随员工删除一并清理 -> workNo={}, rows={}", workNo, removed);
        }
        return removed;
    }

    private Employee requireEmployee(Integer employeeId) {
        Employee employee = employeeId == null ? null : employeeMapper.selectById(employeeId);
        if (employee == null) {
            throw new BizException("员工不存在或已被删除");
        }
        return employee;
    }

    private EmployeeSalary findByWorkNo(String workNo) {
        return employeeSalaryMapper.selectOne(Wrappers.<EmployeeSalary>lambdaQuery()
                .eq(EmployeeSalary::getWorkNo, workNo));
    }

    /** 统一到两位小数（四舍五入），与列精度一致；负数与超范围由 DTO 上的校验拦下。 */
    private Double toAmount(BigDecimal value) {
        return value.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP).doubleValue();
    }

    /** 展示金额也统一两位小数，避免 {@code 1234.5} 这类值在 JSON 里时而 1 位时而 2 位。 */
    private BigDecimal toDisplayAmount(Double value) {
        return BigDecimal.valueOf(value == null ? 0D : value).setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
    }

    private EmployeeSalaryVO toVO(Employee employee, EmployeeSalary salary) {
        EmployeeSalaryVO vo = new EmployeeSalaryVO();
        vo.setEmployeeId(employee.getId());
        vo.setWorkNo(employee.getWorkNo());
        vo.setEmployeeName(employee.getName());
        vo.setCurrency(salary.getCurrency());
        vo.setCurrencyText(CurrencyEnum.descriptionOf(salary.getCurrency()));
        vo.setMonthSalary(toDisplayAmount(salary.getMonthSalary()));
        vo.setYearSalary(toDisplayAmount(salary.getYearSalary()));
        vo.setDaySalary(toDisplayAmount(salary.getDaySalary()));
        vo.setCreateTime(salary.getCreateTime());
        vo.setUpdateTime(salary.getUpdateTime());
        vo.setUpdateBy(salary.getUpdateBy());
        return vo;
    }

}
