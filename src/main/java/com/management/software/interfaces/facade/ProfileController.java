package com.management.software.interfaces.facade;

import com.management.software.application.dto.ApiResult;
import com.management.software.application.dto.CurrentUserVO;
import com.management.software.application.dto.PasswordChangeDTO;
import com.management.software.application.dto.ProfileSaveDTO;
import com.management.software.application.dto.ProfileVO;
import com.management.software.application.service.ProfileService;
import com.management.software.infrastructure.interceptor.LoginInterceptor;
import com.management.software.interfaces.facade.support.SessionUserSupport;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 个人信息与自助修改密码接口（导航「我的」下的两项）。
 *
 * <p>操作对象永远是**会话里的当前登录人**，接口不接受 userId 参数——
 * 一旦开放成 {@code /api/profile/{userId}}，就等于把「改别人资料/密码」的口子开在了前端手里。
 */
@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    /** 当前登录人的资料。 */
    @GetMapping
    public ApiResult<ProfileVO> detail(HttpSession session) {
        return ApiResult.ok(profileService.detail(SessionUserSupport.require(session).getUserId()));
    }

    /**
     * 保存资料（显示名 / 手机 / 邮箱）。
     *
     * <p>保存后**同步刷新会话里的显示名**：顶栏头像旁的名字读的是会话里那份 {@code CurrentUserVO}，
     * 不刷的话要等下次登录才更新，用户会以为没保存成功。
     */
    @PutMapping
    public ApiResult<ProfileVO> save(@Valid @RequestBody ProfileSaveDTO dto, HttpSession session) {
        CurrentUserVO current = SessionUserSupport.require(session);
        ProfileVO vo = profileService.update(current.getUserId(), dto);

        current.setUsername(vo.getUsername());
        session.setAttribute(LoginInterceptor.SESSION_USER, current);
        return ApiResult.ok(vo);
    }

    /**
     * 自助修改密码。
     *
     * <p>成功后**立即作废当前会话**：密码变了，旧会话就不该再是有效凭证。
     * 前端收到成功后跳到登录页，用新密码重新登录。
     */
    @PutMapping("/password")
    public ApiResult<Void> changePassword(@Valid @RequestBody PasswordChangeDTO dto, HttpSession session) {
        CurrentUserVO current = SessionUserSupport.require(session);
        profileService.changePassword(current.getUserId(), dto);

        if (session != null) {
            session.invalidate();
        }
        return ApiResult.ok();
    }

}
