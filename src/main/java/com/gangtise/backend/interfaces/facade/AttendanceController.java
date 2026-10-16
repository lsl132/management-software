package com.gangtise.backend.interfaces.facade;

import com.gangtise.backend.application.dto.ApiResult;
import com.gangtise.backend.application.dto.AttendancePunchDTO;
import com.gangtise.backend.application.dto.AttendancePunchVO;
import com.gangtise.backend.application.service.AttendanceService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 考勤接口。
 *
 * <p><b>打卡接口不校验登录，鉴权靠来源网段</b>：调用方是打卡机这类无会话设备。
 * 登录用户是这个管理软件的管理员（HR、行政），不是员工；让设备先登录既别扭，
 * 又会在服务重启或会话过期时让打卡静默失败。因此 {@code /api/attendance/punch}
 * 从 {@code LoginInterceptor} 的管辖里摘出（见 {@code WebMvcConfig}），
 * 由 {@code AttendancePunchSourceInterceptor} 按 {@code app.attendance.allowed-networks}
 * 收敛来源——这是它唯一的门禁，未命中的来源一律 403 并记 WARN 日志。
 *
 * <p>工号由请求体提供：打卡机从刷卡介质上读到的就是工号，服务端没有比它更可靠的信息源。
 * 防伪造的责任落在「谁能在网络上够到这个接口」上，而不是工号本身。
 */
@RestController
@RequestMapping("/api/attendance")
public class AttendanceController {

    private final AttendanceService attendanceService;

    public AttendanceController(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    /**
     * 员工打卡。
     *
     * <p>打卡时间由服务端写入，请求体只提供工号与位置。
     */
    @PostMapping("/punch")
    public ApiResult<AttendancePunchVO> punch(@Valid @RequestBody AttendancePunchDTO dto) {
        return ApiResult.ok(attendanceService.punch(dto));
    }

}
