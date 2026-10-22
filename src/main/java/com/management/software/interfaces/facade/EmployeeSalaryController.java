package com.management.software.interfaces.facade;

import com.management.software.application.dto.ApiResult;
import com.management.software.application.dto.EmployeeSalarySaveDTO;
import com.management.software.application.dto.EmployeeSalaryVO;
import com.management.software.application.service.EmployeeSalaryService;
import com.management.software.interfaces.facade.support.SessionUserSupport;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 员工薪酬接口（员工页的薪酬弹窗）。
 *
 * <p>做成员工的子资源（{@code /api/employees/{employeeId}/salary}）而不是独立的
 * {@code /api/salaries}：薪酬必须依附于一个具体员工，脱离员工的薪酬列表没有业务含义；
 * 而且 URL 上带着员工主键，服务端能顺手校验「这个员工真的存在」。
 *
 * <p>接口沿用 {@code /api/**} 的登录态拦截，并且**保存时把操作人写进审计字段**：
 * 薪酬是敏感数据，出了争议要能回答「谁在什么时候把它改成这样的」。
 * 注意这里只做「登录」校验——项目的权限字典（{@code authority} / {@code role_authority}）
 * 目前不参与请求鉴权，所以能登录的人就能改薪酬。
 */
@RestController
@RequestMapping("/api/employees/{employeeId}/salary")
public class EmployeeSalaryController {

    private final EmployeeSalaryService employeeSalaryService;

    public EmployeeSalaryController(EmployeeSalaryService employeeSalaryService) {
        this.employeeSalaryService = employeeSalaryService;
    }

    /**
     * 员工薪酬明细。
     *
     * <p>尚未设置过时 {@code data} 为空（不是一条全 0 记录），前端据此提示「尚未设置」。
     */
    @GetMapping
    public ApiResult<EmployeeSalaryVO> detail(@PathVariable Integer employeeId) {
        return ApiResult.ok(employeeSalaryService.detail(employeeId));
    }

    /** 保存薪酬（不存在则新建，存在则覆盖）。 */
    @PutMapping
    public ApiResult<EmployeeSalaryVO> save(@PathVariable Integer employeeId,
                                            @Valid @RequestBody EmployeeSalarySaveDTO dto,
                                            HttpSession session) {
        return ApiResult.ok(employeeSalaryService.save(employeeId, dto, SessionUserSupport.require(session)));
    }

}
