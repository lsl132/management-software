package com.gangtise.backend.application.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gangtise.backend.application.dto.ScheduleQueryDTO;
import com.gangtise.backend.application.dto.ScheduleSaveDTO;
import com.gangtise.backend.application.dto.ScheduleVO;
import com.gangtise.backend.domain.aggregate.entity.UserSchedule;
import com.gangtise.backend.infrastructure.exception.BizException;
import com.gangtise.backend.infrastructure.repository.mapper.UserScheduleMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * 日程应用服务：当前登录用户自己的日程增删改查。
 *
 * <p>两条关键约束：
 * <ul>
 *   <li><b>日程归属登录用户</b>：用户ID由服务端写入，任何读写都带 {@code user_id} 条件，
 *       跨用户访问一律拒绝（不暴露「记录是否存在」以外的信息）；</li>
 *   <li><b>提醒时间不能晚于预约时间</b>：提醒的意义是提前通知，晚于预约时间属于误填。</li>
 * </ul>
 */
@Service
public class ScheduleService {

    private final UserScheduleMapper scheduleMapper;

    public ScheduleService(UserScheduleMapper scheduleMapper) {
        this.scheduleMapper = scheduleMapper;
    }

    /**
     * 查询某个月的日程（按预约时间升序），供日程页列表与欢迎页日历共用。
     *
     * <p>{@code month} 为空取当前月；格式非法直接拒绝，避免静默返回全量数据。
     */
    public List<ScheduleVO> listByMonth(Integer userId, ScheduleQueryDTO query) {
        YearMonth month = parseMonth(query == null ? null : query.getMonth());
        String keyword = query == null ? null : query.getKeyword();

        LambdaQueryWrapper<UserSchedule> wrapper = new LambdaQueryWrapper<UserSchedule>()
                .eq(UserSchedule::getUserId, userId)
                .ge(UserSchedule::getScheduleTime, month.atDay(1).atStartOfDay())
                .lt(UserSchedule::getScheduleTime, month.plusMonths(1).atDay(1).atStartOfDay());
        if (StringUtils.hasText(keyword)) {
            wrapper.like(UserSchedule::getTitle, keyword.trim());
        }
        wrapper.orderByAsc(UserSchedule::getScheduleTime).orderByAsc(UserSchedule::getId);

        return scheduleMapper.selectList(wrapper).stream().map(ScheduleService::toVO).toList();
    }

    public ScheduleVO detail(Integer userId, Long id) {
        return toVO(requireOwned(userId, id));
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(Integer userId, ScheduleSaveDTO dto) {
        ensureRemindNotLate(dto);

        UserSchedule schedule = new UserSchedule();
        schedule.setUserId(userId);
        schedule.setScheduleTime(dto.getScheduleTime());
        schedule.setRemindTime(dto.getRemindTime());
        schedule.setTitle(dto.getTitle().trim());
        schedule.setIntro(trimToNull(dto.getIntro()));
        scheduleMapper.insert(schedule);
        return schedule.getId();
    }

    /** 全量更新：提醒时间与简介传 null 即清空（依赖实体上的 {@code updateStrategy = ALWAYS}）。 */
    @Transactional(rollbackFor = Exception.class)
    public void update(Integer userId, Long id, ScheduleSaveDTO dto) {
        requireOwned(userId, id);
        ensureRemindNotLate(dto);

        UserSchedule schedule = new UserSchedule();
        schedule.setId(id);
        schedule.setScheduleTime(dto.getScheduleTime());
        schedule.setRemindTime(dto.getRemindTime());
        schedule.setTitle(dto.getTitle().trim());
        schedule.setIntro(trimToNull(dto.getIntro()));
        scheduleMapper.updateById(schedule);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Integer userId, Long id) {
        requireOwned(userId, id);
        scheduleMapper.deleteById(id);
    }

    /* ------------------------------------------------------------------ 校验 */

    /** 取出并校验归属：不存在与不属于当前用户都拒绝，且把 user_id 作为查询条件而非事后比对。 */
    private UserSchedule requireOwned(Integer userId, Long id) {
        UserSchedule schedule = id == null ? null : scheduleMapper.selectOne(new LambdaQueryWrapper<UserSchedule>()
                .eq(UserSchedule::getId, id)
                .eq(UserSchedule::getUserId, userId)
                .last("limit 1"));
        if (schedule == null) {
            throw new BizException("日程不存在或不属于当前登录用户");
        }
        return schedule;
    }

    private void ensureRemindNotLate(ScheduleSaveDTO dto) {
        if (dto.getRemindTime() != null && dto.getScheduleTime() != null
                && dto.getRemindTime().isAfter(dto.getScheduleTime())) {
            throw new BizException("提醒时间不能晚于预约时间");
        }
    }

    /* ------------------------------------------------------------------ 装配 */

    private static YearMonth parseMonth(String value) {
        if (!StringUtils.hasText(value)) {
            return YearMonth.now();
        }
        try {
            return YearMonth.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new BizException("月份格式不正确，应为 yyyy-MM");
        }
    }

    private static ScheduleVO toVO(UserSchedule schedule) {
        ScheduleVO vo = new ScheduleVO();
        vo.setId(schedule.getId());
        vo.setUserId(schedule.getUserId());
        vo.setScheduleTime(schedule.getScheduleTime());
        vo.setRemindTime(schedule.getRemindTime());
        vo.setTitle(schedule.getTitle());
        vo.setIntro(schedule.getIntro());
        vo.setCreateTime(schedule.getCreateTime());
        return vo;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

}
