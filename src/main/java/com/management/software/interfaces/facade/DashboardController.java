package com.management.software.interfaces.facade;

import com.management.software.application.dto.ApiResult;
import com.management.software.application.dto.LoginTrendVO;
import com.management.software.application.service.UserLoginRecordService;
import com.management.software.interfaces.facade.support.SessionUserSupport;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 个人工作台（欢迎页）数据接口。
 *
 * <p>只提供欢迎页专有的聚合数据；公告与日程直接复用各自的列表接口。
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final UserLoginRecordService userLoginRecordService;

    public DashboardController(UserLoginRecordService userLoginRecordService) {
        this.userLoginRecordService = userLoginRecordService;
    }

    /**
     * 我的登录记录：近 N 天（含今天）每日登录次数 + 最近一次登录。
     *
     * @param days 统计天数，默认 10，服务端限制在 1~90
     */
    @GetMapping("/login-trend")
    public ApiResult<LoginTrendVO> loginTrend(@RequestParam(required = false) Integer days, HttpSession session) {
        Integer userId = SessionUserSupport.require(session).getUserId();
        return ApiResult.ok(userLoginRecordService.dailyTrend(userId, days));
    }

}
