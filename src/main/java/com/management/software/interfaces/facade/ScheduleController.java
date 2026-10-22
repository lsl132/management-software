package com.management.software.interfaces.facade;

import com.management.software.application.dto.ApiResult;
import com.management.software.application.dto.ScheduleQueryDTO;
import com.management.software.application.dto.ScheduleSaveDTO;
import com.management.software.application.dto.ScheduleVO;
import com.management.software.application.service.ScheduleService;
import com.management.software.interfaces.facade.support.SessionUserSupport;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 日程管理接口（当前登录用户自己的日程增删改查）。
 *
 * <p>接口不暴露「查别人的日程」：用户ID一律取自会话，服务端按 {@code user_id} 过滤，
 * 跨用户访问会被拒绝。按月的列表接口同时服务于日程管理页与欢迎页日历。
 */
@RestController
@RequestMapping("/api/schedules")
public class ScheduleController {

    private final ScheduleService scheduleService;

    public ScheduleController(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    /** 按月查询日程（按预约时间升序），month 格式 yyyy-MM，为空取当前月。 */
    @GetMapping
    public ApiResult<List<ScheduleVO>> list(ScheduleQueryDTO query, HttpSession session) {
        Integer userId = SessionUserSupport.require(session).getUserId();
        return ApiResult.ok(scheduleService.listByMonth(userId, query));
    }

    /** 详情。 */
    @GetMapping("/{id}")
    public ApiResult<ScheduleVO> detail(@PathVariable Long id, HttpSession session) {
        Integer userId = SessionUserSupport.require(session).getUserId();
        return ApiResult.ok(scheduleService.detail(userId, id));
    }

    /** 新增，返回新增记录的ID。 */
    @PostMapping
    public ApiResult<Long> create(@Valid @RequestBody ScheduleSaveDTO dto, HttpSession session) {
        Integer userId = SessionUserSupport.require(session).getUserId();
        return ApiResult.ok(scheduleService.create(userId, dto));
    }

    /** 修改。 */
    @PutMapping("/{id}")
    public ApiResult<Void> update(@PathVariable Long id,
                                 @Valid @RequestBody ScheduleSaveDTO dto,
                                 HttpSession session) {
        Integer userId = SessionUserSupport.require(session).getUserId();
        scheduleService.update(userId, id, dto);
        return ApiResult.ok();
    }

    /** 删除。 */
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id, HttpSession session) {
        Integer userId = SessionUserSupport.require(session).getUserId();
        scheduleService.delete(userId, id);
        return ApiResult.ok();
    }

}
