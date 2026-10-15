package com.gangtise.backend.application.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gangtise.backend.application.dto.LoginTrendVO;
import com.gangtise.backend.domain.aggregate.entity.UserLoginRecord;
import com.gangtise.backend.infrastructure.exception.BizException;
import com.gangtise.backend.infrastructure.repository.mapper.UserLoginRecordMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 登录记录查询服务：欢迎页的「我的登录记录」。
 *
 * <p>登录记录本身只写不删，这里只做只读统计，供个人工作台展示。
 */
@Service
public class UserLoginRecordService {

    /** 欢迎页默认统计天数。 */
    public static final int DEFAULT_TREND_DAYS = 10;
    private static final int MAX_TREND_DAYS = 90;

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter LABEL = DateTimeFormatter.ofPattern("MM-dd");

    private final UserLoginRecordMapper userLoginRecordMapper;

    public UserLoginRecordService(UserLoginRecordMapper userLoginRecordMapper) {
        this.userLoginRecordMapper = userLoginRecordMapper;
    }

    /**
     * 近 N 天（含今天）的每日登录次数趋势 + 最近一次登录。
     *
     * <p>没有登录的日期补 0，保证前端折线图横轴连续、不会因为跳过空日期而画错。
     */
    public LoginTrendVO dailyTrend(Integer userId, Integer days) {
        if (userId == null) {
            throw BizException.unauthorized("登录状态已失效，请重新登录");
        }
        int span = days == null ? DEFAULT_TREND_DAYS : Math.min(Math.max(days, 1), MAX_TREND_DAYS);

        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(span - 1L);

        List<UserLoginRecord> records = userLoginRecordMapper.selectList(new LambdaQueryWrapper<UserLoginRecord>()
                .eq(UserLoginRecord::getUserId, userId)
                .ge(UserLoginRecord::getCreateTime, from.atStartOfDay())
                .lt(UserLoginRecord::getCreateTime, today.plusDays(1).atStartOfDay()));

        Map<LocalDate, Integer> counts = new HashMap<>();
        for (UserLoginRecord record : records) {
            if (record.getCreateTime() == null) {
                continue;
            }
            counts.merge(record.getCreateTime().toLocalDate(), 1, Integer::sum);
        }

        List<LoginTrendVO.Point> points = new ArrayList<>(span);
        int total = 0;
        int max = 0;
        for (int i = 0; i < span; i++) {
            LocalDate date = from.plusDays(i);
            int count = counts.getOrDefault(date, 0);
            total += count;
            max = Math.max(max, count);
            points.add(new LoginTrendVO.Point(date.format(DATE), date.format(LABEL), count));
        }

        UserLoginRecord latest = userLoginRecordMapper.selectOne(new LambdaQueryWrapper<UserLoginRecord>()
                .eq(UserLoginRecord::getUserId, userId)
                .orderByDesc(UserLoginRecord::getCreateTime)
                .orderByDesc(UserLoginRecord::getId)
                .last("limit 1"));

        LoginTrendVO vo = new LoginTrendVO();
        vo.setDays(span);
        vo.setTotal(total);
        vo.setMax(Math.max(max, 1));
        vo.setLastLoginTime(latest == null ? null : latest.getCreateTime());
        vo.setLastLoginIp(latest == null ? null : latest.getLoginIp());
        vo.setPoints(points);
        return vo;
    }

}
