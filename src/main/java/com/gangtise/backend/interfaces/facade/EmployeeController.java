package com.gangtise.backend.interfaces.facade;

import com.gangtise.backend.application.dto.ApiResult;
import com.gangtise.backend.application.dto.EmployeeQueryDTO;
import com.gangtise.backend.application.dto.EmployeeSaveDTO;
import com.gangtise.backend.application.dto.EmployeeVO;
import com.gangtise.backend.application.dto.PageResult;
import com.gangtise.backend.application.service.EmployeeService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 员工管理接口（员工增删改查）。
 *
 * <p>全部以 JSON 交互，前端通过 ajax 调用，不使用表单提交。
 */
@RestController
@RequestMapping("/api/employees")
public class EmployeeController {

    private final EmployeeService employeeService;

    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    /** 分页查询。 */
    @GetMapping
    public ApiResult<PageResult<EmployeeVO>> page(EmployeeQueryDTO query) {
        return ApiResult.ok(employeeService.page(query));
    }

    /** 详情。 */
    @GetMapping("/{id}")
    public ApiResult<EmployeeVO> detail(@PathVariable Integer id) {
        return ApiResult.ok(employeeService.detail(id));
    }

    /** 新增，返回新增记录的ID。 */
    @PostMapping
    public ApiResult<Integer> create(@Valid @RequestBody EmployeeSaveDTO dto) {
        return ApiResult.ok(employeeService.create(dto));
    }

    /** 修改。 */
    @PutMapping("/{id}")
    public ApiResult<Void> update(@PathVariable Integer id, @Valid @RequestBody EmployeeSaveDTO dto) {
        employeeService.update(id, dto);
        return ApiResult.ok();
    }

    /** 删除。 */
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Integer id) {
        employeeService.delete(id);
        return ApiResult.ok();
    }

}
