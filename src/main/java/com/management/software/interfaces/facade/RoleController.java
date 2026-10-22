package com.management.software.interfaces.facade;

import com.management.software.application.dto.ApiResult;
import com.management.software.application.dto.RoleAuthoritySaveDTO;
import com.management.software.application.dto.RoleSaveDTO;
import com.management.software.application.dto.RoleVO;
import com.management.software.application.service.RoleService;
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
 * 角色管理接口（角色增删改查 + 挂载权限）。
 *
 * <p>权限挂载按助记符全量覆盖，服务端会做父子归一化（勾分组自动补全其下功能页），
 * 详见 {@code RoleService#saveAuthorities}。
 */
@RestController
@RequestMapping("/api/roles")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    /** 角色列表（不分页），带用户数与权限数统计。 */
    @GetMapping
    public ApiResult<List<RoleVO>> list(@RequestParam(required = false) String keyword) {
        return ApiResult.ok(roleService.list(keyword));
    }

    /** 详情。 */
    @GetMapping("/{id}")
    public ApiResult<RoleVO> detail(@PathVariable Integer id) {
        return ApiResult.ok(roleService.detail(id));
    }

    /** 新增，返回新增角色ID。 */
    @PostMapping
    public ApiResult<Integer> create(@Valid @RequestBody RoleSaveDTO dto) {
        return ApiResult.ok(roleService.create(dto));
    }

    /** 修改。 */
    @PutMapping("/{id}")
    public ApiResult<Void> update(@PathVariable Integer id, @Valid @RequestBody RoleSaveDTO dto) {
        roleService.update(id, dto);
        return ApiResult.ok();
    }

    /** 删除：存在用户引用时拒绝；成功时连带清理权限挂载记录。 */
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Integer id) {
        roleService.delete(id);
        return ApiResult.ok();
    }

    /** 该角色已挂载的权限助记符。 */
    @GetMapping("/{id}/authorities")
    public ApiResult<List<Integer>> authorities(@PathVariable Integer id) {
        return ApiResult.ok(roleService.authorities(id));
    }

    /** 全量覆盖该角色的权限挂载（模块分组 + 功能页）。 */
    @PutMapping("/{id}/authorities")
    public ApiResult<Void> saveAuthorities(@PathVariable Integer id,
                                           @Valid @RequestBody RoleAuthoritySaveDTO dto) {
        roleService.saveAuthorities(id, dto.getMnemonics());
        return ApiResult.ok();
    }

}
