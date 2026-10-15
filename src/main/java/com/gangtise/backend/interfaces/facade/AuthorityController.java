package com.gangtise.backend.interfaces.facade;

import com.gangtise.backend.application.dto.ApiResult;
import com.gangtise.backend.application.dto.AuthoritySaveDTO;
import com.gangtise.backend.application.dto.AuthorityTreeVO;
import com.gangtise.backend.application.dto.AuthorityVO;
import com.gangtise.backend.application.service.AuthorityService;
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
 * 权限接口：权限字典的增删改查 + 「模块分组 → 功能页」两级权限树。
 *
 * <p>权限树供角色授权弹窗渲染勾选框；权限管理页在同一个树上做增删改查。
 * 路径参数用**主键 id** 而不是助记符：助记符允许被修改，不适合作为稳定标识
 * （{@code role_authority} 仍按助记符引用，改助记符时由服务层同事务级联同步）。
 */
@RestController
@RequestMapping("/api/authorities")
public class AuthorityController {

    private final AuthorityService authorityService;

    public AuthorityController(AuthorityService authorityService) {
        this.authorityService = authorityService;
    }

    /** 权限树，可选关键字（命中名称或助记符；命中分组展开全部子页）。 */
    @GetMapping
    public ApiResult<List<AuthorityTreeVO>> tree(@RequestParam(required = false) String keyword) {
        return ApiResult.ok(authorityService.tree(keyword));
    }

    /** 权限详情（含子页数与引用角色数，供编辑回填与依赖提示）。 */
    @GetMapping("/{id}")
    public ApiResult<AuthorityVO> detail(@PathVariable Integer id) {
        return ApiResult.ok(authorityService.detail(id));
    }

    /** 新增，返回新增记录的主键。 */
    @PostMapping
    public ApiResult<Integer> create(@Valid @RequestBody AuthoritySaveDTO dto) {
        return ApiResult.ok(authorityService.create(dto));
    }

    /** 修改；助记符变更时会级联同步已授权角色。 */
    @PutMapping("/{id}")
    public ApiResult<Void> update(@PathVariable Integer id, @Valid @RequestBody AuthoritySaveDTO dto) {
        authorityService.update(id, dto);
        return ApiResult.ok();
    }

    /** 删除；被角色引用或分组下还有功能页时会被拒绝。 */
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Integer id) {
        authorityService.delete(id);
        return ApiResult.ok();
    }

}
