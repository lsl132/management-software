package com.management.software.interfaces.facade;

import com.management.software.application.dto.ApiResult;
import com.management.software.application.dto.DepartmentRankSaveDTO;
import com.management.software.application.dto.DepartmentRankVO;
import com.management.software.application.service.DepartmentRankService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 部门职级管理接口（职级增删改查）。
 *
 * <p>路径设计为 {@code /api/departments/{departmentId}/ranks}：职级隶属于部门，
 * 归属部门由路径表达，请求体不接受部门参数，服务侧会校验职级归属与路径部门一致。
 */
@RestController
@RequestMapping("/api/departments/{departmentId}/ranks")
public class DepartmentRankController {

    private final DepartmentRankService departmentRankService;

    public DepartmentRankController(DepartmentRankService departmentRankService) {
        this.departmentRankService = departmentRankService;
    }

    /** 某部门下的职级列表。 */
    @GetMapping
    public ApiResult<List<DepartmentRankVO>> list(@PathVariable Integer departmentId) {
        return ApiResult.ok(departmentRankService.list(departmentId));
    }

    /** 在该部门下新增职级，返回新增记录的ID。 */
    @PostMapping
    public ApiResult<Integer> create(@PathVariable Integer departmentId,
                                     @Valid @RequestBody DepartmentRankSaveDTO dto) {
        return ApiResult.ok(departmentRankService.create(departmentId, dto));
    }

    /** 修改该部门下的职级。 */
    @PutMapping("/{rankId}")
    public ApiResult<Void> update(@PathVariable Integer departmentId,
                                  @PathVariable Integer rankId,
                                  @Valid @RequestBody DepartmentRankSaveDTO dto) {
        departmentRankService.update(departmentId, rankId, dto);
        return ApiResult.ok();
    }

    /** 删除该部门下的职级（已被员工引用时拒绝）。 */
    @DeleteMapping("/{rankId}")
    public ApiResult<Void> delete(@PathVariable Integer departmentId, @PathVariable Integer rankId) {
        departmentRankService.delete(departmentId, rankId);
        return ApiResult.ok();
    }

}
