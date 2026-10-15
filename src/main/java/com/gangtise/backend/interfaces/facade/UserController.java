package com.gangtise.backend.interfaces.facade;

import com.gangtise.backend.application.dto.ApiResult;
import com.gangtise.backend.application.dto.AssignRoleDTO;
import com.gangtise.backend.application.dto.PageResult;
import com.gangtise.backend.application.dto.UserQueryDTO;
import com.gangtise.backend.application.dto.UserSaveDTO;
import com.gangtise.backend.application.dto.UserVO;
import com.gangtise.backend.application.service.UserService;
import com.gangtise.backend.interfaces.facade.support.SessionUserSupport;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户管理接口（系统账号增删改查 + 设置角色 + 重置密码 + 冻结/解冻）。
 *
 * <p>刻意**不提供删除接口**：登录记录等审计数据按 {@code user_id} 关联，物理删除会让留痕断链，
 * 废弃账号统一走冻结（{@code POST /api/users/{id}/freeze}）。
 *
 * <p>密码不出现在任何入参与返回体：新用户与重置密码统一使用系统初始密码，由服务端散列写入。
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /** 分页查询。 */
    @GetMapping
    public ApiResult<PageResult<UserVO>> page(UserQueryDTO query) {
        return ApiResult.ok(userService.page(query));
    }

    /** 详情。 */
    @GetMapping("/{id}")
    public ApiResult<UserVO> detail(@PathVariable Integer id) {
        return ApiResult.ok(userService.detail(id));
    }

    /** 新增，返回新增用户ID；登录凭证使用系统初始密码。 */
    @PostMapping
    public ApiResult<Integer> create(@Valid @RequestBody UserSaveDTO dto) {
        return ApiResult.ok(userService.create(dto));
    }

    /** 修改（展示名 / 登录标识 / 手机号 / 邮箱 / 角色 / 状态）。 */
    @PutMapping("/{id}")
    public ApiResult<Void> update(@PathVariable Integer id, @Valid @RequestBody UserSaveDTO dto) {
        userService.update(id, dto);
        return ApiResult.ok();
    }

    /** 设置角色；{@code roleId} 传 null 表示取消角色绑定。 */
    @PostMapping("/{id}/role")
    public ApiResult<Void> assignRole(@PathVariable Integer id, @RequestBody AssignRoleDTO dto) {
        userService.assignRole(id, dto == null ? null : dto.getRoleId());
        return ApiResult.ok();
    }

    /** 重置密码为系统初始密码。 */
    @PostMapping("/{id}/password/reset")
    public ApiResult<Void> resetPassword(@PathVariable Integer id) {
        userService.resetPassword(id);
        return ApiResult.ok();
    }

    /** 冻结账号（禁止冻结当前登录账号），自下次登录起生效。 */
    @PostMapping("/{id}/freeze")
    public ApiResult<Void> freeze(@PathVariable Integer id, HttpSession session) {
        userService.freeze(id, SessionUserSupport.require(session).getUserId());
        return ApiResult.ok();
    }

    /** 解冻账号。 */
    @PostMapping("/{id}/unfreeze")
    public ApiResult<Void> unfreeze(@PathVariable Integer id) {
        userService.unfreeze(id);
        return ApiResult.ok();
    }

}
