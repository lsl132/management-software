package com.gangtise.backend.application.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gangtise.backend.application.dto.AttendancePunchDTO;
import com.gangtise.backend.application.dto.AttendancePunchVO;
import com.gangtise.backend.domain.aggregate.Attendance;
import com.gangtise.backend.domain.aggregate.Employee;
import com.gangtise.backend.domain.aggregate.entity.AttendanceRecord;
import com.gangtise.backend.infrastructure.config.AttendanceProperties;
import com.gangtise.backend.infrastructure.enums.AttendanceRecordStatusEnum;
import com.gangtise.backend.infrastructure.enums.AttendanceStatusEnum;
import com.gangtise.backend.infrastructure.enums.EmployeeStatusEnum;
import com.gangtise.backend.infrastructure.exception.BizException;
import com.gangtise.backend.infrastructure.repository.mapper.AttendanceMapper;
import com.gangtise.backend.infrastructure.repository.mapper.AttendanceRecordMapper;
import com.gangtise.backend.infrastructure.repository.mapper.EmployeeMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 考勤应用服务：原始打卡（{@code attendance_record}）与按人按天的统计（{@code attendance}）。
 *
 * <p>两条链路的关系是「明细 → 汇总」：
 * <ul>
 *   <li>{@link #punch} 只负责落一条原始记录，不做任何判定——判定规则随时会变，
 *       而原始记录一旦落库就不该再改，改规则时重算汇总即可，不必动明细；</li>
 *   <li>{@link #statisticsFor} 把某一天的全部明细归并成每人一行：最早打卡、最晚打卡、
 *       迟到/早退状态、加班时长。可对任意历史日期重跑，结果幂等。</li>
 * </ul>
 */
@Service
public class AttendanceService {

    private static final Logger log = LoggerFactory.getLogger(AttendanceService.class);

    /** 毫秒 → 小时。 */
    private static final double MILLIS_PER_HOUR = 3_600_000.0D;

    private final AttendanceRecordMapper attendanceRecordMapper;
    private final AttendanceMapper attendanceMapper;
    private final EmployeeMapper employeeMapper;
    private final AttendanceProperties properties;

    public AttendanceService(AttendanceRecordMapper attendanceRecordMapper,
                             AttendanceMapper attendanceMapper,
                             EmployeeMapper employeeMapper,
                             AttendanceProperties properties) {
        this.attendanceRecordMapper = attendanceRecordMapper;
        this.attendanceMapper = attendanceMapper;
        this.employeeMapper = employeeMapper;
        this.properties = properties;
    }

    /* ------------------------------------------------------------ 打卡 */

    /**
     * 员工打卡：写入一条原始考勤记录。
     *
     * <p>打卡时间取**服务端时钟**（{@code System.currentTimeMillis()}），入参里没有时间字段，
     * 客户端改本机时间伪造不了考勤。
     *
     * <p>只放行在职员工：离职/辞退的人打卡没有业务意义，而且会让当天的统计里凭空多出一行。
     */
    @Transactional(rollbackFor = Exception.class)
    public AttendancePunchVO punch(AttendancePunchDTO dto) {
        String workNo = dto.getWorkNo().trim();
        Employee employee = employeeMapper.selectOne(new LambdaQueryWrapper<Employee>()
                .eq(Employee::getWorkNo, workNo)
                .last("limit 1"));
        if (employee == null) {
            throw new BizException("工号 " + workNo + " 不存在，无法打卡");
        }
        if (!EmployeeStatusEnum.isActive(employee.getStatus())) {
            throw new BizException("员工「" + employee.getName() + "」已"
                    + EmployeeStatusEnum.descriptionOf(employee.getStatus()) + "，无法打卡");
        }

        AttendanceRecord record = new AttendanceRecord();
        record.setWorkNo(workNo);
        record.setLocation(trimToNull(dto.getLocation()));
        record.setPunchTime(System.currentTimeMillis());
        record.setStatus(AttendanceRecordStatusEnum.VALID.getValue());
        attendanceRecordMapper.insert(record);

        log.info("[考勤] 打卡成功 -> workNo={}, name={}, punchTime={}, location={}",
                workNo, employee.getName(), record.getPunchTime(), record.getLocation());
        return toPunchVO(record, employee.getName());
    }

    /* ------------------------------------------------------------ 统计 */

    /**
     * 统计指定日期全体在职员工的考勤，返回写入的行数。
     *
     * <p>覆盖范围是**全部在职员工**而不只是当天打过卡的人：没打卡的人同样要占一行
     * （{@link AttendanceStatusEnum#ABSENT}），否则「谁缺勤」这件事根本查不出来——
     * 少一行和没这个人无法区分。
     *
     * <p>幂等：同一 {@code (work_no, day)} 重复执行只会覆盖，不会插出第二行，
     * 所以可以放心重跑（多实例同时触发、人工补算、改完上下班时间重算）。
     *
     * <p>整天在一个事务里：几百人的规模下代价可忽略，换来的是要么全部算对、
     * 要么一行都不写，不会留下算了一半的中间状态。
     */
    @Transactional(rollbackFor = Exception.class)
    public int statisticsFor(LocalDate day) {
        ZoneId zone = properties.zoneId();
        // 归属哪一天按配置时区算：容器默认时区多半是 UTC，用系统默认值会把早 8 点的卡算到前一天
        long dayStart = day.atStartOfDay(zone).toInstant().toEpochMilli();
        long dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
        long workStart = day.atTime(properties.getWorkStart()).atZone(zone).toInstant().toEpochMilli();
        long workEnd = day.atTime(properties.getWorkEnd()).atZone(zone).toInstant().toEpochMilli();

        List<Employee> employees = employeeMapper.selectList(new LambdaQueryWrapper<Employee>()
                .in(Employee::getStatus, EmployeeStatusEnum.TRIAL.getStatus(), EmployeeStatusEnum.FORMAL.getStatus())
                .orderByAsc(Employee::getId));
        if (employees.isEmpty()) {
            log.info("[考勤统计] {} 无在职员工，跳过", day);
            return 0;
        }

        // 先一次把全天有效打卡捞出来再按工号分组，而不是逐个员工查一次：
        // 后者在几百人规模下就是一串 N+1 查询
        Map<String, List<AttendanceRecord>> recordsByWorkNo = attendanceRecordMapper
                .selectList(new LambdaQueryWrapper<AttendanceRecord>()
                        .eq(AttendanceRecord::getStatus, AttendanceRecordStatusEnum.VALID.getValue())
                        .ge(AttendanceRecord::getPunchTime, dayStart)
                        .lt(AttendanceRecord::getPunchTime, dayEnd))
                .stream()
                .collect(Collectors.groupingBy(AttendanceRecord::getWorkNo));

        int dayCode = toDayCode(day);
        for (Employee employee : employees) {
            attendanceMapper.upsert(buildStatistics(
                    employee.getWorkNo(), dayCode, recordsByWorkNo.get(employee.getWorkNo()), workStart, workEnd));
        }

        log.info("[考勤统计] {} 完成 -> 在职 {} 人，当天有打卡 {} 人，写入 {} 行",
                day, employees.size(), recordsByWorkNo.size(), employees.size());
        return employees.size();
    }

    /**
     * 把一个人在一天内的全部打卡归并成一行统计。
     *
     * <p>缺卡时 {@code first}/{@code last} 留 null 而不是填 0：0 是 1970-01-01，
     * 一旦混进时间字段，任何按时间排序或做差的地方都会算出荒谬结果。
     */
    private static Attendance buildStatistics(String workNo, int dayCode, List<AttendanceRecord> records,
                                             long workStart, long workEnd) {
        Attendance attendance = new Attendance();
        attendance.setWorkNo(workNo);
        attendance.setDay(dayCode);

        if (records == null || records.isEmpty()) {
            attendance.setStatus(AttendanceStatusEnum.ABSENT.getValue());
            attendance.setExtra(0.0D);
            return attendance;
        }

        long first = Long.MAX_VALUE;
        long last = Long.MIN_VALUE;
        for (AttendanceRecord record : records) {
            long punchTime = record.getPunchTime();
            first = Math.min(first, punchTime);
            last = Math.max(last, punchTime);
        }

        // 跨天加班（比如干到次日凌晨 1 点）会落到第二天的窗口里，被算成第二天的最早打卡。
        // 这里不特殊处理：要正确处理得引入班次（shift）概念，而当前表结构里没有这个维度。
        attendance.setFirst(first);
        attendance.setLast(last);
        attendance.setStatus(AttendanceStatusEnum.of(first > workStart, last < workEnd).getValue());
        attendance.setExtra(round2(last > workEnd ? (last - workEnd) / MILLIS_PER_HOUR : 0.0D));
        return attendance;
    }

    /** yyyyMMdd 整数编码，与 {@code attendance.day} 列对齐。 */
    public static int toDayCode(LocalDate day) {
        return day.getYear() * 10000 + day.getMonthValue() * 100 + day.getDayOfMonth();
    }

    /* ------------------------------------------------------------ 装配 */

    private AttendancePunchVO toPunchVO(AttendanceRecord record, String employeeName) {
        AttendancePunchVO vo = new AttendancePunchVO();
        vo.setId(record.getId());
        vo.setWorkNo(record.getWorkNo());
        vo.setEmployeeName(employeeName);
        vo.setPunchTime(LocalDateTime.ofInstant(
                Instant.ofEpochMilli(record.getPunchTime()), properties.zoneId()));
        vo.setLocation(record.getLocation());
        return vo;
    }

    /** 加班时长保留两位小数，与 {@code attendance.extra numeric(10,2)} 的精度对齐。 */
    private static double round2(double value) {
        return Math.round(value * 100.0D) / 100.0D;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

}
