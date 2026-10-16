package com.gangtise.backend.infrastructure.tasks;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gangtise.backend.application.service.AttendanceService;
import com.gangtise.backend.domain.aggregate.Attendance;
import com.gangtise.backend.domain.aggregate.Employee;
import com.gangtise.backend.domain.aggregate.entity.AttendanceRecord;
import com.gangtise.backend.infrastructure.config.AttendanceProperties;
import com.gangtise.backend.infrastructure.enums.AttendanceRecordStatusEnum;
import com.gangtise.backend.infrastructure.enums.AttendanceStatusEnum;
import com.gangtise.backend.infrastructure.enums.EmployeeStatusEnum;
import com.gangtise.backend.infrastructure.repository.mapper.AttendanceMapper;
import com.gangtise.backend.infrastructure.repository.mapper.AttendanceRecordMapper;
import com.gangtise.backend.infrastructure.repository.mapper.EmployeeMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 考勤统计定时任务回归测试：最早/最晚打卡判定、迟到早退状态、加班时长、幂等、统计范围。
 *
 * <p>直接调 {@link AttendanceService#statisticsFor} 而不是等 cron 触发：日期是入参，
 * 既能精确构造「昨天」，也能覆盖任意历史日期补算——这也是任务只做「挑日子」、
 * 规则全放在服务层的原因。
 *
 * <p>时间基准固定为 2027-01-04（周一），上下班时间固定 09:00 / 18:00，
 * 与跑测试的机器时区、当前时间都无关。用例自建数据并随事务回滚。
 */
@SpringBootTest
@Transactional
class AttendanceStatisticsTaskTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final LocalDate DAY = LocalDate.of(2027, 1, 4);
    private static final LocalTime WORK_START = LocalTime.of(9, 0);
    private static final LocalTime WORK_END = LocalTime.of(18, 0);

    @Autowired
    private EmployeeMapper employeeMapper;

    @Autowired
    private AttendanceRecordMapper attendanceRecordMapper;

    @Autowired
    private AttendanceMapper attendanceMapper;

    /* ------------------------------------------------------------ 最早 / 最晚判定 */

    @Test
    @DisplayName("最早与最晚打卡：多次打卡取最小与最大，与插入顺序无关")
    void picksEarliestAndLatestPunch() {
        String workNo = newEmployee("IT-STAT-MINMAX", "统计测试-早晚", EmployeeStatusEnum.FORMAL);

        // 故意乱序插入，断言结果与插入顺序无关
        punch(workNo, at(12, 0), AttendanceRecordStatusEnum.VALID);
        punch(workNo, at(18, 30), AttendanceRecordStatusEnum.VALID);
        punch(workNo, at(8, 50), AttendanceRecordStatusEnum.VALID);
        punch(workNo, at(9, 30), AttendanceRecordStatusEnum.VALID);

        service().statisticsFor(DAY);

        Attendance record = stats(workNo);
        assertNotNull(record, "在职员工每天都应有一行统计");
        assertEquals(epoch(at(8, 50)), record.getFirst(), "first 应取当天最早打卡");
        assertEquals(epoch(at(18, 30)), record.getLast(), "last 应取当天最晚打卡");
    }

    @Test
    @DisplayName("当天零点整的打卡算作当天最早")
    void midnightBelongsToItsOwnDay() {
        String workNo = newEmployee("IT-STAT-MIDNIGHT", "统计测试-零点", EmployeeStatusEnum.FORMAL);
        punch(workNo, at(0, 0), AttendanceRecordStatusEnum.VALID);
        punch(workNo, at(18, 0), AttendanceRecordStatusEnum.VALID);

        service().statisticsFor(DAY);

        assertEquals(epoch(at(0, 0)), stats(workNo).getFirst());
    }

    @Test
    @DisplayName("前一天 23:59:59 与次日 00:00:00 都不计入当天")
    void windowIsHalfOpen() {
        String workNo = newEmployee("IT-STAT-WINDOW", "统计测试-窗口", EmployeeStatusEnum.FORMAL);
        punch(workNo, DAY.minusDays(1).atTime(23, 59, 59), AttendanceRecordStatusEnum.VALID);
        punch(workNo, DAY.plusDays(1).atTime(0, 0, 0), AttendanceRecordStatusEnum.VALID);

        service().statisticsFor(DAY);

        Attendance record = stats(workNo);
        assertEquals(AttendanceStatusEnum.ABSENT.getValue(), record.getStatus(),
                "窗口是 [当天00:00, 次日00:00)，两侧边界的打卡都不该被算进来");
        assertNull(record.getFirst());
        assertNull(record.getLast());
    }

    /* ------------------------------------------------------------ 状态与加班 */

    @Test
    @DisplayName("准时上下班记正常，不产生加班")
    void normal() {
        String workNo = newEmployee("IT-STAT-NORMAL", "统计测试-正常", EmployeeStatusEnum.FORMAL);
        punch(workNo, at(8, 50), AttendanceRecordStatusEnum.VALID);
        // 恰好 18:00 打卡不算早退，也不产生加班
        punch(workNo, at(18, 0), AttendanceRecordStatusEnum.VALID);

        service().statisticsFor(DAY);

        Attendance record = stats(workNo);
        assertEquals(AttendanceStatusEnum.NORMAL.getValue(), record.getStatus());
        assertEquals(0.0D, record.getExtra().doubleValue(), 0.001D, "18:00 整点下班不算加班");
    }

    @Test
    @DisplayName("迟到：最早打卡晚于上班时间，晚走的部分算加班")
    void lateWithOvertime() {
        String workNo = newEmployee("IT-STAT-LATE", "统计测试-迟到", EmployeeStatusEnum.FORMAL);
        punch(workNo, at(9, 30), AttendanceRecordStatusEnum.VALID);
        punch(workNo, at(19, 15), AttendanceRecordStatusEnum.VALID);

        service().statisticsFor(DAY);

        Attendance record = stats(workNo);
        assertEquals(AttendanceStatusEnum.LATE.getValue(), record.getStatus());
        assertEquals(1.25D, record.getExtra().doubleValue(), 0.001D, "19:15 下班应算 1.25 小时加班");
    }

    @Test
    @DisplayName("早退：最晚打卡早于下班时间，不产生加班")
    void earlyLeave() {
        String workNo = newEmployee("IT-STAT-EARLY", "统计测试-早退", EmployeeStatusEnum.FORMAL);
        punch(workNo, at(8, 50), AttendanceRecordStatusEnum.VALID);
        punch(workNo, at(17, 30), AttendanceRecordStatusEnum.VALID);

        service().statisticsFor(DAY);

        Attendance record = stats(workNo);
        assertEquals(AttendanceStatusEnum.EARLY_LEAVE.getValue(), record.getStatus());
        assertEquals(0.0D, record.getExtra().doubleValue(), 0.001D, "早退不应算加班");
    }

    @Test
    @DisplayName("迟到且早退：两头都没踩上")
    void lateAndEarlyLeave() {
        String workNo = newEmployee("IT-STAT-BOTH", "统计测试-双向", EmployeeStatusEnum.FORMAL);
        punch(workNo, at(9, 10), AttendanceRecordStatusEnum.VALID);
        punch(workNo, at(17, 0), AttendanceRecordStatusEnum.VALID);

        service().statisticsFor(DAY);

        assertEquals(AttendanceStatusEnum.LATE_AND_EARLY_LEAVE.getValue(), stats(workNo).getStatus());
    }

    @Test
    @DisplayName("当天没打卡记缺卡，时间字段留空而不是填 0")
    void absent() {
        String workNo = newEmployee("IT-STAT-ABSENT", "统计测试-缺卡", EmployeeStatusEnum.FORMAL);

        service().statisticsFor(DAY);

        Attendance record = stats(workNo);
        assertNotNull(record, "没打卡的员工也要占一行，否则「缺勤」和「没这个人」无法区分");
        assertEquals(AttendanceStatusEnum.ABSENT.getValue(), record.getStatus());
        assertNull(record.getFirst(), "缺卡时 first 应为 null，填 0 会变成 1970-01-01");
        assertNull(record.getLast());
        assertEquals(0.0D, record.getExtra().doubleValue(), 0.001D);
    }

    @Test
    @DisplayName("只有中午一次打卡也算迟到且早退")
    void singleMiddayPunchCountsBoth() {
        String workNo = newEmployee("IT-STAT-ONE", "统计测试-单次", EmployeeStatusEnum.FORMAL);
        punch(workNo, at(12, 0), AttendanceRecordStatusEnum.VALID);

        service().statisticsFor(DAY);

        Attendance record = stats(workNo);
        assertEquals(epoch(at(12, 0)), record.getFirst());
        assertEquals(epoch(at(12, 0)), record.getLast(), "只有一次打卡时最早与最晚是同一个时刻");
        assertEquals(AttendanceStatusEnum.LATE_AND_EARLY_LEAVE.getValue(), record.getStatus());
    }

    /* ------------------------------------------------------------ 记录有效性 */

    @Test
    @DisplayName("无效打卡记录不参与统计")
    void invalidRecordsAreExcluded() {
        String workNo = newEmployee("IT-STAT-INVALID", "统计测试-无效", EmployeeStatusEnum.FORMAL);
        // 无效记录的时间落在正常区间内，若被算进来就会变成「正常」
        punch(workNo, at(9, 0), AttendanceRecordStatusEnum.INVALID);
        punch(workNo, at(18, 0), AttendanceRecordStatusEnum.INVALID);

        service().statisticsFor(DAY);

        Attendance record = stats(workNo);
        assertEquals(AttendanceStatusEnum.ABSENT.getValue(), record.getStatus(),
                "整天的打卡都是无效记录时应记缺卡");
        assertNull(record.getFirst());
    }

    @Test
    @DisplayName("有效与无效混在一起时只按有效记录判定")
    void mixedValidityUsesOnlyValid() {
        String workNo = newEmployee("IT-STAT-MIX", "统计测试-混合", EmployeeStatusEnum.FORMAL);
        punch(workNo, at(8, 0), AttendanceRecordStatusEnum.INVALID);
        punch(workNo, at(9, 30), AttendanceRecordStatusEnum.VALID);
        punch(workNo, at(23, 0), AttendanceRecordStatusEnum.INVALID);
        punch(workNo, at(18, 30), AttendanceRecordStatusEnum.VALID);

        service().statisticsFor(DAY);

        Attendance record = stats(workNo);
        assertEquals(epoch(at(9, 30)), record.getFirst(), "08:00 那条是无效记录，不应成为最早打卡");
        assertEquals(epoch(at(18, 30)), record.getLast(), "23:00 那条是无效记录，不应成为最晚打卡");
        assertEquals(AttendanceStatusEnum.LATE.getValue(), record.getStatus());
    }

    /* ------------------------------------------------------------ 统计范围与幂等 */

    @Test
    @DisplayName("只统计在职员工，离职与辞退不产生统计行")
    void onlyActiveEmployees() {
        String resigned = newEmployee("IT-STAT-RESIGN", "统计测试-离职", EmployeeStatusEnum.RESIGN);
        String dismissed = newEmployee("IT-STAT-DISMISS", "统计测试-辞退", EmployeeStatusEnum.DISMISS);
        String formal = newEmployee("IT-STAT-FORMAL", "统计测试-在职", EmployeeStatusEnum.FORMAL);
        punch(resigned, at(9, 0), AttendanceRecordStatusEnum.VALID);
        punch(dismissed, at(9, 0), AttendanceRecordStatusEnum.VALID);
        punch(formal, at(9, 0), AttendanceRecordStatusEnum.VALID);

        service().statisticsFor(DAY);

        assertNull(stats(resigned), "离职员工不应产生统计行");
        assertNull(stats(dismissed), "辞退员工不应产生统计行");
        assertNotNull(stats(formal));
    }

    @Test
    @DisplayName("重复执行幂等：同一天不会插出第二行，且按最新明细覆盖")
    void rerunIsIdempotent() {
        String workNo = newEmployee("IT-STAT-RERUN", "统计测试-重跑", EmployeeStatusEnum.FORMAL);
        // 09:30 迟到 + 18:00 准点下班：首轮状态是「迟到」且不产生加班
        punch(workNo, at(9, 30), AttendanceRecordStatusEnum.VALID);
        punch(workNo, at(18, 0), AttendanceRecordStatusEnum.VALID);

        AttendanceService service = service();
        service.statisticsFor(DAY);
        Attendance firstRun = stats(workNo);
        assertEquals(AttendanceStatusEnum.LATE.getValue(), firstRun.getStatus());
        assertEquals(0.0D, firstRun.getExtra().doubleValue(), 0.001D);
        // 必须是 Long：Integer 与 Long 比相等会走 assertEquals(Object, Object)，
        // 而 Integer.equals(Long) 恒为 false，断言会假失败
        Long firstRunId = firstRun.getId();

        // 补上一条下班打卡后重跑：应覆盖同一行而不是新增一行
        punch(workNo, at(18, 30), AttendanceRecordStatusEnum.VALID);
        service.statisticsFor(DAY);

        List<Attendance> rows = rowsOf(workNo);
        assertEquals(1, rows.size(), "同一 (work_no, day) 重跑只能有一行，靠唯一索引 + upsert 保证");
        Attendance secondRun = rows.get(0);
        assertEquals(firstRunId, secondRun.getId(), "覆盖应落在原来那行上");
        assertEquals(epoch(at(9, 30)), secondRun.getFirst(), "最早打卡不应被后写入的时间覆盖");
        assertEquals(epoch(at(18, 30)), secondRun.getLast());
        assertEquals(0.5D, secondRun.getExtra().doubleValue(), 0.001D, "重跑应把新产生的加班算进来");
    }

    /* ------------------------------------------------------------ 定时任务入口 */

    @Test
    @DisplayName("定时任务汇总的是昨天，且开关关闭时不写库")
    void statisticsYesterdayHonoursSwitch() {
        LocalDate yesterday = LocalDate.now(ZONE).minusDays(1);
        String workNo = newEmployee("IT-STAT-CRON", "统计测试-定时", EmployeeStatusEnum.FORMAL);
        punch(workNo, yesterday.atTime(9, 30), AttendanceRecordStatusEnum.VALID);
        punch(workNo, yesterday.atTime(18, 30), AttendanceRecordStatusEnum.VALID);

        // 开关关闭：一行都不该写
        new AttendanceStatisticsTask(service(), properties(false)).statisticsYesterday();
        assertNull(statsOn(workNo, yesterday), "开关关闭时不应该写统计行");

        // 开关打开：走完整入口，统计的是「昨天」这一天
        new AttendanceStatisticsTask(service(), properties(true)).statisticsYesterday();
        Attendance record = statsOn(workNo, yesterday);
        assertNotNull(record, "定时任务应汇总前一天");
        assertEquals(AttendanceService.toDayCode(yesterday), record.getDay());
        assertEquals(epoch(yesterday.atTime(9, 30)), record.getFirst());
        assertEquals(epoch(yesterday.atTime(18, 30)), record.getLast());
        assertEquals(0.5D, record.getExtra().doubleValue(), 0.001D);
    }

    /* ------------------------------------------------------------------ 工具 */

    /** 造一个员工，返回工号。 */
    private String newEmployee(String workNo, String name, EmployeeStatusEnum status) {
        Employee employee = new Employee();
        employee.setWorkNo(workNo);
        employee.setName(name);
        employee.setStatus(status.getStatus());
        employeeMapper.insert(employee);
        assertNotNull(employee.getId());
        return workNo;
    }

    /** 落一条打卡记录。 */
    private void punch(String workNo, LocalDateTime when, AttendanceRecordStatusEnum status) {
        AttendanceRecord record = new AttendanceRecord();
        record.setWorkNo(workNo);
        record.setPunchTime(epoch(when));
        record.setStatus(status.getValue());
        attendanceRecordMapper.insert(record);
    }

    /** 用固定上下班时间（09:00 / 18:00）构造服务，避免污染其它用例的配置。 */
    private AttendanceService service() {
        return service(WORK_START, WORK_END);
    }

    private AttendanceService service(LocalTime workStart, LocalTime workEnd) {
        AttendanceProperties properties = new AttendanceProperties();
        properties.setWorkStart(workStart);
        properties.setWorkEnd(workEnd);
        properties.setZone(ZONE.getId());
        return new AttendanceService(attendanceRecordMapper, attendanceMapper, employeeMapper, properties);
    }

    private AttendanceProperties properties(boolean enabled) {
        AttendanceProperties properties = new AttendanceProperties();
        properties.setEnabled(enabled);
        properties.setWorkStart(WORK_START);
        properties.setWorkEnd(WORK_END);
        properties.setZone(ZONE.getId());
        return properties;
    }

    private static LocalDateTime at(int hour, int minute) {
        return DAY.atTime(hour, minute);
    }

    private static long epoch(LocalDateTime when) {
        return when.atZone(ZONE).toInstant().toEpochMilli();
    }

    private Attendance stats(String workNo) {
        return statsOn(workNo, DAY);
    }

    private Attendance statsOn(String workNo, LocalDate day) {
        return attendanceMapper.selectOne(new LambdaQueryWrapper<Attendance>()
                .eq(Attendance::getWorkNo, workNo)
                .eq(Attendance::getDay, AttendanceService.toDayCode(day))
                .last("limit 1"));
    }

    private List<Attendance> rowsOf(String workNo) {
        return attendanceMapper.selectList(new LambdaQueryWrapper<Attendance>()
                .eq(Attendance::getWorkNo, workNo)
                .orderByAsc(Attendance::getId));
    }

    @Test
    @DisplayName("yyyyMMdd 编码：个位月日要补零")
    void dayCodePadsMonthAndDay() {
        assertEquals(20270104, AttendanceService.toDayCode(LocalDate.of(2027, 1, 4)));
        assertEquals(20271231, AttendanceService.toDayCode(LocalDate.of(2027, 12, 31)));
        assertTrue(AttendanceService.toDayCode(LocalDate.of(2027, 1, 9)) < AttendanceService.toDayCode(
                LocalDate.of(2027, 1, 10)), "补零后同月内按数值比较仍与日期先后一致");
    }

}
