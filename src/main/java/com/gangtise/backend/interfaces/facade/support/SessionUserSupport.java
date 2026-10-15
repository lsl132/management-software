package com.gangtise.backend.interfaces.facade.support;

import com.gangtise.backend.application.dto.CurrentUserVO;
import com.gangtise.backend.infrastructure.exception.BizException;
import com.gangtise.backend.infrastructure.interceptor.LoginInterceptor;
import jakarta.servlet.http.HttpSession;

/**
 * 会话取当前登录用户的工具。
 *
 * <p>公告、日程这类「与登录人强相关」的接口需要拿到用户身份，统一从这里取，
 * 避免各 Controller 各写一遍类型转换；取不到一律按登录失效处理。
 */
public final class SessionUserSupport {

    private SessionUserSupport() {
    }

    /** 取当前登录用户，取不到抛 401 业务异常。 */
    public static CurrentUserVO require(HttpSession session) {
        Object value = session == null ? null : session.getAttribute(LoginInterceptor.SESSION_USER);
        if (value instanceof CurrentUserVO user) {
            return user;
        }
        throw BizException.unauthorized("登录状态已失效，请重新登录");
    }

}
