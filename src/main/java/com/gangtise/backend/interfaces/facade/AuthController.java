package com.gangtise.backend.interfaces.facade;

import com.gangtise.backend.application.dto.ApiResult;
import com.gangtise.backend.application.dto.CurrentUserVO;
import com.gangtise.backend.application.dto.LoginRequest;
import com.gangtise.backend.application.dto.LoginResultDTO;
import com.gangtise.backend.application.dto.Sm2PublicKeyDTO;
import com.gangtise.backend.application.service.AuthService;
import com.gangtise.backend.application.service.CryptoService;
import com.gangtise.backend.infrastructure.interceptor.LoginInterceptor;
import com.gangtise.backend.infrastructure.utils.IpUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口。
 *
 * <p>登录链路：{@code GET /public-key} 取一次性 SM2 公钥 → 前端加密密码 →
 * {@code POST /login} 提交密文 → 服务端解密 + BCrypt 校验 → 写入会话。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /** 登录成功后的默认落地页。 */
    private static final String HOME_PAGE = "/home";

    private final AuthService authService;
    private final CryptoService cryptoService;

    public AuthController(AuthService authService, CryptoService cryptoService) {
        this.authService = authService;
        this.cryptoService = cryptoService;
    }

    /**
     * 下发一次性 SM2 公钥（私钥存 Redis，5 分钟内有效且只能用一次）。
     */
    @GetMapping("/public-key")
    public ApiResult<Sm2PublicKeyDTO> publicKey() {
        return ApiResult.ok(cryptoService.issueKeyPair());
    }

    /**
     * 登录：请求体内的密码为 SM2 密文，服务端解密后才做 BCrypt 比对。
     *
     * <p>客户端 IP 在会话重建之前解析（登录成功会 invalidate 旧会话），解析结果随登录成功写入
     * {@code user_login_record}。
     */
    @PostMapping("/login")
    public ApiResult<LoginResultDTO> login(@Valid @RequestBody LoginRequest request,
                                           HttpServletRequest httpServletRequest) {
        String loginIp = IpUtils.getClientIp(httpServletRequest);
        CurrentUserVO user = authService.login(request, loginIp);
        // 登录成功后重置会话，避免会话固定攻击
        HttpSession oldSession = httpServletRequest.getSession(false);
        if (oldSession != null) {
            oldSession.invalidate();
        }
        httpServletRequest.getSession(true).setAttribute(LoginInterceptor.SESSION_USER, user);
        return ApiResult.ok(new LoginResultDTO(HOME_PAGE));
    }

    @PostMapping("/logout")
    public ApiResult<Void> logout(HttpServletRequest httpServletRequest) {
        HttpSession session = httpServletRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ApiResult.ok();
    }

    /**
     * 当前登录用户，未登录时 data 为空。
     */
    @GetMapping("/current")
    public ApiResult<CurrentUserVO> current(HttpServletRequest httpServletRequest) {
        HttpSession session = httpServletRequest.getSession(false);
        if (session == null) {
            return ApiResult.ok();
        }
        return ApiResult.ok((CurrentUserVO) session.getAttribute(LoginInterceptor.SESSION_USER));
    }

}
