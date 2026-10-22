package com.management.software.interfaces.facade;

import com.management.software.application.dto.ApiResult;
import com.management.software.application.dto.SelectOption;
import com.management.software.application.service.OptionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 下拉选项接口：供页面 ajax 动态获取级联选项。
 *
 * <p>职级隶属于部门，因此员工表单中的职级下拉需要按所选部门动态加载；
 * 用户（审批人）列表会随账号增删变化，也不适合在页面渲染时写死进模板。
 */
@RestController
@RequestMapping("/api/options")
public class OptionController {

    private final OptionService optionService;

    public OptionController(OptionService optionService) {
        this.optionService = optionService;
    }

    /** 按部门查询职级选项；department 为空时返回空集合。 */
    @GetMapping("/ranks")
    public ApiResult<List<SelectOption>> ranks(@RequestParam(required = false) Integer department) {
        return ApiResult.ok(optionService.ranks(department));
    }

    /** 审批人选项：全部正常状态的系统账号，供流程管理的审批节点选人。 */
    @GetMapping("/users")
    public ApiResult<List<SelectOption>> users() {
        return ApiResult.ok(optionService.users());
    }

}
