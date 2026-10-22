package com.management.software.interfaces.facade;

import com.management.software.application.dto.ApiResult;
import com.management.software.application.dto.PageResult;
import com.management.software.application.dto.WorkflowQueryDTO;
import com.management.software.application.dto.WorkflowSaveDTO;
import com.management.software.application.dto.WorkflowStatusDTO;
import com.management.software.application.dto.WorkflowVO;
import com.management.software.application.service.WorkflowService;
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
 * 流程（审批规则）接口：流程头 + 节点链的增删改查，以及启停。
 *
 * <p>权限字典里的「流程管理」（助记符 10004）指向本组接口对应的页面 {@code /workflows}。
 *
 * <p>接口沿用 {@code /api/**} 的登录态拦截。注意这里只做「登录」校验——项目的权限字典
 * （{@code authority} / {@code role_authority}）目前不参与请求鉴权，所以能登录的人就能改审批规则。
 */
@RestController
@RequestMapping("/api/workflows")
public class WorkflowController {

    private final WorkflowService workflowService;

    public WorkflowController(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    /** 分页查询。 */
    @GetMapping
    public ApiResult<PageResult<WorkflowVO>> page(WorkflowQueryDTO query) {
        return ApiResult.ok(workflowService.page(query));
    }

    /** 详情（带完整节点链）。 */
    @GetMapping("/{id}")
    public ApiResult<WorkflowVO> detail(@PathVariable Integer id) {
        return ApiResult.ok(workflowService.detail(id));
    }

    /** 新增，返回新增流程ID。 */
    @PostMapping
    public ApiResult<Integer> create(@Valid @RequestBody WorkflowSaveDTO dto) {
        return ApiResult.ok(workflowService.create(dto));
    }

    /** 修改（流程头 + 节点链整体覆盖）。 */
    @PutMapping("/{id}")
    public ApiResult<Void> update(@PathVariable Integer id, @Valid @RequestBody WorkflowSaveDTO dto) {
        workflowService.update(id, dto);
        return ApiResult.ok();
    }

    /** 删除，连同其全部节点。 */
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Integer id) {
        workflowService.delete(id);
        return ApiResult.ok();
    }

    /** 启用 / 禁用（列表行内快捷操作）。 */
    @PostMapping("/{id}/status")
    public ApiResult<Void> changeStatus(@PathVariable Integer id,
                                        @Valid @RequestBody WorkflowStatusDTO dto) {
        workflowService.changeStatus(id, dto.getStatus());
        return ApiResult.ok();
    }

}
