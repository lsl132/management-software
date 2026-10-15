package com.gangtise.backend.interfaces.facade;

import com.gangtise.backend.application.dto.ApiResult;
import com.gangtise.backend.application.dto.SelectOption;
import com.gangtise.backend.application.service.OptionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 下拉选项接口：供页面 ajax 动态获取级联选项。
 *
 * <p>职级隶属于部门，因此员工表单中的职级下拉需要按所选部门动态加载。
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

}
