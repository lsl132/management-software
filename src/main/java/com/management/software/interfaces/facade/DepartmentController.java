package com.management.software.interfaces.facade;

import com.management.software.application.dto.ApiResult;
import com.management.software.application.dto.DepartmentSaveDTO;
import com.management.software.application.dto.DepartmentVO;
import com.management.software.application.service.DepartmentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 部门管理接口（部门增删改查）。
 *
 * <p>部门量小，列表不分页，直接返回全量并支持按名称模糊过滤；前端通过 ajax 调用，不使用表单提交。
 */
@RestController
@RequestMapping("/api/departments")
public class DepartmentController {

    private final DepartmentService departmentService;

    public DepartmentController(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }

    /** 部门列表，可选按名称模糊查询。 */
    @GetMapping
    public ApiResult<List<DepartmentVO>> list(@RequestParam(required = false) String keyword) {
        return ApiResult.ok(departmentService.list(keyword));
    }

    /** 详情。 */
    @GetMapping("/{id}")
    public ApiResult<DepartmentVO> detail(@PathVariable Integer id) {
        return ApiResult.ok(departmentService.detail(id));
    }

    /** 新增，返回新增记录的ID。 */
    @PostMapping
    public ApiResult<Integer> create(@Valid @RequestBody DepartmentSaveDTO dto) {
        return ApiResult.ok(departmentService.create(dto));
    }

    /** 修改。 */
    @PutMapping("/{id}")
    public ApiResult<Void> update(@PathVariable Integer id, @Valid @RequestBody DepartmentSaveDTO dto) {
        departmentService.update(id, dto);
        return ApiResult.ok();
    }

    /** 删除（存在子部门 / 职级 / 员工时拒绝）。 */
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Integer id) {
        departmentService.delete(id);
        return ApiResult.ok();
    }

}
